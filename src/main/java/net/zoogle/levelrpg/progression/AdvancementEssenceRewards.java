package net.zoogle.levelrpg.progression;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.zoogle.levelrpg.Config;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Objects;

/**
 * One-time Essence rewards from advancement completion.
 */
public final class AdvancementEssenceRewards {
    private AdvancementEssenceRewards() {}

    public static final int TASK_REWARD = 1;
    public static final int GOAL_REWARD = 2;
    public static final int CHALLENGE_REWARD = 3;

    public static Result claim(LevelProfile profile, AdvancementHolder advancement) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(advancement, "advancement");

        if (!Config.awardEssenceFromAdvancements) {
            return Result.noAward();
        }

        DisplayInfo display = advancement.value().display().orElse(null);
        if (display == null) {
            return Result.noAward();
        }
        // Hidden/background advancements are not player-facing milestones.
        // Essence from advancements is intended for visible progression beats only.
        if (display.isHidden()) {
            return Result.noAward();
        }

        int amount = rewardForFrame(display.getType());
        if (amount <= 0) {
            return Result.noAward();
        }

        boolean claimed = profile.claimAdvancementEssenceReward(advancement.id(), amount);
        if (!claimed) {
            return Result.claimedAlready();
        }
        return Result.awarded(amount);
    }

    public static int rewardForFrame(AdvancementType frameType) {
        if (frameType == null) {
            return 0;
        }
        return switch (frameType) {
            case TASK -> Math.max(0, Config.advancementTaskEssence);
            case GOAL -> Math.max(0, Config.advancementGoalEssence);
            case CHALLENGE -> Math.max(0, Config.advancementChallengeEssence);
        };
    }

    public record Result(boolean awarded, boolean alreadyClaimed, int essenceAwarded) {
        public static Result noAward() {
            return new Result(false, false, 0);
        }

        public static Result claimedAlready() {
            return new Result(false, true, 0);
        }

        public static Result awarded(int amount) {
            return new Result(true, false, Math.max(0, amount));
        }
    }
}

