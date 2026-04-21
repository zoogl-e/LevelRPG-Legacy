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
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public SkillLoader() {
        super(GSON, "skills");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, SkillDefinition> parsed = new LinkedHashMap<>();
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
                if (!ProgressionSkill.isCanonicalId(skillId)) {
                    System.out.println("[LevelRPG] Ignoring legacy/non-canonical skill json " + fileId + " -> " + skillId);
                    continue;
                }

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
                parsed.put(skillId, def);
            } catch (Exception ex) {
                // Log and skip invalid entries
                System.err.println("[LevelRPG] Failed to parse skill json " + fileId + ": " + ex);
            }
        }

        LinkedHashMap<ResourceLocation, SkillDefinition> loaded = new LinkedHashMap<>();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillDefinition definition = parsed.get(skill.id());
            if (definition == null) {
                definition = new SkillDefinition(
                        skill.id(),
                        new SkillDefinition.Display(skill.displayName(), null, null, "Fallback canonical skill definition."),
                        ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "default")
                );
                System.out.println("[LevelRPG] Missing datapack skill definition for canonical skill " + skill.id() + "; using fallback definition.");
            }
            loaded.put(skill.id(), definition);
        }
        SkillRegistry.clearAndPutAll(loaded);
        System.out.println("[LevelRPG] Loaded canonical skill catalog: " + loaded.size() + " skills.");
    }
}
