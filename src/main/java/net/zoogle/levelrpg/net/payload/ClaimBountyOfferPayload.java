package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public record ClaimBountyOfferPayload(ResourceLocation bountyId) implements CustomPacketPayload {
    public static final Type<ClaimBountyOfferPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "claim_bounty_offer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimBountyOfferPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ClaimBountyOfferPayload::bountyId,
                    ClaimBountyOfferPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

