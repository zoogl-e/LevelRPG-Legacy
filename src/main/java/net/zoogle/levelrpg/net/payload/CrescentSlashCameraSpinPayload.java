package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.zoogle.levelrpg.LevelRPG;

/**
 * Tells the activating client to sweep yaw 360° over {@code durationTicks} (for Crescent Slash feel).
 */
public record CrescentSlashCameraSpinPayload(int durationTicks) implements CustomPacketPayload {
    public static final Type<CrescentSlashCameraSpinPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "crescent_slash_camera_spin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrescentSlashCameraSpinPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CrescentSlashCameraSpinPayload::durationTicks,
                    CrescentSlashCameraSpinPayload::new
            );

    public CrescentSlashCameraSpinPayload {
        durationTicks = Mth.clamp(durationTicks, 4, 80);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
