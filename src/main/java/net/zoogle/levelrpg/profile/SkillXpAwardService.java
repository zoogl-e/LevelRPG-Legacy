package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Canonical XP award path for skill progression. This is intentionally isolated
 * from events so future gameplay systems can call into it cleanly.
 */
public final class SkillXpAwardService {
    private SkillXpAwardService() {}

    public static SkillXpResult award(LevelProfile profile, ResourceLocation skillId, long xpAmount) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");

        SkillState state = profile.getSkill(skillId);
        long awarded = Math.max(0L, xpAmount);
        if (awarded == 0L) {
            return new SkillXpResult(
                    skillId,
                    0L,
                    0,
                    state.level,
                    state.xp,
                    SkillLeveling.xpToNextLevel(skillId, state.level)
            );
        }

        state.xp = Math.max(0L, state.xp + awarded);
        int levelsGained = 0;
        long needed;
        while (state.xp >= (needed = SkillLeveling.xpToNextLevel(skillId, state.level))) {
            state.xp -= needed;
            state.level += 1;
            levelsGained += 1;
        }

        return new SkillXpResult(
                skillId,
                awarded,
                levelsGained,
                state.level,
                state.xp,
                SkillLeveling.xpToNextLevel(skillId, state.level)
        );
    }
}
