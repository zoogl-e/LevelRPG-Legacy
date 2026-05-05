package net.zoogle.levelrpg.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.zoogle.levelrpg.client.CameraShakeController;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.client.finesse.FinesseFirstPersonUnarmedRenderer;
import net.zoogle.levelrpg.client.finesse.FinesseThirdPersonGuardState;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.net.payload.BindArchetypeRequestPayload;
import net.zoogle.levelrpg.net.payload.BindArchetypeResultPayload;
import net.zoogle.levelrpg.net.payload.ActivateTechniqueSlotPayload;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;
import net.zoogle.levelrpg.net.payload.CrescentSlashCameraSpinPayload;
import net.zoogle.levelrpg.net.payload.GaugeSyncPayload;
import net.zoogle.levelrpg.net.payload.MarkFistCombatIntentPayload;
import net.zoogle.levelrpg.net.payload.MarkFistWeakSpotIntentPayload;
import net.zoogle.levelrpg.net.payload.OpenSkillTreeEditorScreenPayload;
import net.zoogle.levelrpg.net.payload.RecklessChargeStatePayload;
import net.zoogle.levelrpg.net.payload.SelectTechniqueSlotPayload;
import net.zoogle.levelrpg.net.payload.SetHandsUpGuardingPayload;
import net.zoogle.levelrpg.net.payload.RequestForwardFrenzyPayload;
import net.zoogle.levelrpg.net.payload.RequestFistPunchPayload;
import net.zoogle.levelrpg.net.payload.RequestUppercutPayload;
import net.zoogle.levelrpg.net.payload.RequestGhostStepPayload;
import net.zoogle.levelrpg.net.payload.RequestProfileSyncPayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeReleasePayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeStartPayload;
import net.zoogle.levelrpg.net.payload.RequestShieldBashPayload;
import net.zoogle.levelrpg.net.payload.SpendSkillPointRequestPayload;
import net.zoogle.levelrpg.net.payload.TechniqueLoadoutSyncPayload;
import net.zoogle.levelrpg.net.payload.UnlockTreeNodeRequestPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelDeltaPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelProfilePayload;
import net.zoogle.levelrpg.net.payload.SyncFinesseGuardVisualPayload;
import net.zoogle.levelrpg.net.payload.SyncFinesseWeakSpotPayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.net.payload.TriggerFistCombatStancePayload;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.finesse.FinesseGuardState;
import net.zoogle.levelrpg.finesse.FinessePassiveModifiers;
import net.zoogle.levelrpg.finesse.FinesseTechniqueActivations;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.SkillPointProgression;
import net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen;
import net.zoogle.levelrpg.client.valor.ClientCrescentSlashCamera;
import net.zoogle.levelrpg.client.valor.ClientRecklessChargeState;
import net.zoogle.levelrpg.events.ValorTechniqueEvents;
import net.zoogle.levelrpg.technique.PlayerTechniques;

public class Network {
    public static void init() {
        // no-op for now
    }

    // Register payload codecs and handlers
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                CameraShakePayload.TYPE,
                CameraShakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CameraShakeController.apply(payload))
        );
        registrar.playToClient(
                GaugeSyncPayload.TYPE,
                GaugeSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientGaugeCache.apply(payload))
        );
        registrar.playToClient(
                TechniqueLoadoutSyncPayload.TYPE,
                TechniqueLoadoutSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> net.zoogle.levelrpg.client.technique.ClientTechniqueCache.apply(payload))
        );
        registrar.playToClient(
                BindArchetypeResultPayload.TYPE,
                BindArchetypeResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientProfileCache.recordBindResult(payload.success(), payload.message(), payload.archetypeId()))
        );
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
                                payload.spentSkillPoints(),
                                payload.bonusSpecializationPoints(),
                                payload.archetypeId(),
                                payload.archetypeApplied()
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
        registrar.playToClient(
                OpenSkillTreeEditorScreenPayload.TYPE,
                OpenSkillTreeEditorScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null || minecraft.screen != null) {
                        return;
                    }
                    if (net.zoogle.levelrpg.skilltree.SkillTreeRegistry.get(payload.skillId()) == null) {
                        minecraft.player.displayClientMessage(Component.literal("No tree found for " + payload.skillId()), true);
                        return;
                    }
                    minecraft.setScreen(new SkillTreeEditorScreen(payload.skillId()));
                })
        );
        registrar.playToClient(
                CrescentSlashCameraSpinPayload.TYPE,
                CrescentSlashCameraSpinPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCrescentSlashCamera.startSpin(payload.durationTicks()))
        );
        registrar.playToClient(
                RecklessChargeStatePayload.TYPE,
                RecklessChargeStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientRecklessChargeState.apply(payload))
        );
        registrar.playToClient(
                TriggerFistCombatStancePayload.TYPE,
                TriggerFistCombatStancePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(minecraft.player.tickCount);
                    }
                })
        );
        registrar.playToClient(
                SyncFinesseGuardVisualPayload.TYPE,
                SyncFinesseGuardVisualPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FinesseThirdPersonGuardState.apply(payload))
        );
        registrar.playToClient(
                SyncFinesseWeakSpotPayload.TYPE,
                SyncFinesseWeakSpotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FinesseUnarmedCombat.applyWeakSpotSync(payload))
        );
        registrar.playToServer(
                RequestProfileSyncPayload.TYPE,
                RequestProfileSyncPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    LevelProfile profile = LevelProfile.get(player);
                    LevelProfile.save(player, profile);
                    sendSync(player, profile);
                }
        );
        registrar.playToServer(
                BindArchetypeRequestPayload.TYPE,
                BindArchetypeRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    LevelProfile profile = LevelProfile.get(player);
                    LevelProfile.ArchetypeApplyResult result = profile.selectAndApplyArchetype(payload.archetypeId());
                    if (!result.success) {
                        player.connection.send(new ClientboundCustomPayloadPacket(
                                BindArchetypeResultPayload.failure(payload.archetypeId(), result.message)
                        ));
                        player.displayClientMessage(Component.literal(result.message), true);
                        return;
                    }
                    PassiveSkillScalingService.applyIfChanged(player, profile);
                    LevelProfile.save(player, profile);
                    sendSync(player, profile);
                    player.connection.send(new ClientboundCustomPayloadPacket(
                            BindArchetypeResultPayload.success(payload.archetypeId(), "Archetype bound.")
                    ));
                    player.displayClientMessage(Component.literal("Archetype bound: " + payload.archetypeId().getPath()), true);
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
        registrar.playToServer(
                UnlockTreeNodeRequestPayload.TYPE,
                UnlockTreeNodeRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    if (payload.skillId() == null || !ProgressionSkill.isCanonicalId(payload.skillId())) {
                        return;
                    }
                    String nodeId = payload.nodeId();
                    if (nodeId == null || nodeId.isBlank()) {
                        return;
                    }
                    LevelProfile profile = LevelProfile.get(player);
                    LevelProfile.UnlockResult res = profile.unlockNode(payload.skillId(), nodeId.trim());
                    if (!res.success) {
                        player.displayClientMessage(Component.literal(res.message), true);
                        return;
                    }
                    LevelProfile.save(player, profile);
                    sendSync(player, profile);
                    PlayerGauges.syncAll(player, profile);
                    PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, profile);
                    FinessePassiveModifiers.apply(player, profile);
                    player.displayClientMessage(Component.literal(
                            "Inscribed "
                                    + nodeId
                                    + " in "
                                    + displayNameForSkill(payload.skillId())
                                    + ". Specialization "
                                    + res.insight
                                    + " / "
                                    + res.gainedInsight
                                    + " remaining."
                    ), true);
                }
        );
        registrar.playToServer(
                ActivateTechniqueSlotPayload.TYPE,
                ActivateTechniqueSlotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PlayerTechniques.activateSlot(player, payload.slot());
                    }
                }
        );
        registrar.playToServer(
                SelectTechniqueSlotPayload.TYPE,
                SelectTechniqueSlotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PlayerTechniques.selectSlot(player, payload.slot());
                    }
                }
        );
        registrar.playToServer(
                RequestShieldBashPayload.TYPE,
                RequestShieldBashPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    String failure = ValorTechniqueEvents.tryActivateShieldBash(player);
                    if (failure != null && !failure.isBlank()) {
                        player.displayClientMessage(Component.literal(failure), true);
                    }
                }
        );
        registrar.playToServer(
                RequestForwardFrenzyPayload.TYPE,
                RequestForwardFrenzyPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    String failure = ValorTechniqueEvents.tryActivateForwardFrenzy(player);
                    if (failure != null && !failure.isBlank()) {
                        player.displayClientMessage(Component.literal(failure), true);
                    }
                }
        );
        registrar.playToServer(
                RequestGhostStepPayload.TYPE,
                RequestGhostStepPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    FinesseTechniqueActivations.tryActivateGhostStep(player, payload.forward(), payload.strafe());
                }
        );
        registrar.playToServer(
                RequestUppercutPayload.TYPE,
                RequestUppercutPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FinesseTechniqueActivations.activateUppercutFromPayload(player, payload.chargeTicks());
                    }
                }
        );
        registrar.playToServer(
                RequestFistPunchPayload.TYPE,
                RequestFistPunchPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        net.zoogle.levelrpg.finesse.FinesseUnarmedCombat.tryExtendedPunch(player, payload.targetEntityId());
                    }
                }
        );
        registrar.playToServer(
                SetHandsUpGuardingPayload.TYPE,
                SetHandsUpGuardingPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FinesseGuardState.setGuarding(player, payload.guarding());
                    }
                }
        );
        registrar.playToServer(
                MarkFistCombatIntentPayload.TYPE,
                MarkFistCombatIntentPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FinesseGuardState.markFistCombatUsed(player);
                    }
                }
        );
        registrar.playToServer(
                MarkFistWeakSpotIntentPayload.TYPE,
                MarkFistWeakSpotIntentPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        net.zoogle.levelrpg.finesse.FinesseUnarmedCombat.markWeakSpotIntent(player, payload.targetEntityId());
                    }
                }
        );
        registrar.playToServer(
                RequestRecklessChargeStartPayload.TYPE,
                RequestRecklessChargeStartPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    String failure = ValorTechniqueEvents.tryStartRecklessCharge(player);
                    if (failure != null && !failure.isBlank()) {
                        player.displayClientMessage(Component.literal(failure), true);
                    }
                }
        );
        registrar.playToServer(
                RequestRecklessChargeReleasePayload.TYPE,
                RequestRecklessChargeReleasePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    String failure = ValorTechniqueEvents.tryReleaseRecklessCharge(player);
                    if (failure != null && !failure.isBlank()) {
                        player.displayClientMessage(Component.literal(failure), true);
                    }
                }
        );
    }

    // Send a full profile snapshot to a specific player
    public static void sendSync(ServerPlayer target, LevelProfile profile) {
        if (target == null) return;
        var payload = SyncLevelProfilePayload.from(profile);
        target.connection.send(new ClientboundCustomPayloadPacket(payload));
        PlayerTechniques.sync(target, profile);
    }

    // Send a delta (single skill + header values)
    public static void sendDelta(ServerPlayer target, LevelProfile profile, ResourceLocation skillId) {
        if (target == null || profile == null || skillId == null) return;
        SkillState sp = profile.skills.get(skillId);
        if (sp == null) return;
        var payload = new SyncLevelDeltaPayload(
                skillId,
                sp.level,
                sp.rank,
                sp.proficiency,
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

    public static void openSkillTreeEditorScreen(ServerPlayer target, ResourceLocation skillId) {
        if (target == null || skillId == null) {
            return;
        }
        target.connection.send(new ClientboundCustomPayloadPacket(new OpenSkillTreeEditorScreenPayload(skillId)));
    }
}
