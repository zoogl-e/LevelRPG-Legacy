package net.zoogle.levelrpg.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityInvisibleToMixin {
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void levelrpg$ghostStepTranslucent(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this) instanceof LivingEntity living) {
            MobEffectInstance effect = living.getEffect(MobEffects.INVISIBILITY);
            if (effect != null && effect.getDuration() <= 15 && !effect.isVisible()) {
                cir.setReturnValue(false); // Make them translucent instead of fully invisible!
            }
        }
    }
}
