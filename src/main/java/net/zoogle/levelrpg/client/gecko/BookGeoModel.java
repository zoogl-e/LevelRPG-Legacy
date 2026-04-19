package net.zoogle.levelrpg.client.gecko;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import software.bernie.geckolib.model.GeoModel;

public class BookGeoModel extends GeoModel<DummyAnimatable> {
    // Make sure these match your actual assets
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "geo/enchiridion.geo.json");
    private static final ResourceLocation BASE_ATLAS =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "textures/gui/book_tex_2.png");
    private static final ResourceLocation ANIMS =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "animations/model.animation.json");

    @Override
    public ResourceLocation getModelResource(DummyAnimatable animatable) {
        System.out.println("[LevelRPG] BookGeoModel.getModelResource -> " + MODEL);
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DummyAnimatable animatable) {
        ResourceLocation dyn = animatable.getOverrideTexture();
        ResourceLocation chosen = (dyn != null) ? dyn : BASE_ATLAS;
        System.out.println("[LevelRPG] BookGeoModel.getTextureResource -> " + chosen);
        return chosen;
    }

    @Override
    public ResourceLocation getAnimationResource(DummyAnimatable animatable) {
        System.out.println("[LevelRPG] BookGeoModel.getAnimationResource -> " + ANIMS);
        return ANIMS;
    }
}
