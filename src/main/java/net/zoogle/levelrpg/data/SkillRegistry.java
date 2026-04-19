package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillRegistry {
    private static final LinkedHashMap<ResourceLocation, SkillDefinition> REGISTRY = new LinkedHashMap<>();

    private SkillRegistry() {}

    public static void clearAndPutAll(Map<ResourceLocation, SkillDefinition> defs) {
        REGISTRY.clear();
        REGISTRY.putAll(defs);
    }

    public static int size() { return REGISTRY.size(); }

    public static Collection<Map.Entry<ResourceLocation, SkillDefinition>> entries() { return REGISTRY.entrySet(); }

    public static SkillDefinition get(ResourceLocation id) { return REGISTRY.get(id); }

    public static String getDisplayName(ResourceLocation id) {
        SkillDefinition def = REGISTRY.get(id);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        return null;
    }

    public static Iterable<ResourceLocation> ids() { return REGISTRY.keySet(); }
}
