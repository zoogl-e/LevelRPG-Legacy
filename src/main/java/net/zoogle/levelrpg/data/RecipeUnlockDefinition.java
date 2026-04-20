package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Data-driven recipe unlock definition keyed by recipe id.
 */
public record RecipeUnlockDefinition(
        ResourceLocation recipeId,
        List<RecipeSkillRequirement> requirements
) {
    public RecipeUnlockDefinition {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }
}
