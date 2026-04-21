package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record SpendSkillPointRequestPayload(ResourceLocation skillId) implements CustomPacketPayload {
    public static final Type<SpendSkillPointRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "spend_skill_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpendSkillPointRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SpendSkillPointRequestPayload::skillId,
                    SpendSkillPointRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
