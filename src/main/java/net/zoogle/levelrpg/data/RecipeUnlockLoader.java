package net.zoogle.levelrpg.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import org.slf4j.Logger;

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
 *     { "skill": "levelrpg:delving", "minLevel": 7 },
 *     { "skill": "levelrpg:forging", "minLevel": 3 }
 *   ]
 * }
 */
public class RecipeUnlockLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();

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
                if (requirements.isEmpty()) {
                    LOGGER.warn("Ignoring recipe unlock json with no canonical requirements {} -> {}", fileId, recipeId);
                    continue;
                }
                loaded.put(recipeId, new RecipeUnlockDefinition(recipeId, requirements));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse recipe unlock json {}", fileId, ex);
            }
        }

        RecipeUnlockRegistry.clearAndPutAll(loaded);
        LOGGER.info("Loaded {} recipe unlock definitions from datapacks.", loaded.size());
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
            if (!ProgressionSkill.isCanonicalId(skillId)) {
                LOGGER.warn("Ignoring legacy/non-canonical recipe unlock requirement for skill {}", skillId);
                continue;
            }
            int minLevel = req.has("minLevel") ? Math.max(0, req.get("minLevel").getAsInt()) : 0;
            requirements.add(new RecipeSkillRequirement(skillId, minLevel));
        }
        return requirements;
    }
}
