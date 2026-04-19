package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillTreeRegistry {
    private static final LinkedHashMap<ResourceLocation, SkillTreeDefinition> REGISTRY = new LinkedHashMap<>();

    private SkillTreeRegistry() {}

    public static void clearAndPutAll(Map<ResourceLocation, SkillTreeDefinition> defs) {
        REGISTRY.clear();
        REGISTRY.putAll(defs);
    }

    public static int size() { return REGISTRY.size(); }

    public static Collection<Map.Entry<ResourceLocation, SkillTreeDefinition>> entries() { return REGISTRY.entrySet(); }

    public static SkillTreeDefinition get(ResourceLocation skillId) { return REGISTRY.get(skillId); }

    public static Iterable<ResourceLocation> ids() { return REGISTRY.keySet(); }

    public static List<String> nodeIds(ResourceLocation skillId) {
        SkillTreeDefinition def = REGISTRY.get(skillId);
        if (def == null || def.nodes() == null) return java.util.Collections.emptyList();
        return java.util.List.copyOf(def.nodes().keySet());
    }
}
