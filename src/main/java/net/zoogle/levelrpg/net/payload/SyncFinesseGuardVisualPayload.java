package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record SyncFinesseGuardVisualPayload(int entityId, boolean guarding) implements CustomPacketPayload {
    public static final Type<SyncFinesseGuardVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_finesse_guard_visual"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFinesseGuardVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncFinesseGuardVisualPayload::entityId,
                    ByteBufCodecs.BOOL, SyncFinesseGuardVisualPayload::guarding,
                    SyncFinesseGuardVisualPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
