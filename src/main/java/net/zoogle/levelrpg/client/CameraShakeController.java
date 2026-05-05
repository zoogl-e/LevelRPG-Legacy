package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class CameraShakeController {
    private static int ticksRemaining;
    private static float strength;
    private static int cinematicTick;

    private CameraShakeController() {
    }

    public static void apply(CameraShakePayload payload) {
        if (payload.intensity() <= 0.0F || payload.durationTicks() <= 0) {
            return;
        }
        ticksRemaining = Math.max(ticksRemaining, payload.durationTicks());
        strength = Math.max(strength, payload.intensity());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        cinematicTick++;
        if (ticksRemaining <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            ticksRemaining = 0;
            strength = 0.0F;
            return;
        }
        ticksRemaining--;
        strength = Math.max(0.0F, strength * 0.87F);
        if (ticksRemaining <= 0) {
            strength = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ticksRemaining <= 0 || strength <= 0.01F) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float progress = Math.min(1.0F, (ticksRemaining + partial) / 14.0F);
        float envelope = progress * progress;
        float phase = (cinematicTick * 0.9F) + (partial * 1.8F);
        float shakeX = Mth.sin(phase * 2.7F) * strength * envelope;
        float shakeY = Mth.cos((phase * 3.4F) + 0.55F) * strength * 0.7F * envelope;

        event.setYaw(event.getYaw() + shakeX * 0.22F);
        event.setPitch(event.getPitch() + shakeY * 0.22F);
    }
}
