package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Intention-revealing read model for UI and journal code.
 *
 * Skill level is invested build choice. Mastery is earned practice.
 */
public record SkillProgressView(
        ResourceLocation skillId,
        int investedSkillLevel,
        int masteryLevel,
        long masteryProgress,
        long masteryRequiredForNextLevel,
        boolean canSpendSkillPoint
) {}
