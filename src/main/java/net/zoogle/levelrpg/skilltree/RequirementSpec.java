package net.zoogle.levelrpg.skilltree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RequirementSpec(
        Mode mode,
        List<String> nodes,
        int count
) {
    public static final RequirementSpec EMPTY = new RequirementSpec(Mode.ALL, List.of(), 0);

    public RequirementSpec {
        mode = mode == null ? Mode.ALL : mode;
        nodes = cleanNodes(nodes);
        count = normalizedCount(mode, nodes, count);
    }

    public static RequirementSpec all(List<String> nodes) {
        return new RequirementSpec(Mode.ALL, nodes, 0);
    }

    public static RequirementSpec any(List<String> nodes) {
        return new RequirementSpec(Mode.ANY, nodes, 1);
    }

    public static RequirementSpec atLeast(List<String> nodes, int count) {
        return new RequirementSpec(Mode.AT_LEAST, nodes, count);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public boolean isSimpleAll() {
        return mode == Mode.ALL;
    }

    public String describeForDebug() {
        if (nodes.isEmpty()) {
            return "none";
        }
        String joined = String.join(", ", nodes);
        return switch (mode) {
            case ALL -> "all of: " + joined;
            case ANY -> "any of: " + joined;
            case AT_LEAST -> "at least " + count + " of: " + joined;
        };
    }

    public boolean isSatisfied(Set<String> unlockedNodeIds) {
        if (nodes.isEmpty()) {
            return true;
        }
        Set<String> unlocked = unlockedNodeIds == null ? Set.of() : unlockedNodeIds;
        int matches = 0;
        for (String node : nodes) {
            if (unlocked.contains(node)) {
                matches++;
            }
        }
        return switch (mode) {
            case ALL -> matches == nodes.size();
            case ANY -> matches >= 1;
            case AT_LEAST -> matches >= count;
        };
    }

    public RequirementSpec withoutNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || nodes.isEmpty()) {
            return this;
        }
        ArrayList<String> copy = new ArrayList<>(nodes);
        copy.removeIf(nodeId::equals);
        return new RequirementSpec(mode, copy, count);
    }

    public RequirementSpec renamedNode(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId) || nodes.isEmpty()) {
            return this;
        }
        ArrayList<String> copy = new ArrayList<>(nodes);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).equals(oldId)) {
                copy.set(i, newId);
            }
        }
        return new RequirementSpec(mode, copy, count);
    }

    public RequirementSpec toggledNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return this;
        }
        ArrayList<String> copy = new ArrayList<>(nodes);
        if (!copy.remove(nodeId)) {
            copy.add(nodeId);
        }
        return new RequirementSpec(mode, copy, count);
    }

    public RequirementSpec withMode(Mode mode, int count) {
        return new RequirementSpec(mode, nodes, count);
    }

    public enum Mode {
        ALL("all"),
        ANY("any"),
        AT_LEAST("at_least");

        private final String jsonName;

        Mode(String jsonName) {
            this.jsonName = jsonName;
        }

        public String jsonName() {
            return jsonName;
        }

        public static Mode fromJson(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase();
            for (Mode mode : values()) {
                if (mode.jsonName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return ALL;
        }
    }

    private static List<String> cleanNodes(List<String> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String node : nodes) {
            if (node != null && !node.isBlank()) {
                clean.add(node.trim());
            }
        }
        return List.copyOf(clean);
    }

    private static int normalizedCount(Mode mode, List<String> nodes, int count) {
        if (nodes.isEmpty()) {
            return 0;
        }
        if (mode == Mode.AT_LEAST) {
            return Math.max(1, Math.min(nodes.size(), count <= 0 ? 1 : count));
        }
        if (mode == Mode.ANY) {
            return 1;
        }
        return nodes.size();
    }
}
