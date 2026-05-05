package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.skilltree.effect.SkillNodeEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * <b> Authoritative Skill Tree Definition.</b>
 *
 * <p>The primary source of truth for a skill tree, loaded directly from JSON assets.
 * This structure is used for all server-side logic, progression validation, and persistence.
 *
 * <p>This class should remain independent of client-only rendering concerns (such as pixel
 * coordinates or screen states), which are handled by the presentation layer.
 */
public record SkillTreeCanonicalDefinition(
        ResourceLocation skill,
        int minRank,
        String title,
        String summary,
        LinkedHashMap<String, Node> nodes
) {
    public SkillTreeCanonicalDefinition {
        title = title == null ? "" : title;
        summary = summary == null ? "" : summary;
        nodes = nodes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
    }

    public List<String> orderedNodeIds() {
        return List.copyOf(nodes.keySet());
    }

    public List<String> missingRequirements(String nodeId, java.util.Set<String> unlockedNodes) {
        Node node = nodes.get(nodeId);
        if (node == null || node.requires().isEmpty()) {
            return List.of();
        }
        ArrayList<String> missing = new ArrayList<>();
        for (String requirement : node.requires()) {
            if (!unlockedNodes.contains(requirement)) {
                missing.add(requirement);
            }
        }
        return missing.isEmpty() ? List.of() : List.copyOf(missing);
    }

    public record Node(
            String id,
            int cost,
            List<String> requires,
            int requiredRank,
            String branch,
            String type,
            String title,
            String description,
            /** Graph X in pixels; {@link SkillTreeGraphLayout#AUTO} for automatic placement. */
            int layoutX,
            /** Graph Y in pixels; {@link SkillTreeGraphLayout#AUTO} for automatic placement. */
            int layoutY,
            /**
             * Item id (e.g. {@code minecraft:iron_sword}) or a full texture path containing {@code textures/}
             * for a 16×16 blit. Empty = default book icon.
             */
            String iconKey,
            SkillTreeNodeVisibility visibility,
            List<SkillNodeEffect> effects
    ) {
        public Node {
            id = normalize(id);
            requires = requires == null ? List.of() : List.copyOf(requires);
            branch = branch == null ? "" : branch;
            type = type == null ? "" : type;
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            iconKey = iconKey == null ? "" : iconKey.trim();
            visibility = visibility == null ? SkillTreeNodeVisibility.NORMAL : visibility;
            effects = effects == null ? List.of() : List.copyOf(effects);
        }

        public int normalizedCost() {
            return Math.max(1, cost);
        }

        public int normalizedRequiredRank() {
            return Math.max(0, requiredRank);
        }
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
