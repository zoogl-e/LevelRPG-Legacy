package net.zoogle.levelrpg.net.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record IndexChamberGuideTargetPayload(boolean active, long dormantCorePos) implements CustomPacketPayload {
    public static final Type<IndexChamberGuideTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "index_chamber_guide_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IndexChamberGuideTargetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, IndexChamberGuideTargetPayload::active,
                    ByteBufCodecs.VAR_LONG, IndexChamberGuideTargetPayload::dormantCorePos,
                    IndexChamberGuideTargetPayload::new
            );

    public static IndexChamberGuideTargetPayload clear() {
        return new IndexChamberGuideTargetPayload(false, 0L);
    }

    public static IndexChamberGuideTargetPayload active(BlockPos dormantCorePos) {
        return new IndexChamberGuideTargetPayload(true, dormantCorePos.asLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
