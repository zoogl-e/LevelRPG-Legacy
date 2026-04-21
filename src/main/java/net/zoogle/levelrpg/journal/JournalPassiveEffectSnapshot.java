package net.zoogle.levelrpg.journal;

import java.util.List;

/**
 * Book-facing projection of baseline passive effects for one skill or ledger
 * row. This stays compact and suitable for page composition.
 */
public record JournalPassiveEffectSnapshot(
        String title,
        String summary,
        boolean implemented,
        List<Entry> entries
) {
    public JournalPassiveEffectSnapshot {
        title = title == null ? "" : title;
        summary = summary == null ? "" : summary;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(
            String label,
            String valueText,
            double value,
            String description
    ) {
        public Entry {
            label = label == null ? "" : label;
            valueText = valueText == null ? "" : valueText;
            description = description == null ? "" : description;
        }
    }
}
