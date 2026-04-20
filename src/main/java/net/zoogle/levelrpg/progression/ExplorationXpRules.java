package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical Exploration rules. The first migration step keeps the signals
 * simple and testable: meaningful travel plus first-time chunk discovery within
 * the current play session.
 */
public final class ExplorationXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.EXPLORATION.id();

    private static final double MIN_STEP_DISTANCE = 2.0D;
    private static final double MAX_STEP_DISTANCE = 32.0D;
    private static final double MIN_TRAVEL_DISTANCE_FOR_AWARD = 48.0D;
    private static final double MIN_DISPLACEMENT_FROM_LAST_AWARD = 32.0D;
    private static final int SAMPLE_INTERVAL_TICKS = 20;

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private ExplorationXpRules() {}

    public static long xpForMovement(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return 0L;
        }
        if ((player.tickCount % SAMPLE_INTERVAL_TICKS) != 0) {
            return 0L;
        }

        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
        ResourceLocation dimensionId = player.level().dimension().location();
        ChunkKey currentChunk = new ChunkKey(dimensionId, player.chunkPosition().x, player.chunkPosition().z);
        Vec3 currentPos = player.position();

        if (!tracker.isInitializedFor(dimensionId)) {
            tracker.resetForDimension(dimensionId, currentChunk, currentPos);
            return 0L;
        }

        double stepDistance = horizontalDistance(currentPos, tracker.lastSamplePos);
        tracker.lastSamplePos = currentPos;
        if (stepDistance < MIN_STEP_DISTANCE || stepDistance > MAX_STEP_DISTANCE) {
            return 0L;
        }

        tracker.travelSinceLastAward += stepDistance;
        if (currentChunk.equals(tracker.currentChunk)) {
            return 0L;
        }
        tracker.currentChunk = currentChunk;

        if (!tracker.discoveredChunks.add(currentChunk)) {
            return 0L;
        }
        if (tracker.travelSinceLastAward < MIN_TRAVEL_DISTANCE_FOR_AWARD) {
            return 0L;
        }
        if (tracker.lastAwardPos != null
                && horizontalDistance(currentPos, tracker.lastAwardPos) < MIN_DISPLACEMENT_FROM_LAST_AWARD) {
            return 0L;
        }

        tracker.travelSinceLastAward = 0.0D;
        tracker.lastAwardPos = currentPos;
        return 4L;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static final class Tracker {
        private ResourceLocation dimensionId;
        private ChunkKey currentChunk;
        private Vec3 lastSamplePos;
        private Vec3 lastAwardPos;
        private double travelSinceLastAward;
        private final Set<ChunkKey> discoveredChunks = new HashSet<>();

        private boolean isInitializedFor(ResourceLocation dimensionId) {
            return this.dimensionId != null
                    && this.dimensionId.equals(dimensionId)
                    && this.currentChunk != null
                    && this.lastSamplePos != null;
        }

        private void resetForDimension(ResourceLocation dimensionId, ChunkKey currentChunk, Vec3 currentPos) {
            this.dimensionId = dimensionId;
            this.currentChunk = currentChunk;
            this.lastSamplePos = currentPos;
            this.lastAwardPos = null;
            this.travelSinceLastAward = 0.0D;
            this.discoveredChunks.clear();
            this.discoveredChunks.add(currentChunk);
        }
    }

    private record ChunkKey(ResourceLocation dimensionId, int x, int z) {}
}
