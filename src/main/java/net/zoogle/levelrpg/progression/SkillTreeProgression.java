package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
import net.zoogle.levelrpg.data.SkillTreeRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central evaluator for mastery tree state so commands, journal guidance, and
 * client presentation all read the same unlock logic.
 */
public final class SkillTreeProgression {
    private SkillTreeProgression() {}

    public static TreeSnapshot snapshot(LevelProfile profile, ResourceLocation skillId) {
        SkillTreeDefinition tree = SkillTreeRegistry.get(skillId);
        SkillState skillProgress = profile.getSkill(skillId);
        Set<String> unlockedNodes = profile.getUnlockedTreeNodes(skillId);
        int earnedPoints = SpecializationProgression.earnedPoints(profile);
        int spentPoints = SpecializationProgression.spentPoints(profile);
        return snapshot(skillId, tree, skillProgress.level, unlockedNodes, earnedPoints, spentPoints);
    }

    /**
     * Builds the same {@link TreeSnapshot} as {@link #snapshot(LevelProfile, ResourceLocation)} using explicit
     * specialization totals (for example from {@link net.zoogle.levelrpg.client.data.ClientProfileCache} on the client).
     */
    public static TreeSnapshot snapshot(
            ResourceLocation skillId,
            SkillTreeDefinition tree,
            int investedSkillLevel,
            Set<String> unlockedNodesForSkill,
            int specializationEarnedGlobal,
            int specializationSpentGlobal
    ) {
        int availablePoints = Math.max(0, specializationEarnedGlobal - specializationSpentGlobal);
        if (tree == null) {
            return new TreeSnapshot(
                    skillId,
                    null,
                    investedSkillLevel,
                    0,
                    specializationEarnedGlobal,
                    specializationSpentGlobal,
                    availablePoints,
                    unlockedNodesForSkill,
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        int unlockedTiers = unlockedTierCount(tree, investedSkillLevel);
        Optional<SkillTreeDefinition.Threshold> nextThreshold = tree.nextThreshold(investedSkillLevel);

        ArrayList<NodeSnapshot> nodeSnapshots = new ArrayList<>();
        for (String nodeId : tree.orderedNodeIds()) {
            SkillTreeDefinition.Node node = tree.nodes().get(nodeId);
            if (node == null) {
                continue;
            }
            List<String> missingRequirements = tree.missingRequirements(nodeId, unlockedNodesForSkill);
            NodeStatus status = resolveStatus(tree, node, investedSkillLevel, availablePoints, unlockedNodesForSkill, missingRequirements);
            nodeSnapshots.add(new NodeSnapshot(node, status, missingRequirements));
        }

        Optional<NodeSnapshot> suggestedAvailable = nodeSnapshots.stream()
                .filter(node -> node.status() == NodeStatus.AVAILABLE)
                .findFirst();
        Optional<NodeSnapshot> suggestedLocked = nodeSnapshots.stream()
                .filter(node -> node.status() != NodeStatus.UNLOCKED)
                .min(Comparator
                        .comparingInt((NodeSnapshot node) -> requiredLevelFor(tree, node.node()))
                        .thenComparing(node -> node.node().id()));

        return new TreeSnapshot(
                skillId,
                tree,
                investedSkillLevel,
                unlockedTiers,
                specializationEarnedGlobal,
                specializationSpentGlobal,
                availablePoints,
                unlockedNodesForSkill,
                nextThreshold,
                List.copyOf(nodeSnapshots),
                suggestedAvailable,
                suggestedAvailable.isPresent() ? suggestedAvailable : suggestedLocked
        );
    }

    private static NodeStatus resolveStatus(
            SkillTreeDefinition tree,
            SkillTreeDefinition.Node node,
            int skillLevel,
            int availablePoints,
            Set<String> unlockedNodes,
            List<String> missingRequirements
    ) {
        if (unlockedNodes.contains(node.id())) {
            return NodeStatus.UNLOCKED;
        }
        if (skillLevel < requiredLevelFor(tree, node)) {
            return NodeStatus.LOCKED_SKILL_LEVEL;
        }
        if (!missingRequirements.isEmpty()) {
            return NodeStatus.LOCKED_PREREQUISITE;
        }
        if (availablePoints < node.normalizedCost()) {
            return NodeStatus.LOCKED_MASTERY_POINTS;
        }
        return NodeStatus.AVAILABLE;
    }

    public static int requiredLevelFor(SkillTreeDefinition tree, SkillTreeDefinition.Node node) {
        return Math.max(Math.max(0, tree.minSkillLevel()), node.normalizedRequiredSkillLevel());
    }

    public enum NodeStatus {
        UNLOCKED,
        AVAILABLE,
        LOCKED_SKILL_LEVEL,
        LOCKED_PREREQUISITE,
        LOCKED_MASTERY_POINTS
    }

    public record NodeSnapshot(
            SkillTreeDefinition.Node node,
            NodeStatus status,
            List<String> missingRequirements
    ) {
        public NodeSnapshot {
            missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        }
    }

    public record TreeSnapshot(
            ResourceLocation skillId,
            SkillTreeDefinition tree,
            int skillLevel,
            int unlockedTiers,
            int earnedPoints,
            int spentPoints,
            int availablePoints,
            Set<String> unlockedNodes,
            Optional<SkillTreeDefinition.Threshold> nextThreshold,
            List<NodeSnapshot> nodes,
            Optional<NodeSnapshot> suggestedAvailableNode,
            Optional<NodeSnapshot> suggestedNextNode
    ) {
        public TreeSnapshot {
            unlockedNodes = unlockedNodes == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(unlockedNodes));
            nextThreshold = nextThreshold == null ? Optional.empty() : nextThreshold;
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            suggestedAvailableNode = suggestedAvailableNode == null ? Optional.empty() : suggestedAvailableNode;
            suggestedNextNode = suggestedNextNode == null ? Optional.empty() : suggestedNextNode;
        }
    }

    private static int unlockedTierCount(SkillTreeDefinition tree, int skillLevel) {
        if (tree == null) {
            return 0;
        }
        int unlocked = 0;
        for (SkillTreeDefinition.Threshold threshold : tree.thresholds()) {
            if (skillLevel >= Math.max(0, threshold.level())) {
                unlocked++;
            }
        }
        return unlocked;
    }
}
