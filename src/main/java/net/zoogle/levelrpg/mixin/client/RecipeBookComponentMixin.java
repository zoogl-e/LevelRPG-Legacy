package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.zoogle.levelrpg.client.RecipeBookLockState;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import net.zoogle.levelrpg.util.RecipeUnlockText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow protected Minecraft minecraft;

    @Inject(
            method = "mouseClicked(DDI)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/RecipeHolder;Z)V"
            ),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void levelrpg$blockLockedRecipeBookPlacement(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir,
            RecipeHolder<?> recipe,
            RecipeCollection collection
    ) {
        RecipeUnlockService.UnlockCheckResult unlockCheck = RecipeBookLockState.checkAccess(recipe);
        if (unlockCheck == null || unlockCheck.unlocked()) {
            return;
        }

        if (this.minecraft.player != null) {
            Component message = RecipeUnlockText.deniedCraftMessage(unlockCheck);
            this.minecraft.player.displayClientMessage(message, true);
        }
        cir.setReturnValue(false);
    }
}
