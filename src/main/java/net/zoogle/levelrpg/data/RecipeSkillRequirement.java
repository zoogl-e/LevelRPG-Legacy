package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

/**
 * One canonical skill threshold required to unlock a recipe.
 */
public record RecipeSkillRequirement(ResourceLocation skillId, int minLevel) {
    public RecipeSkillRequirement {
        minLevel = Math.max(0, minLevel);
    }
}
