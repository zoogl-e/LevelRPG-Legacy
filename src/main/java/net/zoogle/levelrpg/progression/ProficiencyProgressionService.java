package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProficiencyAwardResult;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Objects;

/**
 * Canonical practice-facing proficiency award path. Gameplay hooks should call this
 * service rather than mutating proficiency state directly.
 */
public final class ProficiencyProgressionService {
    private ProficiencyProgressionService() {}

    public static ProficiencyAwardResult award(LevelProfile profile, ResourceLocation skillId, long amount) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            throw new IllegalArgumentException("Unknown non-canonical skill id: " + skillId);
        }

        SkillState state = profile.getSkill(skillId);
        long awarded = Math.max(0L, amount);
        if (awarded == 0L) {
            return new ProficiencyAwardResult(
                    skillId,
                    0L,
                    0,
                    state.rank,
                    state.proficiency,
                    MasteryLeveling.xpToNextLevel(skillId, state.rank)
            );
        }

        state.proficiency = Math.max(0L, state.proficiency + awarded);
        int levelsGained = 0;
        long needed;
        while (state.proficiency >= (needed = MasteryLeveling.xpToNextLevel(skillId, state.rank))) {
            state.proficiency -= needed;
            state.rank += 1;
            levelsGained += 1;
        }

        return new ProficiencyAwardResult(
                skillId,
                awarded,
                levelsGained,
                state.rank,
                state.proficiency,
                MasteryLeveling.xpToNextLevel(skillId, state.rank)
        );
    }
}
