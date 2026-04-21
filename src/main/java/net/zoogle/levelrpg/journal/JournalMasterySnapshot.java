package net.zoogle.levelrpg.journal;

import java.util.List;

/**
 * Journal-facing mastery tree projection for one skill.
 */
public record JournalMasterySnapshot(
        boolean hasTree,
        String title,
        String summary,
        int skillLevel,
        int unlockedTierCount,
        int earnedPoints,
        int spentPoints,
        int availablePoints,
        JournalUnlockSnapshot nextThreshold,
        List<JournalUnlockSnapshot> milestones,
        List<NodeSnapshot> nodes,
        String suggestedNextNodeId
) {
    public JournalMasterySnapshot {
        title = title == null ? "" : title;
        summary = summary == null ? "" : summary;
        skillLevel = Math.max(0, skillLevel);
        unlockedTierCount = Math.max(0, unlockedTierCount);
        earnedPoints = Math.max(0, earnedPoints);
        spentPoints = Math.max(0, spentPoints);
        availablePoints = Math.max(0, availablePoints);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        suggestedNextNodeId = suggestedNextNodeId == null ? "" : suggestedNextNodeId;
    }

    public record NodeSnapshot(
            String id,
            String title,
            String description,
            String branch,
            int cost,
            int requiredLevel,
            Status status,
            List<String> missingRequirements
    ) {
        public NodeSnapshot {
            id = id == null ? "" : id;
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            branch = branch == null ? "" : branch;
            cost = Math.max(0, cost);
            requiredLevel = Math.max(0, requiredLevel);
            status = status == null ? Status.LOCKED : status;
            missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        }
    }

    public enum Status {
        UNLOCKED,
        AVAILABLE,
        LOCKED_LEVEL,
        LOCKED_PREREQUISITE,
        LOCKED_POINTS,
        LOCKED
    }
}
