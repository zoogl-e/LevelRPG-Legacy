package net.zoogle.levelrpg.gauge;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.net.payload.GaugeSyncPayload;
import net.zoogle.levelrpg.profile.LevelProfile;

public final class PlayerGauges {
    private PlayerGauges() {
    }

    public static boolean set(ServerPlayer player, ResourceLocation gaugeId, double value) {
        LevelProfile profile = LevelProfile.get(player);
        boolean changed = profile.gauges.setValue(player, profile, gaugeId, value);
        if (changed) {
            LevelProfile.save(player, profile);
            sync(player, profile, gaugeId);
        }
        return changed;
    }

    public static boolean add(ServerPlayer player, ResourceLocation gaugeId, double amount) {
        LevelProfile profile = LevelProfile.get(player);
        boolean changed = profile.gauges.add(player, profile, gaugeId, amount);
        if (changed) {
            LevelProfile.save(player, profile);
            sync(player, profile, gaugeId);
        }
        return changed;
    }

    public static boolean spend(ServerPlayer player, ResourceLocation gaugeId, double amount) {
        LevelProfile profile = LevelProfile.get(player);
        boolean changed = profile.gauges.spend(player, profile, gaugeId, amount);
        if (changed) {
            LevelProfile.save(player, profile);
            sync(player, profile, gaugeId);
        }
        return changed;
    }

    public static boolean canSpend(ServerPlayer player, ResourceLocation gaugeId, double amount) {
        LevelProfile profile = LevelProfile.get(player);
        return profile.gauges.canSpend(player, profile, gaugeId, amount);
    }

    public static void syncAll(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return;
        }
        for (GaugeDefinition definition : GaugeRegistry.entries()) {
            sync(player, profile, definition.id());
        }
    }

    public static void sync(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        if (player == null || profile == null || definition == null) {
            return;
        }
        profile.gauges.setValue(player, profile, gaugeId, profile.gauges.getValue(gaugeId));
        double value = profile.gauges.getValue(gaugeId);
        double max = profile.gauges.getMax(player, profile, gaugeId);
        player.connection.send(new ClientboundCustomPayloadPacket(GaugeSyncPayload.from(definition, value, max)));
    }
}
