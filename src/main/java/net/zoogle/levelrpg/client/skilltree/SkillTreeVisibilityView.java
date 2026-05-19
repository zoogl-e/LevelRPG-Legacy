package net.zoogle.levelrpg.client.skilltree;

import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeEdge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client-side fog-of-war view over an already resolved skill tree scene.
 *
 * <p>This is presentation-only. It does not change player progression, JSON data,
 * server unlock rules, or the resolved node status map.
 */
public final class SkillTreeVisibilityView {
    private static final Map<SkillTreeScene, FogHints> FOG_HINTS = new WeakHashMap<>();

    private SkillTreeVisibilityView() {
    }

    public static SkillTreeScene localReveal(SkillTreeScene scene) {
        if (scene == null || scene.state() == null || scene.definition() == null || !scene.hasNodes()) {
            return scene;
        }
        Set<String> visibleNodeIds = visibleNodeIds(scene);
        if (visibleNodeIds.isEmpty()) {
            return scene;
        }

        java.util.ArrayList<SkillTreeNodeView> visibleNodes = new java.util.ArrayList<>();
        LinkedHashMap<String, SkillTreeNodeView> visibleById = new LinkedHashMap<>();
        LinkedHashMap<String, SkillTreeNodeView> allById = new LinkedHashMap<>();
        for (SkillTreeNodeView view : scene.nodeViews()) {
            allById.put(view.definition().id(), view);
        }
        for (SkillTreeNodeView view : scene.nodeViews()) {
            String nodeId = view.definition().id();
            if (!visibleNodeIds.contains(nodeId) || !view.rendered() || view.status() == SkillNodeStatus.HIDDEN) {
                continue;
            }
            visibleNodes.add(view);
            visibleById.put(nodeId, view);
        }
        if (visibleNodes.isEmpty() && scene.rootNode() != null) {
            SkillTreeNodeView root = scene.rootNode();
            visibleNodes.add(root);
            visibleById.put(root.definition().id(), root);
        }
        SkillTreeNodeView root = scene.rootNode() == null ? null : visibleById.get(scene.rootNode().definition().id());
        SkillTreeScene visibleScene = new SkillTreeScene(
                scene.skillId(),
                scene.definition(),
                scene.state(),
                List.copyOf(visibleNodes),
                Map.copyOf(visibleById),
                root
        );
        FOG_HINTS.put(visibleScene, fogHints(scene, visibleNodeIds, allById));
        return visibleScene;
    }

    public static FogHints fogHints(SkillTreeScene scene) {
        return FOG_HINTS.getOrDefault(scene, FogHints.EMPTY);
    }

    private static Set<String> visibleNodeIds(SkillTreeScene scene) {
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        SkillTreeNodeView root = scene.rootNode();
        if (root != null) {
            visible.add(root.definition().id());
        }

        Set<String> unlocked = scene.state().unlockedNodeIds();
        if (unlocked.isEmpty()) {
            return visible;
        }

        visible.addAll(unlocked);
        for (SkillTreeEdge edge : scene.definition().edges()) {
            boolean parentUnlocked = unlocked.contains(edge.parentId());
            boolean childUnlocked = unlocked.contains(edge.childId());
            if (parentUnlocked) {
                visible.add(edge.childId());
            }
            if (childUnlocked) {
                visible.add(edge.parentId());
            }
        }
        return visible;
    }

    private static FogHints fogHints(SkillTreeScene scene, Set<String> visibleNodeIds, Map<String, SkillTreeNodeView> allById) {
        java.util.ArrayList<SkillTreeNodeView> silhouettes = new java.util.ArrayList<>();
        LinkedHashSet<SkillTreeEdge> hintedEdges = new LinkedHashSet<>();
        LinkedHashSet<String> hintedNodeIds = new LinkedHashSet<>();

        for (SkillTreeEdge edge : scene.definition().edges()) {
            boolean parentVisible = visibleNodeIds.contains(edge.parentId());
            boolean childVisible = visibleNodeIds.contains(edge.childId());
            if (parentVisible == childVisible) {
                continue;
            }
            String hiddenId = parentVisible ? edge.childId() : edge.parentId();
            SkillTreeNodeView hidden = allById.get(hiddenId);
            if (hidden == null || !hidden.rendered() || hidden.status() == SkillNodeStatus.HIDDEN || hintedNodeIds.contains(hiddenId)) {
                continue;
            }
            hintedNodeIds.add(hiddenId);
            silhouettes.add(hidden);
            hintedEdges.add(edge);
        }
        return new FogHints(List.copyOf(silhouettes), Set.copyOf(hintedEdges));
    }

    public record FogHints(List<SkillTreeNodeView> silhouettes, Set<SkillTreeEdge> edges) {
        private static final FogHints EMPTY = new FogHints(List.of(), Set.of());

        public boolean isEmpty() {
            return silhouettes.isEmpty() && edges.isEmpty();
        }
    }
}
