package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.data.SkillTreeGraphLayout;
import net.zoogle.levelrpg.data.SkillTreeNodeVisibility;

import java.util.*;

/**
 * <b>Presentation Adapter Registry.</b>
 *
 * <p>Acts as a bridge and cache layer that adapts canonical {@link net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition}
 * objects into {@link SkillTreePresentationDefinition} runtime models. It is responsible for
 * resolving graph layouts and coordinate data consumed by the {@link net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen}
 * and the Enchiridion 3D projection bridge.
 *
 * @see net.zoogle.levelrpg.data.SkillTreeRegistry
 */
public final class SkillTreeRegistry {
    private static final LinkedHashMap<ResourceLocation, SkillTreePresentationDefinition> ADAPTED_CACHE = new LinkedHashMap<>();

    private SkillTreeRegistry() {
    }

    public static SkillTreePresentationDefinition get(ResourceLocation skillId) {
        return adaptLegacyTree(skillId);
    }

    public static Collection<Map.Entry<ResourceLocation, SkillTreePresentationDefinition>> entries() {
        List<Map.Entry<ResourceLocation, SkillTreePresentationDefinition>> result = new ArrayList<>();
        for (ResourceLocation id : net.zoogle.levelrpg.data.SkillTreeRegistry.ids()) {
            SkillTreePresentationDefinition def = adaptLegacyTree(id);
            if (def != null) result.add(Map.entry(id, def));
        }
        return result;
    }

    public static void invalidateAdaptedCache() {
        ADAPTED_CACHE.clear();
    }




    private static SkillTreePresentationDefinition adaptLegacyTree(ResourceLocation skillId) {
        if (ADAPTED_CACHE.containsKey(skillId)) {
            return ADAPTED_CACHE.get(skillId);
        }
        net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition legacy = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skillId);
        if (legacy == null) {
            ADAPTED_CACHE.put(skillId, null);
            return null;
        }
        Map<String, int[]> positions = SkillTreeGraphLayout.resolve(legacy);
        SkillTreePresentationDefinition.Builder builder = SkillTreePresentationDefinition.builder(skillId, legacy.title(), legacy.summary());
        for (net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition.Node node : legacy.nodes().values()) {
            int[] position = positions.getOrDefault(node.id(), new int[]{0, 0});
            boolean hidden = node.visibility() == SkillTreeNodeVisibility.HIDDEN;
            NodeVisibilityMode visibility = switch (node.visibility()) {
                case HIDDEN -> NodeVisibilityMode.HIDDEN;
                case OBFUSCATED -> NodeVisibilityMode.OBFUSCATED;
                default -> NodeVisibilityMode.VISIBLE;
            };
            List<String> parents = node.requires();
            if (hidden) {
                builder.hiddenNodeWithMetadata(
                        node.id(),
                        node.title(),
                        node.description(),
                        position[0],
                        position[1],
                        node.normalizedRequiredRank(),
                        node.normalizedCost(),
                        "trait",
                        node.iconKey(),
                        parents.toArray(String[]::new)
                );
            } else {
                builder.rawNode(new SkillTreeNodeDefinition(
                        node.id(),
                        node.title(),
                        node.description(),
                        position[0],
                        position[1],
                        node.normalizedRequiredRank(),
                        node.normalizedCost(),
                        RequirementSpec.all(parents),
                        null,
                        visibility,
                        "trait",
                        node.iconKey(),
                        node.effects(),
                        false
                ));
            }
        }
        SkillTreePresentationDefinition adapted = builder.build();
        ADAPTED_CACHE.put(skillId, adapted);
        return adapted;
    }
}
