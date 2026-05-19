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
import net.zoogle.levelrpg.skilltree.RequirementSpec;
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
 *
 * <p>Node {@code "requirement"} is the preferred unlock format because it preserves ALL/ANY/AT_LEAST
 * modes. Legacy {@code "requires": [...]} remains supported and is converted to an ALL requirement.
 * Node type/kind strings must remain intact through canonical loading and presentation adaptation so
 * {@code core}, {@code technique}, {@code axiom}, and {@code manifestation} keep their runtime meaning.
 *
 * <p>Node {@code "layout"} is the preferred layout metadata object going forward. Manual
 * {@code layout.x/y} wins over legacy top-level {@code layoutX/layoutY}; semantic
 * {@code layout.branch/tier/lane} can generate coordinates when neither manual form is present.
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
                        RequirementSpec requirement = readRequirement(n, "requirement", "requires");
                        RequirementSpec revealRequirement = readOptionalRequirement(n, "revealRequirement");
                        java.util.List<String> requires = requirement.nodes();
                        int requiredRank = n.has("requiredRank") ? n.get("requiredRank").getAsInt() : minRank;
                        SkillTreeNodeLayout layout = readLayout(n);
                        String branch = !layout.branch().isBlank()
                                ? layout.branch()
                                : n.has("branch") ? n.get("branch").getAsString() : "";
                        String type = n.has("type")
                                ? ProgressionJsonAliases.normalizeSkillTreeNodeType(n.get("type").getAsString())
                                : "";
                        String nodeTitle = n.has("title") ? n.get("title").getAsString() : "";
                        String description = n.has("description") ? n.get("description").getAsString() : "";
                        int layoutX = layout.x() != null
                                ? layout.x()
                                : n.has("layoutX") ? n.get("layoutX").getAsInt() : SkillTreeGraphLayout.AUTO;
                        int layoutY = layout.y() != null
                                ? layout.y()
                                : n.has("layoutY") ? n.get("layoutY").getAsInt() : SkillTreeGraphLayout.AUTO;
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
                                requirement,
                                revealRequirement,
                                requiredRank,
                                branch,
                                layout,
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

    private static RequirementSpec readRequirement(JsonObject json, String objectKey, String legacyKey) {
        RequirementSpec parsed = readOptionalRequirement(json, objectKey);
        if (parsed != null) {
            return parsed;
        }
        if (json != null && json.has(legacyKey) && json.get(legacyKey).isJsonArray()) {
            return RequirementSpec.all(stringsFromArray(json.getAsJsonArray(legacyKey)));
        }
        return RequirementSpec.EMPTY;
    }

    private static SkillTreeNodeLayout readLayout(JsonObject nodeJson) {
        if (nodeJson == null || !nodeJson.has("layout") || !nodeJson.get("layout").isJsonObject()) {
            return SkillTreeNodeLayout.EMPTY;
        }
        JsonObject layout = nodeJson.getAsJsonObject("layout");
        String branch = layout.has("branch") && !layout.get("branch").isJsonNull()
                ? layout.get("branch").getAsString()
                : "";
        Integer tier = intOrNull(layout, "tier");
        Integer lane = intOrNull(layout, "lane");
        Integer x = intOrNull(layout, "x");
        Integer y = intOrNull(layout, "y");
        return new SkillTreeNodeLayout(branch, tier, lane, x, y);
    }

    private static Integer intOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : null;
    }

    private static RequirementSpec readOptionalRequirement(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonObject()) {
            return null;
        }
        JsonObject requirement = json.getAsJsonObject(key);
        RequirementSpec.Mode mode = RequirementSpec.Mode.fromJson(
                requirement.has("mode") && !requirement.get("mode").isJsonNull()
                        ? requirement.get("mode").getAsString()
                        : "all"
        );
        List<String> nodes = requirement.has("nodes") && requirement.get("nodes").isJsonArray()
                ? stringsFromArray(requirement.getAsJsonArray("nodes"))
                : List.of();
        int count = requirement.has("count") && !requirement.get("count").isJsonNull()
                ? requirement.get("count").getAsInt()
                : 1;
        return new RequirementSpec(mode, nodes, count);
    }

    private static List<String> stringsFromArray(JsonArray array) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
        return List.copyOf(values);
    }
}
