package net.zoogle.levelrpg.client.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeState;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepared client-side tree graph consumed by the shared skill tree renderer.
 *
 * <p>This model owns presentation data that is independent from screen widgets, editor forms,
 * networking, chamber animation, and Enchiridion projection compatibility.
 */
public record SkillTreeScene(
        ResourceLocation skillId,
        SkillTreePresentationDefinition definition,
        SkillTreeState state,
        List<SkillTreeNodeView> nodeViews,
        Map<String, SkillTreeNodeView> nodeById,
        SkillTreeNodeView rootNode
) {
    public static SkillTreeScene create(
            ResourceLocation skillId,
            SkillTreePresentationDefinition definition,
            SkillTreeState state,
            int nodeSize
    ) {
        if (skillId == null || definition == null || state == null) {
            return null;
        }
        List<SkillTreeNodeView> views = buildNodeViews(definition, state, nodeSize);
        LinkedHashMap<String, SkillTreeNodeView> byId = new LinkedHashMap<>();
        for (SkillTreeNodeView view : views) {
            byId.put(view.definition().id(), view);
        }
        return new SkillTreeScene(skillId, definition, state, views, Map.copyOf(byId), resolveRootNode(views));
    }

    public SkillTreeScene {
        nodeViews = nodeViews == null ? List.of() : List.copyOf(nodeViews);
        nodeById = nodeById == null ? Map.of() : Map.copyOf(nodeById);
    }

    public SkillTreeNodeView nodeById(String nodeId) {
        return nodeById.get(nodeId);
    }

    public boolean hasNodes() {
        return !nodeViews.isEmpty();
    }

    public Bounds bounds() {
        if (nodeViews.isEmpty()) {
            return Bounds.EMPTY;
        }
        int minX = nodeViews.stream().mapToInt(SkillTreeNodeView::x).min().orElse(0);
        int maxX = nodeViews.stream().mapToInt(SkillTreeNodeView::x).max().orElse(0);
        int minY = nodeViews.stream().mapToInt(SkillTreeNodeView::y).min().orElse(0);
        int maxY = nodeViews.stream().mapToInt(SkillTreeNodeView::y).max().orElse(0);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static List<SkillTreeNodeView> buildNodeViews(
            SkillTreePresentationDefinition definition,
            SkillTreeState state,
            int nodeSize
    ) {
        java.util.ArrayList<SkillTreeNodeView> views = new java.util.ArrayList<>();
        for (SkillTreeNodeDefinition node : definition.nodes().values()) {
            views.add(new SkillTreeNodeView(
                    node,
                    state.status(node.id()),
                    node.x(),
                    node.y(),
                    nodeSize,
                    state.isRendered(node.id()),
                    state.isRevealed(node.id()),
                    state.isObfuscated(node.id())
            ));
        }
        views.sort(Comparator.comparingInt(view -> view.status() == SkillNodeStatus.HIDDEN ? 1 : 0));
        return List.copyOf(views);
    }

    private static SkillTreeNodeView resolveRootNode(List<SkillTreeNodeView> views) {
        for (SkillTreeNodeView view : views) {
            if ("core".equalsIgnoreCase(view.definition().type())) {
                return view;
            }
        }
        for (SkillTreeNodeView view : views) {
            if (view.definition().requires().isEmpty()) {
                return view;
            }
        }
        return views.isEmpty() ? null : views.get(0);
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
    }
}
