package net.zoogle.levelrpg.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class EnchantmentGlyphBarrierBlock extends Block {
    private static final double PARTICLE_VISIBLE_RANGE = 12.0D;
    private static final double PARTICLE_DENSE_RANGE = 2.5D;

    public EnchantmentGlyphBarrierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        Player nearestPlayer = level.getNearestPlayer(centerX, centerY, centerZ, PARTICLE_VISIBLE_RANGE, false);
        if (nearestPlayer == null) {
            return;
        }
        double distance = Math.sqrt(nearestPlayer.distanceToSqr(centerX, centerY, centerZ));
        double density = 1.0D - Mth.clamp(
                (distance - PARTICLE_DENSE_RANGE) / (PARTICLE_VISIBLE_RANGE - PARTICLE_DENSE_RANGE),
                0.0D,
                1.0D
        );
        float chance = Mth.lerp((float) density, 0.10F, 0.85F);
        if (random.nextFloat() > chance) {
            return;
        }
        int count = 1 + (density > 0.55D ? 1 : 0) + (density > 0.82D ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double x = centerX + (random.nextDouble() - 0.5D) * 1.2D;
            double y = pos.getY() + 0.2D + random.nextDouble() * 0.8D;
            double z = centerZ + (random.nextDouble() - 0.5D) * 1.2D;
            double dx = (centerX - x) * 0.08D;
            double dy = 0.02D + random.nextDouble() * 0.03D;
            double dz = (centerZ - z) * 0.08D;
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, dx, dy, dz);
        }
    }
}
