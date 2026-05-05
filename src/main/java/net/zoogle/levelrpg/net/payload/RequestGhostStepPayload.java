package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record RequestGhostStepPayload(int forward, int strafe) implements CustomPacketPayload {
    public static final Type<RequestGhostStepPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_ghost_step"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGhostStepPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequestGhostStepPayload::forward,
                    ByteBufCodecs.VAR_INT, RequestGhostStepPayload::strafe,
                    RequestGhostStepPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
