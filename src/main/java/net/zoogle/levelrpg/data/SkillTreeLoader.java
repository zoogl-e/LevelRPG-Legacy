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

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillTreeLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public SkillTreeLoader() {
        super(GSON, "skill_trees");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, SkillTreeDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                if (root.has("disabled") && root.get("disabled").getAsBoolean()) continue;

                String skillStr = root.has("skill") ? root.get("skill").getAsString() : (LevelRPG.MODID + ":" + fileId.getPath());
                ResourceLocation skillId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(skillStr, LevelRPG.MODID);

                int minSkillLevel = root.has("minSkillLevel") ? root.get("minSkillLevel").getAsInt() : 0;

                LinkedHashMap<String, SkillTreeDefinition.Node> nodes = new LinkedHashMap<>();
                if (root.has("nodes") && root.get("nodes").isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray("nodes");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject n = el.getAsJsonObject();
                        String id = n.has("id") ? n.get("id").getAsString() : null;
                        if (id == null || id.isEmpty()) continue;
                        int cost = n.has("cost") ? n.get("cost").getAsInt() : 1;
                        java.util.List<String> requires = java.util.Collections.emptyList();
                        if (n.has("requires") && n.get("requires").isJsonArray()) {
                            JsonArray req = n.getAsJsonArray("requires");
                            java.util.ArrayList<String> list = new java.util.ArrayList<>();
                            for (JsonElement re : req) list.add(re.getAsString());
                            requires = java.util.List.copyOf(list);
                        }
                        SkillTreeDefinition.Node node = new SkillTreeDefinition.Node(id, cost, requires);
                        nodes.put(id, node);
                    }
                }

                SkillTreeDefinition def = new SkillTreeDefinition(skillId, minSkillLevel, nodes);
                loaded.put(skillId, def);
            } catch (Exception ex) {
                System.err.println("[LevelRPG] Failed to parse skill tree json " + fileId + ": " + ex);
            }
        }
        SkillTreeRegistry.clearAndPutAll(loaded);
        System.out.println("[LevelRPG] Loaded " + loaded.size() + " skill trees from datapacks.");
    }
}
