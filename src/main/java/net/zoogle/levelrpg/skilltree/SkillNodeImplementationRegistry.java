package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.skilltree.effect.SkillNodeEffect;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SkillNodeImplementationRegistry {
    private static final LinkedHashSet<NodeKey> IMPLEMENTED_NODES = new LinkedHashSet<>();
    private static final LinkedHashSet<ResourceLocation> IMPLEMENTED_EFFECTS = new LinkedHashSet<>();

    static {
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.RESOLVE);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.MELEE_COMBATANT);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.COMBO_BREAK);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.SEASONED_FIGHTER);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.VANGUARD);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.DEFLECTION);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.SHIELD_BASH);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.INPENETRABLE);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.IMMOVABLE_OBJECT);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.BATTERING_RAM);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.PROTECTION_AURA);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.BULWARK_OF_HOPE);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.FAR_REACH);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.MONSTER_HUNTER);
        registerGameplayEffect(ValorNodeIds.SKILL, ValorNodeIds.MORTAL_WOUND);
        registerGaugeModifier(ValorNodeIds.SKILL, ValorNodeIds.STEELED);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.OVERDRIVE);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.STONE_RESERVOIR);
        registerGaugeModifier(DelvingNodeIds.SKILL, DelvingNodeIds.MOMENTUM_RESERVOIR);
        registerGaugeModifier(DelvingNodeIds.SKILL, DelvingNodeIds.DEEP_DELVER);
        registerGaugeModifier(DelvingNodeIds.SKILL, DelvingNodeIds.MOMENTUM_CORE);
        registerImplementedNode(DelvingNodeIds.SKILL, DelvingNodeIds.TIDE_DASH);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.PROSPECTOR);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.WATER_ADAPTATION);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.DEEPCUTTER);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.CURRENT_CONTROL);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.AQUA_MINING);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.AQUA_MOBILITY);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.NEUTRAL_BUOYANCY);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.ABYSSAL_FORM);
        registerImplementedNode(DelvingNodeIds.SKILL, DelvingNodeIds.ANCHOR_DROP);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.ABYSS_DWELLER);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.ENDURING_LABOR);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.VEIN_MINER);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.REINFORCED_LUNGS);
        registerImplementedNode(DelvingNodeIds.SKILL, DelvingNodeIds.HAMMER_STRIKE);
        registerGaugeModifier(DelvingNodeIds.SKILL, DelvingNodeIds.EARTHBREAKER);
        registerGameplayEffect(DelvingNodeIds.SKILL, DelvingNodeIds.DEEP_SIGHT);
        registerGaugeModifier(FinesseNodeIds.SKILL, FinesseNodeIds.RHYTHM);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.PRECISION_SHOT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.HAND_TO_HAND_COMBAT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.FLURRY_OF_BLOWS);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.HANDS_UP);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.VERSATILE_BRAWLER);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.LUCKY_SHOT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.QUICK_DRAW);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.VERSATILE_SHOT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.DESPERATE_MEASURE);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.SMOOTH_MOVES);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.ASSASSIN);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.GHOST_STEP);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.RAPID_VOLLEY);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.LIKE_THE_WIND);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.CALCULATED_SHOT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.BLURRED_IMAGE);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.DAVID_AND_GOLIATH);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.UPPERCUT);
        registerGameplayEffect(FinesseNodeIds.SKILL, FinesseNodeIds.PIERCING_SHOT);
        registerImplementedNode(FinesseNodeIds.SKILL, FinesseNodeIds.BLOT_OUT_THE_SUN);
        // TODO: treasure_instinct needs a loot-table-backed generated-container hook that cannot duplicate chest contents.
    }

    private SkillNodeImplementationRegistry() {
    }

    public static void registerImplementedNode(ResourceLocation skillId, String nodeId) {
        if (skillId != null && nodeId != null && !nodeId.isBlank()) {
            IMPLEMENTED_NODES.add(new NodeKey(skillId, normalizeNodeId(skillId, nodeId)));
        }
    }

    public static void registerImplementedEffect(ResourceLocation effectId) {
        if (effectId != null) {
            IMPLEMENTED_EFFECTS.add(effectId);
        }
    }

    public static void registerGameplayEffect(ResourceLocation skillId, String nodeId) {
        registerImplementedNode(skillId, nodeId);
    }

    public static void registerGaugeModifier(ResourceLocation skillId, String nodeId) {
        registerImplementedNode(skillId, nodeId);
    }

    public static boolean isImplemented(ResourceLocation skillId, SkillTreeNodeDefinition node) {
        return node != null && (isImplemented(skillId, node.id()) || hasImplementedEffect(node));
    }

    public static boolean isImplemented(ResourceLocation skillId, String nodeId) {
        return hasExplicitImplementation(skillId, nodeId) || hasRegisteredTechnique(skillId, nodeId);
    }

    public static boolean hasImplementedEffect(ResourceLocation skillId, String nodeId) {
        return hasExplicitImplementation(skillId, nodeId);
    }

    public static boolean hasImplementedEffect(SkillTreeNodeDefinition node) {
        if (node == null || node.effects().isEmpty()) {
            return false;
        }
        for (SkillNodeEffect effect : node.effects()) {
            if (IMPLEMENTED_EFFECTS.contains(effect.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitImplementation(ResourceLocation skillId, String nodeId) {
        if (skillId == null || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return IMPLEMENTED_NODES.contains(new NodeKey(skillId, normalizeNodeId(skillId, nodeId)));
    }

    private static boolean hasRegisteredTechnique(ResourceLocation skillId, String nodeId) {
        if (skillId == null || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        for (TechniqueDefinition technique : TechniqueRegistry.entries()) {
            if (skillId.equals(technique.requiredSkillId())
                    && nodeIdsMatch(skillId, nodeId, technique.requiredNodeId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean nodeIdsMatch(ResourceLocation skillId, String nodeId, String implementedNodeId) {
        return normalizeNodeId(skillId, nodeId).equals(normalizeNodeId(skillId, implementedNodeId));
    }

    private static String normalizeNodeId(ResourceLocation skillId, String nodeId) {
        String clean = nodeId == null ? "" : nodeId.trim();
        if (skillId != null) {
            String prefix = skillId.getPath() + "_";
            if (clean.startsWith(prefix)) {
                return clean.substring(prefix.length());
            }
        }
        return clean;
    }

    public static Set<String> implementedNodeIds(ResourceLocation skillId) {
        if (skillId == null) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (NodeKey key : IMPLEMENTED_NODES) {
            if (skillId.equals(key.skillId())) {
                result.add(key.nodeId());
            }
        }
        for (TechniqueDefinition technique : TechniqueRegistry.entries()) {
            if (skillId.equals(technique.requiredSkillId()) && !technique.requiredNodeId().isBlank()) {
                result.add(normalizeNodeId(skillId, technique.requiredNodeId()));
            }
        }
        return Set.copyOf(result);
    }

    private record NodeKey(ResourceLocation skillId, String nodeId) {
    }
}
