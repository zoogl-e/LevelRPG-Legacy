package net.zoogle.levelrpg.journal;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Explicit journal projection for one canonical skill.
 *
 * The older level/xp/xpToNextLevel trio is retained for incremental
 * compatibility. New UI should prefer investedSkillLevel plus mastery fields.
 */
public record JournalSkillSnapshot(
        ResourceLocation skillId,
        String displayName,
        String color,
        String icon,
        String summary,
        int level,
        long xp,
        long xpToNextLevel,
        int investedSkillLevel,
        int masteryLevel,
        long masteryXp,
        long masteryXpToNextLevel,
        long masteryProgress,
        long masteryRequiredForNextLevel,
        String masteryProgressText,
        boolean canSpendSkillPoint,
        JournalPassiveEffectSnapshot passiveEffects,
        JournalMasterySnapshot mastery,
        List<JournalUnlockSnapshot> recipeUnlocks
) {
    public JournalSkillSnapshot {
        displayName = displayName == null ? "" : displayName;
        color = color == null ? "" : color;
        icon = icon == null ? "" : icon;
        summary = summary == null ? "" : summary;
        level = Math.max(0, level);
        xp = Math.max(0L, xp);
        xpToNextLevel = Math.max(0L, xpToNextLevel);
        investedSkillLevel = Math.max(0, investedSkillLevel);
        masteryLevel = Math.max(0, masteryLevel);
        masteryXp = Math.max(0L, masteryXp);
        masteryXpToNextLevel = Math.max(0L, masteryXpToNextLevel);
        masteryProgress = Math.max(0L, masteryProgress);
        masteryRequiredForNextLevel = Math.max(0L, masteryRequiredForNextLevel);
        masteryProgressText = masteryProgressText == null ? "" : masteryProgressText;
        recipeUnlocks = recipeUnlocks == null ? List.of() : List.copyOf(recipeUnlocks);
    }
}
