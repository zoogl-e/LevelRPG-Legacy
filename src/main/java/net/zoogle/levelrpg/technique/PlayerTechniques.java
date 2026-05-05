package net.zoogle.levelrpg.technique;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.gauge.GaugeDefinition;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.net.payload.TechniqueLoadoutSyncPayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.ArrayList;

public final class PlayerTechniques {
    private PlayerTechniques() {
    }

    public static TechniqueResult assign(ServerPlayer player, LevelProfile profile, int oneBasedSlot, ResourceLocation techniqueId) {
        int slot = oneBasedSlot - 1;
        if (!PlayerTechniqueData.isValidSlot(slot)) {
            return TechniqueResult.failure("Technique slot must be 1-" + PlayerTechniqueData.SLOT_COUNT);
        }
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        if (technique == null) {
            return TechniqueResult.failure("Unknown technique");
        }
        TechniqueResult requirement = validateUnlocked(profile, technique);
        if (!requirement.success()) {
            return requirement;
        }
        profile.techniques.setSlot(slot, techniqueId);
        LevelProfile.save(player, profile);
        sync(player, profile);
        return TechniqueResult.success("Assigned " + technique.displayName() + " to slot " + oneBasedSlot);
    }

    public static void clear(ServerPlayer player, LevelProfile profile, int oneBasedSlot) {
        int slot = oneBasedSlot - 1;
        if (!PlayerTechniqueData.isValidSlot(slot)) {
            return;
        }
        profile.techniques.clearSlot(slot);
        LevelProfile.save(player, profile);
        sync(player, profile);
    }

    public static TechniqueResult activateSlot(ServerPlayer player, int oneBasedSlot) {
        int slot = oneBasedSlot - 1;
        if (!PlayerTechniqueData.isValidSlot(slot)) {
            return fail(player, "Technique slot must be 1-" + PlayerTechniqueData.SLOT_COUNT);
        }
        LevelProfile profile = LevelProfile.get(player);
        ResourceLocation techniqueId = profile.techniques.getSlot(slot);
        if (techniqueId == null) {
            return fail(player, "No technique assigned to slot " + oneBasedSlot);
        }
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        if (technique == null) {
            return fail(player, "Unknown technique in slot " + oneBasedSlot);
        }
        TechniqueResult unlocked = validateUnlocked(profile, technique);
        if (!unlocked.success()) {
            return fail(player, unlocked.message());
        }
        int cooldown = profile.techniques.cooldown(technique.id());
        if (cooldown > 0) {
            return fail(player, technique.displayName() + " is on cooldown: " + cooldown + " ticks");
        }
        if (technique.cost().hasGaugeCost() && !profile.gauges.canSpend(player, profile, technique.cost().gaugeId(), technique.cost().amount())) {
            return fail(player, notEnoughGaugeMessage(technique.cost().gaugeId()));
        }
        TechniqueActivationContext context = new TechniqueActivationContext(player, profile, technique);
        TechniqueResult validation = technique.validator() == null ? TechniqueResult.success("Ready") : technique.validator().validate(context);
        if (!validation.success()) {
            return fail(player, validation.message());
        }
        if (technique.cost().hasGaugeCost() && !profile.gauges.spend(player, profile, technique.cost().gaugeId(), technique.cost().amount())) {
            return fail(player, notEnoughGaugeMessage(technique.cost().gaugeId()));
        }
        TechniqueResult activated = technique.activator() == null ? TechniqueResult.success(technique.displayName()) : technique.activator().activate(context);
        if (!activated.success()) {
            if (technique.cost().hasGaugeCost()) {
                profile.gauges.add(player, profile, technique.cost().gaugeId(), technique.cost().amount());
            }
            return fail(player, activated.message());
        }
        profile.techniques.setCooldown(technique.id(), technique.cooldownTicks());
        LevelProfile.save(player, profile);
        if (technique.cost().hasGaugeCost()) {
            PlayerGauges.sync(player, profile, technique.cost().gaugeId());
        }
        sync(player, profile);
        return activated;
    }

    public static void sync(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return;
        }
        ArrayList<TechniqueLoadoutSyncPayload.SlotEntry> slots = new ArrayList<>(PlayerTechniqueData.SLOT_COUNT);
        ResourceLocation[] assigned = profile.techniques.slotsCopy();
        for (int i = 0; i < assigned.length; i++) {
            slots.add(new TechniqueLoadoutSyncPayload.SlotEntry(i + 1, assigned[i]));
        }
        ArrayList<TechniqueLoadoutSyncPayload.CooldownEntry> cooldowns = new ArrayList<>();
        for (var entry : profile.techniques.cooldownsView().entrySet()) {
            TechniqueDefinition technique = TechniqueRegistry.get(entry.getKey());
            int max = technique == null ? entry.getValue() : technique.cooldownTicks();
            cooldowns.add(new TechniqueLoadoutSyncPayload.CooldownEntry(entry.getKey(), entry.getValue(), max));
        }
        player.connection.send(new ClientboundCustomPayloadPacket(new TechniqueLoadoutSyncPayload(
                slots,
                cooldowns,
                profile.techniques.selectedSlot()
        )));
    }

    public static void selectSlot(ServerPlayer player, int oneBasedSlot) {
        int slot = oneBasedSlot - 1;
        if (!PlayerTechniqueData.isValidSlot(slot)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        profile.techniques.setSelectedSlot(slot);
        LevelProfile.save(player, profile);
        sync(player, profile);
    }

    public static boolean tickCooldowns(ServerPlayer player) {
        LevelProfile profile = LevelProfile.get(player);
        boolean changed = profile.techniques.tickCooldowns();
        if (changed) {
            LevelProfile.save(player, profile);
            sync(player, profile);
        }
        return changed;
    }

    private static String notEnoughGaugeMessage(ResourceLocation gaugeId) {
        GaugeDefinition def = GaugeRegistry.get(gaugeId);
        String name = def != null ? def.displayName() : (gaugeId == null ? "resource" : gaugeId.getPath());
        return "Not enough " + name;
    }

    private static TechniqueResult validateUnlocked(LevelProfile profile, TechniqueDefinition technique) {
        if (!technique.hasRequirement()) {
            return TechniqueResult.success("Unlocked");
        }
        if (!SkillUnlockQuery.hasNode(profile, technique.requiredSkillId(), technique.requiredNodeId())) {
            return TechniqueResult.failure("Requires " + technique.requiredSkillId().getPath() + " node " + technique.requiredNodeId());
        }
        return TechniqueResult.success("Unlocked");
    }

    private static TechniqueResult fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
        return TechniqueResult.failure(message);
    }
}
