package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical result object for per-skill proficiency awards.
 */
public record ProficiencyAwardResult(
        ResourceLocation skillId,
        long proficiencyAwarded,
        int rankLevelsGained,
        int resultingRank,
        long resultingProficiency,
        long proficiencyRequiredForNextLevel
) {
    public boolean leveledUp() {
        return rankLevelsGained > 0;
    }
}
