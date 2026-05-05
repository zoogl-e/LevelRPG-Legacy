package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Intention-revealing read model for UI and journal code.
 *
 * Skill level is invested build choice. Rank is earned through practice (proficiency).
 */
public record SkillProgressView(
        ResourceLocation skillId,
        int investedSkillLevel,
        int rank,
        long proficiency,
        long proficiencyRequiredForNextRank,
        boolean canSpendSkillPoint
) {}
