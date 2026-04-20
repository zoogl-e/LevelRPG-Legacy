package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record SyncLevelDeltaPayload(ResourceLocation skillId, int level, long xp)
        implements CustomPacketPayload {

    public static final Type<SyncLevelDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLevelDeltaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SyncLevelDeltaPayload::skillId,
                    ByteBufCodecs.VAR_INT, SyncLevelDeltaPayload::level,
                    ByteBufCodecs.VAR_LONG, SyncLevelDeltaPayload::xp,
                    SyncLevelDeltaPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
