package net.zoogle.levelrpg.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.model.BookModel;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public class ClientLayers {
    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BookModel.LAYER_LOCATION, BookModel::createBodyLayer);
    }
}