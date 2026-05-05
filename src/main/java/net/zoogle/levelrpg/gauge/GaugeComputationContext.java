package net.zoogle.levelrpg.gauge;

import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

public record GaugeComputationContext(
        ServerPlayer player,
        LevelProfile profile,
        GaugeDefinition gauge
) {
}
