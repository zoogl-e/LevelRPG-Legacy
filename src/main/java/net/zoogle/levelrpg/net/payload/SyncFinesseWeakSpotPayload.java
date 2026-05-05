package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record SyncFinesseWeakSpotPayload(int entityId, int zoneOrdinal, boolean hasWeakSpot) implements CustomPacketPayload {
    public static final Type<SyncFinesseWeakSpotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_finesse_weak_spot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFinesseWeakSpotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncFinesseWeakSpotPayload::entityId,
                    ByteBufCodecs.VAR_INT, SyncFinesseWeakSpotPayload::zoneOrdinal,
                    ByteBufCodecs.BOOL, SyncFinesseWeakSpotPayload::hasWeakSpot,
                    SyncFinesseWeakSpotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
