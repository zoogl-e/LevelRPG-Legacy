package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Set;

public final class SkillUnlockQuery {
    private SkillUnlockQuery() {
    }

    public static boolean hasNode(ServerPlayer player, ResourceLocation skillId, String nodeId) {
        return player != null && hasNode(LevelProfile.get(player), skillId, nodeId);
    }

    public static boolean hasNode(LevelProfile profile, ResourceLocation skillId, String nodeId) {
        if (profile == null || skillId == null || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        Set<String> unlocked = profile.getUnlockedTreeNodes(skillId);
        String clean = nodeId.trim();
        return unlocked.contains(clean) || unlocked.contains(skillId.getPath() + "_" + clean);
    }

    public static Set<String> unlockedNodes(ServerPlayer player, ResourceLocation skillId) {
        return player == null ? Set.of() : unlockedNodes(LevelProfile.get(player), skillId);
    }

    public static Set<String> unlockedNodes(LevelProfile profile, ResourceLocation skillId) {
        return profile == null || skillId == null ? Set.of() : profile.getUnlockedTreeNodes(skillId);
    }
}
