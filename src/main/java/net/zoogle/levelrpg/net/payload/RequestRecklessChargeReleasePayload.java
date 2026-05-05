package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record RequestRecklessChargeReleasePayload() implements CustomPacketPayload {
    public static final Type<RequestRecklessChargeReleasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_reckless_charge_release"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRecklessChargeReleasePayload> STREAM_CODEC =
            StreamCodec.unit(new RequestRecklessChargeReleasePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
