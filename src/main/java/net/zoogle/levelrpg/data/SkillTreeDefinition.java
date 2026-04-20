package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SkillTreeDefinition(
        ResourceLocation skill,
        int minSkillLevel,
        String title,
        String summary,
        List<Threshold> thresholds,
        LinkedHashMap<String, Node> nodes
) {
    public SkillTreeDefinition {
        title = title == null ? "" : title;
        summary = summary == null ? "" : summary;
        thresholds = thresholds == null ? List.of() : List.copyOf(thresholds);
        nodes = nodes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
    }

    public int masteryPointsForLevel(int skillLevel) {
        if (skillLevel < Math.max(0, minSkillLevel)) {
            return 0;
        }
        int total = 0;
        for (Threshold threshold : thresholds) {
            if (skillLevel >= Math.max(0, threshold.level())) {
                total += Math.max(0, threshold.points());
            }
        }
        return Math.max(0, total);
    }

    public Optional<Threshold> nextThreshold(int skillLevel) {
        for (Threshold threshold : thresholds) {
            if (skillLevel < Math.max(0, threshold.level())) {
                return Optional.of(threshold);
            }
        }
        return Optional.empty();
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

    public record Threshold(String id, int level, int points, String title, String description) {
        public Threshold {
            id = normalize(id);
            title = title == null ? "" : title;
            description = description == null ? "" : description;
        }
    }

    public record Node(
            String id,
            int cost,
            List<String> requires,
            int requiredSkillLevel,
            String branch,
            String title,
            String description
    ) {
        public Node {
            id = normalize(id);
            requires = requires == null ? List.of() : List.copyOf(requires);
            branch = branch == null ? "" : branch;
            title = title == null ? "" : title;
            description = description == null ? "" : description;
        }

        public int normalizedCost() {
            return Math.max(1, cost);
        }

        public int normalizedRequiredSkillLevel() {
            return Math.max(0, requiredSkillLevel);
        }
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
