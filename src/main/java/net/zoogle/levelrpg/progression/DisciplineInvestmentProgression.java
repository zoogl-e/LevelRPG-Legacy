package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.Config;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Objects;

/**
 * Active Discipline Level investment path: spends global Essence, checks Potential
 * (currently practice rank stand-in), and enforces the total invested-level cap.
 */
public final class DisciplineInvestmentProgression {
    public static final int TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP = 50;

    private DisciplineInvestmentProgression() {}

    public static int essenceCostForNextDisciplineLevel(LevelProfile profile, ResourceLocation skillId) {
        return DisciplineInvestmentCosts.fixedV1CostForNextDisciplineLevel();
    }

    /**
     * Source policy shell for future Index gating.
     *
     * <p>No real Index block/proximity validation exists yet. This only enforces
     * source-type + config intent while keeping command/system testing paths open.
     */
    public static boolean canInvestFromSource(DisciplineInvestmentSource source) {
        if (source == null) {
            return false;
        }
        return switch (source) {
            case COMMAND, SYSTEM, INDEX -> true;
            case BOOK -> Config.allowBookDisciplineInvestment || !Config.requireIndexForDisciplineInvestment;
        };
    }

    public static InvestmentResult investOne(LevelProfile profile, ResourceLocation skillId, DisciplineInvestmentSource source) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        if (!canInvestFromSource(source)) {
            return InvestmentResult.failure(
                    FailureReason.INVESTMENT_SOURCE_BLOCKED,
                    "Discipline investment must be performed at The Index (book investment may be enabled by server config)."
            );
        }
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            return InvestmentResult.failure(FailureReason.UNKNOWN_DISCIPLINE, "Unknown canonical discipline");
        }

        SkillState state = profile.getSkill(skillId);
        int currentLevel = Math.max(0, state.level);
        int potentialCap = Math.max(0, profile.practiceRank(skillId));
        if (currentLevel >= potentialCap) {
            return InvestmentResult.failure(FailureReason.POTENTIAL_CAP_REACHED,
                    "Potential cap reached (" + currentLevel + "/" + potentialCap + ")");
        }

        int totalInvested = profile.totalInvestedDisciplineLevels();
        if (totalInvested >= TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP) {
            return InvestmentResult.failure(FailureReason.CHARACTER_LEVEL_CAP_REACHED,
                    "Total invested Discipline Level cap reached (" + TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP + ")");
        }

        int cost = Math.max(1, essenceCostForNextDisciplineLevel(profile, skillId));
        if (!profile.canSpendEssence(cost)) {
            return InvestmentResult.failure(FailureReason.NOT_ENOUGH_ESSENCE, "Not enough Essence");
        }
        if (!profile.spendEssence(cost)) {
            return InvestmentResult.failure(FailureReason.NOT_ENOUGH_ESSENCE, "Not enough Essence");
        }

        state.level = currentLevel + 1;
        return InvestmentResult.success(skillId, state.level, cost, profile.essence(), potentialCap, TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP);
    }

    public enum FailureReason {
        INVESTMENT_SOURCE_BLOCKED,
        UNKNOWN_DISCIPLINE,
        NOT_ENOUGH_ESSENCE,
        POTENTIAL_CAP_REACHED,
        CHARACTER_LEVEL_CAP_REACHED,
        INVALID_STATE
    }

    public record InvestmentResult(
            boolean success,
            FailureReason failureReason,
            String message,
            ResourceLocation skillId,
            int resultingDisciplineLevel,
            int essenceSpent,
            int essenceRemaining,
            int potentialCap,
            int totalDisciplineLevelCap
    ) {
        public static InvestmentResult success(
                ResourceLocation skillId,
                int resultingDisciplineLevel,
                int essenceSpent,
                int essenceRemaining,
                int potentialCap,
                int totalDisciplineLevelCap
        ) {
            return new InvestmentResult(
                    true,
                    null,
                    "Invested",
                    skillId,
                    resultingDisciplineLevel,
                    essenceSpent,
                    essenceRemaining,
                    potentialCap,
                    totalDisciplineLevelCap
            );
        }

        public static InvestmentResult failure(FailureReason reason, String message) {
            return new InvestmentResult(false, reason, message, null, 0, 0, 0, 0, TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP);
        }
    }
}

