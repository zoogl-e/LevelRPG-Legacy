package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

public record SkillTreeState(
        ResourceLocation skillId,
        /**
         * Invested Discipline Level used for tree gates (not practice rank).
         */
        int rank,
        int insight,
        Set<String> unlockedNodeIds,
        Map<String, SkillNodeStatus> statuses,
        Map<String, Boolean> revealed,
        Map<String, Boolean> rendered,
        Map<String, Boolean> obfuscated
) {
    public SkillTreeState {
        unlockedNodeIds = unlockedNodeIds == null ? Set.of() : Set.copyOf(unlockedNodeIds);
        statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
        revealed = revealed == null ? Map.of() : Map.copyOf(revealed);
        rendered = rendered == null ? Map.of() : Map.copyOf(rendered);
        obfuscated = obfuscated == null ? Map.of() : Map.copyOf(obfuscated);
    }

    public SkillNodeStatus status(String nodeId) {
        return statuses.getOrDefault(nodeId, SkillNodeStatus.HIDDEN);
    }

    public boolean isRevealed(String nodeId) {
        return revealed.getOrDefault(nodeId, false);
    }

    public boolean isRendered(String nodeId) {
        return rendered.getOrDefault(nodeId, false);
    }

    public boolean isObfuscated(String nodeId) {
        return obfuscated.getOrDefault(nodeId, false);
    }
}
