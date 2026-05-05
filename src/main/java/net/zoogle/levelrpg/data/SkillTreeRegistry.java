package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Canonical Server Registry.</b>
 *
 * <p>Stores the authoritative {@link SkillTreeCanonicalDefinition} objects loaded directly from JSON
 * via {@link SkillTreeLoader}. This registry is the primary source of truth for server-side
 * progression logic, requirement evaluation, and client-sync payloads.
 *
 * @see net.zoogle.levelrpg.skilltree.SkillTreeRegistry
 */
public final class SkillTreeRegistry {
    private static final LinkedHashMap<ResourceLocation, SkillTreeCanonicalDefinition> REGISTRY = new LinkedHashMap<>();

    private SkillTreeRegistry() {}

    public static void clearAndPutAll(Map<ResourceLocation, SkillTreeCanonicalDefinition> defs) {
        REGISTRY.clear();
        REGISTRY.putAll(defs);
    }

    public static int size() { return REGISTRY.size(); }

    public static Collection<Map.Entry<ResourceLocation, SkillTreeCanonicalDefinition>> entries() { return REGISTRY.entrySet(); }

    public static SkillTreeCanonicalDefinition get(ResourceLocation skillId) { return REGISTRY.get(skillId); }

    public static Iterable<ResourceLocation> ids() { return REGISTRY.keySet(); }

    public static List<String> nodeIds(ResourceLocation skillId) {
        SkillTreeCanonicalDefinition def = REGISTRY.get(skillId);
        if (def == null || def.nodes() == null) return java.util.Collections.emptyList();
        return java.util.List.copyOf(def.nodes().keySet());
    }
}
