package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static archetype catalog for the early rewrite. This can move to data-driven
 * loading later without changing the profile API.
 */
public final class ArchetypeRegistry {
    private static final LinkedHashMap<ResourceLocation, ArchetypeDefinition> REGISTRY = new LinkedHashMap<>();

    private ArchetypeRegistry() {}

    static {
        register(
                "knight",
                "Knight",
                "A martial start focused on direct combat, resilience, and basic smithing.",
                Map.of(
                        ProgressionSkill.VALOR, 3,
                        ProgressionSkill.VITALITY, 2,
                        ProgressionSkill.FORGING, 1
                )
        );
        register(
                "inventor",
                "Inventor",
                "A technical start oriented around crafting systems, materials, and experimental practice.",
                Map.of(
                        ProgressionSkill.ARTIFICING, 3,
                        ProgressionSkill.FORGING, 2,
                        ProgressionSkill.MINING, 1,
                        ProgressionSkill.MAGICK, 1
                )
        );
        register(
                "peasant",
                "Peasant",
                "A humble, broad start with light experience across the fundamentals.",
                Map.of(
                        ProgressionSkill.VALOR, 1,
                        ProgressionSkill.VITALITY, 1,
                        ProgressionSkill.MINING, 1,
                        ProgressionSkill.CULINARY, 1,
                        ProgressionSkill.FORGING, 1,
                        ProgressionSkill.ARTIFICING, 1,
                        ProgressionSkill.MAGICK, 1,
                        ProgressionSkill.EXPLORATION, 1
                )
        );
    }

    public static ArchetypeDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<ArchetypeDefinition> values() {
        return java.util.List.copyOf(REGISTRY.values());
    }

    public static Iterable<ResourceLocation> ids() {
        return REGISTRY.keySet();
    }

    public static ResourceLocation defaultId() {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "peasant");
    }

    public static ArchetypeDefinition defaultArchetype() {
        return get(defaultId());
    }

    private static void register(String path, String displayName, String description, Map<ProgressionSkill, Integer> startingLevels) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
        LinkedHashMap<ProgressionSkill, Integer> ordered = new LinkedHashMap<>();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            int amount = Math.max(0, startingLevels.getOrDefault(skill, 0));
            if (amount > 0) {
                ordered.put(skill, amount);
            }
        }
        REGISTRY.put(id, new ArchetypeDefinition(id, displayName, description, ordered));
    }
}
