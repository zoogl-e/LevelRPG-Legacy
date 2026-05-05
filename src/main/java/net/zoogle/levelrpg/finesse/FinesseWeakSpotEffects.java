package net.zoogle.levelrpg.finesse;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FinesseWeakSpotEffects {
    private static final int ENDERMAN_ANCHOR_MIN_TICKS = 60;
    private static final int ENDERMAN_ANCHOR_MAX_TICKS = 110;
    private static final int ENDERMAN_FOLLOW_TELEPORT_ATTEMPTS = 24;
    private static final int ENDERMAN_ROUGH_RELOCATE_ATTEMPTS = 48;
    private static final double ENDERMAN_TELEPORT_RANGE = 32.0D;
    private static final double FOLLOW_THROUGH_PLAYER_OFFSET = 2.7D;
    private static final double FOLLOW_THROUGH_MAX_LINK_RANGE = 48.0D;
    private static final double FOLLOW_THROUGH_MAX_EXIT_RANGE = 192.0D;
    private static final int CHORUS_STYLE_PLAYER_ATTEMPTS = 20;
    private static final double CHORUS_STYLE_PLAYER_SPREAD = 12.0D;
    private static final float ENDERMAN_PANIC_HEALTH_FRACTION = 0.35F;
    private static final int ENDERMAN_PANIC_TELEPORT_COUNT = 3;
    private static final int ENDERMAN_PANIC_COOLDOWN_TICKS = 100;
    private static final float ENDERMAN_SUPER_PANIC_HEALTH_FRACTION = 0.16F;
    private static final float ENDERMAN_SUPER_PANIC_CHANCE = 0.42F;
    private static final int ENDERMAN_SUPER_PANIC_MIN_TELEPORTS = 8;
    private static final int ENDERMAN_SUPER_PANIC_MAX_TELEPORTS = 13;
    private static final double ENDERMAN_SUPER_PANIC_RANGE = 96.0D;
    private static final int ENDERMAN_SUPER_PANIC_VERTICAL_SPREAD = 54;
    private static final int ENDERMAN_SUPER_PANIC_ATTEMPTS = 38;
    private static final int ENDERMAN_SUPER_PANIC_ROUGH_ATTEMPTS = 96;
    private static final int ENDERMAN_CONFUSED_MIN_TICKS = 45;
    private static final int ENDERMAN_CONFUSED_MAX_TICKS = 75;
    private static final int ENDERMAN_CONFUSED_TARGET_LOCK_TICKS = 35;
    private static final float CREEPER_IMPLOSION_MIN_SWELL = 0.25F;
    private static final int CREEPER_IMPLOSION_DELAY_TICKS = 35;
    private static final Map<UUID, EndermanAnchorState> ANCHORED_ENDERMEN = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LINKED_ENDERMEN = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingFollowThrough> PENDING_FOLLOW_THROUGH = new ConcurrentHashMap<>();
    private static final Map<UUID, PanicBurst> ENDERMAN_PANIC = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ENDERMAN_LAST_PANIC_TICK = new ConcurrentHashMap<>();
    private static final Set<UUID> ENDERMAN_SUPER_PANIC_USED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, EndermanConfusionState> CONFUSED_ENDERMEN = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingCreeperImplosion> PENDING_CREEPER_IMPLOSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> FORCED_ENDERMAN_TELEPORTS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, DisarmedWeapon> DISARMED_WEAPONS = new ConcurrentHashMap<>();

    private record DisarmedWeapon(ItemStack weapon, float dropChance) {}

    public FinesseWeakSpotEffects() {
    }

    public static void onWeakSpotStrike(ServerPlayer attacker, LivingEntity target, FinesseUnarmedCombat.WeakSpotZone zone) {
        if (attacker == null || target == null || target.level().isClientSide()) {
            return;
        }
        switch (zone) {
            case HEAD -> applyHeadWeakSpot(attacker, target);
            case CHEST -> applyChestWeakSpot(attacker, target);
            case LEGS -> applyLegWeakSpot(attacker, target);
        }
    }

    private static void applyHeadWeakSpot(ServerPlayer attacker, LivingEntity target) {
        if (target instanceof EnderMan enderMan) {
            anchorEnderman(attacker, enderMan);
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, false, true, true));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void applyChestWeakSpot(ServerPlayer attacker, LivingEntity target) {
        if (target instanceof Creeper creeper && isCreeperInImplosionWindow(creeper)) {
            primeCreeperImplosion(attacker, creeper);
            return;
        }
        if (target instanceof AbstractSkeleton skeleton) {
            disarmSkeleton(skeleton);
        }
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0, false, true, true));
        Vec3 away = target.position().subtract(attacker.position());
        if (away.lengthSqr() > 1.0E-6D && !FinesseTechniqueActivations.isJuggleTarget(target.getId())) {
            Vec3 push = away.normalize().scale(0.42D);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.08D, push.z));
            target.hurtMarked = true;
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        playZoneFeedback(attacker, target, ParticleTypes.POOF, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.72F, 1.35F);
    }

    private static void disarmSkeleton(AbstractSkeleton skeleton) {
        ItemStack weapon = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
        if (weapon.isEmpty()) {
            return;
        }
        DISARMED_WEAPONS.put(skeleton.getUUID(), new DisarmedWeapon(weapon.copy(), 0.085F));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        skeleton.reassessWeaponGoal();
        skeleton.level().playSound(null, skeleton.getX(), skeleton.getY(), skeleton.getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.85F, 1.25F);
    }

    private static void primeCreeperImplosion(ServerPlayer attacker, Creeper creeper) {
        if (!(creeper.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (PENDING_CREEPER_IMPLOSIONS.containsKey(creeper.getUUID())) {
            return;
        }
        int triggerTick = creeper.tickCount + CREEPER_IMPLOSION_DELAY_TICKS;
        PENDING_CREEPER_IMPLOSIONS.put(creeper.getUUID(), new PendingCreeperImplosion(attacker.getUUID(), triggerTick, creeper.isPowered()));
        creeper.setSwellDir(-1);
        creeper.getNavigation().stop();
        serverLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                creeper.getX(),
                creeper.getY() + creeper.getBbHeight() * 0.55D,
                creeper.getZ(),
                28,
                creeper.getBbWidth() * 0.35D,
                creeper.getBbHeight() * 0.22D,
                creeper.getBbWidth() * 0.35D,
                0.06D
        );
        serverLevel.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.15F, 1.75F);
    }

    private static void triggerCreeperImplosion(ServerLevel serverLevel, ServerPlayer attacker, Creeper creeper, boolean powered) {
        double radius = powered ? 7.0D : 5.0D;
        Vec3 center = creeper.position().add(0.0D, creeper.getBbHeight() * 0.5D, 0.0D);
        serverLevel.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x,
                center.y,
                center.z,
                powered ? 3 : 1,
                0.15D,
                0.15D,
                0.15D,
                0.0D
        );
        serverLevel.sendParticles(
                ParticleTypes.SONIC_BOOM,
                center.x,
                center.y,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
        serverLevel.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.45F, 1.55F);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, creeper.getBoundingBox().inflate(radius), LivingEntity::isAlive)) {
            if (entity == creeper) {
                continue;
            }
            Vec3 away = entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D).subtract(center);
            double dist = Math.max(0.35D, away.length());
            if (dist > radius) {
                continue;
            }
            double strength = (1.0D - dist / radius) * (powered ? 3.0D : 2.0D);
            Vec3 push = away.normalize().scale(strength);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push.x, 0.32D + strength * 0.16D, push.z));
            entity.hurtMarked = true;
            
            float damage = (float) ((1.0D - dist / radius) * (powered ? 40.0D : 20.0D));
            if (damage > 0.0F && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                entity.hurt(attacker.damageSources().explosion(creeper, attacker), damage);
            }
        }
        creeper.setSwellDir(-1);
        creeper.hurt(attacker.damageSources().explosion(creeper, attacker), Math.max(1000.0F, creeper.getMaxHealth() * 4.0F));
    }

    public static boolean isCreeperInImplosionWindow(Creeper creeper) {
        return creeper != null
                && creeper.isAlive()
                && (creeper.isIgnited() || creeper.getSwellDir() > 0)
                && creeper.getSwelling(0.0F) >= CREEPER_IMPLOSION_MIN_SWELL;
    }

    private static void applyLegWeakSpot(ServerPlayer attacker, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, 0, false, true, true));
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(movement.x * 0.35D, Math.min(movement.y, 0.0D), movement.z * 0.35D);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        playZoneFeedback(attacker, target, ParticleTypes.DUST_PLUME, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.82F);
    }

    private static void playZoneFeedback(ServerPlayer attacker, LivingEntity target, net.minecraft.core.particles.ParticleOptions particle, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    particle,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.45D,
                    target.getZ(),
                    10,
                    target.getBbWidth() * 0.25D,
                    target.getBbHeight() * 0.18D,
                    target.getBbWidth() * 0.25D,
                    0.04D
            );
        }
    }

    private static void anchorEnderman(ServerPlayer attacker, EnderMan enderMan) {
        EndermanAnchorState previous = ANCHORED_ENDERMEN.get(enderMan.getUUID());
        int anchorTicks = ENDERMAN_ANCHOR_MIN_TICKS + enderMan.getRandom().nextInt(ENDERMAN_ANCHOR_MAX_TICKS - ENDERMAN_ANCHOR_MIN_TICKS + 1);
        int expiresAt = enderMan.tickCount + anchorTicks;
        LINKED_ENDERMEN.put(enderMan.getUUID(), attacker.getUUID());
        if (previous != null && enderMan.tickCount <= previous.expiresAtTick()) {
            ANCHORED_ENDERMEN.put(
                    enderMan.getUUID(),
                    new EndermanAnchorState(expiresAt, attacker.getUUID())
            );
            return;
        }

        ANCHORED_ENDERMEN.put(
                enderMan.getUUID(),
                new EndermanAnchorState(expiresAt, attacker.getUUID())
        );
        if (enderMan.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    enderMan.getX(),
                    enderMan.getY() + enderMan.getBbHeight() * 0.62D,
                    enderMan.getZ(),
                    24,
                    enderMan.getBbWidth() * 0.42D,
                    enderMan.getBbHeight() * 0.24D,
                    enderMan.getBbWidth() * 0.42D,
                    0.04D
            );
        }
        enderMan.level().playSound(
                null,
                enderMan.getX(),
                enderMan.getY(),
                enderMan.getZ(),
                SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                SoundSource.HOSTILE,
                0.75F,
                1.65F + attacker.getRandom().nextFloat() * 0.12F
        );
    }

    @SubscribeEvent
    public void onEndermanDamagedPost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof EnderMan enderMan) || enderMan.level().isClientSide()) {
            return;
        }
        if (event.getNewDamage() <= 0.0F || !enderMan.isAlive()) {
            return;
        }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (attacker != null && CONFUSED_ENDERMEN.remove(enderMan.getUUID()) != null) {
            enrageEndermanAt(enderMan, attacker, false);
        }
        float maxHealth = enderMan.getMaxHealth();
        if (maxHealth <= 1.0E-4F) {
            return;
        }
        float threshold = maxHealth * ENDERMAN_PANIC_HEALTH_FRACTION;
        float superThreshold = maxHealth * ENDERMAN_SUPER_PANIC_HEALTH_FRACTION;
        float afterHealth = enderMan.getHealth();
        float beforeHealth = afterHealth + event.getNewDamage();
        if (beforeHealth > superThreshold && afterHealth <= superThreshold && afterHealth > 0.5F && tryStartSuperPanicBurst(enderMan)) {
            return;
        }
        if (beforeHealth > threshold && afterHealth <= threshold && afterHealth > 0.5F) {
            tryStartPanicBurst(enderMan);
        }
    }

    @SubscribeEvent
    public void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (!(event.getEntity() instanceof EnderMan enderMan)) {
            return;
        }
        if (FORCED_ENDERMAN_TELEPORTS.contains(enderMan.getUUID())) {
            return;
        }
        EndermanAnchorState anchor = ANCHORED_ENDERMEN.get(enderMan.getUUID());
        if (anchor != null) {
            if (enderMan.tickCount > anchor.expiresAtTick()) {
                ANCHORED_ENDERMEN.remove(enderMan.getUUID());
            } else {
                event.setCanceled(true);
                if (enderMan.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.PORTAL,
                            enderMan.getX(),
                            enderMan.getY() + enderMan.getBbHeight() * 0.55D,
                            enderMan.getZ(),
                            12,
                            enderMan.getBbWidth() * 0.24D,
                            enderMan.getBbHeight() * 0.18D,
                            enderMan.getBbWidth() * 0.24D,
                            0.02D
                    );
                }
                enderMan.level().playSound(
                        null,
                        enderMan.getX(),
                        enderMan.getY(),
                        enderMan.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE,
                        0.45F,
                        0.55F
                );
                return;
            }
        }
        followNaturalTeleport(event, enderMan);
    }

    @SubscribeEvent
    public void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Creeper creeper && creeper.level() instanceof ServerLevel serverLevel) {
            tickPendingCreeperImplosion(serverLevel, creeper);
        }
        if (!(event.getEntity() instanceof EnderMan enderMan) || !(enderMan.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        tickEndermanConfusion(serverLevel, enderMan);
        tickEndermanPanic(serverLevel, enderMan);
        PendingFollowThrough pending = PENDING_FOLLOW_THROUGH.get(enderMan.getUUID());
        if (pending != null) {
            if (enderMan.tickCount > pending.expiresAtTick()) {
                PENDING_FOLLOW_THROUGH.remove(enderMan.getUUID());
            } else if (enderMan.distanceToSqr(pending.startX(), pending.startY(), pending.startZ()) > 1.0D) {
                PENDING_FOLLOW_THROUGH.remove(enderMan.getUUID());
                completeFollowThrough(serverLevel, enderMan, pending.attackerId());
            }
        }
        EndermanAnchorState anchor = ANCHORED_ENDERMEN.get(enderMan.getUUID());
        if (anchor == null || enderMan.tickCount <= anchor.expiresAtTick()) {
            return;
        }
        ANCHORED_ENDERMEN.remove(enderMan.getUUID());
        triggerFollowThroughTeleport(serverLevel, enderMan, anchor);
    }

    private static void tickPendingCreeperImplosion(ServerLevel serverLevel, Creeper creeper) {
        PendingCreeperImplosion pending = PENDING_CREEPER_IMPLOSIONS.get(creeper.getUUID());
        if (pending == null) {
            return;
        }
        if (!creeper.isAlive()) {
            PENDING_CREEPER_IMPLOSIONS.remove(creeper.getUUID());
            return;
        }
        
        int ticksLeft = pending.triggerTick() - creeper.tickCount;
        boolean wantPowered = pending.originallyPowered() || (ticksLeft % 6 < 3);
        if (ticksLeft < 10 && !pending.originallyPowered()) {
            wantPowered = (ticksLeft % 2 == 0);
        }
        if (creeper.isPowered() != wantPowered) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            creeper.saveWithoutId(tag);
            tag.putBoolean("powered", wantPowered);
            creeper.load(tag);
        }

        creeper.setSwellDir(-1);
        creeper.getNavigation().stop();
        
        int particleCount = ticksLeft < 15 ? 4 : 2;
        for (int i = 0; i < particleCount; i++) {
            double rx = (creeper.getRandom().nextDouble() - 0.5D) * 2.5D;
            double ry = (creeper.getRandom().nextDouble() - 0.5D) * 2.5D;
            double rz = (creeper.getRandom().nextDouble() - 0.5D) * 2.5D;
            serverLevel.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    creeper.getX() + rx,
                    creeper.getY() + creeper.getBbHeight() * 0.5D + ry,
                    creeper.getZ() + rz,
                    0,
                    -rx,
                    -ry,
                    -rz,
                    0.08D
            );
        }

        if (creeper.tickCount < pending.triggerTick()) {
            return;
        }
        PENDING_CREEPER_IMPLOSIONS.remove(creeper.getUUID());
        ServerPlayer attacker = serverLevel.getServer().getPlayerList().getPlayer(pending.attackerId());
        if (attacker == null || attacker.isRemoved()) {
            creeper.setSwellDir(-1);
            return;
        }
        triggerCreeperImplosion(serverLevel, attacker, creeper, pending.originallyPowered());
    }

    private static void followNaturalTeleport(EntityTeleportEvent.EnderEntity event, EnderMan enderMan) {
        if (!(enderMan.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID attackerId = LINKED_ENDERMEN.get(enderMan.getUUID());
        if (attackerId == null) {
            return;
        }
        ServerPlayer attacker = serverLevel.getServer().getPlayerList().getPlayer(attackerId);
        if (!canFollowLinkedEnderman(attacker, enderMan)) {
            LINKED_ENDERMEN.remove(enderMan.getUUID());
            return;
        }
        if (attacker.distanceToSqr(enderMan) > FOLLOW_THROUGH_MAX_LINK_RANGE * FOLLOW_THROUGH_MAX_LINK_RANGE) {
            return;
        }
        PENDING_FOLLOW_THROUGH.put(
                enderMan.getUUID(),
                new PendingFollowThrough(attackerId, enderMan.getX(), enderMan.getY(), enderMan.getZ(), enderMan.tickCount + 3)
        );
    }

    private static void triggerFollowThroughTeleport(ServerLevel serverLevel, EnderMan enderMan, EndermanAnchorState anchor) {
        ServerPlayer attacker = serverLevel.getServer().getPlayerList().getPlayer(anchor.attackerId());
        if (attacker == null || attacker.isRemoved() || attacker.isSpectator() || attacker.level() != enderMan.level()) {
            LINKED_ENDERMEN.remove(enderMan.getUUID());
            return;
        }
        if (attacker.distanceToSqr(enderMan) > FOLLOW_THROUGH_MAX_LINK_RANGE * FOLLOW_THROUGH_MAX_LINK_RANGE) {
            return;
        }

        double startX = enderMan.getX();
        double startY = enderMan.getY();
        double startZ = enderMan.getZ();
        if (!tryForcedEndermanTeleport(enderMan)) {
            return;
        }
        teleportLinkedPlayerAlways(serverLevel, attacker, enderMan, enderMan.getX(), enderMan.getY(), enderMan.getZ());
        markEndermanConfusedFromFollow(enderMan, attacker);
        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                startX,
                startY + enderMan.getBbHeight() * 0.55D,
                startZ,
                24,
                enderMan.getBbWidth() * 0.3D,
                enderMan.getBbHeight() * 0.22D,
                enderMan.getBbWidth() * 0.3D,
                0.04D
        );
        playFollowThroughFeedback(serverLevel, attacker);
    }

    private static void completeFollowThrough(ServerLevel serverLevel, EnderMan enderMan, UUID attackerId) {
        ServerPlayer attacker = serverLevel.getServer().getPlayerList().getPlayer(attackerId);
        if (!canFollowLinkedEnderman(attacker, enderMan)) {
            LINKED_ENDERMEN.remove(enderMan.getUUID());
            return;
        }
        if (attacker.distanceToSqr(enderMan) > FOLLOW_THROUGH_MAX_EXIT_RANGE * FOLLOW_THROUGH_MAX_EXIT_RANGE) {
            return;
        }
        teleportLinkedPlayerAlways(serverLevel, attacker, enderMan, enderMan.getX(), enderMan.getY(), enderMan.getZ());
        markEndermanConfusedFromFollow(enderMan, attacker);
        playFollowThroughFeedback(serverLevel, attacker);
    }

    private static boolean canFollowLinkedEnderman(ServerPlayer attacker, EnderMan enderMan) {
        return attacker != null
                && !attacker.isRemoved()
                && !attacker.isSpectator()
                && attacker.isAlive()
                && attacker.level() == enderMan.level();
    }

    private static void playFollowThroughFeedback(ServerLevel serverLevel, ServerPlayer attacker) {
        serverLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                attacker.getX(),
                attacker.getY() + attacker.getBbHeight() * 0.5D,
                attacker.getZ(),
                28,
                attacker.getBbWidth() * 0.22D,
                attacker.getBbHeight() * 0.18D,
                attacker.getBbWidth() * 0.22D,
                0.03D
        );
    }

    private static boolean tryForcedEndermanTeleport(EnderMan enderMan) {
        return tryForcedEndermanTeleport(
                enderMan,
                ENDERMAN_TELEPORT_RANGE,
                16,
                ENDERMAN_FOLLOW_TELEPORT_ATTEMPTS,
                ENDERMAN_ROUGH_RELOCATE_ATTEMPTS,
                false
        );
    }

    private static boolean tryForcedEndermanTeleport(
            EnderMan enderMan,
            double range,
            int verticalSpread,
            int attempts,
            int roughAttempts,
            boolean chaotic
    ) {
        if (!(enderMan.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        FORCED_ENDERMAN_TELEPORTS.add(enderMan.getUUID());
        try {
            for (int i = 0; i < attempts; i++) {
                double x = enderMan.getX() + (enderMan.getRandom().nextDouble() - 0.5D) * range * 2.0D;
                double y = enderMan.getY() + enderMan.getRandom().nextInt(verticalSpread * 2 + 1) - verticalSpread;
                double z = enderMan.getZ() + (enderMan.getRandom().nextDouble() - 0.5D) * range * 2.0D;
                if (enderMan.randomTeleport(x, y, z, true)) {
                    return true;
                }
            }
            return forceRoughEndermanRelocate(serverLevel, enderMan, range, roughAttempts, chaotic);
        } finally {
            FORCED_ENDERMAN_TELEPORTS.remove(enderMan.getUUID());
        }
    }

    /**
     * When {@link EnderMan#randomTeleport} cannot find a vanilla-valid spot, snap the enderman to a loaded
     * column near the surface anyway so linked follow-through is not abandoned.
     */
    private static boolean forceRoughEndermanRelocate(ServerLevel level, EnderMan enderMan) {
        return forceRoughEndermanRelocate(level, enderMan, ENDERMAN_TELEPORT_RANGE, ENDERMAN_ROUGH_RELOCATE_ATTEMPTS, false);
    }

    private static boolean forceRoughEndermanRelocate(ServerLevel level, EnderMan enderMan, double range, int attempts, boolean chaotic) {
        RandomSource rnd = enderMan.getRandom();
        double originX = enderMan.getX();
        double originY = enderMan.getY();
        double originZ = enderMan.getZ();
        for (int strictLiquid = 0; strictLiquid < 2; strictLiquid++) {
            boolean allowLiquid = strictLiquid > 0;
            for (int i = 0; i < attempts; i++) {
                double tx = originX + (rnd.nextDouble() - 0.5D) * range * 2.0D;
                double tz = originZ + (rnd.nextDouble() - 0.5D) * range * 2.0D;
                int bx = Mth.floor(tx);
                int bz = Mth.floor(tz);
                if (!level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                int minY = chaotic
                        ? Math.max(level.getMinBuildHeight() + 2, Math.min(surface - 42, Mth.floor(originY) - 48))
                        : Math.max(level.getMinBuildHeight() + 2, surface - 6);
                int maxY = chaotic
                        ? Math.min(level.getMaxBuildHeight() - 2, Math.max(surface + 18, Mth.floor(originY) + 32))
                        : Math.min(level.getMaxBuildHeight() - 2, surface + 10);
                int startY = chaotic ? Mth.clamp(minY + rnd.nextInt(Math.max(1, maxY - minY + 1)), minY, maxY) : maxY;
                for (int ty = maxY; ty >= minY; ty--) {
                    int candidateY = chaotic ? wrapVerticalSearch(startY, ty, minY, maxY) : ty;
                    if (isValidRoughEndermanDestination(level, enderMan, tx, candidateY, tz, allowLiquid)) {
                        enderMan.teleportTo(tx, candidateY, tz);
                        level.broadcastEntityEvent(enderMan, (byte) 46);
                        enderMan.resetFallDistance();
                        enderMan.getNavigation().stop();
                        return true;
                    }
                }
            }
        }
        enderMan.teleportTo(originX, originY, originZ);
        return false;
    }

    private static int wrapVerticalSearch(int startY, int cursorY, int minY, int maxY) {
        int span = maxY - minY + 1;
        int offset = maxY - cursorY;
        return minY + Math.floorMod((startY - minY) - offset, span);
    }

    private static boolean isValidRoughEndermanDestination(ServerLevel level, EnderMan enderMan, double x, int y, double z, boolean allowLiquid) {
        BlockPos feetPos = BlockPos.containing(x, y, z);
        BlockState floor = level.getBlockState(feetPos.below());
        if (floor.getCollisionShape(level, feetPos.below()).isEmpty()) {
            return false;
        }
        EntityDimensions dimensions = enderMan.getDimensions(Pose.STANDING);
        AABB box = dimensions.makeBoundingBox(x, y, z);
        return level.noCollision(enderMan, box) && (allowLiquid || !level.containsAnyLiquid(box));
    }

    private static void tryStartPanicBurst(EnderMan enderMan) {
        UUID id = enderMan.getUUID();
        if (ENDERMAN_PANIC.containsKey(id)) {
            return;
        }
        Integer last = ENDERMAN_LAST_PANIC_TICK.get(id);
        if (last != null && enderMan.tickCount - last < ENDERMAN_PANIC_COOLDOWN_TICKS) {
            return;
        }
        ENDERMAN_LAST_PANIC_TICK.put(id, enderMan.tickCount);
        ENDERMAN_PANIC.put(id, new PanicBurst(ENDERMAN_PANIC_TELEPORT_COUNT, enderMan.tickCount + 1, false));
    }

    private static boolean tryStartSuperPanicBurst(EnderMan enderMan) {
        UUID id = enderMan.getUUID();
        if (ENDERMAN_SUPER_PANIC_USED.contains(id)
                || enderMan.getRandom().nextFloat() > ENDERMAN_SUPER_PANIC_CHANCE) {
            return false;
        }
        int span = ENDERMAN_SUPER_PANIC_MAX_TELEPORTS - ENDERMAN_SUPER_PANIC_MIN_TELEPORTS + 1;
        int count = ENDERMAN_SUPER_PANIC_MIN_TELEPORTS + enderMan.getRandom().nextInt(Math.max(1, span));
        ENDERMAN_SUPER_PANIC_USED.add(id);
        ENDERMAN_LAST_PANIC_TICK.put(id, enderMan.tickCount);
        ENDERMAN_PANIC.put(id, new PanicBurst(count, enderMan.tickCount + 1, true));
        if (enderMan.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.DRAGON_BREATH,
                    enderMan.getX(),
                    enderMan.getY() + enderMan.getBbHeight() * 0.55D,
                    enderMan.getZ(),
                    36,
                    enderMan.getBbWidth() * 0.5D,
                    enderMan.getBbHeight() * 0.3D,
                    enderMan.getBbWidth() * 0.5D,
                    0.02D
            );
        }
        enderMan.level().playSound(
                null,
                enderMan.getX(),
                enderMan.getY(),
                enderMan.getZ(),
                SoundEvents.ENDERMAN_SCREAM,
                SoundSource.HOSTILE,
                1.35F,
                0.55F + enderMan.getRandom().nextFloat() * 0.12F
        );
        return true;
    }

    private static void tickEndermanPanic(ServerLevel serverLevel, EnderMan enderMan) {
        PanicBurst panic = ENDERMAN_PANIC.get(enderMan.getUUID());
        if (panic == null) {
            return;
        }
        if (panic.remainingTeleports() <= 0) {
            ENDERMAN_PANIC.remove(enderMan.getUUID());
            return;
        }
        if (enderMan.tickCount < panic.nextAttemptTick()) {
            return;
        }
        if (panic.superPanic()) {
            tryForcedEndermanTeleport(
                    enderMan,
                    ENDERMAN_SUPER_PANIC_RANGE,
                    ENDERMAN_SUPER_PANIC_VERTICAL_SPREAD,
                    ENDERMAN_SUPER_PANIC_ATTEMPTS,
                    ENDERMAN_SUPER_PANIC_ROUGH_ATTEMPTS,
                    true
            );
        } else {
            tryForcedEndermanTeleport(enderMan);
        }
        UUID linkedId = LINKED_ENDERMEN.get(enderMan.getUUID());
        if (linkedId != null) {
            ServerPlayer linked = serverLevel.getServer().getPlayerList().getPlayer(linkedId);
            if (linked != null
                    && canFollowLinkedEnderman(linked, enderMan)
                    && linked.distanceToSqr(enderMan) <= FOLLOW_THROUGH_MAX_EXIT_RANGE * FOLLOW_THROUGH_MAX_EXIT_RANGE) {
                teleportLinkedPlayerAlways(serverLevel, linked, enderMan, enderMan.getX(), enderMan.getY(), enderMan.getZ());
                markEndermanConfusedFromFollow(enderMan, linked);
            }
        }
        int left = panic.remainingTeleports() - 1;
        if (left <= 0) {
            ENDERMAN_PANIC.remove(enderMan.getUUID());
        } else {
            int gap = panic.superPanic() ? 2 + enderMan.getRandom().nextInt(4) : 4 + enderMan.getRandom().nextInt(6);
            ENDERMAN_PANIC.put(enderMan.getUUID(), new PanicBurst(left, enderMan.tickCount + gap, panic.superPanic()));
        }
    }

    private static void markEndermanConfusedFromFollow(EnderMan enderMan, ServerPlayer linkedPlayer) {
        int span = ENDERMAN_CONFUSED_MAX_TICKS - ENDERMAN_CONFUSED_MIN_TICKS + 1;
        int duration = ENDERMAN_CONFUSED_MIN_TICKS + (span > 0 ? enderMan.getRandom().nextInt(span) : 0);
        int targetLockTicks = Math.min(duration, ENDERMAN_CONFUSED_TARGET_LOCK_TICKS);
        CONFUSED_ENDERMEN.put(enderMan.getUUID(), new EndermanConfusionState(enderMan.tickCount + targetLockTicks, linkedPlayer.getUUID()));
        enderMan.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, true, true));
        enderMan.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true, true));
        staggerEndermanWithoutPacifying(enderMan, linkedPlayer);
        playEndermanConfusionFeedback(enderMan);
    }

    private static void tickEndermanConfusion(ServerLevel serverLevel, EnderMan enderMan) {
        EndermanConfusionState confusion = CONFUSED_ENDERMEN.get(enderMan.getUUID());
        if (confusion == null) {
            return;
        }
        if (enderMan.tickCount > confusion.expiresAtTick() || !enderMan.isAlive()) {
            CONFUSED_ENDERMEN.remove(enderMan.getUUID());
            return;
        }
        staggerEndermanWithoutPacifying(enderMan, serverLevel.getServer().getPlayerList().getPlayer(confusion.attackerId()));
        if (enderMan.tickCount % 8 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.NOTE,
                    enderMan.getX(),
                    enderMan.getY() + enderMan.getBbHeight() + 0.18D,
                    enderMan.getZ(),
                    2,
                    enderMan.getBbWidth() * 0.22D,
                    0.08D,
                    enderMan.getBbWidth() * 0.22D,
                    0.0D
            );
        }
    }

    private static void staggerEndermanWithoutPacifying(EnderMan enderMan, LivingEntity angerTarget) {
        if (angerTarget != null && angerTarget.isAlive() && angerTarget.level() == enderMan.level()) {
            enrageEndermanAt(enderMan, angerTarget, true);
        }
        enderMan.getNavigation().stop();
    }

    private static void enrageEndermanAt(EnderMan enderMan, LivingEntity target, boolean visualOnlyDuringConfusion) {
        enderMan.setPersistentAngerTarget(target.getUUID());
        enderMan.startPersistentAngerTimer();
        enderMan.setBeingStaredAt();
        enderMan.setTarget(target);
        if (!visualOnlyDuringConfusion) {
            enderMan.setAggressive(true);
        }
    }

    private static void playEndermanConfusionFeedback(EnderMan enderMan) {
        if (enderMan.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.NOTE,
                    enderMan.getX(),
                    enderMan.getY() + enderMan.getBbHeight() + 0.2D,
                    enderMan.getZ(),
                    8,
                    enderMan.getBbWidth() * 0.25D,
                    0.12D,
                    enderMan.getBbWidth() * 0.25D,
                    0.0D
            );
            serverLevel.sendParticles(
                    ParticleTypes.WITCH,
                    enderMan.getX(),
                    enderMan.getY() + enderMan.getBbHeight() * 0.58D,
                    enderMan.getZ(),
                    14,
                    enderMan.getBbWidth() * 0.22D,
                    enderMan.getBbHeight() * 0.16D,
                    enderMan.getBbWidth() * 0.22D,
                    0.01D
            );
        }
        enderMan.level().playSound(
                null,
                enderMan.getX(),
                enderMan.getY(),
                enderMan.getZ(),
                SoundEvents.ENDERMAN_AMBIENT,
                SoundSource.HOSTILE,
                0.8F,
                1.45F + enderMan.getRandom().nextFloat() * 0.2F
        );
    }

    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EnderMan enderMan) || enderMan.level().isClientSide()) {
            return;
        }
        EndermanConfusionState confusion = CONFUSED_ENDERMEN.get(enderMan.getUUID());
        if (confusion == null) {
            return;
        }
        if (enderMan.tickCount > confusion.expiresAtTick()) {
            CONFUSED_ENDERMEN.remove(enderMan.getUUID());
            return;
        }
        event.setNewAboutToBeSetTarget(null);
    }

    private static void teleportLinkedPlayerAlways(ServerLevel level, ServerPlayer player, EnderMan enderMan, double targetX, double targetY, double targetZ) {
        if (tryTeleportLinkedPlayerPreferred(level, player, enderMan, targetX, targetY, targetZ)) {
            return;
        }
        if (tryChorusStylePlayerTeleport(level, player, enderMan, targetX, targetY, targetZ)) {
            return;
        }
        forcePlayerNearEnderman(level, player, enderMan, targetX, targetY, targetZ);
    }

    private static boolean tryTeleportLinkedPlayerPreferred(ServerLevel level, ServerPlayer player, EnderMan enderMan, double targetX, double targetY, double targetZ) {
        double angle = enderMan.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < 12; i++) {
            double x = targetX + Math.cos(angle) * FOLLOW_THROUGH_PLAYER_OFFSET;
            double z = targetZ + Math.sin(angle) * FOLLOW_THROUGH_PLAYER_OFFSET;
            double y = findSafePlayerY(level, x, targetY, z);
            if (!Double.isNaN(y)) {
                faceTeleport(player, enderMan, x, y, z, targetX, targetY, targetZ);
                return true;
            }
            angle += Math.PI / 6.0D;
        }
        return false;
    }

    private static boolean tryChorusStylePlayerTeleport(ServerLevel level, ServerPlayer player, EnderMan enderMan, double centerX, double centerY, double centerZ) {
        RandomSource rnd = player.getRandom();
        for (int attempt = 0; attempt < CHORUS_STYLE_PLAYER_ATTEMPTS; attempt++) {
            double x = centerX + (rnd.nextDouble() - 0.5D) * 2.0D * CHORUS_STYLE_PLAYER_SPREAD;
            double z = centerZ + (rnd.nextDouble() - 0.5D) * 2.0D * CHORUS_STYLE_PLAYER_SPREAD;
            double y = findSafePlayerYExpanded(level, x, z, centerY);
            if (!Double.isNaN(y)) {
                faceTeleport(player, enderMan, x, y, z, centerX, centerY, centerZ);
                return true;
            }
        }
        double x = centerX + (rnd.nextDouble() - 0.5D) * 6.0D;
        double z = centerZ + (rnd.nextDouble() - 0.5D) * 6.0D;
        double y = findClearestStandingY(level, x, z);
        if (!Double.isNaN(y)) {
            faceTeleport(player, enderMan, x, y, z, centerX, centerY, centerZ);
            return true;
        }
        return false;
    }

    private static boolean playerStandingBoxClear(ServerLevel level, ServerPlayer player, double x, double y, double z, boolean allowLiquid) {
        EntityDimensions dims = player.getDimensions(Pose.STANDING);
        AABB box = dims.makeBoundingBox(x, y, z);
        if (!level.noCollision(player, box)) {
            return false;
        }
        return allowLiquid || !level.containsAnyLiquid(box);
    }

    private static void forcePlayerNearEnderman(ServerLevel level, ServerPlayer player, EnderMan enderMan, double ex, double ey, double ez) {
        RandomSource rnd = player.getRandom();
        for (int liquidPass = 0; liquidPass < 2; liquidPass++) {
            boolean allowLiquid = liquidPass > 0;
            for (double radius = 0.35D; radius <= 5.5D; radius += 0.3D) {
                for (int sector = 0; sector < 36; sector++) {
                    double angle = sector * (Math.PI * 2.0D / 36.0D) + rnd.nextDouble() * 0.08D;
                    double x = ex + Math.cos(angle) * radius;
                    double z = ez + Math.sin(angle) * radius;
                    for (int dy = 5; dy >= -14; dy--) {
                        double y = ey + dy;
                        if (y < level.getMinBuildHeight() + 1.0D) {
                            continue;
                        }
                        if (y + player.getBbHeight() >= level.getMaxBuildHeight() - 1) {
                            continue;
                        }
                        if (playerStandingBoxClear(level, player, x, y, z, allowLiquid)) {
                            faceTeleport(player, enderMan, x, y, z, ex, ey, ez);
                            return;
                        }
                    }
                }
            }
        }
        double ySnap = ey;
        int ix = Mth.floor(ex);
        int iz = Mth.floor(ez);
        if (level.hasChunk(SectionPos.blockToSectionCoord(ix), SectionPos.blockToSectionCoord(iz))) {
            ySnap = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz) + 1.0D;
        }
        double maxY = level.getMaxBuildHeight() - player.getBbHeight() - 1.0D;
        ySnap = Mth.clamp(ySnap, level.getMinBuildHeight() + 1.0D, maxY);
        faceTeleport(player, enderMan, ex + 0.6D, ySnap, ez + 0.15D, ex, ey, ez);
    }

    private static double findSafePlayerY(ServerLevel level, double x, double aroundY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(aroundY) + 3, Mth.floor(z));
        int minY = Math.max(level.getMinBuildHeight() + 1, Mth.floor(aroundY) - 8);
        while (pos.getY() >= minY) {
            BlockState feet = level.getBlockState(pos);
            BlockState head = level.getBlockState(pos.above());
            BlockState floor = level.getBlockState(pos.below());
            if (feet.getCollisionShape(level, pos).isEmpty()
                    && head.getCollisionShape(level, pos.above()).isEmpty()
                    && !floor.getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }

    private static double findSafePlayerYExpanded(ServerLevel level, double x, double z, double hintY) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(ix), SectionPos.blockToSectionCoord(iz))) {
            return Double.NaN;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
        int[] starts = new int[]{surface + 4, surface + 10, surface + 1, surface + 16, Mth.floor(hintY) + 4};
        for (int startY : starts) {
            double y = findSafePlayerY(level, x, startY, z);
            if (!Double.isNaN(y)) {
                return y;
            }
        }
        return Double.NaN;
    }

    private static double findClearestStandingY(ServerLevel level, double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(ix), SectionPos.blockToSectionCoord(iz))) {
            return Double.NaN;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(ix, Math.min(surface + 6, level.getMaxBuildHeight() - 2), iz);
        int minY = Math.max(level.getMinBuildHeight() + 2, surface - 24);
        while (pos.getY() >= minY) {
            BlockState feet = level.getBlockState(pos);
            BlockState head = level.getBlockState(pos.above());
            BlockState floor = level.getBlockState(pos.below());
            if (feet.getCollisionShape(level, pos).isEmpty()
                    && head.getCollisionShape(level, pos.above()).isEmpty()
                    && !floor.getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }

    private static void faceTeleport(ServerPlayer player, EnderMan enderMan, double x, double y, double z, double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dz = targetZ - z;
        double dy = (targetY + enderMan.getBbHeight() * 0.48D) - (y + player.getEyeHeight());
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
        player.teleportTo((ServerLevel) player.level(), x, y, z, new HashSet<>(), yaw, pitch);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_TELEPORT,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        FinesseUnarmedCombat.removeWeakSpotState(entity);
        if (entity instanceof AbstractSkeleton skeleton) {
            DisarmedWeapon disarmed = DISARMED_WEAPONS.remove(skeleton.getUUID());
            if (disarmed != null) {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, disarmed.weapon());
                skeleton.setDropChance(EquipmentSlot.MAINHAND, disarmed.dropChance());
            }
        }
        if (entity instanceof EnderMan enderMan) {
            ANCHORED_ENDERMEN.remove(enderMan.getUUID());
            LINKED_ENDERMEN.remove(enderMan.getUUID());
            PENDING_FOLLOW_THROUGH.remove(enderMan.getUUID());
            ENDERMAN_PANIC.remove(enderMan.getUUID());
            ENDERMAN_LAST_PANIC_TICK.remove(enderMan.getUUID());
            ENDERMAN_SUPER_PANIC_USED.remove(enderMan.getUUID());
            CONFUSED_ENDERMEN.remove(enderMan.getUUID());
            PENDING_CREEPER_IMPLOSIONS.remove(enderMan.getUUID());
            return;
        }
        if (entity instanceof Creeper creeper) {
            PENDING_CREEPER_IMPLOSIONS.remove(creeper.getUUID());
            return;
        }
        if (entity instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            ANCHORED_ENDERMEN.entrySet().removeIf(entry -> entry.getValue().attackerId().equals(playerId));
            LINKED_ENDERMEN.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
            PENDING_FOLLOW_THROUGH.entrySet().removeIf(entry -> entry.getValue().attackerId().equals(playerId));
        }
    }

    private record EndermanAnchorState(int expiresAtTick, UUID attackerId) {
    }

    private record PendingFollowThrough(UUID attackerId, double startX, double startY, double startZ, int expiresAtTick) {
    }

    private record PanicBurst(int remainingTeleports, int nextAttemptTick, boolean superPanic) {
    }

    private record EndermanConfusionState(int expiresAtTick, UUID attackerId) {
    }

    private record PendingCreeperImplosion(UUID attackerId, int triggerTick, boolean originallyPowered) {
    }
}
