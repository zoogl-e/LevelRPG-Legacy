package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Objects;

/**
 * Central spendable skill-point logic. Mastery awards points here; future UIs
 * and menus should spend through this class rather than mutating skill levels
 * directly.
 */
public final class SkillPointProgression {
    private SkillPointProgression() {}

    public static int availablePoints(LevelProfile profile) {
        return profile == null ? 0 : Math.max(0, profile.availableSkillPoints);
    }

    public static int spentPoints(LevelProfile profile) {
        return profile == null ? 0 : Math.max(0, profile.spentSkillPoints);
    }

    public static int earnedPoints(LevelProfile profile) {
        return availablePoints(profile) + spentPoints(profile);
    }

    public static boolean canSpendPoint(LevelProfile profile, ResourceLocation skillId) {
        if (profile == null || skillId == null || !ProgressionSkill.isCanonicalId(skillId)) {
            return false;
        }
        return availablePoints(profile) > 0;
    }

    public static void grantPoint(LevelProfile profile, int amount) {
        if (profile == null || amount <= 0) {
            return;
        }
        profile.availableSkillPoints = Math.max(0, profile.availableSkillPoints + amount);
    }

    public static SkillPointSpendResult spendPoint(LevelProfile profile, ResourceLocation skillId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            return SkillPointSpendResult.failure(skillId, "Unknown canonical skill");
        }
        if (profile.availableSkillPoints <= 0) {
            return SkillPointSpendResult.failure(skillId, "No skill points available");
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
            int availablePoints,
            int spentPoints
    ) {
        public static SkillPointSpendResult success(ResourceLocation skillId, int resultingSkillLevel, int availablePoints, int spentPoints) {
            return new SkillPointSpendResult(skillId, true, "Spent", resultingSkillLevel, availablePoints, spentPoints);
        }

        public static SkillPointSpendResult failure(ResourceLocation skillId, String message) {
            return new SkillPointSpendResult(skillId, false, message, 0, 0, 0);
        }
    }
}
