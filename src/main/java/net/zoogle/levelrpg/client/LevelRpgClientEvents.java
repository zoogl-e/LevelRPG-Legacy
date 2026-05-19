package net.zoogle.levelrpg.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.render.TheIndexBlockEntityRenderer;
import net.zoogle.levelrpg.registry.LevelRpgBlockEntities;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LevelRpgClientEvents {
    private LevelRpgClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LevelRpgBlockEntities.THE_INDEX.get(), TheIndexBlockEntityRenderer::new);
    }
}
