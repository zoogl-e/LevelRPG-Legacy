package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record SyncLevelDeltaPayload(
        ResourceLocation skillId,
        int level,
        int masteryLevel,
        long masteryXp,
        int availableSkillPoints,
        int spentSkillPoints,
        int essence
)
        implements CustomPacketPayload {

    public static final Type<SyncLevelDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLevelDeltaPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ResourceLocation.STREAM_CODEC.encode(buf, payload.skillId());
                        buf.writeVarInt(payload.level());
                        buf.writeVarInt(payload.masteryLevel());
                        buf.writeVarLong(payload.masteryXp());
                        buf.writeVarInt(payload.availableSkillPoints());
                        buf.writeVarInt(payload.spentSkillPoints());
                        buf.writeVarInt(payload.essence());
                    },
                    buf -> new SyncLevelDeltaPayload(
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarLong(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
