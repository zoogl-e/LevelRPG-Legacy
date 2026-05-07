package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

/**
 * One canonical <b>discipline</b> threshold required to unlock a recipe. The component {@code skillId} is the
 * discipline {@link ResourceLocation}; JSON may use {@code "skill"} or alias {@code "discipline"} on requirements.
 */
public record RecipeSkillRequirement(ResourceLocation skillId, int minLevel) {
    public RecipeSkillRequirement {
        minLevel = Math.max(0, minLevel);
    }
}
