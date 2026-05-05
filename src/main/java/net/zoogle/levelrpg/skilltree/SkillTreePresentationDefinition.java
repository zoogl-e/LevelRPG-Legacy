package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * <b>Client/Editor Runtime Graph Model.</b>
 *
 * <p>A presentation-oriented representation of a skill tree that includes layout coordinates (X/Y)
 * and UI visibility metadata. This model is consumed by renderers and editors.
 *
 * <p><b>Important:</b> Despite holding node data, this class is <em>not authoritative</em>.
 * It must not be used for server-side progression checks or stat calculations; use
 * {@link net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition} for those purposes.
 */
public record SkillTreePresentationDefinition(
        ResourceLocation skillId,
        String title,
        String description,
        LinkedHashMap<String, SkillTreeNodeDefinition> nodes
) {
    public SkillTreePresentationDefinition {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        nodes = nodes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
    }

    public List<SkillTreeEdge> edges() {
        ArrayList<SkillTreeEdge> edges = new ArrayList<>();
        for (SkillTreeNodeDefinition node : nodes.values()) {
            for (String parentId : node.requires()) {
                if (nodes.containsKey(parentId)) {
                    edges.add(new SkillTreeEdge(parentId, node.id()));
                }
            }
        }
        return List.copyOf(edges);
    }

    public SkillTreeNodeDefinition node(String nodeId) {
        return nodes.get(nodeId);
    }

    public static Builder builder(ResourceLocation skillId, String title, String description) {
        return new Builder(skillId, title, description);
    }

    public static final class Builder {
        private final ResourceLocation skillId;
        private final String title;
        private final String description;
        private final LinkedHashMap<String, SkillTreeNodeDefinition> nodes = new LinkedHashMap<>();

        private Builder(ResourceLocation skillId, String title, String description) {
            this.skillId = skillId;
            this.title = title;
            this.description = description;
        }

        public Builder node(String id, String title, String description, int x, int y, int level, int cost, String... requires) {
            nodes.put(id, new SkillTreeNodeDefinition(id, title, description, x, y, level, cost, List.of(requires), false));
            return this;
        }

        public Builder hiddenNode(String id, String title, String description, int x, int y, int level, int cost, String... requires) {
            nodes.put(id, new SkillTreeNodeDefinition(id, title, description, x, y, level, cost, List.of(requires), true));
            return this;
        }

        public Builder nodeWithMetadata(String id, String title, String description, int x, int y, int level, int cost, String type, String icon, String... requires) {
            nodes.put(id, new SkillTreeNodeDefinition(id, title, description, x, y, level, cost, List.of(requires), type, icon, false));
            return this;
        }

        public Builder hiddenNodeWithMetadata(String id, String title, String description, int x, int y, int level, int cost, String type, String icon, String... requires) {
            nodes.put(id, new SkillTreeNodeDefinition(id, title, description, x, y, level, cost, List.of(requires), type, icon, true));
            return this;
        }

        public Builder rawNode(SkillTreeNodeDefinition node) {
            if (node != null) {
                nodes.put(node.id(), node);
            }
            return this;
        }

        public SkillTreePresentationDefinition build() {
            return new SkillTreePresentationDefinition(skillId, title, description, new LinkedHashMap<>(nodes));
        }
    }
}
