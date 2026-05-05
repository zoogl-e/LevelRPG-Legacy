package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record MarkFistWeakSpotIntentPayload(int targetEntityId) implements CustomPacketPayload {
    public static final Type<MarkFistWeakSpotIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "mark_fist_weak_spot_intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarkFistWeakSpotIntentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MarkFistWeakSpotIntentPayload::targetEntityId,
                    MarkFistWeakSpotIntentPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
