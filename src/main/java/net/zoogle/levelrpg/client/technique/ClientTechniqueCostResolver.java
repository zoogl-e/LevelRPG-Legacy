package net.zoogle.levelrpg.client.technique;

import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.skilltree.DelvingNodeIds;
import net.zoogle.levelrpg.technique.TechniqueDefinition;

import java.util.Set;

/**
 * Mirrors client-visible technique cost modifiers so HUD/tooltips match spend checks.
 */
public final class ClientTechniqueCostResolver {
    private ClientTechniqueCostResolver() {
    }

    public static double effectiveCost(TechniqueDefinition technique) {
        if (technique == null || !technique.cost().hasGaugeCost()) {
            return 0.0;
        }
        double cost = technique.cost().amount();
        if (GaugeRegistry.MOMENTUM.equals(technique.cost().gaugeId())) {
            if (hasDelvingNode(DelvingNodeIds.MOMENTUM_CORE)) {
                cost *= 2.0;
            }
            if (hasDelvingNode(DelvingNodeIds.EARTHBREAKER)) {
                cost *= 0.8;
            }
        }
        return cost;
    }

    private static boolean hasDelvingNode(String nodeId) {
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(DelvingNodeIds.SKILL);
        if (unlocked.contains(nodeId)) {
            return true;
        }
        return unlocked.contains(DelvingNodeIds.SKILL.getPath() + "_" + nodeId);
    }
}
