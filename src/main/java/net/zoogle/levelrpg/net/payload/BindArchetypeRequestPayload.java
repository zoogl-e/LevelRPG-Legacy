package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record BindArchetypeRequestPayload(ResourceLocation archetypeId) implements CustomPacketPayload {
    public static final Type<BindArchetypeRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "bind_archetype"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindArchetypeRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, BindArchetypeRequestPayload::archetypeId,
                    BindArchetypeRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
