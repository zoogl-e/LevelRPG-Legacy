package net.zoogle.levelrpg.finesse;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.net.payload.SyncFinesseGuardVisualPayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FinesseGuardState {
    private static final ResourceLocation HANDS_UP_MOVE_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "hands_up_guard_move_slow");
    private static final double HANDS_UP_MOVE_SPEED_MULTIPLIER = -0.45D;
    private static final int GUARD_PACKET_TIMEOUT_TICKS = 40;
    private static final int FIST_COMBAT_TIMEOUT_TICKS = 100;
    private static final double HANDS_UP_THREAT_REACH = 24.0D;
    private static final double HANDS_UP_NEARBY_THREAT_RADIUS = 4.0D;
    private static final double HANDS_UP_PROJECTILE_THREAT_RADIUS = 16.0D;
    private static final double SWIM_GUARD_HORIZONTAL_DAMPING = 0.72D;
    private static final double SWIM_GUARD_VERTICAL_DAMPING = 0.84D;

    private static final Map<UUID, GuardState> GUARDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> FIST_COMBAT_USED_TICK = new ConcurrentHashMap<>();

    private FinesseGuardState() {
    }

    public static void setGuarding(ServerPlayer player, boolean guarding) {
        if (player == null) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        GuardState previous = GUARDING.get(player.getUUID());
        if (!guarding || !canGuard(player, profile, previous != null)) {
            GuardState removed = GUARDING.remove(player.getUUID());
            if (removed != null) {
                syncGuardVisual(player, false);
            }
            clearMovementSlow(player);
            return;
        }
        FIST_COMBAT_USED_TICK.put(player.getUUID(), player.tickCount);
        int now = player.tickCount;
        if (previous == null) {
            GUARDING.put(player.getUUID(), new GuardState(now, now));
            syncGuardVisual(player, true);
        } else {
            GUARDING.put(player.getUUID(), new GuardState(previous.firstGuardTick(), now));
        }
        applyMovementSlow(player);
    }

    public static void tick(ServerPlayer player, LevelProfile profile) {
        if (player == null) {
            return;
        }
        GuardState state = GUARDING.get(player.getUUID());
        if (state == null) {
            clearMovementSlow(player);
            return;
        }
        int passed = player.tickCount - state.lastPacketTick();
        if (passed < 0 || passed > GUARD_PACKET_TIMEOUT_TICKS || !canGuard(player, profile, true)) {
            GUARDING.remove(player.getUUID());
            syncGuardVisual(player, false);
            clearMovementSlow(player);
            return;
        }
        FIST_COMBAT_USED_TICK.put(player.getUUID(), player.tickCount);
        applyMovementSlow(player);
        applySwimDamping(player);
    }

    public static boolean markFistCombatUsed(ServerPlayer player) {
        if (player != null && canUseFistCombat(player, LevelProfile.get(player))) {
            FIST_COMBAT_USED_TICK.put(player.getUUID(), player.tickCount);
            return true;
        }
        return false;
    }

    public static boolean shouldReserveSelectedEmptyHandSlot(ServerPlayer player) {
        return player != null
                && player.getInventory().getSelected().isEmpty()
                && player.getOffhandItem().isEmpty()
                && canUseFistCombat(player, LevelProfile.get(player))
                && (hasRecentFistCombat(player) || isGuarding(player));
    }

    public static boolean isGuarding(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        GuardState state = GUARDING.get(player.getUUID());
        if (state == null) {
            return false;
        }
        int passed = player.tickCount - state.lastPacketTick();
        return passed >= 0 && passed <= GUARD_PACKET_TIMEOUT_TICKS
                && canGuard(player, LevelProfile.get(player), true);
    }

    /**
     * Ticks since the current hands-up guard session began (first guarding packet of this stance).
     * Returns a large value if not guarding.
     */
    public static int ticksSinceGuardStarted(ServerPlayer player) {
        if (player == null) {
            return Integer.MAX_VALUE;
        }
        GuardState state = GUARDING.get(player.getUUID());
        if (state == null || !isGuarding(player)) {
            return Integer.MAX_VALUE;
        }
        int passed = player.tickCount - state.firstGuardTick();
        return passed >= 0 ? passed : Integer.MAX_VALUE;
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            GUARDING.remove(playerId);
            FIST_COMBAT_USED_TICK.remove(playerId);
        }
    }

    private static boolean canGuard(ServerPlayer player, LevelProfile profile, boolean alreadyGuarding) {
        return player != null
                && profile != null
                && !player.isSpectator()
                && !player.isCreative()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && canUseFistCombat(player, profile)
                && SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.HANDS_UP)
                && (alreadyGuarding || hasRecentFistCombat(player) || hasImmediateHandsUpThreat(player));
    }

    private static boolean canUseFistCombat(ServerPlayer player, LevelProfile profile) {
        return player != null
                && profile != null
                && !player.isSpectator()
                && !player.isCreative()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.HAND_TO_HAND_COMBAT);
    }

    private static boolean hasRecentFistCombat(ServerPlayer player) {
        Integer tick = FIST_COMBAT_USED_TICK.get(player.getUUID());
        if (tick == null) {
            return false;
        }
        int passed = player.tickCount - tick;
        return passed >= 0 && passed <= FIST_COMBAT_TIMEOUT_TICKS;
    }

    private static boolean hasImmediateHandsUpThreat(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(HANDS_UP_THREAT_REACH));
        AABB aimedBox = player.getBoundingBox().expandTowards(look.scale(HANDS_UP_THREAT_REACH)).inflate(1.5D);
        EntityHitResult aimed = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eye,
                end,
                aimedBox,
                entity -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && entity instanceof Enemy
        );
        if (aimed != null) {
            return true;
        }
        AABB nearby = player.getBoundingBox().inflate(HANDS_UP_NEARBY_THREAT_RADIUS);
        if (!player.level().getEntities(player, nearby, entity -> entity instanceof Enemy).isEmpty()) {
            return true;
        }
        AABB projectileBox = player.getBoundingBox().inflate(HANDS_UP_PROJECTILE_THREAT_RADIUS);
        return !player.level().getEntitiesOfClass(Projectile.class, projectileBox, projectile -> {
            if (!FinesseProjectileParry.isParryableProjectile(projectile) || projectile.getOwner() == player) {
                return false;
            }
            Vec3 movement = projectile.getDeltaMovement();
            Vec3 toPlayer = player.getEyePosition().subtract(projectile.position());
            if (movement.lengthSqr() < 0.0025D) {
                return false;
            }
            if (toPlayer.lengthSqr() < 0.0001D) {
                return false;
            }
            return movement.normalize().dot(toPlayer.normalize()) > 0.55D;
        }).isEmpty();
    }

    private static void applyMovementSlow(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                HANDS_UP_MOVE_SLOW_ID,
                HANDS_UP_MOVE_SPEED_MULTIPLIER,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void clearMovementSlow(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(HANDS_UP_MOVE_SLOW_ID);
        }
    }

    private static void applySwimDamping(ServerPlayer player) {
        if (!player.isInWaterOrBubble() && !player.isSwimming()) {
            return;
        }
        var movement = player.getDeltaMovement();
        player.setDeltaMovement(
                movement.x * SWIM_GUARD_HORIZONTAL_DAMPING,
                movement.y * SWIM_GUARD_VERTICAL_DAMPING,
                movement.z * SWIM_GUARD_HORIZONTAL_DAMPING
        );
        player.hurtMarked = true;
    }

    private static void syncGuardVisual(ServerPlayer player, boolean guarding) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new SyncFinesseGuardVisualPayload(player.getId(), guarding)
        );
    }

    private record GuardState(int firstGuardTick, int lastPacketTick) {
    }
}
