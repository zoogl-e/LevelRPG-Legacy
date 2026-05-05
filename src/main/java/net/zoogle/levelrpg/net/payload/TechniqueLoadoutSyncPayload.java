package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

import java.util.ArrayList;
import java.util.List;

public record TechniqueLoadoutSyncPayload(
        List<SlotEntry> slots,
        List<CooldownEntry> cooldowns,
        int selectedSlot
) implements CustomPacketPayload {
    public static final Type<TechniqueLoadoutSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "technique_loadout_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueLoadoutSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slots().size());
                for (SlotEntry slot : payload.slots()) {
                    buf.writeVarInt(slot.slot());
                    buf.writeBoolean(slot.techniqueId() != null);
                    if (slot.techniqueId() != null) {
                        ResourceLocation.STREAM_CODEC.encode(buf, slot.techniqueId());
                    }
                }
                buf.writeVarInt(payload.cooldowns().size());
                for (CooldownEntry cooldown : payload.cooldowns()) {
                    ResourceLocation.STREAM_CODEC.encode(buf, cooldown.techniqueId());
                    buf.writeVarInt(cooldown.remainingTicks());
                    buf.writeVarInt(cooldown.maxTicks());
                }
                buf.writeVarInt(payload.selectedSlot());
            },
            buf -> {
                int slotCount = Math.min(9, Math.max(0, buf.readVarInt()));
                ArrayList<SlotEntry> slots = new ArrayList<>(slotCount);
                for (int i = 0; i < slotCount; i++) {
                    int slot = buf.readVarInt();
                    ResourceLocation techniqueId = buf.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
                    slots.add(new SlotEntry(slot, techniqueId));
                }
                int cooldownCount = Math.min(64, Math.max(0, buf.readVarInt()));
                ArrayList<CooldownEntry> cooldowns = new ArrayList<>(cooldownCount);
                for (int i = 0; i < cooldownCount; i++) {
                    cooldowns.add(new CooldownEntry(
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            buf.readVarInt(),
                            buf.readVarInt()
                    ));
                }
                int selectedSlot = Math.max(0, buf.readVarInt());
                return new TechniqueLoadoutSyncPayload(List.copyOf(slots), List.copyOf(cooldowns), selectedSlot);
            }
    );

    public TechniqueLoadoutSyncPayload {
        slots = slots == null ? List.of() : List.copyOf(slots);
        cooldowns = cooldowns == null ? List.of() : List.copyOf(cooldowns);
        selectedSlot = Math.max(0, selectedSlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record SlotEntry(int slot, ResourceLocation techniqueId) {
    }

    public record CooldownEntry(ResourceLocation techniqueId, int remainingTicks, int maxTicks) {
    }
}
