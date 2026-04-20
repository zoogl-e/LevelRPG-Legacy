package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.zoogle.levelrpg.client.RecipeBookLockState;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import net.zoogle.levelrpg.util.RecipeUnlockText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
    private static final int LOCK_OVERLAY_COLOR = 0xAA7A0000;
    private static final int LOCK_MARKER_COLOR = 0xFFF5D76E;

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void levelrpg$renderLockedRecipeBookButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RecipeButton self = (RecipeButton) (Object) this;
        RecipeUnlockService.UnlockCheckResult unlockCheck = RecipeBookLockState.checkAccess(self.getRecipe());
        if (unlockCheck == null || unlockCheck.unlocked()) {
            return;
        }

        int x = self.getX();
        int y = self.getY();
        guiGraphics.fill(x, y, x + self.getWidth(), y + self.getHeight(), LOCK_OVERLAY_COLOR);
        guiGraphics.drawString(Minecraft.getInstance().font, "!", x + 10, y + 8, LOCK_MARKER_COLOR, false);
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void levelrpg$appendLockedRecipeBookTooltip(CallbackInfoReturnable<List<Component>> cir) {
        RecipeButton self = (RecipeButton) (Object) this;
        RecipeUnlockService.UnlockCheckResult unlockCheck = RecipeBookLockState.checkAccess(self.getRecipe());
        if (unlockCheck == null || unlockCheck.unlocked()) {
            return;
        }

        ArrayList<Component> lines = new ArrayList<>(cir.getReturnValue());
        lines.addAll(RecipeUnlockText.tooltipLines(unlockCheck));
        cir.setReturnValue(List.copyOf(lines));
    }
}
