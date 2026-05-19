package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition;
import net.zoogle.levelrpg.data.SkillTreeRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Server-side: uses data.SkillTreeCanonicalDefinition (JSON-loaded). Client rendering uses skilltree.SkillTreePresentationDefinition via SkillTreeRegistry.
/**
 * Central evaluator for discipline tree unlock state so commands, journal guidance, and client presentation
 * share the same logic. Tree unlock currency is {@link SpecializationProgression} (design: <b>Insight</b>);
 * node availability vs invested Discipline Level uses the {@code investedSkillLevel} argument to
 * {@link #snapshot(ResourceLocation, SkillTreeCanonicalDefinition, int, Set, int, int)} (same backing as
 * {@link net.zoogle.levelrpg.profile.LevelProfile#investedDisciplineLevel}; not practice rank).
 */
public final class SkillTreeProgression {
    private SkillTreeProgression() {}

    public static TreeSnapshot snapshot(LevelProfile profile, ResourceLocation skillId) {
        SkillTreeCanonicalDefinition tree = SkillTreeRegistry.get(skillId);
        SkillState skillProgress = profile.getSkill(skillId);
        Set<String> inscribedNodes = profile.getUnlockedTreeNodes(skillId);
        int gainedInsight = profile.globalInsightEarned();
        int inscribedPoints = profile.globalInsightInscribed();
        return snapshot(skillId, tree, skillProgress.level, inscribedNodes, gainedInsight, inscribedPoints);
    }

    /**
     * Builds the same {@link TreeSnapshot} as {@link #snapshot(LevelProfile, ResourceLocation)} using explicit
     * specialization totals (for example from {@link net.zoogle.levelrpg.client.data.ClientProfileCache} on the client).
     */
    public static TreeSnapshot snapshot(
            ResourceLocation skillId,
            SkillTreeCanonicalDefinition tree,
            int investedSkillLevel,
            Set<String> unlockedNodesForSkill,
            int specializationEarnedGlobal,
            int specializationSpentGlobal
    ) {
        int insight = Math.max(0, specializationEarnedGlobal - specializationSpentGlobal);
        if (tree == null) {
            return new TreeSnapshot(
                    skillId,
                    null,
                    investedSkillLevel,
                    specializationEarnedGlobal,
                    specializationSpentGlobal,
                    insight,
                    unlockedNodesForSkill,
                    List.of(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        ArrayList<NodeSnapshot> nodeSnapshots = new ArrayList<>();
        for (String nodeId : tree.orderedNodeIds()) {
            SkillTreeCanonicalDefinition.Node node = tree.nodes().get(nodeId);
            if (node == null) {
                continue;
            }
            List<String> missingRequirements = tree.missingRequirements(nodeId, unlockedNodesForSkill);
            NodeStatus status = resolveStatus(
                    tree,
                    node,
                    investedSkillLevel,
                    insight,
                    unlockedNodesForSkill,
                    missingRequirements
            );
            nodeSnapshots.add(new NodeSnapshot(node, status, missingRequirements));
        }

        Optional<NodeSnapshot> suggestedAvailable = nodeSnapshots.stream()
                .filter(node -> node.status() == NodeStatus.AVAILABLE)
                .findFirst();
        Optional<NodeSnapshot> suggestedLocked = nodeSnapshots.stream()
                .filter(node -> node.status() != NodeStatus.INSCRIBED)
                .min(Comparator
                        .comparingInt((NodeSnapshot node) -> requiredLevelFor(tree, node.node()))
                        .thenComparing(node -> node.node().id()));

        return new TreeSnapshot(
                skillId,
                tree,
                investedSkillLevel,
                specializationEarnedGlobal,
                specializationSpentGlobal,
                insight,
                unlockedNodesForSkill,
                List.copyOf(nodeSnapshots),
                suggestedAvailable,
                suggestedAvailable.isPresent() ? suggestedAvailable : suggestedLocked
        );
    }

    private static NodeStatus resolveStatus(
            SkillTreeCanonicalDefinition tree,
            SkillTreeCanonicalDefinition.Node node,
            int investedDisciplineLevel,
            int insight,
            Set<String> inscribedNodes,
            List<String> missingRequirements
    ) {
        if (inscribedNodes.contains(node.id())) {
            return NodeStatus.INSCRIBED;
        }
        if (investedDisciplineLevel < requiredLevelFor(tree, node)) {
            return NodeStatus.LOCKED_SKILL_LEVEL;
        }
        if (!node.requirement().isSatisfied(inscribedNodes)) {
            return NodeStatus.LOCKED_PREREQUISITE;
        }
        if (insight < node.normalizedCost()) {
            return NodeStatus.LOCKED_INSIGHT;
        }
        return NodeStatus.AVAILABLE;
    }

    public static int requiredLevelFor(SkillTreeCanonicalDefinition tree, SkillTreeCanonicalDefinition.Node node) {
        return Math.max(Math.max(0, tree.minRank()), node.normalizedRequiredRank());
    }

    public enum NodeStatus {
        INSCRIBED,
        AVAILABLE,
        LOCKED_SKILL_LEVEL,
        LOCKED_PREREQUISITE,
        LOCKED_INSIGHT
    }

    public record NodeSnapshot(
            SkillTreeCanonicalDefinition.Node node,
            NodeStatus status,
            List<String> missingRequirements
    ) {
        public NodeSnapshot {
            missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        }
    }

    public record TreeSnapshot(
            ResourceLocation skillId,
            SkillTreeCanonicalDefinition tree,
            /**
             * Invested Discipline Level used for tree availability (not practice rank).
             */
            int rank,
            int gainedInsight,
            int inscribedPoints,
            int insight,
            Set<String> inscribedNodes,
            List<NodeSnapshot> nodes,
            Optional<NodeSnapshot> suggestedAvailableNode,
            Optional<NodeSnapshot> suggestedNextNode
    ) {
        public TreeSnapshot {
            inscribedNodes = inscribedNodes == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(inscribedNodes));
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            suggestedAvailableNode = suggestedAvailableNode == null ? Optional.empty() : suggestedAvailableNode;
            suggestedNextNode = suggestedNextNode == null ? Optional.empty() : suggestedNextNode;
        }
    }
}
