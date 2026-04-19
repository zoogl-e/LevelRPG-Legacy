package net.zoogle.levelrpg.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.zoogle.levelrpg.LevelRPG;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public SkillLoader() {
        super(GSON, "skills");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, SkillDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                // Allow skill json to be explicitly disabled without removal/rename
                if (root.has("disabled") && root.get("disabled").getAsBoolean()) {
                    continue;
                }
                String idStr = root.has("id") ? root.get("id").getAsString() : (LevelRPG.MODID + ":" + fileId.getPath());
                ResourceLocation skillId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(idStr, LevelRPG.MODID);

                SkillDefinition.Display display = null;
                if (root.has("display") && root.get("display").isJsonObject()) {
                    JsonObject d = root.getAsJsonObject("display");
                    String name = d.has("name") ? d.get("name").getAsString() : null;
                    String color = d.has("color") ? d.get("color").getAsString() : null;
                    String icon = d.has("icon") ? d.get("icon").getAsString() : null;
                    String notes = d.has("notes") ? d.get("notes").getAsString() : null;
                    display = new SkillDefinition.Display(name, color, icon, notes);
                }
                ResourceLocation curve = null;
                if (root.has("xpCurve")) {
                    curve = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(root.get("xpCurve").getAsString(), LevelRPG.MODID);
                } else {
                    curve = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "default");
                }
                SkillDefinition def = new SkillDefinition(skillId, display, curve);
                loaded.put(skillId, def);
            } catch (Exception ex) {
                // Log and skip invalid entries
                System.err.println("[LevelRPG] Failed to parse skill json " + fileId + ": " + ex);
            }
        }
        SkillRegistry.clearAndPutAll(loaded);
        System.out.println("[LevelRPG] Loaded " + loaded.size() + " skills from datapacks.");
    }
}
