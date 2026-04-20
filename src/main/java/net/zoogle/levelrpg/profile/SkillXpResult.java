package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical result object for per-skill XP awards.
 */
public record SkillXpResult(
        ResourceLocation skillId,
        long xpAwarded,
        int levelsGained,
        int resultingLevel,
        long resultingXp,
        long xpToNextLevel
) {
    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
