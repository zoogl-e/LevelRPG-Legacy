package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record TriggerFistCombatStancePayload() implements CustomPacketPayload {
    public static final Type<TriggerFistCombatStancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "trigger_fist_combat_stance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TriggerFistCombatStancePayload> STREAM_CODEC =
            StreamCodec.unit(new TriggerFistCombatStancePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
