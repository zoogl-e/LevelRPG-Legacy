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
import net.zoogle.levelrpg.skilltree.effect.SkillNodeEffect;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads canonical discipline tree definitions from {@code data/<ns>/skill_trees/}. The JSON root key
 * {@code "skill"} holds the discipline id; {@code "discipline"} is accepted as an alias (see
 * {@link ProgressionJsonAliases#readDisciplineIdFromTreeRoot}). Node {@code "type"} strings support legacy
 * aliases {@code keystone}→{@code axiom} and {@code mastery}→{@code manifestation}.
 */
public class SkillTreeLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    public SkillTreeLoader() {
        super(GSON, "skill_trees");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, SkillTreeCanonicalDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                if (root.has("disabled") && root.get("disabled").getAsBoolean()) continue;

                String skillStr = ProgressionJsonAliases.readDisciplineIdFromTreeRoot(root, fileId);
                ResourceLocation skillId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(skillStr, LevelRPG.MODID);
                if (!ProgressionSkill.isCanonicalId(skillId)) {
                    LOGGER.warn("Ignoring legacy/non-canonical skill tree json {} -> {}", fileId, skillId);
                    continue;
                }

                int minRank = root.has("minRank") ? root.get("minRank").getAsInt() : 0;
                String title = root.has("title") ? root.get("title").getAsString() : "";
                String summary = root.has("summary") ? root.get("summary").getAsString() : "";

                LinkedHashMap<String, SkillTreeCanonicalDefinition.Node> nodes = new LinkedHashMap<>();
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
                        int requiredRank = n.has("requiredRank") ? n.get("requiredRank").getAsInt() : minRank;
                        String branch = n.has("branch") ? n.get("branch").getAsString() : "";
                        String type = n.has("type")
                                ? ProgressionJsonAliases.normalizeSkillTreeNodeType(n.get("type").getAsString())
                                : "";
                        String nodeTitle = n.has("title") ? n.get("title").getAsString() : "";
                        String description = n.has("description") ? n.get("description").getAsString() : "";
                        int layoutX = n.has("layoutX") ? n.get("layoutX").getAsInt() : SkillTreeGraphLayout.AUTO;
                        int layoutY = n.has("layoutY") ? n.get("layoutY").getAsInt() : SkillTreeGraphLayout.AUTO;
                        String iconKey = n.has("icon") ? n.get("icon").getAsString() : "";
                        SkillTreeNodeVisibility visibility = SkillTreeNodeVisibility.fromJson(
                                n.has("visibility") ? n.get("visibility").getAsString() : null
                        );
                        java.util.ArrayList<SkillNodeEffect> effects = new java.util.ArrayList<>();
                        if (n.has("effects") && n.get("effects").isJsonArray()) {
                            for (JsonElement effectElement : n.getAsJsonArray("effects")) {
                                if (!effectElement.isJsonObject()) {
                                    continue;
                                }
                                JsonObject effectJson = effectElement.getAsJsonObject();
                                if (!effectJson.has("type") || !effectJson.has("id")) {
                                    continue;
                                }
                                ResourceLocation effectType = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(effectJson.get("type").getAsString(), LevelRPG.MODID);
                                ResourceLocation effectId = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(effectJson.get("id").getAsString(), LevelRPG.MODID);
                                if (effectType != null && effectId != null) {
                                    effects.add(new SkillNodeEffect(effectType, effectId));
                                }
                            }
                        }
                        SkillTreeCanonicalDefinition.Node node = new SkillTreeCanonicalDefinition.Node(
                                id,
                                cost,
                                requires,
                                requiredRank,
                                branch,
                                type,
                                nodeTitle,
                                description,
                                layoutX,
                                layoutY,
                                iconKey,
                                visibility,
                                List.copyOf(effects)
                        );
                        nodes.put(id, node);
                    }
                }

                SkillTreeCanonicalDefinition def = new SkillTreeCanonicalDefinition(
                        skillId,
                        minRank,
                        title,
                        summary,
                        nodes
                );
                loaded.put(skillId, def);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse skill tree json {}", fileId, ex);
            }
        }
        SkillTreeRegistry.clearAndPutAll(loaded);
        net.zoogle.levelrpg.skilltree.SkillTreeRegistry.invalidateAdaptedCache();
        LOGGER.info("Loaded canonical skill trees: {}.", loaded.size());
    }
}
