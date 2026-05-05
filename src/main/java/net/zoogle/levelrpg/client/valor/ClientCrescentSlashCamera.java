package net.zoogle.levelrpg.client.valor;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Applies a smooth 360° horizontal camera / body yaw sweep for Crescent Slash (driven by server payload).
 */
@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class ClientCrescentSlashCamera {
    private static int ticksRemaining;
    private static int totalTicks;

    private ClientCrescentSlashCamera() {
    }

    public static void startSpin(int durationTicks) {
        int n = Mth.clamp(durationTicks, 4, 80);
        ticksRemaining = n;
        totalTicks = n;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksRemaining <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ticksRemaining = 0;
            totalTicks = 0;
            return;
        }
        int tickIndex = totalTicks - ticksRemaining;
        float prevT = totalTicks <= 0 ? 0.0F : Mth.clamp(tickIndex / (float) totalTicks, 0.0F, 1.0F);
        float nextT = totalTicks <= 0 ? 1.0F : Mth.clamp((tickIndex + 1) / (float) totalTicks, 0.0F, 1.0F);
        float prevEase = prevT * prevT * (3.0F - 2.0F * prevT);
        float nextEase = nextT * nextT * (3.0F - 2.0F * nextT);
        float deltaYaw = 360.0F * (nextEase - prevEase);
        mc.player.yRotO = mc.player.getYRot();
        mc.player.setYRot(mc.player.getYRot() + deltaYaw);
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            totalTicks = 0;
        }
    }
}
