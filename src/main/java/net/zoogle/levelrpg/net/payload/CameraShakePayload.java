package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.zoogle.levelrpg.LevelRPG;

public record CameraShakePayload(int durationTicks, float intensity, float roughness) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "camera_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShakePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CameraShakePayload::durationTicks,
                    ByteBufCodecs.FLOAT, CameraShakePayload::intensity,
                    ByteBufCodecs.FLOAT, CameraShakePayload::roughness,
                    CameraShakePayload::new
            );

    public CameraShakePayload {
        durationTicks = Mth.clamp(durationTicks, 2, 40);
        intensity = Mth.clamp(intensity, 0.0F, 3.0F);
        roughness = Mth.clamp(roughness, 0.25F, 4.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
