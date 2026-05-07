package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record IndexDisciplineInvestRequestPayload(ResourceLocation skillId) implements CustomPacketPayload {
    public static final Type<IndexDisciplineInvestRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "index_discipline_invest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IndexDisciplineInvestRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, IndexDisciplineInvestRequestPayload::skillId,
                    IndexDisciplineInvestRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

