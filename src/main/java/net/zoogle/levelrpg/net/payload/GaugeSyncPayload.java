package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.gauge.GaugeDefinition;

public record GaugeSyncPayload(
        ResourceLocation gaugeId,
        String displayName,
        double value,
        double max,
        boolean hiddenWhenEmpty,
        int primaryColor,
        int backgroundColor
) implements CustomPacketPayload {
    public static final Type<GaugeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "gauge_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GaugeSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, payload.gaugeId);
                buf.writeUtf(payload.displayName);
                buf.writeDouble(payload.value);
                buf.writeDouble(payload.max);
                buf.writeBoolean(payload.hiddenWhenEmpty);
                buf.writeInt(payload.primaryColor);
                buf.writeInt(payload.backgroundColor);
            },
            buf -> new GaugeSyncPayload(
                    ResourceLocation.STREAM_CODEC.decode(buf),
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    public static GaugeSyncPayload from(GaugeDefinition definition, double value, double max) {
        return new GaugeSyncPayload(
                definition.id(),
                definition.displayName(),
                value,
                max,
                definition.hiddenWhenEmpty(),
                definition.primaryColor(),
                definition.backgroundColor()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
