package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Intention-revealing read model for UI and journal code.
 *
 * <p>Discipline Level is the invested build choice. Practice rank is earned through proficiency.
 *
 * @apiNote Record component names mirror older journal payloads. For design vocabulary aligned with the
 * current server model, prefer {@link LevelProfile#investedDisciplineLevel}, {@link LevelProfile#practiceRank},
 * {@link LevelProfile#practiceProgress}, and global Insight accessors on {@link LevelProfile}.
 */
public record SkillProgressView(
        ResourceLocation skillId,
        int investedSkillLevel,
        int rank,
        long proficiency,
        long proficiencyRequiredForNextRank,
        boolean canSpendSkillPoint
) {}
