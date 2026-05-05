package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record MarkFistCombatIntentPayload() implements CustomPacketPayload {
    public static final Type<MarkFistCombatIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "mark_fist_combat_intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarkFistCombatIntentPayload> STREAM_CODEC =
            StreamCodec.unit(new MarkFistCombatIntentPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
