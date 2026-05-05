package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SkillTreeStateResolver {
    private SkillTreeStateResolver() {
    }

    public static SkillTreeState resolve(
            ResourceLocation skillId,
            SkillTreePresentationDefinition tree,
            int rank,
            int insight,
            Set<String> unlockedNodeIds
    ) {
        LinkedHashMap<String, SkillNodeStatus> statuses = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> revealed = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> rendered = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> obfuscated = new LinkedHashMap<>();
        if (tree == null) {
            return new SkillTreeState(skillId, rank, insight, unlockedNodeIds, statuses, revealed, rendered, obfuscated);
        }

        for (SkillTreeNodeDefinition node : tree.nodes().values()) {
            boolean isRevealed = resolveRevealed(node, unlockedNodeIds);
            boolean isRendered = node.visibility() != NodeVisibilityMode.HIDDEN || isRevealed;
            boolean isObfuscated = node.visibility() == NodeVisibilityMode.OBFUSCATED && !isRevealed;
            revealed.put(node.id(), isRevealed);
            rendered.put(node.id(), isRendered);
            obfuscated.put(node.id(), isObfuscated);
            statuses.put(node.id(), isRendered ? resolveNode(node, rank, insight, unlockedNodeIds) : SkillNodeStatus.HIDDEN);
        }
        return new SkillTreeState(skillId, rank, insight, unlockedNodeIds, statuses, revealed, rendered, obfuscated);
    }

    private static SkillNodeStatus resolveNode(
            SkillTreeNodeDefinition node,
            int rank,
            int insight,
            Set<String> unlockedNodeIds
    ) {
        if (unlockedNodeIds.contains(node.id())) {
            return SkillNodeStatus.INSCRIBED;
        }
        if (rank < node.requiredRank()) {
            return SkillNodeStatus.LOCKED_LEVEL;
        }
        if (!node.requirement().isSatisfied(unlockedNodeIds)) {
            return SkillNodeStatus.LOCKED_PARENT;
        }
        if (insight < node.cost()) {
            return SkillNodeStatus.LOCKED_POINTS;
        }
        return SkillNodeStatus.AVAILABLE;
    }

    private static boolean resolveRevealed(SkillTreeNodeDefinition node, Set<String> unlockedNodeIds) {
        if (node.visibility() == NodeVisibilityMode.VISIBLE) {
            return true;
        }
        RequirementSpec revealRequirement = node.revealRequirement();
        if (revealRequirement == null || revealRequirement.isEmpty()) {
            return node.requirement().isEmpty() || node.requirement().isSatisfied(unlockedNodeIds);
        }
        return revealRequirement.isSatisfied(unlockedNodeIds);
    }
}
