package net.zoogle.levelrpg.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class AeronauticsRopeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SIMULATED_MOD_ID = "simulated";
    private static final String AERONAUTICS_BUNDLED_MOD_ID = "aeronautics_bundled";
    private static final String ROPE_HOLDER_CLASS_NAME = "dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior";
    private static final String SMART_BLOCK_ENTITY_CLASS_NAME = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";
    private static final String BEHAVIOUR_TYPE_CLASS_NAME = "com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType";
    private static final List<PendingRopeScan> PENDING_SCANS = new ArrayList<>();
    private static final List<PendingRopeEndpointRefresh> PENDING_ENDPOINT_REFRESHES = new ArrayList<>();

    private AeronauticsRopeCompat() {
    }

    public static void scheduleGeneratedRopeConnection(ServerLevel level, BoundingBox bounds) {
        if (!isSimulatedLoaded()) {
            return;
        }
        PENDING_ENDPOINT_REFRESHES.add(new PendingRopeEndpointRefresh(level.dimension(), bounds, level.getGameTime() + 1L));
        LOGGER.info("Scheduled Aeronautics generated rope endpoint refresh for bounds {}", bounds);
    }

    public static void tick(ServerLevel level) {
        if (!isSimulatedLoaded()) {
            return;
        }
        long gameTime = level.getGameTime();
        tickEndpointRefreshes(level, gameTime);
        tickRopeScans(level, gameTime);
    }

    private static void tickEndpointRefreshes(ServerLevel level, long gameTime) {
        if (PENDING_ENDPOINT_REFRESHES.isEmpty()) {
            return;
        }
        Iterator<PendingRopeEndpointRefresh> iterator = PENDING_ENDPOINT_REFRESHES.iterator();
        while (iterator.hasNext()) {
            PendingRopeEndpointRefresh pending = iterator.next();
            if (!pending.dimension().equals(level.dimension())) {
                continue;
            }
            if (gameTime < pending.dueGameTime()) {
                continue;
            }
            iterator.remove();
            refreshRopeEndpoints(level, pending.bounds());
            PENDING_SCANS.add(new PendingRopeScan(level.dimension(), pending.bounds(), gameTime + 1L));
            LOGGER.info("Scheduled Aeronautics generated rope connection after endpoint refresh for bounds {}", pending.bounds());
        }
    }

    private static void tickRopeScans(ServerLevel level, long gameTime) {
        if (PENDING_SCANS.isEmpty()) {
            return;
        }
        Iterator<PendingRopeScan> iterator = PENDING_SCANS.iterator();
        while (iterator.hasNext()) {
            PendingRopeScan pending = iterator.next();
            if (!pending.dimension().equals(level.dimension())) {
                continue;
            }
            if (gameTime < pending.dueGameTime()) {
                continue;
            }
            iterator.remove();
            tryConnectGeneratedRopes(level, pending.bounds());
        }
    }

    private static void refreshRopeEndpoints(ServerLevel level, BoundingBox bounds) {
        List<RefreshedEndpoint> endpoints = new ArrayList<>();
        scanRopeEndpoints(level, bounds, endpoints);
        for (RefreshedEndpoint endpoint : endpoints) {
            level.setBlock(endpoint.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
        for (RefreshedEndpoint endpoint : endpoints) {
            level.setBlock(endpoint.pos(), endpoint.state(), 3);
            level.updateNeighborsAt(endpoint.pos(), endpoint.state().getBlock());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = endpoint.pos().relative(direction);
                level.updateNeighborsAt(neighbor, level.getBlockState(neighbor).getBlock());
            }
        }
        LOGGER.info("Refreshed {} generated Aeronautics rope endpoint block(s) in {}", endpoints.size(), bounds);
    }

    public static void tryConnectGeneratedRopes(ServerLevel level, BoundingBox bounds) {
        if (!isSimulatedLoaded()) {
            return;
        }
        try {
            RopeReflection ropeReflection = RopeReflection.load();
            List<BlockPos> connectors = new ArrayList<>();
            List<BlockPos> winches = new ArrayList<>();
            scanRopeEndpoints(level, bounds, connectors, winches);
            connectVerticalPairs(level, ropeReflection, connectors, winches);
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to run Aeronautics rope compat for generated structure bounds {}", bounds, throwable);
        }
    }

    private static void scanRopeEndpoints(ServerLevel level, BoundingBox bounds, List<RefreshedEndpoint> endpoints) {
        for (BlockPos cursor : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (isRopeConnectorId(id) || isRopeWinchId(id)) {
                endpoints.add(new RefreshedEndpoint(pos, state));
            }
        }
    }

    private static boolean isSimulatedLoaded() {
        ModList modList = ModList.get();
        return modList.isLoaded(SIMULATED_MOD_ID) || modList.isLoaded(AERONAUTICS_BUNDLED_MOD_ID);
    }

    private static void scanRopeEndpoints(ServerLevel level, BoundingBox bounds, List<BlockPos> connectors, List<BlockPos> winches) {
        int candidateCount = 0;
        for (BlockPos cursor : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null || !SIMULATED_MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            String path = id.getPath();
            if (path.contains("rope") || path.contains("winch")) {
                candidateCount++;
                LOGGER.info("Found generated Simulated rope candidate block {} at {}", id, pos);
            }
            if (isRopeConnectorId(id)) {
                connectors.add(pos);
            } else if (isRopeWinchId(id)) {
                winches.add(pos);
            }
        }
        LOGGER.info("Aeronautics rope scan found {} connector(s), {} winch(es), {} logged candidate(s) in {}", connectors.size(), winches.size(), candidateCount, bounds);
    }

    private static boolean isRopeConnectorId(ResourceLocation id) {
        return id != null && SIMULATED_MOD_ID.equals(id.getNamespace()) && "rope_connector".equals(id.getPath());
    }

    private static boolean isRopeWinchId(ResourceLocation id) {
        return id != null && SIMULATED_MOD_ID.equals(id.getNamespace()) && "rope_winch".equals(id.getPath());
    }

    private static void connectVerticalPairs(ServerLevel level, RopeReflection ropeReflection, List<BlockPos> connectors, List<BlockPos> winches) {
        Set<BlockPos> usedConnectors = new HashSet<>();
        Set<BlockPos> usedWinches = new HashSet<>();
        int attempted = 0;
        int connected = 0;

        for (BlockPos connector : connectors) {
            BlockPos winch = nearestUnusedVerticalWinch(connector, winches, usedWinches);
            if (winch == null) {
                continue;
            }
            attempted++;
            usedConnectors.add(connector);
            usedWinches.add(winch);
            if (connectPair(level, ropeReflection, connector, winch)) {
                connected++;
            }
        }

        LOGGER.info("Aeronautics rope compat attempted {} vertical pair(s), connected {}", attempted, connected);
    }

    private static BlockPos nearestUnusedVerticalWinch(BlockPos connector, List<BlockPos> winches, Set<BlockPos> usedWinches) {
        BlockPos nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (BlockPos winch : winches) {
            if (usedWinches.contains(winch) || winch.getX() != connector.getX() || winch.getZ() != connector.getZ()) {
                continue;
            }
            int distance = Math.abs(winch.getY() - connector.getY());
            if (distance < nearestDistance) {
                nearest = winch;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean connectPair(ServerLevel level, RopeReflection ropeReflection, BlockPos connector, BlockPos winch) {
        try {
            Object connectorHolder = ropeReflection.ropeHolder(level.getBlockEntity(connector));
            Object winchHolder = ropeReflection.ropeHolder(level.getBlockEntity(winch));
            if (connectorHolder == null || winchHolder == null) {
                LOGGER.warn("Skipping Aeronautics rope pair; missing rope holder behavior. connector={} winch={}", connector, winch);
                return false;
            }
            if (ropeReflection.isAttached(connectorHolder) || ropeReflection.isAttached(winchHolder)) {
                LOGGER.info("Skipping Aeronautics rope pair; endpoint already attached. connector={} winch={}", connector, winch);
                return false;
            }
            boolean created = ropeReflection.createRope(winchHolder, connectorHolder);
            LOGGER.info("Aeronautics rope createRope result={} connector={} winch={}", created, connector, winch);
            return created;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to connect generated Aeronautics rope pair connector={} winch={}", connector, winch, throwable);
            return false;
        }
    }

    private record PendingRopeScan(ResourceKey<Level> dimension, BoundingBox bounds, long dueGameTime) {
    }

    private record PendingRopeEndpointRefresh(ResourceKey<Level> dimension, BoundingBox bounds, long dueGameTime) {
    }

    private record RefreshedEndpoint(BlockPos pos, BlockState state) {
    }

    private record RopeReflection(Class<?> smartBlockEntityClass, Object ropeHolderType, Method getBehaviourMethod, Method createRopeMethod, Method isAttachedMethod) {
        static RopeReflection load() throws ReflectiveOperationException {
            Class<?> ropeHolderClass = Class.forName(ROPE_HOLDER_CLASS_NAME);
            Class<?> smartBlockEntityClass = Class.forName(SMART_BLOCK_ENTITY_CLASS_NAME);
            Class<?> behaviourTypeClass = Class.forName(BEHAVIOUR_TYPE_CLASS_NAME);
            Field typeField = ropeHolderClass.getField("TYPE");
            Object ropeHolderType = typeField.get(null);
            Method getBehaviourMethod = smartBlockEntityClass.getMethod("getBehaviour", behaviourTypeClass);
            Method createRopeMethod = ropeHolderClass.getMethod("createRope", ropeHolderClass);
            Method isAttachedMethod = ropeHolderClass.getMethod("isAttached");
            return new RopeReflection(smartBlockEntityClass, ropeHolderType, getBehaviourMethod, createRopeMethod, isAttachedMethod);
        }

        Object ropeHolder(BlockEntity blockEntity) throws ReflectiveOperationException {
            if (blockEntity == null || !smartBlockEntityClass.isInstance(blockEntity)) {
                return null;
            }
            return getBehaviourMethod.invoke(blockEntity, ropeHolderType);
        }

        boolean isAttached(Object ropeHolder) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(isAttachedMethod.invoke(ropeHolder));
        }

        boolean createRope(Object first, Object second) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(createRopeMethod.invoke(first, second));
        }
    }
}
