package net.zoogle.levelrpg.client.technique;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.net.payload.TechniqueLoadoutSyncPayload;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientTechniqueCache {
    private static final ResourceLocation[] SLOTS = new ResourceLocation[PlayerTechniqueData.SLOT_COUNT];
    private static final LinkedHashMap<ResourceLocation, CooldownView> COOLDOWNS = new LinkedHashMap<>();
    private static int selectedSlot = 0;

    private ClientTechniqueCache() {
    }

    public static void apply(TechniqueLoadoutSyncPayload payload) {
        java.util.Arrays.fill(SLOTS, null);
        for (TechniqueLoadoutSyncPayload.SlotEntry slot : payload.slots()) {
            int index = slot.slot() - 1;
            if (PlayerTechniqueData.isValidSlot(index)) {
                SLOTS[index] = slot.techniqueId();
            }
        }
        selectedSlot = PlayerTechniqueData.isValidSlot(payload.selectedSlot()) ? payload.selectedSlot() : 0;
        COOLDOWNS.clear();
        for (TechniqueLoadoutSyncPayload.CooldownEntry cooldown : payload.cooldowns()) {
            COOLDOWNS.put(cooldown.techniqueId(), new CooldownView(
                    Math.max(0, cooldown.remainingTicks()),
                    Math.max(0, cooldown.maxTicks())
            ));
        }
    }

    public static ResourceLocation slot(int zeroBasedSlot) {
        return PlayerTechniqueData.isValidSlot(zeroBasedSlot) ? SLOTS[zeroBasedSlot] : null;
    }

    public static CooldownView cooldown(ResourceLocation techniqueId) {
        return techniqueId == null ? CooldownView.NONE : COOLDOWNS.getOrDefault(techniqueId, CooldownView.NONE);
    }

    public static int selectedSlot() {
        return selectedSlot;
    }

    public static Map<ResourceLocation, CooldownView> cooldownsView() {
        return Map.copyOf(COOLDOWNS);
    }

    public record CooldownView(int remainingTicks, int maxTicks) {
        public static final CooldownView NONE = new CooldownView(0, 0);

        public boolean active() {
            return remainingTicks > 0;
        }
    }
}
