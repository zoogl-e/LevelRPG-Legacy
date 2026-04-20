package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.SkillRegistry;
import net.zoogle.levelrpg.data.XpCurves;

/**
 * Canonical leveling math for per-skill progression.
 */
public final class SkillLeveling {
    private SkillLeveling() {}

    public static long xpToNextLevel(ResourceLocation skillId, int level) {
        ResourceLocation curveId = null;
        var def = SkillRegistry.get(skillId);
        if (def != null && def.xpCurve() != null) {
            curveId = def.xpCurve();
        }
        return XpCurves.computeNeeded(curveId, level);
    }

    public static long xpToNextLevel(int level) {
        return XpCurves.computeNeeded(null, level);
    }
}
