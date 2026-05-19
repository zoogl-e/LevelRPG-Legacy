package net.zoogle.levelrpg.client.render;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.block.entity.TheIndexBlockEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class TheIndexGeoModel extends GeoModel<TheIndexBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "geo/block/the_index.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "textures/block/the_index.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "animations/block/the_index.animation.json");

    @Override
    public ResourceLocation getModelResource(TheIndexBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TheIndexBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TheIndexBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(TheIndexBlockEntity animatable, long instanceId, AnimationState<TheIndexBlockEntity> animationState) {
        animatable.updateClientSettleState();
        float idleWeight = 1.0F - animatable.settleStrength();
        if (animatable.isSettled()) {
            idleWeight = 0.0F;
        }
        settleBone("dais", idleWeight);
        settleBone("pedestal", idleWeight);
    }

    private void settleBone(String boneName, float idleWeight) {
        getBone(boneName).ifPresent(bone -> applySettle(bone, idleWeight));
    }

    private static void applySettle(GeoBone bone, float idleWeight) {
        bone.setRotX(bone.getRotX() * idleWeight);
        bone.setRotY(bone.getRotY() * idleWeight);
        bone.setRotZ(bone.getRotZ() * idleWeight);
    }
}
