package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record AbandonSoloBountyPayload() implements CustomPacketPayload {
    public static final Type<AbandonSoloBountyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "abandon_solo_bounty"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbandonSoloBountyPayload> STREAM_CODEC =
            StreamCodec.unit(new AbandonSoloBountyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

