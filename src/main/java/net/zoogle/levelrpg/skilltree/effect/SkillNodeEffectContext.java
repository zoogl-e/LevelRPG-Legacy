package net.zoogle.levelrpg.skilltree.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

public record SkillNodeEffectContext(
        ServerPlayer player,
        LevelProfile profile,
        ResourceLocation skillId,
        String nodeId,
        SkillNodeEffect effect
) {
}
