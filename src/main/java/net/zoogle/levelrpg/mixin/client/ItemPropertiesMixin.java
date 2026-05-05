package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.zoogle.levelrpg.finesse.FinesseDrawSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemProperties.class)
public abstract class ItemPropertiesMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void levelrpg$registerQuickDrawBowPullProperty(CallbackInfo ci) {
        ItemProperties.register(Items.BOW, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
            if (entity == null || entity.getUseItem() != stack) {
                return 0.0F;
            }
            int chargeTicks = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
            return FinesseDrawSpeed.effectiveBowChargeTicks(entity, chargeTicks) / 20.0F;
        });
    }
}
