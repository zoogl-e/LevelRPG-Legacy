package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.progression.MasteryProgressionService;

import java.util.Objects;

/**
 * Deprecated XP-named compatibility shim.
 *
 * New gameplay code should call {@link LevelProfile#awardMastery(ResourceLocation, long)}
 * or {@link MasteryProgressionService} directly.
 */
@Deprecated
public final class SkillXpAwardService {
    private SkillXpAwardService() {}

    @Deprecated
    public static SkillXpResult award(LevelProfile profile, ResourceLocation skillId, long xpAmount) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skillId, "skillId");
        return SkillXpResult.from(MasteryProgressionService.award(profile, skillId, xpAmount));
    }
}
