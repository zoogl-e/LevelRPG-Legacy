package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.zoogle.levelrpg.LevelRPG;

public record RecklessChargeStatePayload(boolean active, int maxChargeTicks) implements CustomPacketPayload {
    public static final Type<RecklessChargeStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "reckless_charge_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecklessChargeStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RecklessChargeStatePayload::active,
                    ByteBufCodecs.VAR_INT, RecklessChargeStatePayload::maxChargeTicks,
                    RecklessChargeStatePayload::new
            );

    public RecklessChargeStatePayload {
        maxChargeTicks = Mth.clamp(maxChargeTicks, 0, 200);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
