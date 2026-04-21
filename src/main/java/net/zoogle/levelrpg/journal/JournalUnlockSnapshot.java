package net.zoogle.levelrpg.journal;

/**
 * Stable journal-facing unlock/milestone entry. This is intentionally small
 * and explicit so book renderers do not have to reverse-engineer raw trees or
 * recipe requirements into player-facing rows.
 */
public record JournalUnlockSnapshot(
        Kind kind,
        String id,
        String title,
        String description,
        int currentLevel,
        int requiredLevel,
        boolean unlocked
) {
    public JournalUnlockSnapshot {
        kind = kind == null ? Kind.MILESTONE : kind;
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        currentLevel = Math.max(0, currentLevel);
        requiredLevel = Math.max(0, requiredLevel);
    }

    public enum Kind {
        MILESTONE,
        RECIPE
    }
}
