package net.zoogle.levelrpg.events;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.zoogle.levelrpg.Config;
import net.zoogle.levelrpg.registry.LevelRpgBlocks;
import net.zoogle.levelrpg.world.IndexPlacementData;
import org.slf4j.Logger;

public class IndexPlacementEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        tryEnsureOriginalIndex(serverLevel, "level_load");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tryEnsureOriginalIndex(player.serverLevel(), "player_login");
    }

    private void tryEnsureOriginalIndex(ServerLevel serverLevel, String trigger) {
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        IndexPlacementData data = IndexPlacementData.get(serverLevel);
        LOGGER.info(
                "Index placement check [{}]: dim={}, enabled={}, alreadyPlaced={}, spawn={}, radius={}",
                trigger,
                serverLevel.dimension().location(),
                Config.generateIndexNearSpawn,
                data.placed(),
                serverLevel.getSharedSpawnPos(),
                Math.max(0, Config.indexSpawnSearchRadius)
        );

        if (!Config.generateIndexNearSpawn) {
            return;
        }
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        if (data.placed()) {
            return;
        }

        BlockPos spawn = serverLevel.getSharedSpawnPos();
        int radius = Math.max(0, Config.indexSpawnSearchRadius);
        PlacementScanStats stats = new PlacementScanStats();
        BlockPos placed = tryPlaceNearSpawn(serverLevel, spawn, radius, stats);
        if (placed == null) {
            LOGGER.warn(
                    "Could not find suitable Index placement near spawn. spawn={}, radius={}, checked={}, rejectNoChunk={}, rejectFluid={}, rejectNonReplaceable={}, rejectNoSturdyBelow={}",
                    spawn,
                    radius,
                    stats.checkedColumns,
                    stats.rejectNoChunk,
                    stats.rejectFluid,
                    stats.rejectNonReplaceable,
                    stats.rejectNoSturdyBelow
            );
            return;
        }

        data.markPlaced(placed);
        int dx = placed.getX() - spawn.getX();
        int dz = placed.getZ() - spawn.getZ();
        int dist2 = dx * dx + dz * dz;
        LOGGER.info(
                "Placed original The Index at {} {} {} near spawn {} {} {} (dx={}, dz={}, dist2={}, radius={})",
                placed.getX(),
                placed.getY(),
                placed.getZ(),
                spawn.getX(),
                spawn.getY(),
                spawn.getZ(),
                dx,
                dz,
                dist2,
                radius
        );
    }

    private static BlockPos tryPlaceNearSpawn(ServerLevel level, BlockPos spawn, int radius, PlacementScanStats stats) {
        int spawnX = spawn.getX();
        int spawnZ = spawn.getZ();
        int radius2 = radius * radius;

        BlockPos bestPos = null;
        int bestDist2 = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > radius2) {
                    continue;
                }

                int x = spawnX + dx;
                int z = spawnZ + dz;
                int worldDist2 = horizontalDistanceSquared(spawnX, spawnZ, x, z);
                if (worldDist2 > radius2) {
                    stats.logOutOfRadiusCandidateOnce(spawnX, spawnZ, x, z, radius, worldDist2);
                    continue;
                }

                stats.checkedColumns++;
                BlockPos pos = findPlacementAtColumn(level, x, z, stats);
                if (pos == null) {
                    continue;
                }

                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    bestPos = pos;
                }
            }
        }

        if (bestPos != null) {
            int finalDist2 = horizontalDistanceSquared(spawnX, spawnZ, bestPos.getX(), bestPos.getZ());
            if (finalDist2 > radius2) {
                stats.logOutOfRadiusCandidateOnce(spawnX, spawnZ, bestPos.getX(), bestPos.getZ(), radius, finalDist2);
                return null;
            }
            level.setBlock(bestPos, LevelRpgBlocks.THE_INDEX.get().defaultBlockState(), 3);
        }
        return bestPos;
    }

    private static int horizontalDistanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    private static BlockPos findPlacementAtColumn(ServerLevel level, int x, int z, PlacementScanStats stats) {
        // Ensure we can query terrain for this column at runtime.
        level.getChunk(x >> 4, z >> 4);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = surfaceY;
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight()) {
            return null;
        }
        BlockPos target = new BlockPos(x, y, z);
        return isSuitablePlacement(level, target, stats, surfaceY) ? target : null;
    }

    private static boolean isSuitablePlacement(ServerLevel level, BlockPos pos, PlacementScanStats stats, int surfaceY) {
        BlockState targetState = level.getBlockState(pos);
        if (!(targetState.isAir() || targetState.canBeReplaced())) {
            stats.rejectNonReplaceable++;
            stats.logSample(level, pos, surfaceY, "target_not_replaceable_or_air");
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            stats.rejectFluid++;
            stats.logSample(level, pos, surfaceY, "target_has_fluid");
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.isAir()) {
            stats.rejectNoSturdyBelow++;
            stats.logSample(level, pos, surfaceY, "below_is_air");
            return false;
        }
        if (!level.getFluidState(belowPos).isEmpty()) {
            stats.rejectNoSturdyBelow++;
            stats.logSample(level, pos, surfaceY, "below_has_fluid");
            return false;
        }
        if (belowState.canBeReplaced()) {
            stats.rejectNoSturdyBelow++;
            stats.logSample(level, pos, surfaceY, "below_replaceable");
            return false;
        }
        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            // V1 reliability-first support check: accept a non-replaceable, non-air, non-fluid block below.
            if (!isProbablySolidSupport(level, belowPos, belowState)) {
                stats.rejectNoSturdyBelow++;
                stats.logSample(level, pos, surfaceY, "below_not_sturdy");
                return false;
            }
        }
        return true;
    }

    private static boolean isProbablySolidSupport(ServerLevel level, BlockPos belowPos, BlockState belowState) {
        if (belowState.getCollisionShape(level, belowPos).isEmpty()) {
            return false;
        }
        return belowState.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, belowPos)
                || belowState.getDestroySpeed(level, belowPos) >= 0.0F
                || belowState.isSolid();
    }

    private static final class PlacementScanStats {
        private static final int MAX_DEBUG_SAMPLES = 5;
        int checkedColumns;
        int rejectNoChunk;
        int rejectFluid;
        int rejectNonReplaceable;
        int rejectNoSturdyBelow;
        int debugSamplesLogged;
        boolean outOfRadiusWarningLogged;

        void logSample(ServerLevel level, BlockPos target, int surfaceY, String reason) {
            if (debugSamplesLogged >= MAX_DEBUG_SAMPLES) {
                return;
            }
            debugSamplesLogged++;
            BlockPos below = target.below();
            LOGGER.info(
                    "Index placement reject sample {}: reason={}, x={}, z={}, surfaceY={}, targetY={}, targetBlock={}, belowBlock={}",
                    debugSamplesLogged,
                    reason,
                    target.getX(),
                    target.getZ(),
                    surfaceY,
                    target.getY(),
                    level.getBlockState(target).getBlock(),
                    level.getBlockState(below).getBlock()
            );
        }

        void logOutOfRadiusCandidateOnce(int spawnX, int spawnZ, int x, int z, int radius, int dist2) {
            if (outOfRadiusWarningLogged) {
                return;
            }
            outOfRadiusWarningLogged = true;
            LOGGER.warn(
                    "Index placement skipped out-of-radius candidate: spawn=({}, {}), candidate=({}, {}), dist2={}, radius={}",
                    spawnX,
                    spawnZ,
                    x,
                    z,
                    dist2,
                    radius
            );
        }
    }
}

