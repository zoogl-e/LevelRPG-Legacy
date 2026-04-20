package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.progression.RecipeUnlockService;

/**
 * Client-side recipe book lock lookup backed by synced profile skill state.
 */
public final class RecipeBookLockState {
    private RecipeBookLockState() {}

    public static RecipeUnlockService.UnlockCheckResult checkAccess(RecipeHolder<?> recipe) {
        if (recipe == null || !ClientProfileCache.isReady()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        ResourceLocation recipeId = recipe.id();
        return RecipeUnlockService.checkAccess(recipeId, ClientProfileCache.getSkillsView());
    }

    public static boolean isLocked(RecipeHolder<?> recipe) {
        RecipeUnlockService.UnlockCheckResult check = checkAccess(recipe);
        return check != null && !check.unlocked();
    }
}
