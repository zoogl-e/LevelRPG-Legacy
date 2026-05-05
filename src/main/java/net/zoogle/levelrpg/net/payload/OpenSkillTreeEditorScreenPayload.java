package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record OpenSkillTreeEditorScreenPayload(ResourceLocation skillId) implements CustomPacketPayload {
    public static final Type<OpenSkillTreeEditorScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "open_skill_tree_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSkillTreeEditorScreenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    OpenSkillTreeEditorScreenPayload::skillId,
                    OpenSkillTreeEditorScreenPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
