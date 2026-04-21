package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Read-model for current baseline passive effects derived from canonical skill
 * levels. This is safe for journals/UI to consume without becoming the source
 * of truth for the formulas themselves.
 */
public record PassiveSkillSummary(
        ResourceLocation skillId,
        String title,
        String summary,
        boolean implemented,
        List<Entry> entries
) {
    public PassiveSkillSummary {
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
