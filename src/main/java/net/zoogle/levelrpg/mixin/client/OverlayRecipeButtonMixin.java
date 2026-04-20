package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.zoogle.levelrpg.client.RecipeBookLockState;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin {
    private static final int LOCK_OVERLAY_COLOR = 0xAA7A0000;
    private static final int LOCK_MARKER_COLOR = 0xFFF5D76E;

    @Shadow @Final
    RecipeHolder<?> recipe;

    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void levelrpg$renderLockedOverlayRecipeButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RecipeUnlockService.UnlockCheckResult unlockCheck = RecipeBookLockState.checkAccess(this.recipe);
        if (unlockCheck == null || unlockCheck.unlocked()) {
            return;
        }

        int x = this.getX();
        int y = this.getY();
        guiGraphics.fill(x, y, x + this.getWidth(), y + this.getHeight(), LOCK_OVERLAY_COLOR);
        guiGraphics.drawString(Minecraft.getInstance().font, "!", x + 9, y + 7, LOCK_MARKER_COLOR, false);
    }
}
