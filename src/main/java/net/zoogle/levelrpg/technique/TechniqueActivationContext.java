package net.zoogle.levelrpg.technique;

import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

public record TechniqueActivationContext(ServerPlayer player, LevelProfile profile, TechniqueDefinition technique) {
}
