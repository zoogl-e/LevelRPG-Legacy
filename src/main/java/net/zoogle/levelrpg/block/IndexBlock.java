package net.zoogle.levelrpg.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.profile.LevelProfile;

/**
 * V1 shell for The Index: a single interactable block that acts as the heart/seed
 * of the future larger Index structure.
 *
 * <p>Future phases will replace this placeholder interaction with the real
 * investment screen and route spending through DisciplineInvestmentSource.INDEX.
 */
public class IndexBlock extends Block {
    public IndexBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LevelProfile profile = LevelProfile.get(serverPlayer);
            if (!profile.hasBoundArchetype()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("msg.levelrpg.index.requires_binding"),
                        true
                );
                return InteractionResult.SUCCESS;
            }
            // V1: open the simple Index investment screen.
            // TODO(index-flow): route through richer Index structure/ritual interaction in future phases.
            Network.openIndexInvestmentScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}

