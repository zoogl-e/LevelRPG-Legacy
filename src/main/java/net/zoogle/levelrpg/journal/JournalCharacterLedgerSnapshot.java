package net.zoogle.levelrpg.journal;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Character-ledger projection used by journals and other player-facing
 * compendium views. This stays focused on read-model concerns rather than
 * raw profile persistence.
 */
public record JournalCharacterLedgerSnapshot(
        String archetypeName,
        String archetypeDescription,
        boolean archetypeLockedIn,
        int totalSkillLevels,
        int totalMasteryLevels,
        int earnedSkillPoints,
        int spentSkillPoints,
        int availableSkillPoints,
        int earnedSpecializationPoints,
        int spentSpecializationPoints,
        int availableSpecializationPoints,
        List<Row> rows
) {
    public JournalCharacterLedgerSnapshot {
        archetypeName = archetypeName == null ? "" : archetypeName;
        archetypeDescription = archetypeDescription == null ? "" : archetypeDescription;
        totalSkillLevels = Math.max(0, totalSkillLevels);
        totalMasteryLevels = Math.max(0, totalMasteryLevels);
        earnedSkillPoints = Math.max(0, earnedSkillPoints);
        spentSkillPoints = Math.max(0, spentSkillPoints);
        availableSkillPoints = Math.max(0, availableSkillPoints);
        earnedSpecializationPoints = Math.max(0, earnedSpecializationPoints);
        spentSpecializationPoints = Math.max(0, spentSpecializationPoints);
        availableSpecializationPoints = Math.max(0, availableSpecializationPoints);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public record Row(
            ResourceLocation skillId,
            String label,
            // Legacy compatibility field; this is the invested skill level.
            int level,
            long xp,
            long xpToNextLevel,
            int masteryLevel,
            long masteryXp,
            long masteryXpToNextLevel,
            long masteryProgress,
            long masteryRequiredForNextLevel,
            String masteryProgressText,
            boolean canSpendSkillPoint,
            JournalPassiveEffectSnapshot passiveEffects
    ) {
        public Row {
            label = label == null ? "" : label;
            level = Math.max(0, level);
            xp = Math.max(0L, xp);
            xpToNextLevel = Math.max(0L, xpToNextLevel);
            masteryLevel = Math.max(0, masteryLevel);
            masteryXp = Math.max(0L, masteryXp);
            masteryXpToNextLevel = Math.max(0L, masteryXpToNextLevel);
            masteryProgress = Math.max(0L, masteryProgress);
            masteryRequiredForNextLevel = Math.max(0L, masteryRequiredForNextLevel);
            masteryProgressText = masteryProgressText == null ? "" : masteryProgressText;
        }
    }
}
