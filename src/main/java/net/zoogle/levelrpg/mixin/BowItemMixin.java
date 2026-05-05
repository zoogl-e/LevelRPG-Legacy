package net.zoogle.levelrpg.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zoogle.levelrpg.finesse.FinesseDrawSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Redirect(
            method = "releaseUsing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F")
    )
    private float levelrpg$applyQuickDrawBowPower(int chargeTicks, ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        return BowItem.getPowerForTime(FinesseDrawSpeed.effectiveBowChargeTicks(entityLiving, chargeTicks));
    }
}
