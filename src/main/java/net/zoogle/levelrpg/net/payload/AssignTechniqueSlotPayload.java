package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record AssignTechniqueSlotPayload(int slot, String techniqueId) implements CustomPacketPayload {
    public static final Type<AssignTechniqueSlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "assign_technique_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignTechniqueSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AssignTechniqueSlotPayload::slot,
                    ByteBufCodecs.STRING_UTF8, AssignTechniqueSlotPayload::techniqueId,
                    AssignTechniqueSlotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
