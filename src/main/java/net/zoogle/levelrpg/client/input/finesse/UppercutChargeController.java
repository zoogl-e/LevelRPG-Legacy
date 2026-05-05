package net.zoogle.levelrpg.client.input.finesse;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.net.payload.RequestUppercutPayload;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;

public final class UppercutChargeController {
    private static boolean uppercutCharging;
    private static int uppercutChargeStartTick;
    private static boolean uppercutChargePending;
    private static int uppercutPressClientTick;

    private UppercutChargeController() {}

    public static void resetForPlayerTickRollback() {
        uppercutChargeStartTick = 0;
        uppercutPressClientTick = 0;
    }

    public static float getChargeProgress(net.minecraft.client.player.AbstractClientPlayer player, float partialTicks) {
        if (!uppercutCharging || player == null) return 0.0F;
        float ticks = (player.tickCount - uppercutChargeStartTick) + partialTicks;
        return Math.min(1.0F, ticks / 20.0F);
    }

    public static boolean isCharging() {
        return uppercutCharging;
    }

    public static void onLeftMouseButton(Minecraft mc, int action) {
        if (action == 1) {
            if (canUseUppercut(mc) && mc.options.keyUse.isDown()) {
                uppercutChargePending = true;
                uppercutPressClientTick = mc.player.tickCount;
            }
        } else if (action == 0) {
            uppercutChargePending = false;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (uppercutChargePending) {
            if (!mc.options.keyAttack.isDown() || !canUseUppercut(mc)) {
                uppercutChargePending = false;
                uppercutCharging = false;
            } else if (mc.player.tickCount - uppercutPressClientTick >= 4) {
                if (!uppercutCharging) {
                    uppercutCharging = true;
                    uppercutChargeStartTick = mc.player.tickCount;
                }
            }
        }
        if (uppercutCharging && mc.level != null) {
            if (!mc.options.keyAttack.isDown() || !canUseUppercut(mc)) {
                uppercutChargePending = false;
                uppercutCharging = false;
                PacketDistributor.sendToServer(new RequestUppercutPayload(mc.player.tickCount - uppercutChargeStartTick));
                return;
            }
            double progress = Math.max(0.0, Math.min(1.0, (mc.player.tickCount - uppercutChargeStartTick) / 20.0));
            net.zoogle.levelrpg.client.ui.ChargeUiController.offer("uppercut", "Uppercut", progress, 0xFFBB00);

            if (mc.player.tickCount % 2 == 0) {
                float offset = mc.player.getBbHeight() * 0.45F;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        mc.player.getX() + (mc.player.getRandom().nextDouble() - 0.5D) * 0.6D,
                        mc.player.getY() + offset + (mc.player.getRandom().nextDouble() - 0.5D) * 0.5D,
                        mc.player.getZ() + (mc.player.getRandom().nextDouble() - 0.5D) * 0.6D,
                        0.0D, 0.05D, 0.0D);
            }
        }
    }

    private static boolean canUseUppercut(Minecraft mc) {
        if (!HandsUpGuardController.shouldUseHandsUpGuard(mc)) return false;
        var unlocked = net.zoogle.levelrpg.client.data.ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
        return unlocked.contains(FinesseNodeIds.UPPERCUT)
                || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.UPPERCUT);
    }
}
