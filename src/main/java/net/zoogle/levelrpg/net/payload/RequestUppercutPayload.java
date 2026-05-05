package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record RequestUppercutPayload(int chargeTicks) implements CustomPacketPayload {
    public static final Type<RequestUppercutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "request_uppercut"));
    public static final StreamCodec<FriendlyByteBuf, RequestUppercutPayload> STREAM_CODEC = StreamCodec.ofMember(
            RequestUppercutPayload::write,
            RequestUppercutPayload::new
    );

    public RequestUppercutPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.chargeTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
