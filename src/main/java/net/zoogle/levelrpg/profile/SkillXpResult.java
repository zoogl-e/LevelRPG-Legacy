package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Deprecated compatibility result for older XP-named award code.
 *
 * New code should consume {@link ProficiencyAwardResult} instead.
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
    public static SkillXpResult from(ProficiencyAwardResult result) {
        return new SkillXpResult(
                result.skillId(),
                result.proficiencyAwarded(),
                result.rankLevelsGained(),
                result.resultingRank(),
                result.resultingProficiency(),
                result.proficiencyRequiredForNextLevel()
        );
    }

    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
