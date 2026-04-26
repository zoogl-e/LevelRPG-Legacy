package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

/**
 * Global specialization-point progression. Skill levels still reflect what the
 * player has practiced, while this pool defines how many meaningful mastery
 * choices they can commit to overall.
 */
public final class SpecializationProgression {
    // One point every 5 total canonical levels, capped so players cannot
    // eventually max every branch through raw practice alone.
    private static final int[] TOTAL_LEVEL_THRESHOLDS = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};

    private SpecializationProgression() {}

    public static int totalCanonicalLevels(LevelProfile profile) {
        if (profile == null) {
            return 0;
        }
        int total = 0;
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillState state = profile.getSkill(skill);
            total += Math.max(0, state.level);
        }
        return total;
    }

    public static int earnedPoints(LevelProfile profile) {
        return earnedPointsForTotalLevels(totalCanonicalLevels(profile));
    }

    /** Same thresholds as {@link #earnedPoints(LevelProfile)} but for an arbitrary invested-level total. */
    public static int earnedPointsForTotalLevels(int totalInvestedLevelsAcrossSkills) {
        int totalLevels = Math.max(0, totalInvestedLevelsAcrossSkills);
        int earned = 0;
        for (int threshold : TOTAL_LEVEL_THRESHOLDS) {
            if (totalLevels >= threshold) {
                earned++;
            }
        }
        return earned;
    }

    public static int spentPoints(LevelProfile profile) {
        if (profile == null) {
            return 0;
        }
        int spent = 0;
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            ResourceLocation skillId = skill.id();
            spent += Math.max(0, profile.getTreePointsSpent(skillId));
        }
        return spent;
    }

    public static int availablePoints(LevelProfile profile) {
        return Math.max(0, earnedPoints(profile) - spentPoints(profile));
    }

    public static int pointCap() {
        return TOTAL_LEVEL_THRESHOLDS.length;
    }
}
