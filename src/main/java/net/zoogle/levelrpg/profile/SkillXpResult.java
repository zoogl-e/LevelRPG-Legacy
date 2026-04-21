package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Deprecated compatibility result for older XP-named award code.
 *
 * New code should consume {@link MasteryAwardResult} instead.
 */
@Deprecated
public record SkillXpResult(
        ResourceLocation skillId,
        long xpAwarded,
        int levelsGained,
        int resultingLevel,
        long resultingXp,
        long xpToNextLevel
) {
    public static SkillXpResult from(MasteryAwardResult result) {
        return new SkillXpResult(
                result.skillId(),
                result.masteryAwarded(),
                result.masteryLevelsGained(),
                result.resultingMasteryLevel(),
                result.resultingMasteryProgress(),
                result.masteryRequiredForNextLevel()
        );
    }

    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
