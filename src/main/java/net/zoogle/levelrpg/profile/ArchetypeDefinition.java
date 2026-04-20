package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical starting-archetype definition. Archetypes provide an initial skill
 * distribution and are not intended to be rigid permanent classes.
 */
public record ArchetypeDefinition(
        ResourceLocation id,
        String displayName,
        String description,
        LinkedHashMap<ProgressionSkill, Integer> startingLevels
) {
    public ArchetypeDefinition {
        startingLevels = normalize(startingLevels);
    }

    private static LinkedHashMap<ProgressionSkill, Integer> normalize(Map<ProgressionSkill, Integer> values) {
        LinkedHashMap<ProgressionSkill, Integer> normalized = new LinkedHashMap<>();
        if (values == null) {
            return normalized;
        }
        for (Map.Entry<ProgressionSkill, Integer> entry : values.entrySet()) {
            ProgressionSkill skill = entry.getKey();
            Integer level = entry.getValue();
            if (skill == null) {
                continue;
            }
            normalized.put(skill, Math.max(0, level == null ? 0 : level));
        }
        return normalized;
    }
}
