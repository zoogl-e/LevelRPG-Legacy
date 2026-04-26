package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record UnlockTreeNodeRequestPayload(ResourceLocation skillId, String nodeId) implements CustomPacketPayload {
    public static final Type<UnlockTreeNodeRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "unlock_tree_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockTreeNodeRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, UnlockTreeNodeRequestPayload::skillId,
                    ByteBufCodecs.STRING_UTF8, UnlockTreeNodeRequestPayload::nodeId,
                    UnlockTreeNodeRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
