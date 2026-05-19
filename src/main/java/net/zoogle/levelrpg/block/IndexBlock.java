package net.zoogle.levelrpg.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zoogle.levelrpg.block.entity.TheIndexBlockEntity;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.world.IndexChamberManager;

import javax.annotation.Nullable;

/**
 * V1 shell for The Index: a single interactable block that acts as the heart/seed
 * of the future larger Index structure.
 *
 * <p>Future phases will replace this placeholder interaction with the real
 * investment screen and route spending through DisciplineInvestmentSource.INDEX.
 */
public class IndexBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE_BASE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape SHAPE_PILLAR = Block.box(4.0D, 2.0D, 4.0D, 12.0D, 13.0D, 12.0D);
    private static final VoxelShape SHAPE_UPPER_PEDESTAL = Block.box(1.0D, 11.0D, 1.0D, 15.0D, 13.0D, 15.0D);
    private static final VoxelShape SHAPE_DAIS = Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE = Shapes.or(
            SHAPE_BASE,
            SHAPE_PILLAR,
            SHAPE_UPPER_PEDESTAL,
            SHAPE_DAIS
    );

    public IndexBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheIndexBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
            if (!IndexChamberManager.isActivated(serverPlayer.serverLevel())) {
                serverPlayer.displayClientMessage(
                        Component.translatable("msg.levelrpg.index.inactive"),
                        true
                );
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.32F, 0.58F);
                return InteractionResult.SUCCESS;
            }
            LevelProfile profile = LevelProfile.get(serverPlayer);
            if (!profile.hasBoundArchetype()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("msg.levelrpg.index.requires_binding"),
                        true
                );
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.28F, 0.72F);
                return InteractionResult.SUCCESS;
            }
            // V1: open the simple Index investment screen.
            // TODO(index-flow): route through richer Index structure/ritual interaction in future phases.
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.55F, 0.92F);
            Network.openIndexInvestmentScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
