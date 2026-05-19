package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zoogle.levelrpg.LevelRPG;

import java.util.List;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class IndexChamberCrystalGuideParticles {
    private static final double GUIDE_RANGE = 200.0D;
    private static final double GUIDE_RANGE_SQR = GUIDE_RANGE * GUIDE_RANGE;
    private static final double ENCHANT_PARTICLE_FALL_COMPENSATION = 1.2D;
    private static final double CRYSTAL_PARTICLE_TARGET_Y_OFFSET = 1.22D;
    private static final int MIN_EMIT_DELAY_TICKS = 2;
    private static final int MAX_EMIT_DELAY_TICKS = 9;
    private static long nextGuideEmissionGameTime;
    private static BlockPos syncedDormantCorePos;

    private IndexChamberCrystalGuideParticles() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime < nextGuideEmissionGameTime) {
            return;
        }

        Vec3 crystalTarget = crystalGuideTarget(level, player);
        if (crystalTarget == null) {
            nextGuideEmissionGameTime = gameTime + MAX_EMIT_DELAY_TICKS;
            return;
        }
        nextGuideEmissionGameTime = gameTime + MIN_EMIT_DELAY_TICKS + level.random.nextInt(MAX_EMIT_DELAY_TICKS - MIN_EMIT_DELAY_TICKS + 1);
        emitGuideParticles(level, player, crystalTarget);
    }

    public static void applyGuideTarget(boolean active, BlockPos dormantCorePos) {
        syncedDormantCorePos = active ? dormantCorePos : null;
    }

    private static Vec3 crystalGuideTarget(ClientLevel level, Player player) {
        EndCrystal crystal = nearestDormantIndexCrystal(level, player);
        if (crystal != null) {
            return new Vec3(crystal.getX(), crystal.getY() + CRYSTAL_PARTICLE_TARGET_Y_OFFSET, crystal.getZ());
        }
        if (syncedDormantCorePos == null) {
            return null;
        }
        Vec3 target = new Vec3(
                syncedDormantCorePos.getX() + 0.5D,
                syncedDormantCorePos.getY() - 0.5D + CRYSTAL_PARTICLE_TARGET_Y_OFFSET,
                syncedDormantCorePos.getZ() + 0.5D
        );
        return target.distanceToSqr(player.position()) <= GUIDE_RANGE_SQR ? target : null;
    }

    private static EndCrystal nearestDormantIndexCrystal(ClientLevel level, Player player) {
        AABB searchBox = player.getBoundingBox().inflate(GUIDE_RANGE);
        List<EndCrystal> crystals = level.getEntitiesOfClass(
                EndCrystal.class,
                searchBox,
                crystal -> crystal.isAlive() && !crystal.showsBottom() && crystal.distanceToSqr(player) <= GUIDE_RANGE_SQR
        );
        EndCrystal nearest = null;
        double nearestDistanceSqr = GUIDE_RANGE_SQR;
        for (EndCrystal crystal : crystals) {
            double distanceSqr = crystal.distanceToSqr(player);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = crystal;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return nearest;
    }

    private static void emitGuideParticles(ClientLevel level, Player player, Vec3 crystalTarget) {
        RandomSource random = level.random;
        double distance = Math.sqrt(crystalTarget.distanceToSqr(player.position()));
        int count = 1 + random.nextInt(distance < 32.0D ? 5 : 4);
        double crystalX = crystalTarget.x;
        double particleTargetY = crystalTarget.y + ENCHANT_PARTICLE_FALL_COMPENSATION;
        double crystalZ = crystalTarget.z;
        Vec3 playerEye = player.getEyePosition();
        Vec3 toCrystal = new Vec3(crystalX - playerEye.x, crystalTarget.y - playerEye.y, crystalZ - playerEye.z);
        Vec3 direction = toCrystal.lengthSqr() < 1.0E-5D ? player.getViewVector(1.0F) : toCrystal.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        if (side.lengthSqr() < 1.0E-5D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }

        for (int i = 0; i < count; i++) {
            double forward = random.nextDouble() < 0.35D ? random.nextDouble() * Math.min(distance, 6.0D) : random.nextDouble() * 1.15D;
            double sideOffset = (random.nextDouble() - 0.5D) * (1.4D + random.nextDouble() * 1.6D);
            double verticalOffset = -0.45D + random.nextDouble() * 1.75D;
            Vec3 source = playerEye
                    .add(direction.scale(forward))
                    .add(side.scale(sideOffset))
                    .add(0.0D, verticalOffset, 0.0D);
            emitEnchantPull(level, source.x, source.y, source.z, crystalX, particleTargetY, crystalZ);
            if (random.nextDouble() < 0.38D) {
                Vec3 branch = source
                        .add(side.scale((random.nextDouble() - 0.5D) * 0.75D))
                        .add(0.0D, (random.nextDouble() - 0.5D) * 0.55D, 0.0D)
                        .add(direction.scale((random.nextDouble() - 0.5D) * 0.45D));
                emitEnchantPull(level, branch.x, branch.y, branch.z, crystalX, particleTargetY, crystalZ);
            }
        }
    }

    private static void emitEnchantPull(ClientLevel level, double sourceX, double sourceY, double sourceZ, double targetX, double targetY, double targetZ) {
        level.addParticle(
                ParticleTypes.ENCHANT,
                targetX,
                targetY,
                targetZ,
                sourceX - targetX,
                sourceY - targetY,
                sourceZ - targetZ
        );
    }
}
