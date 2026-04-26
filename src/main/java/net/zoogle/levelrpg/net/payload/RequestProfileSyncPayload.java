package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Client asks the server for a full {@link SyncLevelProfilePayload} refresh.
 * Used when opening the journal so UI always sees current server profile.
 */
public record RequestProfileSyncPayload() implements CustomPacketPayload {
    public static final Type<RequestProfileSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_profile_sync"));

    public static final RequestProfileSyncPayload INSTANCE = new RequestProfileSyncPayload();

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestProfileSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, RequestProfileSyncPayload payload) -> {},
                    buf -> INSTANCE
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
