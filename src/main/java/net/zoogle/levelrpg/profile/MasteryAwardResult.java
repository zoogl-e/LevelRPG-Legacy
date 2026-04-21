package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical result object for per-skill mastery awards.
 */
public record MasteryAwardResult(
        ResourceLocation skillId,
        long masteryAwarded,
        int masteryLevelsGained,
        int resultingMasteryLevel,
        long resultingMasteryProgress,
        long masteryRequiredForNextLevel
) {
    public boolean leveledUp() {
        return masteryLevelsGained > 0;
    }
}
