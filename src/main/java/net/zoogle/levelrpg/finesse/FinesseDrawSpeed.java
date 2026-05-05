package net.zoogle.levelrpg.finesse;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.Set;

public final class FinesseDrawSpeed {
    private static final double QUICK_DRAW_MIN_MULTIPLIER = 1.10D;
    private static final double QUICK_DRAW_MAX_MULTIPLIER = 1.75D;

    private FinesseDrawSpeed() {
    }

    public static int effectiveBowChargeTicks(LivingEntity entity, int chargeTicks) {
        if (!hasQuickDraw(entity)) {
            return chargeTicks;
        }
        return Math.max(chargeTicks, (int) Math.ceil(chargeTicks * drawSpeedMultiplier(entity)));
    }

    public static int effectiveCrossbowChargeDuration(LivingEntity entity, int chargeDurationTicks) {
        if (!hasQuickDraw(entity)) {
            return chargeDurationTicks;
        }
        return Math.max(1, (int) Math.floor(chargeDurationTicks / drawSpeedMultiplier(entity)));
    }

    private static double drawSpeedMultiplier(LivingEntity entity) {
        double rhythmFraction = rhythmFraction(entity);
        return QUICK_DRAW_MIN_MULTIPLIER
                + ((QUICK_DRAW_MAX_MULTIPLIER - QUICK_DRAW_MIN_MULTIPLIER) * rhythmFraction);
    }

    private static double rhythmFraction(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            LevelProfile profile = LevelProfile.get(player);
            double max = profile.gauges.getMax(player, profile, GaugeRegistry.RHYTHM);
            if (max <= 0.0D) {
                return 0.0D;
            }
            return clamp01(profile.gauges.getValue(GaugeRegistry.RHYTHM) / max);
        }
        if (entity != null && entity.level().isClientSide()) {
            for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
                if (GaugeRegistry.RHYTHM.equals(gauge.id()) && gauge.max() > 0.0D) {
                    return clamp01(gauge.value() / gauge.max());
                }
            }
        }
        return 0.0D;
    }

    private static boolean hasQuickDraw(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return entity != null && entity.level().isClientSide() && clientHasQuickDraw();
        }
        return SkillUnlockQuery.hasNode(LevelProfile.get(player), FinesseNodeIds.SKILL, FinesseNodeIds.QUICK_DRAW);
    }

    private static boolean clientHasQuickDraw() {
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
        return unlocked.contains(FinesseNodeIds.QUICK_DRAW)
                || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.QUICK_DRAW);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
