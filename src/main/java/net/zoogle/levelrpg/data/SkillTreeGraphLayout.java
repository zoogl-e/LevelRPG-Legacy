package net.zoogle.levelrpg.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves graph coordinates for skill tree nodes. Explicit {@link SkillTreeDefinition.Node#layoutX()}
 * / {@code layoutY} win; otherwise a layered layout is derived from prerequisite edges.
 */
public final class SkillTreeGraphLayout {
    /** Sentinel: use automatic layout for this axis (both axes must be auto to trigger). */
    public static final int AUTO = Integer.MIN_VALUE;
    public static final int NODE_SLOT = 32;
    public static final int LAYER_SPACING_X = 140;
    public static final int ROW_SPACING_Y = 88;

    private SkillTreeGraphLayout() {}

    /**
     * @return map node id → pixel position in graph space (top-left of the node slot)
     */
    public static Map<String, int[]> resolve(SkillTreeDefinition tree) {
        Map<String, int[]> placed = new LinkedHashMap<>();
        if (tree == null || tree.nodes().isEmpty()) {
            return placed;
        }
        List<String> ids = tree.orderedNodeIds();
        List<String> needAuto = new ArrayList<>();
        for (String id : ids) {
            SkillTreeDefinition.Node node = tree.nodes().get(id);
            if (node == null) {
                continue;
            }
            if (node.layoutX() != AUTO && node.layoutY() != AUTO) {
                placed.put(id, new int[]{node.layoutX(), node.layoutY()});
            } else {
                needAuto.add(id);
            }
        }
        if (needAuto.isEmpty()) {
            return placed;
        }

        Map<String, Integer> layer = new HashMap<>();
        for (String id : needAuto) {
            layer.put(id, 0);
        }
        for (int pass = 0; pass < ids.size() + 2; pass++) {
            boolean changed = false;
            for (String id : needAuto) {
                SkillTreeDefinition.Node node = tree.nodes().get(id);
                if (node == null) {
                    continue;
                }
                int depth = 0;
                for (String req : node.requires()) {
                    int parentLayer = 0;
                    if (placed.containsKey(req)) {
                        parentLayer = Math.max(0, placed.get(req)[0]) / LAYER_SPACING_X;
                    } else {
                        parentLayer = layer.getOrDefault(req, 0);
                    }
                    depth = Math.max(depth, parentLayer + 1);
                }
                int old = layer.getOrDefault(id, 0);
                if (depth != old) {
                    layer.put(id, depth);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Map<Integer, List<String>> byLayer = new HashMap<>();
        for (String id : needAuto) {
            byLayer.computeIfAbsent(layer.getOrDefault(id, 0), k -> new ArrayList<>()).add(id);
        }
        for (List<String> row : byLayer.values()) {
            row.sort(Comparator
                    .comparing((String id) -> Objects.toString(tree.nodes().get(id).branch(), ""))
                    .thenComparing(id -> id));
        }

        for (Map.Entry<Integer, List<String>> e : byLayer.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            int lx = e.getKey() * LAYER_SPACING_X;
            int y = 0;
            for (String id : e.getValue()) {
                placed.put(id, new int[]{lx, y});
                y += ROW_SPACING_Y;
            }
        }
        return placed;
    }

    public static int[] boundsOf(Map<String, int[]> positions) {
        int minX = 0;
        int minY = 0;
        int maxX = NODE_SLOT;
        int maxY = NODE_SLOT;
        for (int[] p : positions.values()) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0] + NODE_SLOT);
            maxY = Math.max(maxY, p[1] + NODE_SLOT);
        }
        return new int[]{minX, minY, maxX, maxY};
    }
}
