package net.zoogle.levelrpg.client.input.finesse;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.client.finesse.FinesseFirstPersonUnarmedRenderer;
import net.zoogle.levelrpg.finesse.FinesseProjectileParry;
import net.zoogle.levelrpg.net.payload.SetHandsUpGuardingPayload;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;

public final class HandsUpGuardController {
    private static final double HANDS_UP_OPENER_THREAT_REACH = 6.0D;
    private static final double HANDS_UP_OPENER_NEARBY_RADIUS = 4.0D;
    private static final double HANDS_UP_OPENER_PROJECTILE_RADIUS = 5.5D;
    private static final int HANDS_UP_GUARD_KEEPALIVE_TICKS = 5;

    private static boolean handsUpGuardSent;
    private static int lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
    private static int lastHandsUpGuardingClientTick = Integer.MIN_VALUE;

    private HandsUpGuardController() {}

    public static void resetForPlayerTickRollback() {
        lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
        lastHandsUpGuardingClientTick = Integer.MIN_VALUE;
    }

    public static int lastGuardingClientTick() {
        return lastHandsUpGuardingClientTick;
    }

    public static boolean shouldUseHandsUpGuard(Minecraft mc) {
        if (mc.player == null || mc.player.isSpectator()) {
            return false;
        }
        if (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()) {
            return false;
        }
        var unlocked = net.zoogle.levelrpg.client.data.ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
        boolean hasHandsUp = unlocked.contains(FinesseNodeIds.HANDS_UP)
                || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.HANDS_UP);
        return hasHandsUp
                && (FinesseFirstPersonUnarmedRenderer.isFistCombatActive(mc.player)
                || hasHandsUpOpenerThreat(mc));
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            handsUpGuardSent = false;
            lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
            return;
        }
        boolean guarding = mc.screen == null
                && mc.options != null
                && mc.options.keyUse.isDown()
                && shouldUseHandsUpGuard(mc);
        int tick = mc.player.tickCount;
        if (guarding) {
            lastHandsUpGuardingClientTick = tick;
            FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(tick);
        }
        boolean needsKeepalive = guarding
                && tick - lastHandsUpGuardPacketTick >= HANDS_UP_GUARD_KEEPALIVE_TICKS;
        if (guarding != handsUpGuardSent || needsKeepalive) {
            PacketDistributor.sendToServer(new SetHandsUpGuardingPayload(guarding));
            handsUpGuardSent = guarding;
            lastHandsUpGuardPacketTick = tick;
        }
    }

    private static boolean hasHandsUpOpenerThreat(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(HANDS_UP_OPENER_THREAT_REACH));
        EntityHitResult aimed = ProjectileUtil.getEntityHitResult(
                mc.level,
                mc.player,
                eye,
                end,
                mc.player.getBoundingBox().expandTowards(look.scale(HANDS_UP_OPENER_THREAT_REACH)).inflate(1.5D),
                entity -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && entity instanceof Enemy
        );
        if (aimed != null) {
            return true;
        }
        AABB nearby = mc.player.getBoundingBox().inflate(HANDS_UP_OPENER_NEARBY_RADIUS);
        if (!mc.level.getEntities(mc.player, nearby, entity -> entity instanceof Enemy).isEmpty()) {
            return true;
        }
        AABB projectileBox = mc.player.getBoundingBox().inflate(HANDS_UP_OPENER_PROJECTILE_RADIUS);
        return !mc.level.getEntitiesOfClass(Projectile.class, projectileBox, projectile -> {
            if (!FinesseProjectileParry.isParryableProjectile(projectile) || projectile.getOwner() == mc.player) {
                return false;
            }
            Vec3 movement = projectile.getDeltaMovement();
            Vec3 toPlayer = mc.player.getEyePosition().subtract(projectile.position());
            return movement.lengthSqr() >= 0.0025D
                    && toPlayer.lengthSqr() >= 0.0001D
                    && movement.normalize().dot(toPlayer.normalize()) > 0.55D;
        }).isEmpty();
    }
}
