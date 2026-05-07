package net.zoogle.levelrpg.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Terminology compatibility for progression JSON only. Does not change NBT, packets, or Java field names.
 *
 * <p><b>Discipline:</b> Datapack JSON historically used the key {@code "skill"} for a canonical discipline id
 * (e.g. {@code levelrpg:valor}). The alias key {@code "discipline"} is accepted alongside {@code "skill"}.
 *
 * <p><b>Tree node types:</b> Legacy node type strings {@code keystone} and {@code mastery} map to the
 * current canonical types {@code axiom} and {@code manifestation} at parse time only.
 */
public final class ProgressionJsonAliases {
    private ProgressionJsonAliases() {}

    /**
     * Normalizes a skill-tree node {@code "type"} string from JSON. Legacy values {@code keystone}
     * and {@code mastery} (case-insensitive) become {@code axiom} and {@code manifestation}; all other
     * values are returned trimmed, preserving casing for non-legacy strings.
     */
    public static String normalizeSkillTreeNodeType(String rawFromJson) {
        if (rawFromJson == null) {
            return "";
        }
        String trimmed = rawFromJson.trim();
        String lower = trimmed.toLowerCase();
        if ("keystone".equals(lower)) {
            return "axiom";
        }
        if ("mastery".equals(lower)) {
            return "manifestation";
        }
        return trimmed;
    }

    /**
     * Reads the discipline id string from a skill-tree JSON root. Prefers {@code "skill"} when both
     * keys are present so existing datapacks behave identically.
     */
    public static String readDisciplineIdFromTreeRoot(JsonObject root, ResourceLocation fileId) {
        if (root.has("skill") && !root.get("skill").isJsonNull()) {
            return root.get("skill").getAsString();
        }
        if (root.has("discipline") && !root.get("discipline").isJsonNull()) {
            return root.get("discipline").getAsString();
        }
        return LevelRPG.MODID + ":" + fileId.getPath();
    }

    /**
     * Reads a discipline id element from an activity rule or requirement object. Prefers {@code "skill"}
     * when both are present. Returns null if neither key is present or the chosen value is not a JSON primitive.
     */
    public static JsonElement disciplineIdElementFromRule(JsonObject obj) {
        if (obj.has("skill")) {
            return obj.get("skill");
        }
        if (obj.has("discipline")) {
            return obj.get("discipline");
        }
        return null;
    }
}
