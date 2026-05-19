package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.client.finesse.FinesseFirstPersonUnarmedRenderer;
import net.zoogle.levelrpg.client.finesse.FinesseThirdPersonGuardState;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.client.skilltree.SkillTreeViewMode;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCache;
import net.zoogle.levelrpg.client.ui.IndexInvestmentScreen;
import net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen;
import net.zoogle.levelrpg.client.valor.ClientCrescentSlashCamera;
import net.zoogle.levelrpg.client.valor.ClientRecklessChargeState;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.net.payload.BindArchetypeResultPayload;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;
import net.zoogle.levelrpg.net.payload.CrescentSlashCameraSpinPayload;
import net.zoogle.levelrpg.net.payload.GaugeSyncPayload;
import net.zoogle.levelrpg.net.payload.IndexChamberGuideTargetPayload;
import net.zoogle.levelrpg.net.payload.OpenIndexInvestmentScreenPayload;
import net.zoogle.levelrpg.net.payload.OpenSkillTreeEditorScreenPayload;
import net.zoogle.levelrpg.net.payload.RecklessChargeStatePayload;
import net.zoogle.levelrpg.net.payload.SyncFinesseGuardVisualPayload;
import net.zoogle.levelrpg.net.payload.SyncFinesseWeakSpotPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelDeltaPayload;
import net.zoogle.levelrpg.net.payload.SyncLevelProfilePayload;
import net.zoogle.levelrpg.net.payload.TechniqueLoadoutSyncPayload;
import net.zoogle.levelrpg.net.payload.TriggerFistCombatStancePayload;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handle(CameraShakePayload payload) {
        CameraShakeController.apply(payload);
    }

    public static void handle(GaugeSyncPayload payload) {
        ClientGaugeCache.apply(payload);
    }

    public static void handle(IndexChamberGuideTargetPayload payload) {
        IndexChamberCrystalGuideParticles.applyGuideTarget(
                payload.active(),
                BlockPos.of(payload.dormantCorePos())
        );
    }

    public static void handle(TechniqueLoadoutSyncPayload payload) {
        ClientTechniqueCache.apply(payload);
    }

    public static void handle(BindArchetypeResultPayload payload) {
        ClientProfileCache.recordBindResult(payload.success(), payload.message(), payload.archetypeId());
    }

    public static void handle(SyncLevelProfilePayload payload) {
        ClientProfileCache.set(
                payload.toMap(),
                payload.toTreeSpentMap(),
                payload.toTreeUnlockedMap(),
                payload.availableSkillPoints(),
                payload.spentSkillPoints(),
                payload.bonusSpecializationPoints(),
                payload.essence(),
                payload.activeSoloBountyId(),
                payload.activeSoloBountyObjectiveMet(),
                payload.activeSoloBountyProgress(),
                payload.bountyOfferTier(),
                payload.completedBountiesSet(),
                payload.archetypeId(),
                payload.archetypeApplied()
        );
    }

    public static void handle(SyncLevelDeltaPayload payload) {
        ClientProfileCache.applyDelta(
                payload.skillId(),
                payload.level(),
                payload.masteryLevel(),
                payload.masteryXp(),
                payload.availableSkillPoints(),
                payload.spentSkillPoints(),
                payload.essence()
        );
    }

    public static void handle(OpenIndexInvestmentScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(new IndexInvestmentScreen());
    }

    public static void handle(OpenSkillTreeEditorScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (net.zoogle.levelrpg.skilltree.SkillTreeRegistry.get(payload.skillId()) == null) {
            minecraft.player.displayClientMessage(Component.literal("No discipline tree found for " + payload.skillId()), true);
            return;
        }
        minecraft.setScreen(new SkillTreeEditorScreen(payload.skillId(), -1, SkillTreeViewMode.EDITOR));
    }

    public static void handle(CrescentSlashCameraSpinPayload payload) {
        ClientCrescentSlashCamera.startSpin(payload.durationTicks());
    }

    public static void handle(RecklessChargeStatePayload payload) {
        ClientRecklessChargeState.apply(payload);
    }

    public static void handle(TriggerFistCombatStancePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(minecraft.player.tickCount);
        }
    }

    public static void handle(SyncFinesseGuardVisualPayload payload) {
        FinesseThirdPersonGuardState.apply(payload);
    }

    public static void handle(SyncFinesseWeakSpotPayload payload) {
        FinesseUnarmedCombat.applyWeakSpotSync(payload);
    }
}
