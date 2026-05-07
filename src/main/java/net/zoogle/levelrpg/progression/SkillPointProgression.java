package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Objects;

/**
 * Spendable pool for raising invested <b>discipline level</b> ({@link net.zoogle.levelrpg.profile.LevelProfile#availableSkillPoints}).
 * Practice rank-ups grant into this pool. This is unrelated to tree-unlock <b>Insight</b>
 * ({@link SpecializationProgression#insight(net.zoogle.levelrpg.profile.LevelProfile)} — specialization totals).
 */
public final class SkillPointProgression {
    private SkillPointProgression() {}

    /**
     * @return {@link net.zoogle.levelrpg.profile.LevelProfile#availableSkillPoints} — the <b>discipline
     * investment</b> pool (uncommitted points that raise {@link net.zoogle.levelrpg.profile.SkillState#level}).
     * Despite the historical
     * method name {@code insight}, this is <b>not</b> tree {@link net.zoogle.levelrpg.profile.LevelProfile#globalInsightAvailable()}.
     * Prefer {@link net.zoogle.levelrpg.profile.LevelProfile#uncommittedDisciplineInvestmentPoints()} on new call paths.
     */
    public static int insight(LevelProfile profile) {
        return profile == null ? 0 : Math.max(0, profile.availableSkillPoints);
    }

    public static int spentPoints(LevelProfile profile) {
        return profile == null ? 0 : Math.max(0, profile.spentSkillPoints);
    }

    public static int earnedPoints(LevelProfile profile) {
        return insight(profile) + spentPoints(profile);
    }

    public static boolean canSpendPoint(LevelProfile profile, ResourceLocation skillId) {
        if (profile == null || skillId == null || !ProgressionSkill.isCanonicalId(skillId)) {
            return false;
        }
        return insight(profile) > 0;
    }

    /**
     * @deprecated Legacy/debug pool writer only. Practice rank-up no longer uses this.
     * Active Discipline investment now spends global Essence via
     * {@link net.zoogle.levelrpg.profile.LevelProfile#spendEssenceForDisciplineLevel(net.minecraft.resources.ResourceLocation, DisciplineInvestmentSource)}.
     */
    @Deprecated
    public static void grantPoint(LevelProfile profile, int amount) {
        if (profile == null || amount <= 0) {
            return;
        }
        profile.availableSkillPoints = Math.max(0, profile.availableSkillPoints + amount);
    }

    /**
     * @deprecated Legacy compatibility/debug spend path. This consumes
     * {@link net.zoogle.levelrpg.profile.LevelProfile#availableSkillPoints} and is not
     * the active gameplay investment route. New gameplay code should use
     * {@link net.zoogle.levelrpg.profile.LevelProfile#spendEssenceForDisciplineLevel(net.minecraft.resources.ResourceLocation, DisciplineInvestmentSource)}
     * or {@link DisciplineInvestmentProgression}.
     */
    @Deprecated
    public static SkillPointSpendResult spendPoint(LevelProfile profile, ResourceLocation skillId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            return SkillPointSpendResult.failure(skillId, "Unknown canonical discipline");
        }
        if (profile.availableSkillPoints <= 0) {
            return SkillPointSpendResult.failure(skillId, "No discipline investment points available");
        }

        SkillState state = profile.getSkill(skillId);
        state.level = Math.max(0, state.level + 1);
        profile.availableSkillPoints = Math.max(0, profile.availableSkillPoints - 1);
        profile.spentSkillPoints = Math.max(0, profile.spentSkillPoints + 1);
        return SkillPointSpendResult.success(skillId, state.level, profile.availableSkillPoints, profile.spentSkillPoints);
    }

    public record SkillPointSpendResult(
            ResourceLocation skillId,
            boolean success,
            String message,
            int resultingSkillLevel,
            int insight,
            int spentPoints
    ) {
        public static SkillPointSpendResult success(ResourceLocation skillId, int resultingSkillLevel, int insight, int spentPoints) {
            return new SkillPointSpendResult(skillId, true, "Spent", resultingSkillLevel, insight, spentPoints);
        }

        public static SkillPointSpendResult failure(ResourceLocation skillId, String message) {
            return new SkillPointSpendResult(skillId, false, message, 0, 0, 0);
        }
    }
}
