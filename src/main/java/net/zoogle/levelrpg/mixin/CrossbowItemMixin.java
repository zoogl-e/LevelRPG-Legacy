package net.zoogle.levelrpg.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.finesse.FinesseDrawSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void levelrpg$applyQuickDrawCrossbowDuration(ItemStack stack, LivingEntity shooter, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FinesseDrawSpeed.effectiveCrossbowChargeDuration(shooter, cir.getReturnValue()));
    }
}

