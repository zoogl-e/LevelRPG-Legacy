package net.zoogle.levelrpg.client.skilltree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.ProgressionJsonAliases;
import net.zoogle.levelrpg.skilltree.NodeVisibilityMode;
import net.zoogle.levelrpg.skilltree.RequirementSpec;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;
import net.zoogle.levelrpg.skilltree.effect.SkillNodeEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SkillTreeEditorDraft {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern GENERATED_ID = Pattern.compile("new_node_\\d+");

    private final ResourceLocation skillId;
    private final String title;
    private final String description;
    private final LinkedHashMap<String, DraftNode> nodes = new LinkedHashMap<>();

    private SkillTreeEditorDraft(ResourceLocation skillId, String title, String description) {
        this.skillId = skillId;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
    }

    public static SkillTreeEditorDraft copyOf(SkillTreePresentationDefinition definition, ResourceLocation fallbackSkillId) {
        ResourceLocation skillId = definition == null ? fallbackSkillId : definition.skillId();
        SkillTreeEditorDraft draft = new SkillTreeEditorDraft(
                skillId,
                definition == null ? "" : definition.title(),
                definition == null ? "" : definition.description()
        );
        if (definition != null) {
            for (SkillTreeNodeDefinition node : definition.nodes().values()) {
                draft.nodes.put(node.id(), DraftNode.copyOf(node));
            }
        }
        return draft;
    }

    public void applyJsonMetadata(JsonObject root) {
        if (root == null || !root.has("nodes") || !root.get("nodes").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("nodes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject nodeJson = element.getAsJsonObject();
            String id = stringValue(nodeJson, "id", "");
            DraftNode node = nodes.get(id);
            if (node == null) {
                continue;
            }
            node.branch = stringValue(nodeJson, "branch", node.branch);
            node.iconKey = stringValue(nodeJson, "icon", stringValue(nodeJson, "iconKey", node.iconKey));
            node.visibility = NodeVisibilityMode.fromJson(stringValue(nodeJson, "visibility", node.visibility.jsonName()));
            node.type = NodeType.fromJson(stringValue(nodeJson, "type", node.type.jsonName()));
            node.requirement = requirementFromJson(nodeJson, "requirement", "requires", node.requirement);
            node.revealRequirement = optionalRequirementFromJson(nodeJson, "revealRequirement", node.revealRequirement);
            node.effects = effectsFromJson(nodeJson, node.effects);
        }
    }

    public SkillTreePresentationDefinition toDefinition() {
        LinkedHashMap<String, SkillTreeNodeDefinition> copy = new LinkedHashMap<>();
        for (DraftNode node : nodes.values()) {
            copy.put(node.id, node.toDefinition());
        }
        return new SkillTreePresentationDefinition(skillId, title, description, copy);
    }

    public boolean contains(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public DraftNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    public String createNode(int x, int y) {
        String id = nextNodeId();
        nodes.put(id, new DraftNode(id, "", "", x, y, 0, 1, RequirementSpec.EMPTY, null, NodeVisibilityMode.VISIBLE, false, NodeType.TRAIT, "", "", List.of(), ""));
        return id;
    }

    public RenameResult renameNode(String oldId, String newId) {
        newId = normalizeId(newId);
        if (oldId == null || oldId.equals(newId)) {
            return RenameResult.unchanged(oldId);
        }
        if (newId.isBlank()) {
            return RenameResult.invalid(oldId, "Node ID cannot be empty");
        }
        if (!isValidNodeId(newId)) {
            return RenameResult.invalid(oldId, "Node ID must use lowercase a-z, 0-9, and _");
        }
        if (nodes.containsKey(newId)) {
            return RenameResult.invalid(oldId, "Duplicate node id: " + newId);
        }
        DraftNode existing = nodes.get(oldId);
        if (existing == null) {
            return RenameResult.invalid(oldId, "Selected node no longer exists");
        }
        LinkedHashMap<String, DraftNode> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, DraftNode> entry : nodes.entrySet()) {
            if (entry.getKey().equals(oldId)) {
                existing.id = newId;
                renamed.put(newId, existing);
            } else {
                renamed.put(entry.getKey(), entry.getValue());
            }
        }
        nodes.clear();
        nodes.putAll(renamed);
        for (DraftNode node : nodes.values()) {
            node.requirement = node.requirement.renamedNode(oldId, newId);
            node.revealRequirement = node.revealRequirement == null ? null : node.revealRequirement.renamedNode(oldId, newId);
        }
        return RenameResult.renamed(newId);
    }

    public void deleteNode(String nodeId) {
        if (nodeId == null) {
            return;
        }
        nodes.remove(nodeId);
        for (DraftNode node : nodes.values()) {
            node.requirement = node.requirement.withoutNode(nodeId);
            node.revealRequirement = node.revealRequirement == null ? null : node.revealRequirement.withoutNode(nodeId);
        }
    }

    public void moveNode(String nodeId, int x, int y) {
        DraftNode node = nodes.get(nodeId);
        if (node != null) {
            node.x = x;
            node.y = y;
        }
    }

    public void updateNode(String nodeId, String title, String description, int cost, int requiredLevel, String icon) {
        DraftNode node = nodes.get(nodeId);
        if (node == null) {
            return;
        }
        node.title = title == null ? "" : title;
        node.description = description == null ? "" : description;
        node.cost = Math.max(1, cost);
        node.requiredRank = Math.max(0, requiredLevel);
        node.iconKey = icon == null ? "" : icon.trim();
    }

    public void updateNodeType(String nodeId, NodeType type) {
        DraftNode node = nodes.get(nodeId);
        if (node != null) {
            node.type = type == null ? NodeType.TRAIT : type;
        }
    }

    public void updateNodeVisibility(String nodeId, NodeVisibilityMode visibility) {
        DraftNode node = nodes.get(nodeId);
        if (node != null) {
            node.visibility = visibility == null ? NodeVisibilityMode.VISIBLE : visibility;
        }
    }

    public void updateRequirement(String nodeId, RequirementTarget target, RequirementSpec.Mode mode, List<String> ids, int count) {
        DraftNode node = nodes.get(nodeId);
        if (node == null) {
            return;
        }
        RequirementSpec spec = new RequirementSpec(mode, ids, count);
        if (target == RequirementTarget.REVEAL) {
            node.revealRequirement = spec.isEmpty() ? null : spec;
        } else {
            node.requirement = spec;
        }
    }

    public void toggleRequirement(String nodeId, String requirementId, RequirementTarget target) {
        if (nodeId == null || requirementId == null || nodeId.equals(requirementId)) {
            return;
        }
        DraftNode node = nodes.get(nodeId);
        if (node == null || !nodes.containsKey(requirementId)) {
            return;
        }
        if (target == RequirementTarget.REVEAL) {
            RequirementSpec current = node.revealRequirement == null ? RequirementSpec.EMPTY : node.revealRequirement;
            RequirementSpec toggled = current.toggledNode(requirementId);
            node.revealRequirement = toggled.isEmpty() ? null : toggled;
        } else {
            node.requirement = node.requirement.toggledNode(requirementId);
        }
    }

    public List<String> validate() {
        ArrayList<String> errors = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (DraftNode node : nodes.values()) {
            if (!seen.add(node.id)) {
                errors.add("Duplicate node id: " + node.id);
            }
            if (node.title == null || node.title.isBlank()) {
                errors.add("Empty title: " + node.id);
            }
            if (!isValidNodeId(node.id)) {
                errors.add("Invalid node id: " + node.id);
            }
            if (!node.iconKey.isBlank() && !isValidResourceLocation(node.iconKey)) {
                errors.add("Invalid icon '" + node.iconKey + "' on " + node.id);
            }
            validateRequirement(errors, node.id, "requirement", node.requirement, true);
            validateRequirement(errors, node.id, "revealRequirement", node.revealRequirement, false);
        }
        for (String nodeId : nodes.keySet()) {
            if (hasCycle(nodeId, new HashSet<>(), new HashSet<>())) {
                errors.add("Cycle involving: " + nodeId);
                break;
            }
        }
        return List.copyOf(errors);
    }

    public List<String> warnings() {
        ArrayList<String> warnings = new ArrayList<>();
        List<String> generated = generatedIds();
        if (!generated.isEmpty()) {
            warnings.add("Generated node ids: " + String.join(", ", generated));
        }
        for (DraftNode node : nodes.values()) {
            if (node.visibility != NodeVisibilityMode.VISIBLE && node.revealRequirement == null && node.requirement.isEmpty()) {
                warnings.add(node.id + " is " + node.visibility.jsonName() + " without revealRequirement");
                break;
            }
            collectRequirementWarnings(warnings, node.id, "requirement", node.requirement);
            collectRequirementWarnings(warnings, node.id, "revealRequirement", node.revealRequirement);
        }
        return List.copyOf(warnings);
    }

    public List<String> generatedIds() {
        ArrayList<String> generated = new ArrayList<>();
        for (String nodeId : nodes.keySet()) {
            if (GENERATED_ID.matcher(nodeId).matches()) {
                generated.add(nodeId);
            }
        }
        return List.copyOf(generated);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public String toJson() {
        return GSON.toJson(toJsonObject(null));
    }

    public String toJsonPreserving(JsonObject existingRoot) {
        return GSON.toJson(toJsonObject(existingRoot));
    }

    private JsonObject toJsonObject(JsonObject existingRoot) {
        JsonObject root = new JsonObject();
        if (existingRoot != null) {
            for (Map.Entry<String, JsonElement> entry : existingRoot.entrySet()) {
                if (!entry.getKey().equals("nodes") && !entry.getKey().equals("thresholds")) {
                    root.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }
        if (!root.has("skill")) {
            root.addProperty("skill", skillId.toString());
        }
        if (!root.has("title")) {
            root.addProperty("title", title);
        }
        if (!root.has("summary")) {
            root.addProperty("summary", description);
        }
        JsonArray arr = new JsonArray();
        for (DraftNode node : nodes.values()) {
            JsonObject n = new JsonObject();
            n.addProperty("id", node.id);
            n.addProperty("title", node.title);
            n.addProperty("description", node.description);
            n.addProperty("type", node.type.jsonName());
            if (!node.branch.isBlank()) {
                n.addProperty("branch", node.branch);
            }
            n.addProperty("cost", node.cost);
            n.addProperty("requiredRank", node.requiredRank);
            n.addProperty("layoutX", node.x);
            n.addProperty("layoutY", node.y);
            if (!node.iconKey.isBlank()) {
                n.addProperty("icon", node.iconKey);
            }
            n.add("requirement", requirementToJson(node.requirement));
            if (!node.requirement.nodes().isEmpty()) {
                JsonArray legacyRequires = new JsonArray();
                for (String requirement : node.requirement.nodes()) {
                    legacyRequires.add(requirement);
                }
                n.add("requires", legacyRequires);
            }
            if (node.revealRequirement != null && !node.revealRequirement.isEmpty()) {
                n.add("revealRequirement", requirementToJson(node.revealRequirement));
            }
            if (node.visibility != NodeVisibilityMode.VISIBLE) {
                n.addProperty("visibility", node.visibility.jsonName());
            }
            if (!node.effects.isEmpty()) {
                JsonArray effects = new JsonArray();
                for (SkillNodeEffect effect : node.effects) {
                    JsonObject effectJson = new JsonObject();
                    effectJson.addProperty("type", effect.type().toString());
                    effectJson.addProperty("id", effect.id().toString());
                    effects.add(effectJson);
                }
                n.add("effects", effects);
            }
            arr.add(n);
        }
        root.add("nodes", arr);
        return root;
    }

    private boolean hasCycle(String nodeId, Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        DraftNode node = nodes.get(nodeId);
        if (node != null) {
            for (String requirement : node.requirement.nodes()) {
                if (nodes.containsKey(requirement) && hasCycle(requirement, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private String nextNodeId() {
        int index = nodes.size() + 1;
        String id;
        do {
            id = "new_node_" + index++;
        } while (nodes.containsKey(id));
        return id;
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static RequirementSpec requirementFromJson(JsonObject json, String objectKey, String legacyKey, RequirementSpec fallback) {
        RequirementSpec parsed = optionalRequirementFromJson(json, objectKey, null);
        if (parsed != null) {
            return parsed;
        }
        if (json != null && json.has(legacyKey) && json.get(legacyKey).isJsonArray()) {
            return RequirementSpec.all(stringsFromArray(json.getAsJsonArray(legacyKey)));
        }
        return fallback == null ? RequirementSpec.EMPTY : fallback;
    }

    private static RequirementSpec optionalRequirementFromJson(JsonObject json, String key, RequirementSpec fallback) {
        if (json == null || !json.has(key) || !json.get(key).isJsonObject()) {
            return fallback;
        }
        JsonObject requirement = json.getAsJsonObject(key);
        RequirementSpec.Mode mode = RequirementSpec.Mode.fromJson(stringValue(requirement, "mode", "all"));
        List<String> nodes = requirement.has("nodes") && requirement.get("nodes").isJsonArray()
                ? stringsFromArray(requirement.getAsJsonArray("nodes"))
                : List.of();
        int count = requirement.has("count") ? requirement.get("count").getAsInt() : 1;
        return new RequirementSpec(mode, nodes, count);
    }

    private static List<String> stringsFromArray(JsonArray array) {
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
        return values;
    }

    private static JsonObject requirementToJson(RequirementSpec requirement) {
        RequirementSpec spec = requirement == null ? RequirementSpec.EMPTY : requirement;
        JsonObject json = new JsonObject();
        json.addProperty("mode", spec.mode().jsonName());
        if (spec.mode() == RequirementSpec.Mode.AT_LEAST) {
            json.addProperty("count", spec.count());
        }
        JsonArray nodes = new JsonArray();
        for (String node : spec.nodes()) {
            nodes.add(node);
        }
        json.add("nodes", nodes);
        return json;
    }

    private static List<SkillNodeEffect> effectsFromJson(JsonObject json, List<SkillNodeEffect> fallback) {
        if (json == null || !json.has("effects") || !json.get("effects").isJsonArray()) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }
        ArrayList<SkillNodeEffect> effects = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("effects")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject effect = element.getAsJsonObject();
            if (!effect.has("type") || !effect.has("id")) {
                continue;
            }
            try {
                ResourceLocation type = ResourceLocation.parse(effect.get("type").getAsString());
                ResourceLocation id = ResourceLocation.parse(effect.get("id").getAsString());
                effects.add(new SkillNodeEffect(type, id));
            } catch (Exception ignored) {
            }
        }
        return List.copyOf(effects);
    }

    private void validateRequirement(ArrayList<String> errors, String nodeId, String label, RequirementSpec requirement, boolean includeCycle) {
        if (requirement == null || requirement.isEmpty()) {
            return;
        }
        HashSet<String> seen = new HashSet<>();
        for (String requirementId : requirement.nodes()) {
            if (!seen.add(requirementId)) {
                errors.add("Duplicate " + label + " id '" + requirementId + "' on " + nodeId);
            }
            if (nodeId.equals(requirementId)) {
                errors.add("Self " + label + ": " + nodeId);
            }
        }
        if (requirement.mode() == RequirementSpec.Mode.AT_LEAST
                && (requirement.count() < 1 || requirement.count() > requirement.nodes().size())) {
            errors.add("Invalid at_least count on " + nodeId);
        }
    }

    private void collectRequirementWarnings(ArrayList<String> warnings, String nodeId, String label, RequirementSpec requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return;
        }
        for (String requirementId : requirement.nodes()) {
            if (!nodes.containsKey(requirementId)) {
                warnings.add("Missing " + label + " '" + requirementId + "' on " + nodeId);
                return;
            }
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isValidNodeId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        return colon > 0
                && colon == trimmed.lastIndexOf(':')
                && colon < trimmed.length() - 1
                && isValidResourcePart(trimmed.substring(0, colon), false)
                && isValidResourcePart(trimmed.substring(colon + 1), true);
    }

    private static boolean isValidResourcePart(String value, boolean path) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            if (path) {
                ok = ok || c == '/';
            }
            if (!ok) {
                return false;
            }
        }
        return !value.isBlank();
    }

    public enum NodeType {
        CORE("Core", "core"),
        TRAIT("Trait", "trait"),
        TECHNIQUE("Technique", "technique"),
        MANIFESTATION("Manifestation", "manifestation"),
        AXIOM("Axiom", "axiom");

        private final String label;
        private final String jsonName;

        NodeType(String label, String jsonName) {
            this.label = label;
            this.jsonName = jsonName;
        }

        public String label() {
            return label;
        }

        public String jsonName() {
            return jsonName;
        }

        /**
         * Accepts legacy JSON type strings {@code keystone}→{@link NodeType#AXIOM} and {@code mastery}→{@link NodeType#MANIFESTATION}
         * (same normalization as server {@link ProgressionJsonAliases#normalizeSkillTreeNodeType}).
         */
        public static NodeType fromJson(String value) {
            String raw = value == null ? "" : value.trim();
            String normalized = ProgressionJsonAliases.normalizeSkillTreeNodeType(raw).toLowerCase();
            for (NodeType type : values()) {
                if (type.jsonName.equals(normalized) || type.label.toLowerCase().equals(normalized)) {
                    return type;
                }
            }
            return TRAIT;
        }
    }

    public enum RequirementTarget {
        UNLOCK("Unlock Requirement"),
        REVEAL("Reveal Requirement");

        private final String label;

        RequirementTarget(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class DraftNode {
        private String id;
        private String title;
        private String description;
        private int x;
        private int y;
        private int requiredRank;
        private int cost;
        private RequirementSpec requirement;
        private RequirementSpec revealRequirement;
        private NodeVisibilityMode visibility;
        private final boolean hidden;
        private NodeType type;
        private String branch;
        private String iconKey;
        private List<SkillNodeEffect> effects;

        private DraftNode(String id, String title, String description, int x, int y, int requiredRank, int cost, RequirementSpec requirement, RequirementSpec revealRequirement, NodeVisibilityMode visibility, boolean hidden, NodeType type, String branch, String iconKey, List<SkillNodeEffect> effects, String ignoredVisibility) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.x = x;
            this.y = y;
            this.requiredRank = requiredRank;
            this.cost = cost;
            this.requirement = requirement == null ? RequirementSpec.EMPTY : requirement;
            this.revealRequirement = revealRequirement;
            this.visibility = visibility == null ? NodeVisibilityMode.VISIBLE : visibility;
            this.hidden = hidden;
            this.type = type == null ? NodeType.TRAIT : type;
            this.branch = branch == null ? "" : branch;
            this.iconKey = iconKey == null ? "" : iconKey;
            this.effects = effects == null ? List.of() : List.copyOf(effects);
        }

        private static DraftNode copyOf(SkillTreeNodeDefinition node) {
            return new DraftNode(
                    node.id(),
                    node.title(),
                    node.description(),
                    node.x(),
                    node.y(),
                    node.requiredRank(),
                    node.cost(),
                    node.requirement(),
                    node.revealRequirement(),
                    node.visibility(),
                    node.hidden(),
                    NodeType.fromJson(node.type()),
                    "",
                    node.icon(),
                    node.effects(),
                    ""
            );
        }

        private SkillTreeNodeDefinition toDefinition() {
            return new SkillTreeNodeDefinition(id, title, description, x, y, requiredRank, cost, requirement, revealRequirement, visibility, type.jsonName(), iconKey, effects, hidden);
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int requiredRank() {
            return requiredRank;
        }

        public int cost() {
            return cost;
        }

        public List<String> requires() {
            return requirement.nodes();
        }

        public RequirementSpec requirement() {
            return requirement;
        }

        public RequirementSpec revealRequirement() {
            return revealRequirement;
        }

        public NodeVisibilityMode visibility() {
            return visibility;
        }

        public NodeType type() {
            return type;
        }

        public String icon() {
            return iconKey;
        }
    }

    public record RenameResult(String nodeId, boolean renamed, String warning) {
        private static RenameResult unchanged(String nodeId) {
            return new RenameResult(nodeId, false, "");
        }

        private static RenameResult renamed(String nodeId) {
            return new RenameResult(nodeId, true, "");
        }

        private static RenameResult invalid(String nodeId, String warning) {
            return new RenameResult(nodeId, false, warning);
        }
    }
}
