package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import org.jetbrains.annotations.Nullable;

public record BindArchetypeResultPayload(
        boolean success,
        String message,
        @Nullable ResourceLocation archetypeId
) implements CustomPacketPayload {
    public static final Type<BindArchetypeResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "bind_archetype_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindArchetypeResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.success());
                        buf.writeUtf(payload.message(), 256);
                        buf.writeBoolean(payload.archetypeId() != null);
                        if (payload.archetypeId() != null) {
                            ResourceLocation.STREAM_CODEC.encode(buf, payload.archetypeId());
                        }
                    },
                    buf -> {
                        boolean success = buf.readBoolean();
                        String message = buf.readUtf(256);
                        ResourceLocation archetypeId = buf.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
                        return new BindArchetypeResultPayload(success, message, archetypeId);
                    }
            );

    public BindArchetypeResultPayload {
        message = message == null ? "" : message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BindArchetypeResultPayload success(ResourceLocation archetypeId, String message) {
        return new BindArchetypeResultPayload(true, message, archetypeId);
    }

    public static BindArchetypeResultPayload failure(@Nullable ResourceLocation archetypeId, String message) {
        return new BindArchetypeResultPayload(false, message, archetypeId);
    }
}
