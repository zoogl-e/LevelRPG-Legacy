package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.SkillLeveling;

/**
 * Central mastery-threshold helper. For now mastery uses the same curve family
 * as the older direct skill leveling path, but the split is explicit so later
 * balance work can diverge without touching event code.
 */
public final class MasteryLeveling {
    private MasteryLeveling() {}

    public static long xpToNextLevel(ResourceLocation skillId, int masteryLevel) {
        return SkillLeveling.xpToNextLevel(skillId, masteryLevel);
    }
}
