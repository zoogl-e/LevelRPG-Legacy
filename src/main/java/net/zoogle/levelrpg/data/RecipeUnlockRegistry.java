package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime registry of recipe unlock requirements loaded from datapacks.
 */
public final class RecipeUnlockRegistry {
    private static final LinkedHashMap<ResourceLocation, RecipeUnlockDefinition> REGISTRY = new LinkedHashMap<>();

    private RecipeUnlockRegistry() {}

    public static void clearAndPutAll(Map<ResourceLocation, RecipeUnlockDefinition> defs) {
        REGISTRY.clear();
        REGISTRY.putAll(defs);
    }

    public static RecipeUnlockDefinition get(ResourceLocation recipeId) {
        return REGISTRY.get(recipeId);
    }

    public static boolean contains(ResourceLocation recipeId) {
        return REGISTRY.containsKey(recipeId);
    }

    public static int size() {
        return REGISTRY.size();
    }

    public static Collection<Map.Entry<ResourceLocation, RecipeUnlockDefinition>> entries() {
        return REGISTRY.entrySet();
    }
}
