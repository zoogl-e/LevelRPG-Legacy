package net.zoogle.levelrpg.net;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.net.payload.SpendSkillPointRequestPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelDeltaPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelProfilePayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.SkillPointProgression;

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
                                payload.toMap(),
                                payload.toTreeSpentMap(),
                                payload.toTreeUnlockedMap(),
                                payload.availableSkillPoints(),
                                payload.spentSkillPoints()
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
                                payload.masteryLevel(),
                                payload.masteryXp(),
                                payload.availableSkillPoints(),
                                payload.spentSkillPoints()
                        );
                    });
                }
        );
        registrar.playToServer(
                SpendSkillPointRequestPayload.TYPE,
                SpendSkillPointRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    LevelProfile profile = LevelProfile.get(player);
                    SkillPointProgression.SkillPointSpendResult result = profile.spendSkillPoint(payload.skillId());
                    if (!result.success()) {
                        player.displayClientMessage(Component.literal(result.message()), true);
                        return;
                    }
                    PassiveSkillScalingService.applyIfChanged(player, profile);
                    LevelProfile.save(player, profile);
                    sendDelta(player, profile, payload.skillId());
                    player.displayClientMessage(Component.literal(
                            "Invested 1 Skill Point into "
                                    + displayNameForSkill(payload.skillId())
                                    + ". Skill Level is now "
                                    + result.resultingSkillLevel()
                                    + "."
                    ), true);
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
        SkillState sp = profile.skills.get(skillId);
        if (sp == null) return;
        var payload = new SyncLevelDeltaPayload(
                skillId,
                sp.level,
                sp.masteryLevel,
                sp.masteryXp,
                profile.availableSkillPoints,
                profile.spentSkillPoints
        );
        target.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
