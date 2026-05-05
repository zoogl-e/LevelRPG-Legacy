package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record RequestShieldBashPayload() implements CustomPacketPayload {
    public static final Type<RequestShieldBashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_shield_bash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestShieldBashPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestShieldBashPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
