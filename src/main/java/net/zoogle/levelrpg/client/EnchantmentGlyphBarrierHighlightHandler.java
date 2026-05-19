package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.registry.LevelRpgBlocks;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class EnchantmentGlyphBarrierHighlightHandler {
    private EnchantmentGlyphBarrierHighlightHandler() {
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = event.getTarget().getBlockPos();
        if (level.getBlockState(pos).is(LevelRpgBlocks.ENCHANTMENT_GLYPH_BARRIER.get())) {
            event.setCanceled(true);
        }
    }
}
