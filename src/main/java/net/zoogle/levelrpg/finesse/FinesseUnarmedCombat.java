package net.zoogle.levelrpg.finesse;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;
import net.zoogle.levelrpg.net.payload.SyncFinesseWeakSpotPayload;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FinesseUnarmedCombat {
    private static final Map<UUID, Boolean> NEXT_OFFHAND_PUNCH = new ConcurrentHashMap<>();
    private static final Map<UUID, WeakSpotIntent> WEAK_SPOT_INTENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, WeakSpotState> WEAK_SPOT_STATES = new ConcurrentHashMap<>();
    private static final Map<Integer, ClientWeakSpotState> CLIENT_WEAK_SPOTS = new ConcurrentHashMap<>();
    private static final double EXTENDED_FIST_REACH = 3.35D;
    /** Living target this close takes priority over parry-return cone scan (server). */
    private static final double PARRY_CONE_MELEE_PRIORITY_REACH = 5.15D;
    private static final float SERVER_ATTACK_READY_THRESHOLD = 0.75F;
    private static final double WEAK_SPOT_REACH = 3.65D;
    private static final int WEAK_SPOT_INTENT_TICKS = 6;
    private static final double NO_WEAK_SPOT_CHANCE = 0.18D;
    private static final int WEAK_SPOT_MIN_HITS_BEFORE_SWAP = 2;
    private static final int WEAK_SPOT_MAX_HITS_BEFORE_SWAP = 4;
    private static final double WEAK_SPOT_SWAP_CHANCE = 0.55D;
    private static final double SKELETON_INITIAL_CHEST_CHANCE = 0.55D;
    private static final double SKELETON_SWAP_TO_CHEST_CHANCE = 0.82D;
    private static final double WEAK_SPOT_MIN_RADIUS = 0.45D;
    private static final double WEAK_SPOT_WIDTH_RADIUS_SCALE = 0.62D;
    private static final double WEAK_SPOT_HEIGHT_RADIUS_SCALE = 0.14D;
    private static final double WEAK_SPOT_TARGET_INFLATE = 0.06D;
    private static final double WEAK_SPOT_BOX_INFLATE = 0.08D;
    private static final double WEAK_SPOT_HIT_Y_TOLERANCE = 0.06D;
    private static final float HEAD_WEAK_SPOT_DAMAGE_MULT = 1.50F;
    private static final float CHEST_WEAK_SPOT_DAMAGE_MULT = 1.35F;
    private static final float LEGS_WEAK_SPOT_DAMAGE_MULT = 1.25F;
    private static final float GUARDED_WEAK_SPOT_DAMAGE_MULT = 1.20F;

    private FinesseUnarmedCombat() {
    }

    public static boolean canUseUnarmedCombat(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null || player.isSpectator()) {
            return false;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return false;
        }
        return SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.HAND_TO_HAND_COMBAT);
    }

    public static void onSuccessfulUnarmedStrike(ServerPlayer player, LevelProfile profile) {
        if (!canUseUnarmedCombat(player, profile)) {
            forget(player);
            return;
        }
        UUID id = player.getUUID();
        FinesseGuardState.markFistCombatUsed(player);
        boolean useOffhand = NEXT_OFFHAND_PUNCH.getOrDefault(id, false);
        NEXT_OFFHAND_PUNCH.put(id, !useOffhand);
        if (useOffhand) {
            broadcastOffhandSwing(player);
        }
    }

    public static void tryExtendedPunch(ServerPlayer player, int targetEntityId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!canUseUnarmedCombat(player, profile)) {
            return;
        }
        Entity entity = level.getEntity(targetEntityId);
        double meleePriorityReachSq = PARRY_CONE_MELEE_PRIORITY_REACH * PARRY_CONE_MELEE_PRIORITY_REACH;
        boolean meleePriority = entity instanceof LivingEntity livingPriority
                && livingPriority != player
                && livingPriority.isAttackable()
                && player.distanceToSqr(livingPriority) <= meleePriorityReachSq
                && player.hasLineOfSight(livingPriority);
        if (!meleePriority && FinesseProjectileParry.tryPunchSuspendedParry(player, level)) {
            onSuccessfulUnarmedStrike(player, profile);
            return;
        }
        if (entity instanceof Projectile projectile && FinesseProjectileParry.isParryableProjectile(projectile)) {
            if (FinesseProjectileParry.tryPunchParriedProjectile(player, projectile)) {
                onSuccessfulUnarmedStrike(player, profile);
            }
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < SERVER_ATTACK_READY_THRESHOLD) {
            return;
        }
        if (!(entity instanceof LivingEntity target) || target == player || !target.isAttackable()) {
            return;
        }
        double reach = EXTENDED_FIST_REACH + target.getBbWidth() * 0.5D;
        if (player.distanceToSqr(target) > reach * reach || !player.hasLineOfSight(target)) {
            return;
        }
        if (isAimingAtWeakSpot(player, target)) {
            WeakSpotZone zone = activeWeakSpotZone(target);
            if (zone != null) {
                WEAK_SPOT_INTENTS.put(player.getUUID(), new WeakSpotIntent(targetEntityId, zone, player.tickCount + WEAK_SPOT_INTENT_TICKS));
            }
        }
        player.attack(target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    public static WeakSpotResult applyWeakSpotStrike(ServerPlayer player, LevelProfile profile, LivingEntity target, float damage, boolean guarding) {
        if (!canUseUnarmedCombat(player, profile)) {
            forget(player);
            return new WeakSpotResult(damage, false);
        }
        if (target == null) {
            return new WeakSpotResult(damage, false);
        }
        WeakSpotZone zone = consumeWeakSpotIntent(player, target);
        if (zone == null && isAimingAtWeakSpot(player, target)) {
            zone = activeWeakSpotZone(target);
        }
        if (zone == null) {
            return new WeakSpotResult(damage, false);
        }
        FinesseWeakSpotEffects.onWeakSpotStrike(player, target, zone);
        recordWeakSpotHit(target, zone);
        float multiplier = guarding ? GUARDED_WEAK_SPOT_DAMAGE_MULT : weakSpotDamageMultiplier(zone);
        return new WeakSpotResult(damage * multiplier, true);
    }

    public static void markWeakSpotIntent(ServerPlayer player, int targetEntityId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!canUseUnarmedCombat(player, profile)) {
            return;
        }
        Entity entity = level.getEntity(targetEntityId);
        if (!(entity instanceof LivingEntity target) || target == player || !target.isAttackable()) {
            return;
        }
        double reach = WEAK_SPOT_REACH + target.getBbWidth() * 0.5D;
        if (player.distanceToSqr(target) > reach * reach || !player.hasLineOfSight(target)) {
            return;
        }
        if (isAimingAtWeakSpot(player, target)) {
            WeakSpotZone zone = activeWeakSpotZone(target);
            if (zone != null) {
                WEAK_SPOT_INTENTS.put(player.getUUID(), new WeakSpotIntent(targetEntityId, zone, player.tickCount + WEAK_SPOT_INTENT_TICKS));
            }
        }
    }

    public static @Nullable AABB weakSpotDebugBox(LivingEntity target) {
        WeakSpotZone zone = activeWeakSpotZone(target);
        return zone == null ? null : weakSpotBox(target, zone);
    }

    public static @Nullable Vec3 weakSpotDebugCenter(LivingEntity target) {
        WeakSpotZone zone = activeWeakSpotZone(target);
        return zone == null ? null : weakSpotCenter(target, zone);
    }

    public static @Nullable WeakSpotZone activeWeakSpotZone(LivingEntity target) {
        if (target instanceof Creeper creeper && FinesseWeakSpotEffects.isCreeperInImplosionWindow(creeper)) {
            return WeakSpotZone.CHEST;
        }
        if (target instanceof Creeper) {
            return null;
        }
        if (target instanceof EnderMan) {
            return WeakSpotZone.HEAD;
        }
        if (target.level().isClientSide()) {
            ClientWeakSpotState synced = CLIENT_WEAK_SPOTS.get(target.getId());
            if (synced != null) {
                return synced.hasWeakSpot() ? synced.zone() : null;
            }
            return initialWeakSpotState(target).hasWeakSpot() ? initialWeakSpotState(target).zone() : null;
        }
        WeakSpotState state = WEAK_SPOT_STATES.computeIfAbsent(target.getUUID(), ignored -> initialWeakSpotState(target));
        return state.hasWeakSpot() ? state.zone() : null;
    }

    public static void applyWeakSpotSync(SyncFinesseWeakSpotPayload payload) {
        WeakSpotZone zone = zoneFromOrdinal(payload.zoneOrdinal());
        CLIENT_WEAK_SPOTS.put(payload.entityId(), new ClientWeakSpotState(payload.hasWeakSpot(), zone));
    }

    public static void removeWeakSpotState(LivingEntity target) {
        if (target != null) {
            WEAK_SPOT_STATES.remove(target.getUUID());
            CLIENT_WEAK_SPOTS.remove(target.getId());
        }
    }

    public static double weakSpotDebugReach() {
        return WEAK_SPOT_REACH;
    }

    public static boolean isRayInWeakSpot(Vec3 eye, Vec3 look, LivingEntity target) {
        Vec3 end = eye.add(look.scale(WEAK_SPOT_REACH));
        AABB box = target.getBoundingBox().inflate(WEAK_SPOT_TARGET_INFLATE);
        WeakSpotZone zone = activeWeakSpotZone(target);
        if (zone == null) {
            return false;
        }
        AABB weakSpotBox = weakSpotBox(target, zone);
        Optional<Vec3> targetHit = box.clip(eye, end);
        if (targetHit.isPresent() && weakSpotBox.inflate(WEAK_SPOT_HIT_Y_TOLERANCE).contains(targetHit.get())) {
            return true;
        }
        if (weakSpotBox.clip(eye, end).isPresent()) {
            return true;
        }

        Vec3 weakSpot = weakSpotCenter(target, zone);
        Vec3 toWeakSpot = weakSpot.subtract(eye);
        double projectedDistance = toWeakSpot.dot(look);
        if (projectedDistance < 0.0D || projectedDistance > WEAK_SPOT_REACH) {
            return false;
        }
        Vec3 closestPointOnAim = eye.add(look.scale(projectedDistance));
        double radius = Math.max(WEAK_SPOT_MIN_RADIUS, Math.max(target.getBbWidth() * WEAK_SPOT_WIDTH_RADIUS_SCALE, target.getBbHeight() * WEAK_SPOT_HEIGHT_RADIUS_SCALE));
        return closestPointOnAim.distanceToSqr(weakSpot) <= radius * radius;
    }

    public static void playUnarmedHitSound(ServerPlayer attacker, LivingEntity target, boolean timedCritical) {
        if (attacker == null || target == null) {
            return;
        }
        attacker.connection.send(new ClientboundCustomPayloadPacket(
                timedCritical
                        ? new CameraShakePayload(8, 2.0F, 2.2F)
                        : new CameraShakePayload(5, 1.15F, 1.7F)
        ));
        if (timedCritical) {
            playWeakSpotCritFeedback(attacker, target);
            return;
        }
        attacker.level().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS,
                0.65F,
                0.92F + attacker.getRandom().nextFloat() * 0.12F
        );
    }

    private static void playWeakSpotCritFeedback(ServerPlayer attacker, LivingEntity target) {
        WeakSpotZone zone = activeWeakSpotZone(target);
        attacker.level().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS,
                1.15F,
                1.05F + attacker.getRandom().nextFloat() * 0.08F
        );
        attacker.level().playSound(
                null,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.7D,
                target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.25F,
                1.35F + attacker.getRandom().nextFloat() * 0.12F
        );
        attacker.level().playSound(
                attacker,
                attacker.getX(),
                attacker.getY(),
                attacker.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.1F,
                1.55F + attacker.getRandom().nextFloat() * 0.12F
        );
        if (target.level() instanceof ServerLevel serverLevel) {
            Vec3 center = zone == null ? target.position().add(0.0D, target.getBbHeight() * 0.72D, 0.0D) : weakSpotCenter(target, zone);
            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    center.x,
                    center.y,
                    center.z,
                    12,
                    target.getBbWidth() * 0.28D,
                    target.getBbHeight() * 0.12D,
                    target.getBbWidth() * 0.28D,
                    0.08D
            );
        }
    }

    private static void broadcastOffhandSwing(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcastAndSend(
                    player,
                    new ClientboundAnimatePacket(player, ClientboundAnimatePacket.SWING_OFF_HAND)
            );
        }
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            remove(player.getUUID());
        }
    }

    public static void remove(UUID playerId) {
        NEXT_OFFHAND_PUNCH.remove(playerId);
        WEAK_SPOT_INTENTS.remove(playerId);
    }

    private static void recordWeakSpotHit(LivingEntity target, WeakSpotZone hitZone) {
        if (!(target.level() instanceof ServerLevel)) {
            return;
        }
        WeakSpotState state = WEAK_SPOT_STATES.computeIfAbsent(target.getUUID(), ignored -> initialWeakSpotState(target));
        if (!state.hasWeakSpot()
                || state.zone() != hitZone
                || target instanceof Creeper creeper && FinesseWeakSpotEffects.isCreeperInImplosionWindow(creeper)) {
            return;
        }
        int hits = state.hits() + 1;
        if (hits < state.hitsBeforeSwap()) {
            WEAK_SPOT_STATES.put(target.getUUID(), new WeakSpotState(true, state.zone(), hits, state.hitsBeforeSwap()));
            return;
        }
        RandomSource random = target.getRandom();
        if (random.nextDouble() <= WEAK_SPOT_SWAP_CHANCE) {
            WeakSpotState next = new WeakSpotState(true, nextWeakSpotZone(target, random, state.zone()), 0, nextSwapThreshold(random));
            WEAK_SPOT_STATES.put(target.getUUID(), next);
            syncWeakSpot(target, next);
        } else {
            WEAK_SPOT_STATES.put(target.getUUID(), new WeakSpotState(true, state.zone(), 0, nextSwapThreshold(random)));
        }
    }

    private static WeakSpotState initialWeakSpotState(LivingEntity target) {
        if (target instanceof Creeper) {
            return new WeakSpotState(false, WeakSpotZone.CHEST, 0, 1);
        }
        if (target instanceof EnderMan) {
            return new WeakSpotState(true, WeakSpotZone.HEAD, 0, Integer.MAX_VALUE);
        }
        RandomSource random = RandomSource.create(target.getUUID().getMostSignificantBits() ^ target.getUUID().getLeastSignificantBits());
        if (random.nextDouble() < NO_WEAK_SPOT_CHANCE) {
            return new WeakSpotState(false, WeakSpotZone.CHEST, 0, nextSwapThreshold(random));
        }
        if (target instanceof AbstractSkeleton && random.nextDouble() < SKELETON_INITIAL_CHEST_CHANCE) {
            return new WeakSpotState(true, WeakSpotZone.CHEST, 0, nextSwapThreshold(random));
        }
        WeakSpotZone[] zones = WeakSpotZone.values();
        return new WeakSpotState(true, zones[random.nextInt(zones.length)], 0, nextSwapThreshold(random));
    }

    private static int nextSwapThreshold(RandomSource random) {
        return WEAK_SPOT_MIN_HITS_BEFORE_SWAP + random.nextInt(WEAK_SPOT_MAX_HITS_BEFORE_SWAP - WEAK_SPOT_MIN_HITS_BEFORE_SWAP + 1);
    }

    private static WeakSpotZone randomDifferentZone(RandomSource random, WeakSpotZone previous) {
        WeakSpotZone[] zones = WeakSpotZone.values();
        WeakSpotZone next = previous;
        for (int i = 0; i < 6 && next == previous; i++) {
            next = zones[random.nextInt(zones.length)];
        }
        if (next != previous) {
            return next;
        }
        return zones[(previous.ordinal() + 1) % zones.length];
    }

    private static WeakSpotZone nextWeakSpotZone(LivingEntity target, RandomSource random, WeakSpotZone previous) {
        if (target instanceof AbstractSkeleton && previous != WeakSpotZone.CHEST && random.nextDouble() < SKELETON_SWAP_TO_CHEST_CHANCE) {
            return WeakSpotZone.CHEST;
        }
        return randomDifferentZone(random, previous);
    }

    private static void syncWeakSpot(LivingEntity target, WeakSpotState state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new SyncFinesseWeakSpotPayload(target.getId(), state.zone().ordinal(), state.hasWeakSpot())
        );
    }

    private static WeakSpotZone zoneFromOrdinal(int ordinal) {
        WeakSpotZone[] zones = WeakSpotZone.values();
        if (ordinal < 0 || ordinal >= zones.length) {
            return WeakSpotZone.CHEST;
        }
        return zones[ordinal];
    }

    private static WeakSpotZone consumeWeakSpotIntent(ServerPlayer player, LivingEntity target) {
        WeakSpotIntent intent = WEAK_SPOT_INTENTS.get(player.getUUID());
        if (intent == null) {
            return null;
        }
        if (player.tickCount > intent.expiresAtTick || target.getId() != intent.targetEntityId) {
            WEAK_SPOT_INTENTS.remove(player.getUUID());
            return null;
        }
        WEAK_SPOT_INTENTS.remove(player.getUUID());
        return intent.zone();
    }

    private static boolean isAimingAtWeakSpot(ServerPlayer player, LivingEntity target) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        return isRayInWeakSpot(eye, look, target);
    }

    private static AABB weakSpotBox(LivingEntity target, WeakSpotZone zone) {
        AABB box = target.getBoundingBox().inflate(WEAK_SPOT_TARGET_INFLATE);
        double width = Math.max(0.28D, box.getXsize());
        double depth = Math.max(0.28D, box.getZsize());
        double height = box.getYsize();
        Vec3 center = weakSpotCenter(target, zone);
        double halfX = width * switch (zone) {
            case HEAD -> 0.42D;
            case CHEST -> 0.52D;
            case LEGS -> 0.46D;
        };
        double halfY = height * switch (zone) {
            case HEAD -> 0.11D;
            case CHEST -> 0.15D;
            case LEGS -> 0.17D;
        };
        double halfZ = depth * switch (zone) {
            case HEAD -> 0.42D;
            case CHEST -> 0.50D;
            case LEGS -> 0.46D;
        };
        return new AABB(
                center.x - halfX,
                center.y - halfY,
                center.z - halfZ,
                center.x + halfX,
                center.y + halfY,
                center.z + halfZ
        ).inflate(WEAK_SPOT_BOX_INFLATE);
    }

    private static Vec3 weakSpotCenter(LivingEntity target, WeakSpotZone zone) {
        AABB box = target.getBoundingBox().inflate(WEAK_SPOT_TARGET_INFLATE);
        double yFraction = switch (zone) {
            case HEAD -> 0.84D;
            case CHEST -> 0.58D;
            case LEGS -> 0.28D;
        };
        return new Vec3(target.getX(), box.minY + box.getYsize() * yFraction, target.getZ());
    }

    private static float weakSpotDamageMultiplier(WeakSpotZone zone) {
        return switch (zone) {
            case HEAD -> HEAD_WEAK_SPOT_DAMAGE_MULT;
            case CHEST -> CHEST_WEAK_SPOT_DAMAGE_MULT;
            case LEGS -> LEGS_WEAK_SPOT_DAMAGE_MULT;
        };
    }

    public record WeakSpotResult(float damage, boolean timedCritical) {
    }

    public enum WeakSpotZone {
        HEAD,
        CHEST,
        LEGS
    }

    private record WeakSpotIntent(int targetEntityId, WeakSpotZone zone, int expiresAtTick) {
    }

    private record WeakSpotState(boolean hasWeakSpot, WeakSpotZone zone, int hits, int hitsBeforeSwap) {
    }

    private record ClientWeakSpotState(boolean hasWeakSpot, WeakSpotZone zone) {
    }
}
