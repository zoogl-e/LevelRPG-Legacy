package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.zoogle.levelrpg.client.finesse.FinesseThirdPersonGuardState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelGuardPoseMixin {
    @Shadow
    public ModelPart leftSleeve;

    @Shadow
    public ModelPart rightSleeve;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void levelrpg$applyFinesseGuardPose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        float progress = FinesseThirdPersonGuardState.progress(entity.getId());
        if (progress <= 0.001F) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        applyArmPose(model.rightArm, true, progress);
        applyArmPose(model.leftArm, false, progress);
        this.rightSleeve.copyFrom(model.rightArm);
        this.leftSleeve.copyFrom(model.leftArm);
    }

    private static void applyArmPose(ModelPart arm, boolean right, float progress) {
        float side = right ? 1.0F : -1.0F;
        arm.xRot = lerp(progress, arm.xRot, -1.22F);
        arm.yRot = lerp(progress, arm.yRot, side * -0.54F);
        arm.zRot = lerp(progress, arm.zRot, side * 0.34F);
    }

    private static float lerp(float progress, float from, float to) {
        return from + (to - from) * progress;
    }
}
