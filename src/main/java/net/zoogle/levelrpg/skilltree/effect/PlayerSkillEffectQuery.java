package net.zoogle.levelrpg.skilltree.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition;
import net.zoogle.levelrpg.data.SkillTreeRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerSkillEffectQuery {
    private PlayerSkillEffectQuery() {
    }

    public static boolean hasNodeUnlocked(ServerPlayer player, ResourceLocation skillId, String nodeId) {
        return player != null && hasNodeUnlocked(LevelProfile.get(player), skillId, nodeId);
    }

    public static boolean hasNodeUnlocked(LevelProfile profile, ResourceLocation skillId, String nodeId) {
        return profile != null
                && skillId != null
                && nodeId != null
                && profile.getUnlockedTreeNodes(skillId).contains(nodeId);
    }

    public static boolean hasEffect(ServerPlayer player, ResourceLocation effectId) {
        return player != null && hasEffect(LevelProfile.get(player), effectId);
    }

    public static boolean hasEffect(LevelProfile profile, ResourceLocation effectId) {
        if (profile == null || effectId == null) {
            return false;
        }
        for (ActiveSkillNodeEffect active : activeEffects(profile)) {
            if (active.effect().id().equals(effectId)) {
                return true;
            }
        }
        return false;
    }

    public static List<ActiveSkillNodeEffect> activeEffects(ServerPlayer player) {
        return player == null ? List.of() : activeEffects(LevelProfile.get(player));
    }

    public static List<ActiveSkillNodeEffect> activeEffects(LevelProfile profile) {
        if (profile == null) {
            return List.of();
        }
        ArrayList<ActiveSkillNodeEffect> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, SkillTreeCanonicalDefinition> entry : SkillTreeRegistry.entries()) {
            result.addAll(activeEffects(profile, entry.getKey()));
        }
        return List.copyOf(result);
    }

    public static List<ActiveSkillNodeEffect> activeEffects(ServerPlayer player, ResourceLocation skillId) {
        return player == null ? List.of() : activeEffects(LevelProfile.get(player), skillId);
    }

    public static List<ActiveSkillNodeEffect> activeEffects(LevelProfile profile, ResourceLocation skillId) {
        if (profile == null || skillId == null) {
            return List.of();
        }
        SkillTreeCanonicalDefinition tree = SkillTreeRegistry.get(skillId);
        if (tree == null || tree.nodes() == null || tree.nodes().isEmpty()) {
            return List.of();
        }
        ArrayList<ActiveSkillNodeEffect> result = new ArrayList<>();
        for (String nodeId : profile.getUnlockedTreeNodes(skillId)) {
            SkillTreeCanonicalDefinition.Node node = tree.nodes().get(nodeId);
            if (node == null || node.effects().isEmpty()) {
                continue;
            }
            for (SkillNodeEffect effect : node.effects()) {
                result.add(new ActiveSkillNodeEffect(skillId, nodeId, effect));
            }
        }
        return List.copyOf(result);
    }

    public static List<ActiveSkillNodeEffect> activeEffectsOfType(ServerPlayer player, ResourceLocation type) {
        return player == null ? List.of() : activeEffectsOfType(LevelProfile.get(player), type);
    }

    public static List<ActiveSkillNodeEffect> activeEffectsOfType(LevelProfile profile, ResourceLocation type) {
        if (profile == null || type == null) {
            return List.of();
        }
        ArrayList<ActiveSkillNodeEffect> result = new ArrayList<>();
        for (ActiveSkillNodeEffect active : activeEffects(profile)) {
            if (active.effect().type().equals(type)) {
                result.add(active);
            }
        }
        return List.copyOf(result);
    }

    public record ActiveSkillNodeEffect(
            ResourceLocation skillId,
            String nodeId,
            SkillNodeEffect effect
    ) {
    }
}
