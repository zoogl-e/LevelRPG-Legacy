package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.RecipeSkillRequirement;
import net.zoogle.levelrpg.data.RecipeUnlockDefinition;
import net.zoogle.levelrpg.data.RecipeUnlockRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Canonical recipe unlock check path. This resolves recipe requirements against
 * the current canonical profile skill levels without altering profile schema.
 */
public final class RecipeUnlockService {
    private RecipeUnlockService() {}

    public static boolean hasAccess(LevelProfile profile, ResourceLocation recipeId) {
        return checkAccess(profile, recipeId).unlocked();
    }

    public static UnlockCheckResult checkAccess(LevelProfile profile, ResourceLocation recipeId) {
        Objects.requireNonNull(profile, "profile");
        return checkAccess(recipeId, skillId -> {
            SkillState state = profile.getSkill(skillId);
            return state != null ? state.level : 0;
        });
    }

    public static UnlockCheckResult checkAccess(ResourceLocation recipeId, Map<ResourceLocation, SkillState> skillLevels) {
        Map<ResourceLocation, SkillState> safeMap = skillLevels == null ? Collections.emptyMap() : skillLevels;
        return checkAccess(recipeId, skillId -> {
            SkillState state = safeMap.get(skillId);
            return state != null ? state.level : 0;
        });
    }

    public static UnlockCheckResult checkAccess(ResourceLocation recipeId, Function<ResourceLocation, Integer> levelLookup) {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(levelLookup, "levelLookup");

        RecipeUnlockDefinition definition = RecipeUnlockRegistry.get(recipeId);
        if (definition == null) {
            return new UnlockCheckResult(recipeId, null, true, List.of());
        }

        ArrayList<MissingRequirement> missing = new ArrayList<>();
        for (RecipeSkillRequirement requirement : definition.requirements()) {
            int currentLevel = Math.max(0, levelLookup.apply(requirement.skillId()));
            if (currentLevel < requirement.minLevel()) {
                missing.add(new MissingRequirement(requirement.skillId(), currentLevel, requirement.minLevel()));
            }
        }
        return new UnlockCheckResult(recipeId, definition, missing.isEmpty(), List.copyOf(missing));
    }

    public record UnlockCheckResult(
            ResourceLocation recipeId,
            RecipeUnlockDefinition definition,
            boolean unlocked,
            List<MissingRequirement> missingRequirements
    ) {}

    public record MissingRequirement(
            ResourceLocation skillId,
            int currentLevel,
            int requiredLevel
    ) {}
}
