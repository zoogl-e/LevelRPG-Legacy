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
                        ProgressionSkill.VALOR, 4,
                        ProgressionSkill.HEARTH, 1,
                        ProgressionSkill.FORGING, 1,
                        ProgressionSkill.FINESSE, 1
                )
        );
        register(
                "inventor",
                "Inventor",
                "A technical start oriented around crafting systems, materials, and experimental practice.",
                Map.of(
                        ProgressionSkill.ARTIFICING, 3,
                        ProgressionSkill.FORGING, 2,
                        ProgressionSkill.DELVING, 1,
                        ProgressionSkill.ARCANA, 1
                )
        );
        register(
                "warden",
                "Warden",
                "A steadfast protector who endures pressure, controls chokepoints, and thrives on the long haul.",
                Map.of(
                        ProgressionSkill.VALOR, 3,
                        ProgressionSkill.HEARTH, 2,
                        ProgressionSkill.DELVING, 1,
                        ProgressionSkill.FORGING, 1
                )
        );
        register(
                "spellblade",
                "Spellblade",
                "A duelist who interleaves steel and spellcraft to punish openings in close quarters.",
                Map.of(
                        ProgressionSkill.VALOR, 3,
                        ProgressionSkill.ARCANA, 2,
                        ProgressionSkill.FINESSE, 2
                )
        );
        register(
                "runesmith",
                "Runesmith",
                "A precision artisan of etched power, blending warding craft with practical battle utility.",
                Map.of(
                        ProgressionSkill.FORGING, 3,
                        ProgressionSkill.ARTIFICING, 2,
                        ProgressionSkill.ARCANA, 1,
                        ProgressionSkill.HEARTH, 1
                )
        );
        register(
                "alchemist",
                "Alchemist",
                "A prepared specialist who turns ingredients into tactical advantage, sustain, and disruption.",
                Map.of(
                        ProgressionSkill.ARTIFICING, 3,
                        ProgressionSkill.ARCANA, 2,
                        ProgressionSkill.HEARTH, 1,
                        ProgressionSkill.DELVING, 1
                )
        );
        register(
                "wayfarer",
                "Wayfarer",
                "A mobile explorer tuned for pathfinding, survival, and reliable gains from dangerous routes.",
                Map.of(
                        ProgressionSkill.DELVING, 3,
                        ProgressionSkill.FINESSE, 2,
                        ProgressionSkill.HEARTH, 1,
                        ProgressionSkill.VALOR, 1
                )
        );
        register(
                "templar",
                "Templar",
                "A disciplined guardian who anchors allies with conviction, wards, and measured force.",
                Map.of(
                        ProgressionSkill.VALOR, 3,
                        ProgressionSkill.HEARTH, 2,
                        ProgressionSkill.ARCANA, 1,
                        ProgressionSkill.FORGING, 1
                )
        );
        register(
                "saboteur",
                "Saboteur",
                "A control-minded opportunist specializing in setup, disruption, and punishing bad positioning.",
                Map.of(
                        ProgressionSkill.FINESSE, 3,
                        ProgressionSkill.ARTIFICING, 2,
                        ProgressionSkill.DELVING, 1,
                        ProgressionSkill.ARCANA, 1
                )
        );
        register(
                "beastmarshal",
                "Beastmarshal",
                "A field leader whose rhythm favors sustained pressure, adaptability, and terrain mastery.",
                Map.of(
                        ProgressionSkill.HEARTH, 3,
                        ProgressionSkill.VALOR, 2,
                        ProgressionSkill.DELVING, 1,
                        ProgressionSkill.FINESSE, 1
                )
        );
        register(
                "arcanist",
                "Arcanist",
                "A high-focus scholar of raw magic who leverages precision and preparation over brute force.",
                Map.of(
                        ProgressionSkill.ARCANA, 4,
                        ProgressionSkill.FINESSE, 1,
                        ProgressionSkill.ARTIFICING, 1,
                        ProgressionSkill.HEARTH, 1
                )
        );
        register(
                "master_crafter",
                "Master Crafter",
                "A production-minded specialist with broad crafting literacy and dependable workshop momentum.",
                Map.of(
                        ProgressionSkill.FORGING, 3,
                        ProgressionSkill.ARTIFICING, 2,
                        ProgressionSkill.HEARTH, 1,
                        ProgressionSkill.DELVING, 1
                )
        );
        register(
                "peasant",
                "Peasant",
                "A humble, broad start with light experience across the fundamentals.",
                Map.of(
                        ProgressionSkill.VALOR, 1,
                        ProgressionSkill.FINESSE, 1,
                        ProgressionSkill.DELVING, 1,
                        ProgressionSkill.HEARTH, 1,
                        ProgressionSkill.FORGING, 1,
                        ProgressionSkill.ARTIFICING, 1,
                        ProgressionSkill.ARCANA, 1
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
