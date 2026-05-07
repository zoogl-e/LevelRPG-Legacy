package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record OpenIndexInvestmentScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenIndexInvestmentScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "open_index_investment_screen"));

    public static final OpenIndexInvestmentScreenPayload INSTANCE = new OpenIndexInvestmentScreenPayload();

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenIndexInvestmentScreenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, OpenIndexInvestmentScreenPayload payload) -> {},
                    buf -> INSTANCE
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

