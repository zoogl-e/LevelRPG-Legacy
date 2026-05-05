package net.zoogle.levelrpg.client.valor;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Adds an exaggerated first-person weapon swell while Reckless Strike is charging.
 */
@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class RecklessChargeHandEffect {
    private RecklessChargeHandEffect() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!ClientRecklessChargeState.isActive()) {
            return;
        }
        float progress = (float) ClientRecklessChargeState.progress(minecraft.player.tickCount);
        if (progress <= 0.01F) {
            return;
        }
        float scale = 1.0F + progress * 0.52F;
        event.getPoseStack().translate(0.0F, -0.13F * progress, -0.10F * progress);
        event.getPoseStack().scale(scale, scale, scale);
    }
}
