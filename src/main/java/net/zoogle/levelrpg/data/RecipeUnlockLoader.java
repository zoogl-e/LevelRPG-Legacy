package net.zoogle.levelrpg.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.zoogle.levelrpg.LevelRPG;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads recipe unlock requirements from datapacks.
 * Folder: data/<ns>/recipe_unlocks/*.json
 *
 * Example:
 * {
 *   "recipe": "minecraft:diamond_pickaxe",
 *   "requirements": [
 *     { "skill": "levelrpg:mining", "minLevel": 7 },
 *     { "skill": "levelrpg:forging", "minLevel": 3 }
 *   ]
 * }
 */
public class RecipeUnlockLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public RecipeUnlockLoader() {
        super(GSON, "recipe_unlocks");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, RecipeUnlockDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                if (root.has("disabled") && root.get("disabled").getAsBoolean()) {
                    continue;
                }

                String recipeStr = root.has("recipe")
                        ? root.get("recipe").getAsString()
                        : (fileId.getNamespace() + ":" + fileId.getPath());
                ResourceLocation recipeId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(recipeStr, LevelRPG.MODID);
                List<RecipeSkillRequirement> requirements = parseRequirements(root);
                loaded.put(recipeId, new RecipeUnlockDefinition(recipeId, requirements));
            } catch (Exception ex) {
                System.err.println("[LevelRPG] Failed to parse recipe unlock json " + fileId + ": " + ex);
            }
        }

        RecipeUnlockRegistry.clearAndPutAll(loaded);
        System.out.println("[LevelRPG] Loaded " + loaded.size() + " recipe unlock definitions from datapacks.");
    }

    private static List<RecipeSkillRequirement> parseRequirements(JsonObject root) {
        if (!root.has("requirements") || !root.get("requirements").isJsonArray()) {
            return List.of();
        }

        JsonArray array = root.getAsJsonArray("requirements");
        ArrayList<RecipeSkillRequirement> requirements = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject req = element.getAsJsonObject();
            if (!req.has("skill")) {
                continue;
            }
            ResourceLocation skillId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(req.get("skill").getAsString(), LevelRPG.MODID);
            int minLevel = req.has("minLevel") ? Math.max(0, req.get("minLevel").getAsInt()) : 0;
            requirements.add(new RecipeSkillRequirement(skillId, minLevel));
        }
        return requirements;
    }
}
