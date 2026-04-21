package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.MasteryAwardResult;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Objects;

/**
 * Canonical practice-facing mastery award path. Gameplay hooks should call this
 * service rather than mutating mastery state directly.
 */
public final class MasteryProgressionService {
    private MasteryProgressionService() {}

    public static MasteryAwardResult award(LevelProfile profile, ResourceLocation skillId, long amount) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            throw new IllegalArgumentException("Unknown non-canonical skill id: " + skillId);
        }

        SkillState state = profile.getSkill(skillId);
        long awarded = Math.max(0L, amount);
        if (awarded == 0L) {
            return new MasteryAwardResult(
                    skillId,
                    0L,
                    0,
                    state.masteryLevel,
                    state.masteryXp,
                    MasteryLeveling.xpToNextLevel(skillId, state.masteryLevel)
            );
        }

        state.masteryXp = Math.max(0L, state.masteryXp + awarded);
        int levelsGained = 0;
        long needed;
        while (state.masteryXp >= (needed = MasteryLeveling.xpToNextLevel(skillId, state.masteryLevel))) {
            state.masteryXp -= needed;
            state.masteryLevel += 1;
            SkillPointProgression.grantPoint(profile, 1);
            levelsGained += 1;
        }

        return new MasteryAwardResult(
                skillId,
                awarded,
                levelsGained,
                state.masteryLevel,
                state.masteryXp,
                MasteryLeveling.xpToNextLevel(skillId, state.masteryLevel)
        );
    }
}
