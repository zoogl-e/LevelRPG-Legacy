package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zoogle.levelrpg.LevelRPG;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class IndexChamberTetherRenderer {
    private static final double CRYSTAL_SEARCH_RANGE = 128.0D;
    private static final double CRYSTAL_SEARCH_RANGE_SQR = CRYSTAL_SEARCH_RANGE * CRYSTAL_SEARCH_RANGE;
    private static final int TRIAL_SEARCH_CHUNK_RADIUS = 4;
    private static final double MAX_TETHER_DISTANCE_SQR = 72.0D * 72.0D;
    private static final int PARTICLE_INTERVAL_TICKS = 2;
    private static final double ENCHANT_PARTICLE_FALL_COMPENSATION = 1.2D;

    private IndexChamberTetherRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || level.dimension() != Level.OVERWORLD || level.getGameTime() % PARTICLE_INTERVAL_TICKS != 0L) {
            return;
        }
        List<Tether> tethers = findTethers(level, player);
        if (tethers.isEmpty()) {
            return;
        }
        RandomSource random = level.random;
        for (Tether tether : tethers) {
            emitTetherParticles(level, random, tether);
        }
    }

    private static void emitTetherParticles(ClientLevel level, RandomSource random, Tether tether) {
        Vec3 crystal = tether.crystalCenter();
        Vec3 spawner = tether.spawnerCenter();
        Vec3 delta = spawner.subtract(crystal);
        double distance = delta.length();
        if (distance < 0.01D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / distance);
        Vec3 side = sideVector(direction);
        long gameTime = level.getGameTime();
        double movingProgress = (gameTime * 0.035D + tether.phase()) % 1.0D;

        for (int i = 0; i < 3; i++) {
            double progress = (movingProgress + i * 0.09D + (random.nextDouble() - 0.5D) * 0.025D) % 1.0D;
            Vec3 source = crystal.add(delta.scale(progress))
                    .add(side.scale((random.nextDouble() - 0.5D) * 0.32D))
                    .add(0.0D, (random.nextDouble() - 0.5D) * 0.20D, 0.0D);
            Vec3 target = crystal.add(delta.scale(Math.min(1.0D, progress + 0.10D)));
            addEnchantPull(level, source, target);
        }

    }

    private static Vec3 sideVector(Vec3 direction) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        if (side.lengthSqr() < 1.0E-5D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return side.normalize();
    }

    private static void addEnchantPull(ClientLevel level, Vec3 source, Vec3 target) {
        level.addParticle(
                ParticleTypes.ENCHANT,
                target.x,
                target.y + ENCHANT_PARTICLE_FALL_COMPENSATION,
                target.z,
                source.x - target.x,
                source.y - target.y - ENCHANT_PARTICLE_FALL_COMPENSATION,
                source.z - target.z
        );
    }

    private static List<Tether> findTethers(ClientLevel level, Player player) {
        EndCrystal crystal = nearestDormantIndexCrystal(level, player);
        if (crystal == null) {
            return List.of();
        }
        Vec3 crystalCenter = new Vec3(crystal.getX(), crystal.getY() + 1.2D, crystal.getZ());
        ChunkPos crystalChunk = crystal.chunkPosition();
        List<Tether> tethers = new ArrayList<>();
        for (int dx = -TRIAL_SEARCH_CHUNK_RADIUS; dx <= TRIAL_SEARCH_CHUNK_RADIUS; dx++) {
            for (int dz = -TRIAL_SEARCH_CHUNK_RADIUS; dz <= TRIAL_SEARCH_CHUNK_RADIUS; dz++) {
                ChunkAccess chunk = level.getChunk(crystalChunk.x + dx, crystalChunk.z + dz, ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk levelChunk)) {
                    continue;
                }
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.distToCenterSqr(crystalCenter) > MAX_TETHER_DISTANCE_SQR) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.TRIAL_SPAWNER) || isCompletedTrialSpawner(state)) {
                        continue;
                    }
                    Vec3 spawnerCenter = Vec3.atCenterOf(pos).add(0.0D, 0.35D, 0.0D);
                    tethers.add(new Tether(spawnerCenter, crystalCenter, phaseFor(pos)));
                }
            }
        }
        return tethers;
    }

    private static EndCrystal nearestDormantIndexCrystal(ClientLevel level, Player player) {
        AABB searchBox = player.getBoundingBox().inflate(CRYSTAL_SEARCH_RANGE);
        List<EndCrystal> crystals = level.getEntitiesOfClass(
                EndCrystal.class,
                searchBox,
                crystal -> crystal.isAlive() && !crystal.showsBottom() && crystal.distanceToSqr(player) <= CRYSTAL_SEARCH_RANGE_SQR
        );
        EndCrystal nearest = null;
        double nearestDistanceSqr = CRYSTAL_SEARCH_RANGE_SQR;
        for (EndCrystal crystal : crystals) {
            double distanceSqr = crystal.distanceToSqr(player);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = crystal;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return nearest;
    }

    private static boolean isCompletedTrialSpawner(BlockState state) {
        if (!state.hasProperty(TrialSpawnerBlock.STATE)) {
            return false;
        }
        TrialSpawnerState trialState = state.getValue(TrialSpawnerBlock.STATE);
        return trialState == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION
                || trialState == TrialSpawnerState.EJECTING_REWARD
                || trialState == TrialSpawnerState.COOLDOWN;
    }

    private static double phaseFor(BlockPos pos) {
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
        return ((hash >>> 24) & 1023L) / 1024.0D;
    }

    private record Tether(Vec3 spawnerCenter, Vec3 crystalCenter, double phase) {
    }
}
