package net.zoogle.levelrpg.finesse;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.zoogle.levelrpg.mixin.AbstractArrowGroundAccessor;
import net.zoogle.levelrpg.mixin.AbstractArrowPierceInvoker;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Perfect projectile parry for Finesse hand-to-hand: tight block timing + punch follow-up to return projectiles.
 */
public final class FinesseProjectileParry {
    public static final String PERSIST_RETURN = "levelrpg:finesse_return";
    public static final String PERSIST_IMMUNE_UNTIL = "levelrpg:finesse_immune_parrier_until";
    public static final String PERSIST_PARRIER_UUID = "levelrpg:finesse_parrier";

    private static final int PERFECT_PROJECTILE_PARRY_WINDOW_TICKS = 5;
    /** Game-time ticks the parrier has to land the return punch (not player tickCount). */
    private static final int SUSPENDED_RETURN_WINDOW_TICKS = 30;
    private static final int RETURN_HOMING_TICKS = 45;
    private static final double RETURN_SPEED_MULTIPLIER = 1.15D;
    private static final double PARRY_FRONT_DOT_MIN = 0.38D;
    private static final double PARRY_SUSPEND_DISTANCE = 1.05D;
    /** Max distance along look from eye to projectile center for return punch. */
    private static final double PARRY_PROJECTILE_PUNCH_REACH = 6.0D;
    /** Half-width (m) of punch “cylinder” around view ray; arrows sit near the face so this must be generous. */
    private static final double PARRY_PROJECTILE_PUNCH_CROSS_SECTION = 1.45D;
    private static final int PARRY_GRACE_TICKS = 18;
    private static final double HOMING_TURN_STRENGTH = 0.14D;
    private static final double ARROW_MANUAL_RETURN_HIT_INFLATE = 1.25D;
    private static final double ARROW_RETURN_MIN_SPEED = 1.35D;
    private static final double FALLBACK_TARGET_RANGE = 24.0D;
    private static final double AIMED_RETURN_TARGET_RANGE = 32.0D;
    private static final double RETURN_TO_SENDER_DOT = 0.30D;
    private static final double RETURN_TO_OTHER_TARGET_DOT = 0.48D;
    private static final double RETURN_TO_OTHER_TARGET_RAY_WIDTH = 2.25D;
    private static final double MAX_INCOMING_DIST_SQ = 5.5D * 5.5D;

    private static final Map<UUID, ParryTrack> ACTIVE = new ConcurrentHashMap<>();

    private FinesseProjectileParry() {
    }

    public static final class EventHandler {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onProjectileImpact(ProjectileImpactEvent event) {
            FinesseProjectileParry.handleProjectileImpact(event);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onLivingDamagePre(LivingDamageEvent.Pre event) {
            FinesseProjectileParry.handleLivingDamagePre(event);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onLivingDamagePost(LivingDamageEvent.Post event) {
            FinesseProjectileParry.handleLivingDamagePost(event);
        }

        @SubscribeEvent
        public void onServerTickPost(ServerTickEvent.Post event) {
            FinesseProjectileParry.tickAll(event.getServer());
        }

        @SubscribeEvent
        public void onEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof Projectile p) {
                ACTIVE.remove(p.getUUID());
            }
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                FinesseProjectileParry.cleanupForParrier(serverPlayer);
            }
        }
    }

    private static void handleProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isParryableProjectile(projectile)) {
            return;
        }
        if (ACTIVE.containsKey(projectile.getUUID())) {
            return;
        }
        if (!tryBeginPerfectParry(player, projectile)) {
            return;
        }
        event.setCanceled(true);
        beginSuspension(player, projectile);
    }

    private static void handleLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile)) {
            return;
        }
        ParryTrack active = ACTIVE.get(projectile.getUUID());
        if (active != null && active.phase() == ParryPhase.SUSPENDED && victim.getUUID().equals(active.parrierId())) {
            event.setNewDamage(0.0F);
            return;
        }
        CompoundTag tag = projectile.getPersistentData();
        if (tag.contains(PERSIST_IMMUNE_UNTIL)) {
            long until = tag.getLong(PERSIST_IMMUNE_UNTIL);
            long now = victim.level().getGameTime();
            if (now < until && victim.getUUID().equals(tag.getUUID(PERSIST_PARRIER_UUID))) {
                event.setNewDamage(0.0F);
            }
        }
    }

    private static void handleLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile)) {
            return;
        }
        CompoundTag root = projectile.getPersistentData();
        if (!root.contains(PERSIST_RETURN)) {
            return;
        }
        CompoundTag ret = root.getCompound(PERSIST_RETURN);
        if (!ret.hasUUID("parrier") || !ret.hasUUID("original")) {
            return;
        }
        if (!victim.getUUID().equals(ret.getUUID("original"))) {
            return;
        }
        UUID parrierId = ret.getUUID("parrier");
        ServerPlayer parrier = victim.level().getServer() == null
                ? null
                : victim.level().getServer().getPlayerList().getPlayer(parrierId);
        onReturnedProjectileHitOriginalShooter(parrier, victim, projectile);
        root.remove(PERSIST_RETURN);
    }

    private static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, ParryTrack> e : new ArrayList<>(ACTIVE.entrySet())) {
            ParryTrack track = e.getValue();
            ServerLevel level = server.getLevel(track.dimension());
            if (level == null) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            Entity entity = level.getEntity(track.projectileNetworkId());
            if (!(entity instanceof Projectile projectile)) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            tickOneTrack(server, level, projectile, track);
        }
    }

    private static void tickOneTrack(MinecraftServer server, ServerLevel level, Projectile projectile, ParryTrack track) {
        long gameTime = level.getGameTime();
        ServerPlayer parrier = server.getPlayerList().getPlayer(track.parrierId());
        if (parrier == null || parrier.isRemoved() || !parrier.isAlive()) {
            fizzleProjectile(projectile);
            ACTIVE.remove(projectile.getUUID());
            return;
        }

        if (track.phase() == ParryPhase.SUSPENDED) {
            if (gameTime >= track.suspendDeadlineTick()) {
                fizzleProjectile(projectile);
                ACTIVE.remove(projectile.getUUID());
                return;
            }
            bindSuspendedPosition(parrier, projectile);
            projectile.setDeltaMovement(Vec3.ZERO);
            dampProjectileGravity(projectile);
            return;
        }

        if (track.phase() == ParryPhase.RETURNING) {
            if (gameTime >= track.homingDeadlineTick()) {
                restoreProjectileGravity(projectile);
                CompoundTag pd = projectile.getPersistentData();
                pd.remove(PERSIST_IMMUNE_UNTIL);
                pd.remove(PERSIST_PARRIER_UUID);
                ACTIVE.remove(projectile.getUUID());
                return;
            }
            applyHoming(level, projectile, track, server);
        }
    }

    private static void applyHoming(ServerLevel level, Projectile projectile, ParryTrack track, MinecraftServer server) {
        clearAbstractArrowGroundStuck(projectile);
        Vec3 v = projectile.getDeltaMovement();
        boolean manualArrow = projectile instanceof AbstractArrow;
        boolean directHoming = usesDirectReturnHoming(projectile);
        double speed = Math.max(directHoming ? ARROW_RETURN_MIN_SPEED : 0.45D, v.length());
        LivingEntity target = resolveReturnTarget(server, track);
        Vec3 desired;
        if (target != null && target.isAlive()) {
            desired = target.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
        } else {
            desired = v.lengthSqr() > 1.0E-6D ? v.normalize() : projectile.getViewVector(1.0F);
        }
        Vec3 current = v.lengthSqr() > 1.0E-6D ? v.normalize() : desired;
        Vec3 blended = directHoming
                ? desired
                : current.add(desired.subtract(current).scale(HOMING_TURN_STRENGTH)).normalize();
        projectile.setDeltaMovement(blended.scale(speed));
        dampProjectileGravity(projectile);
        if (manualArrow) {
            tickManualReturningArrow(level, projectile, track, blended, speed, target);
        }
    }

    private static boolean usesDirectReturnHoming(Projectile projectile) {
        return projectile instanceof AbstractArrow
                || projectile instanceof ThrowableItemProjectile
                || projectile instanceof LlamaSpit
                || projectile instanceof AbstractWindCharge;
    }

    private static void tickManualReturningArrow(
            ServerLevel level,
            Projectile projectile,
            ParryTrack track,
            Vec3 direction,
            double speed,
            @Nullable LivingEntity target
    ) {
        Vec3 start = projectile.position();
        Vec3 next = start.add(direction.scale(speed));
        projectile.moveTo(next.x, next.y, next.z, yawFrom(direction), pitchFrom(direction));
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        if (target == null || !target.isAlive()) {
            return;
        }
        if (target.getBoundingBox().inflate(ARROW_MANUAL_RETURN_HIT_INFLATE).clip(start, next).isEmpty()) {
            return;
        }
        ServerPlayer parrier = level.getServer().getPlayerList().getPlayer(track.parrierId());
        float damage = projectile instanceof AbstractArrow arrow
                ? (float) Math.max(4.0D, arrow.getBaseDamage())
                : 4.0F;
        if (projectile instanceof AbstractArrow arrow) {
            target.hurt(level.damageSources().arrow(arrow, parrier != null ? parrier : projectile), damage);
        } else {
            target.hurt(level.damageSources().thrown(projectile, parrier != null ? parrier : projectile), damage);
        }
        onReturnedProjectileHitOriginalShooter(parrier, target, projectile);
        projectile.getPersistentData().remove(PERSIST_RETURN);
        ACTIVE.remove(projectile.getUUID());
        projectile.discard();
    }

    private static float yawFrom(Vec3 direction) {
        return (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
    }

    private static float pitchFrom(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);
    }

    private static void dampProjectileGravity(Projectile projectile) {
        projectile.setNoGravity(true);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoGravity(true);
        }
    }

    private static void restoreProjectileGravity(Projectile projectile) {
        projectile.setNoGravity(false);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoGravity(false);
        }
    }

    private static void clearAbstractArrowGroundStuck(Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            AbstractArrowGroundAccessor acc = (AbstractArrowGroundAccessor) (Object) arrow;
            acc.levelrpg$setInGround(false);
            acc.levelrpg$setInGroundTime(0);
        }
    }

    private static void bindSuspendedPosition(ServerPlayer player, Projectile projectile) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 pos = eye.add(look.scale(PARRY_SUSPEND_DISTANCE));
        projectile.moveTo(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
    }

    private static boolean tryBeginPerfectParry(ServerPlayer player, Projectile projectile) {
        LevelProfile profile = LevelProfile.get(player);
        if (!FinesseUnarmedCombat.canUseUnarmedCombat(player, profile)) {
            return false;
        }
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.HANDS_UP)) {
            return false;
        }
        if (!FinesseGuardState.isGuarding(player)) {
            return false;
        }
        if (FinesseGuardState.ticksSinceGuardStarted(player) > PERFECT_PROJECTILE_PARRY_WINDOW_TICKS) {
            return false;
        }
        if (player.distanceToSqr(projectile) > MAX_INCOMING_DIST_SQ) {
            return false;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 toProj = projectile.position().subtract(eye);
        double lenSq = toProj.lengthSqr();
        if (lenSq < 1.0E-4D) {
            return false;
        }
        Vec3 toN = toProj.normalize();
        if (look.dot(toN) < PARRY_FRONT_DOT_MIN) {
            return false;
        }
        return true;
    }

    private static void beginSuspension(ServerPlayer player, Projectile projectile) {
        long deadline = player.level().getGameTime() + SUSPENDED_RETURN_WINDOW_TICKS;
        Entity owner = projectile.getOwner();
        ResourceKey<Level> ownerDim = player.level().dimension();
        int ownerNet = -1;
        UUID ownerUuid = null;
        if (owner instanceof LivingEntity livingOwner) {
            ownerUuid = livingOwner.getUUID();
            ownerDim = owner.level().dimension();
            ownerNet = owner.getId();
        } else if (owner != null) {
            ownerDim = owner.level().dimension();
            ownerNet = owner.getId();
        }
        Vec3 vel = projectile.getDeltaMovement();
        ParryTrack track = new ParryTrack(
                player.level().dimension(),
                projectile.getId(),
                player.getUUID(),
                ownerDim,
                ownerNet,
                ownerUuid,
                null,
                ownerDim,
                -1,
                vel,
                ParryPhase.SUSPENDED,
                deadline,
                0L
        );
        ACTIVE.put(projectile.getUUID(), track);
        projectile.setDeltaMovement(Vec3.ZERO);
        dampProjectileGravity(projectile);
        bindSuspendedPosition(player, projectile);
        player.level().playSound(
                null,
                projectile.getX(),
                projectile.getY(),
                projectile.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS,
                0.35F,
                1.8F + player.getRandom().nextFloat() * 0.08F
        );
        player.connection.send(new ClientboundCustomPayloadPacket(new CameraShakePayload(7, 2.2F, 2.6F)));
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getZ(),
                    8,
                    0.12D,
                    0.12D,
                    0.12D,
                    0.02D
            );
        }
    }

    /**
     * If this player has any suspended parry in this level, return the best candidate.
     * Prefer the one in the player's view, but do not require a precise ray hit: the projectile is already owned by
     * this timed parry state, and the follow-up punch should be server-authoritative.
     */
    public static boolean tryPunchSuspendedParry(ServerPlayer player, ServerLevel level) {
        Projectile best = null;
        ParryTrack bestTrack = null;
        double bestPerpSq = Double.MAX_VALUE;
        double bestDistSq = Double.MAX_VALUE;
        for (Map.Entry<UUID, ParryTrack> e : ACTIVE.entrySet()) {
            ParryTrack t = e.getValue();
            if (t.phase() != ParryPhase.SUSPENDED || !t.parrierId().equals(player.getUUID())) {
                continue;
            }
            if (!t.dimension().equals(level.dimension())) {
                continue;
            }
            Entity ent = level.getEntity(t.projectileNetworkId());
            if (!(ent instanceof Projectile p) || !isParryableProjectile(p)) {
                continue;
            }
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F).normalize();
            Vec3 center = punchAimPoint(p);
            Vec3 to = center.subtract(eye);
            double along = to.dot(look);
            Vec3 closest = eye.add(look.scale(along));
            double perpSq = center.distanceToSqr(closest);
            double distSq = player.distanceToSqr(p);
            if (best == null || perpSq < bestPerpSq || (Math.abs(perpSq - bestPerpSq) < 1.0E-5D && distSq < bestDistSq)) {
                bestPerpSq = perpSq;
                bestDistSq = distSq;
                best = p;
                bestTrack = t;
            }
        }
        if (best == null || bestTrack == null) {
            return false;
        }
        launchReturn(player, best, bestTrack);
        return true;
    }

    private static Vec3 punchAimPoint(Projectile projectile) {
        return projectile.getBoundingBox().getCenter();
    }

    /**
     * Called from {@link FinesseUnarmedCombat#tryExtendedPunch} when the punch targets a projectile entity.
     */
    public static boolean tryPunchParriedProjectile(ServerPlayer player, Projectile projectile) {
        ParryTrack track = ACTIVE.get(projectile.getUUID());
        if (track == null || track.phase() != ParryPhase.SUSPENDED) {
            return false;
        }
        if (!track.parrierId().equals(player.getUUID())) {
            return false;
        }
        if (!canPunchReachProjectile(player, projectile)) {
            return false;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!FinesseUnarmedCombat.canUseUnarmedCombat(player, profile)) {
            return false;
        }
        launchReturn(player, projectile, track);
        return true;
    }

    private static boolean canPunchReachProjectile(ServerPlayer player, Projectile projectile) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 point = punchAimPoint(projectile);
        Vec3 to = point.subtract(eye);
        double along = to.dot(look);
        if (along < -0.35D || along > PARRY_PROJECTILE_PUNCH_REACH) {
            return false;
        }
        Vec3 closest = eye.add(look.scale(along));
        double perpSq = point.distanceToSqr(closest);
        return perpSq <= PARRY_PROJECTILE_PUNCH_CROSS_SECTION * PARRY_PROJECTILE_PUNCH_CROSS_SECTION;
    }

    private static void launchReturn(ServerPlayer parrier, Projectile projectile, ParryTrack oldTrack) {
        ServerLevel level = (ServerLevel) projectile.level();
        long gameTime = level.getGameTime();
        long homingEnd = gameTime + RETURN_HOMING_TICKS;
        long immuneUntil = gameTime + PARRY_GRACE_TICKS;

        LivingEntity originalShooter = resolveOriginalShooter(level.getServer(), oldTrack.ownerUuid(), oldTrack.ownerDimension(), oldTrack.ownerNetworkId());
        LivingEntity homingTarget = selectReturnTarget(level, parrier, projectile, originalShooter);
        Vec3 dir;
        if (homingTarget != null && homingTarget.isAlive()) {
            dir = homingTarget.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
        } else {
            dir = parrier.getViewVector(1.0F).normalize();
        }
        double speed = Math.max(0.55D, oldTrack.originalVelocity().length() * RETURN_SPEED_MULTIPLIER);
        clearAbstractArrowGroundStuck(projectile);
        Projectile returningProjectile = prepareReturnProjectile(level, parrier, projectile, dir, speed);

        CompoundTag tag = returningProjectile.getPersistentData();
        CompoundTag ret = new CompoundTag();
        ret.putUUID("parrier", parrier.getUUID());
        if (originalShooter != null) {
            ret.putUUID("original", originalShooter.getUUID());
        }
        tag.put(PERSIST_RETURN, ret);
        tag.putLong(PERSIST_IMMUNE_UNTIL, immuneUntil);
        tag.putUUID(PERSIST_PARRIER_UUID, parrier.getUUID());

        ParryTrack newTrack = new ParryTrack(
                oldTrack.dimension(),
                returningProjectile.getId(),
                oldTrack.parrierId(),
                oldTrack.ownerDimension(),
                oldTrack.ownerNetworkId(),
                oldTrack.ownerUuid(),
                homingTarget == null ? null : homingTarget.getUUID(),
                homingTarget == null ? oldTrack.ownerDimension() : homingTarget.level().dimension(),
                homingTarget == null ? -1 : homingTarget.getId(),
                returningProjectile.getDeltaMovement(),
                ParryPhase.RETURNING,
                oldTrack.suspendDeadlineTick(),
                homingEnd
        );
        ACTIVE.remove(projectile.getUUID());
        ACTIVE.put(returningProjectile.getUUID(), newTrack);

        level.playSound(
                null,
                returningProjectile.getX(),
                returningProjectile.getY(),
                returningProjectile.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS,
                0.9F,
                0.85F + parrier.getRandom().nextFloat() * 0.1F
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                returningProjectile.getX(),
                returningProjectile.getY(),
                returningProjectile.getZ(),
                6,
                0.08D,
                0.08D,
                0.08D,
                0.04D
        );
        parrier.connection.send(new ClientboundCustomPayloadPacket(new CameraShakePayload(9, 2.6F, 2.9F)));
        parrier.swing(InteractionHand.MAIN_HAND, true);
    }

    private static Projectile prepareReturnProjectile(ServerLevel level, ServerPlayer parrier, Projectile suspended, Vec3 dir, double speed) {
        Vec3 launchPos = suspended.position().add(dir.scale(0.35D));
        if (suspended instanceof AbstractArrow oldArrow) {
            ItemStack pickup = oldArrow.getPickupItemStackOrigin();
            if (pickup.isEmpty()) {
                pickup = new ItemStack(Items.ARROW);
            } else {
                pickup = pickup.copy();
            }
            ItemStack weapon = oldArrow.getWeaponItem();
            weapon = weapon == null ? ItemStack.EMPTY : weapon.copy();
            Arrow arrow = new Arrow(level, launchPos.x, launchPos.y, launchPos.z, pickup, weapon);
            arrow.setOwner(parrier);
            arrow.setBaseDamage(Math.max(3.0D, oldArrow.getBaseDamage() * 1.25D));
            arrow.setCritArrow(true);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            ((AbstractArrowPierceInvoker) (Object) arrow).levelrpg$setPierceLevel(oldArrow.getPierceLevel());
            arrow.setNoGravity(true);
            arrow.setNoPhysics(true);
            arrow.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
            arrow.hasImpulse = true;
            level.addFreshEntity(arrow);
            suspended.discard();
            return arrow;
        }

        suspended.setOwner(parrier);
        suspended.moveTo(launchPos.x, launchPos.y, launchPos.z, suspended.getYRot(), suspended.getXRot());
        suspended.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
        suspended.hasImpulse = true;
        dampProjectileGravity(suspended);
        return suspended;
    }

    private static @Nullable LivingEntity selectReturnTarget(
            ServerLevel level,
            ServerPlayer parrier,
            Projectile projectile,
            @Nullable LivingEntity originalShooter
    ) {
        if (originalShooter != null && originalShooter.isAlive() && isPlayerLookingToward(parrier, originalShooter, RETURN_TO_SENDER_DOT)) {
            return originalShooter;
        }
        LivingEntity aimed = findAimedReturnTarget(level, parrier, projectile, originalShooter);
        if (aimed != null) {
            return aimed;
        }
        if (originalShooter != null && originalShooter.isAlive()) {
            return originalShooter;
        }
        return findFallbackReturnTarget(level, parrier, projectile);
    }

    private static boolean isPlayerLookingToward(ServerPlayer player, LivingEntity target, double minDot) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
        if (to.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return player.getViewVector(1.0F).normalize().dot(to.normalize()) >= minDot;
    }

    private static @Nullable LivingEntity findAimedReturnTarget(
            ServerLevel level,
            ServerPlayer parrier,
            Projectile projectile,
            @Nullable LivingEntity originalShooter
    ) {
        Vec3 eye = parrier.getEyePosition(1.0F);
        Vec3 look = parrier.getViewVector(1.0F).normalize();
        AABB search = parrier.getBoundingBox().expandTowards(look.scale(AIMED_RETURN_TARGET_RANGE)).inflate(RETURN_TO_OTHER_TARGET_RAY_WIDTH);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search, entity ->
                entity.isAlive()
                        && entity != parrier
                        && entity != originalShooter
                        && entity instanceof Enemy
                        && !entity.isSpectator()
        )) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double distSq = to.lengthSqr();
            if (distSq < 1.0E-6D) {
                continue;
            }
            double along = to.dot(look);
            if (along < 0.0D || along > AIMED_RETURN_TARGET_RANGE) {
                continue;
            }
            double dot = look.dot(to.normalize());
            if (dot < RETURN_TO_OTHER_TARGET_DOT) {
                continue;
            }
            Vec3 closest = eye.add(look.scale(along));
            double perpSq = center.distanceToSqr(closest);
            double width = RETURN_TO_OTHER_TARGET_RAY_WIDTH + candidate.getBbWidth() * 0.5D;
            if (perpSq > width * width) {
                continue;
            }
            double projectileBias = projectile.distanceToSqr(candidate) * 0.08D;
            double score = perpSq * 12.0D + distSq * 0.04D - dot * 4.0D + projectileBias;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static @Nullable LivingEntity findFallbackReturnTarget(ServerLevel level, ServerPlayer parrier, Projectile projectile) {
        double range = FALLBACK_TARGET_RANGE;
        AABB search = projectile.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 from = projectile.position();
        Vec3 look = parrier.getViewVector(1.0F).normalize();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search, entity ->
                entity.isAlive()
                        && entity != parrier
                        && entity instanceof Enemy
                        && !entity.isSpectator()
        )) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(from);
            double distSq = to.lengthSqr();
            double alignmentPenalty = 0.0D;
            if (distSq > 1.0E-6D) {
                alignmentPenalty = Math.max(0.0D, 1.0D - look.dot(to.normalize())) * 10.0D;
            }
            double score = distSq + alignmentPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static void fizzleProjectile(Projectile projectile) {
        restoreProjectileGravity(projectile);
        if (projectile.level() instanceof ServerLevel sl) {
            sl.sendParticles(
                    ParticleTypes.SMOKE,
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getZ(),
                    6,
                    0.05D,
                    0.05D,
                    0.05D,
                    0.01D
            );
        }
        projectile.discard();
    }

    public static boolean isParryableProjectile(Entity entity) {
        return entity instanceof AbstractArrow
                || entity instanceof LargeFireball
                || entity instanceof LlamaSpit
                || entity instanceof SmallFireball
                || entity instanceof Snowball
                || entity instanceof ThrownEgg
                || entity instanceof ThrownPotion
                || entity instanceof AbstractWindCharge
                || entity instanceof ShulkerBullet;
    }

    /**
     * Prefer UUID (stable); fall back to network id in the last-known dimension if UUID was unavailable.
     */
    private static @Nullable LivingEntity resolveOriginalShooter(
            @Nullable MinecraftServer server,
            @Nullable UUID ownerUuid,
            ResourceKey<Level> ownerDimension,
            int ownerNetworkId
    ) {
        if (server == null) {
            return null;
        }
        if (ownerUuid != null) {
            for (ServerLevel world : server.getAllLevels()) {
                Entity e = world.getEntity(ownerUuid);
                if (e instanceof LivingEntity living && living.isAlive()) {
                    return living;
                }
            }
        }
        if (ownerNetworkId < 0) {
            return null;
        }
        ServerLevel sl = server.getLevel(ownerDimension);
        if (sl == null) {
            return null;
        }
        Entity e = sl.getEntity(ownerNetworkId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static @Nullable LivingEntity resolveReturnTarget(@Nullable MinecraftServer server, ParryTrack track) {
        LivingEntity target = resolveOriginalShooter(server, track.returnTargetUuid(), track.returnTargetDimension(), track.returnTargetNetworkId());
        if (target != null) {
            return target;
        }
        return resolveOriginalShooter(server, track.ownerUuid(), track.ownerDimension(), track.ownerNetworkId());
    }

    public static void cleanupForParrier(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        List<UUID> keys = new ArrayList<>();
        for (Map.Entry<UUID, ParryTrack> e : ACTIVE.entrySet()) {
            if (e.getValue().parrierId().equals(player.getUUID())) {
                keys.add(e.getKey());
            }
        }
        for (UUID projUuid : keys) {
            ParryTrack t = ACTIVE.remove(projUuid);
            if (t == null) {
                continue;
            }
            ServerLevel w = server.getLevel(t.dimension());
            if (w == null) {
                continue;
            }
            Entity ent = w.getEntity(t.projectileNetworkId());
            if (ent instanceof Projectile p) {
                fizzleProjectile(p);
            }
        }
    }

    /**
     * Future hook: stagger, posture, mob-specific reactions when a returned shot lands on the original shooter.
     */
    public static void onReturnedProjectileHitOriginalShooter(
            @Nullable ServerPlayer parrier,
            LivingEntity originalShooter,
            Projectile projectile
    ) {
        if (parrier != null && originalShooter.distanceToSqr(parrier) < 256.0D) {
            parrier.connection.send(new ClientboundCustomPayloadPacket(new CameraShakePayload(8, 2.0F, 2.4F)));
            originalShooter.knockback(0.35D, parrier.getX() - originalShooter.getX(), parrier.getZ() - originalShooter.getZ());
        }
    }

    private enum ParryPhase {
        SUSPENDED,
        RETURNING
    }

    private record ParryTrack(
            ResourceKey<Level> dimension,
            int projectileNetworkId,
            UUID parrierId,
            ResourceKey<Level> ownerDimension,
            int ownerNetworkId,
            @Nullable UUID ownerUuid,
            @Nullable UUID returnTargetUuid,
            ResourceKey<Level> returnTargetDimension,
            int returnTargetNetworkId,
            Vec3 originalVelocity,
            ParryPhase phase,
            long suspendDeadlineTick,
            long homingDeadlineTick
    ) {
    }
}
