package net.zoogle.levelrpg.net;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.net.payload.SyncLevelDeltaPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelProfilePayload;
import net.zoogle.levelrpg.profile.LevelProfile;

public class Network {
    public static void init() {
        // no-op for now
    }

    // Register payload codecs and handlers
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                SyncLevelProfilePayload.TYPE,
                SyncLevelProfilePayload.STREAM_CODEC,
                (payload, context) -> {
                    // Apply on client thread
                    context.enqueueWork(() -> {
                        ClientProfileCache.set(
                                payload.playerLevel(),
                                payload.unspentSkillPoints(),
                                payload.toMap()
                        );
                    });
                }
        );
        registrar.playToClient(
                SyncLevelDeltaPayload.TYPE,
                SyncLevelDeltaPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ClientProfileCache.applyDelta(
                                payload.skillId(),
                                payload.level(),
                                payload.xp(),
                                payload.playerLevel(),
                                payload.unspentSkillPoints()
                        );
                    });
                }
        );
    }

    // Send a full profile snapshot to a specific player
    public static void sendSync(ServerPlayer target, LevelProfile profile) {
        if (target == null) return;
        var payload = SyncLevelProfilePayload.from(profile);
        target.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    // Send a delta (single skill + header values)
    public static void sendDelta(ServerPlayer target, LevelProfile profile, ResourceLocation skillId) {
        if (target == null || profile == null || skillId == null) return;
        LevelProfile.SkillProgress sp = profile.skills.get(skillId);
        if (sp == null) return;
        var payload = new SyncLevelDeltaPayload(skillId, sp.level, sp.xp, profile.playerLevel, profile.unspentSkillPoints);
        target.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
