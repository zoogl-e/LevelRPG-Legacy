package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record RequestForwardFrenzyPayload() implements CustomPacketPayload {
    public static final Type<RequestForwardFrenzyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_forward_frenzy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestForwardFrenzyPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestForwardFrenzyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
