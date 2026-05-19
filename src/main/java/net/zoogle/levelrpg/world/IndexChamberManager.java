package net.zoogle.levelrpg.world;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.compat.AeronauticsRopeCompat;
import net.zoogle.levelrpg.compat.CreateKineticCompat;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.registry.LevelRpgBlocks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class IndexChamberManager {
    public static final ResourceLocation INDEX_CHAMBER_STRUCTURE = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "index_chamber");
    public static final ResourceLocation INDEX_CHAMBER_SABLE_STRUCTURE = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "index_chamber_sable_test");
    public static final ResourceKey<LootTable> INDEX_KEY_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "chests/index_trial_key")
    );
    private static final String SABLE_MOD_ID = "sable";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<PendingSableAssembly> PENDING_SABLE_ASSEMBLIES = new ArrayList<>();
    private static final List<PendingKineticRefresh> PENDING_KINETIC_REFRESHES = new ArrayList<>();
    private static final int SABLE_ASSEMBLY_DELAY_AFTER_ROPES_TICKS = 2;
    private static final int KINETIC_REFRESH_DELAY_AFTER_ASSEMBLY_TICKS = 2;
    private static final boolean EXPERIMENTAL_ASSEMBLE_OBSIDIAN_AS_CRACKED_STONE_BRICKS = true;
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 58;
    private static final int SEARCH_MIN_RADIUS = 48;
    private static final int SEARCH_MAX_RADIUS = 160;
    private static final int SURFACE_CLEARANCE = 6;
    private static final int RELAXED_SURFACE_CLEARANCE = 2;
    private static final double IDEAL_MAX_AIR_SAMPLE_RATIO = 0.25D;
    private static final double RELAXED_MAX_AIR_SAMPLE_RATIO = 0.70D;
    private static final int IDEAL_ATTEMPTS = 96;
    private static final int RELAXED_ATTEMPTS = 96;
    private static final int FALLBACK_BURY_DEPTH = 28;
    private static final int[][] FALLBACK_OFFSETS = {
            {96, -96},
            {-96, 96},
            {128, 0},
            {-128, 0},
            {0, 128},
            {0, -128},
            {144, 96},
            {-144, -96},
            {160, 160},
            {-160, 160},
            {160, -160},
            {-160, -160}
    };

    private IndexChamberManager() {}

    public static void tryEnsureChamber(ServerLevel level, String trigger) {
        tryEnsureChamber(level, trigger, level.getSharedSpawnPos());
    }

    public static void tryEnsureChamber(ServerLevel level, String trigger, BlockPos placementAnchor) {
        if (level.dimension() != Level.OVERWORLD) {
            LOGGER.info("Index Chamber placement skipped [{}]: non-Overworld dimension={}", trigger, level.dimension().location());
            return;
        }
        IndexPlacementData data = IndexPlacementData.get(level);
        if (data.chamberPlaced()) {
            LOGGER.info("Index Chamber placement skipped [{}]: already placed origin={} vault={} activeIndex={}", trigger, data.chamberOrigin(), data.vaultPos(), data.activeIndexPos());
            return;
        }
        BlockPos anchor = placementAnchor == null ? level.getSharedSpawnPos() : placementAnchor;
        LOGGER.info("Index Chamber placement event [{}]: dimension={} spawn={} anchor={} placed={}", trigger, level.dimension().location(), level.getSharedSpawnPos(), anchor, data.chamberPlaced());
        ResourceLocation structureId = chamberStructureId();
        Optional<StructureTemplate> loadedTemplate = loadTemplate(level, trigger, structureId);
        if (loadedTemplate.isEmpty()) {
            return;
        }
        StructureTemplate template = loadedTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            LOGGER.warn("Index Chamber placement skipped [{}]: template {} was found but has invalid size {}. Inspect NBT root for a vanilla structure 'size' list.", trigger, structureId, size);
            return;
        }
        PlacementStats stats = new PlacementStats();
        PlacementResult result = findPlacement(level, size, stats, anchor);
        if (result == null) {
            LOGGER.error("Index Chamber placement failed [{}]: no suitable candidate or fallback near anchor {}. This should be impossible unless the template cannot fit world bounds. {}", trigger, anchor, stats.summary());
            return;
        }
        if (!placeTemplate(level, template, result.origin())) {
            stats.placementFailed++;
            LOGGER.warn("Index Chamber placement [{}]: template placement returned false for {} result at origin={}; trying guaranteed fallback placement. {}", trigger, result.tier(), result.origin(), stats.summary());
            result = findGuaranteedFallback(level, size, stats, anchor);
            if (result == null || !placeTemplate(level, template, result.origin())) {
                stats.placementFailed++;
                LOGGER.error("Index Chamber placement failed [{}]: guaranteed fallback placement also failed. {}", trigger, stats.summary());
                return;
            }
        }
        MarkerScanResult markers = scanAndReplaceMarkers(level, data, result.origin(), size, result.tier().name().toLowerCase(java.util.Locale.ROOT), structureId);
        if (!markers.requiredMarkersPresent()) {
            stats.markerScanFailed++;
            LOGGER.warn("Index Chamber marker scan incomplete [{}]: origin={} vaultCount={} trialCount={} coreCount={}. {}", trigger, result.origin(), markers.vaultCount(), markers.trialCount(), markers.coreCount(), stats.summary());
        }
        LOGGER.info(
                "Placed Index Chamber [{}] tier={} at origin={} size={} template={} spawn={} anchor={} attempts={} scoreAir={} vault={} trialSpawner={} activeIndex={}. {}",
                trigger,
                result.tier(),
                result.origin(),
                size,
                structureId,
                level.getSharedSpawnPos(),
                anchor,
                result.attempts(),
                result.airBlocks(),
                data.vaultPos(),
                data.trialSpawnerPos(),
                data.activeIndexPos(),
                stats.summary()
        );
    }

    public static ForcePlaceResult forcePlaceNear(ServerLevel level, BlockPos anchor) {
        if (level.dimension() != Level.OVERWORLD) {
            String message = "Index Chamber force-place rejected: command must run in the Overworld, got " + level.dimension().location();
            LOGGER.warn(message);
            return new ForcePlaceResult(false, null, message);
        }
        ResourceLocation structureId = chamberStructureId();
        Optional<StructureTemplate> loadedTemplate = loadTemplate(level, "force_place", structureId);
        if (loadedTemplate.isEmpty()) {
            String message = "Index Chamber force-place failed: template " + structureId + " was not found.";
            LOGGER.warn(message);
            return new ForcePlaceResult(false, null, message);
        }
        StructureTemplate template = loadedTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            String message = "Index Chamber force-place failed: template " + structureId + " was found but has invalid size " + size + ". Inspect NBT root for a vanilla structure 'size' list.";
            LOGGER.warn(message);
            return new ForcePlaceResult(false, null, message);
        }
        BlockPos origin = new BlockPos(anchor.getX() - (size.getX() / 2), anchor.getY(), anchor.getZ() - (size.getZ() / 2));
        LOGGER.info("Index Chamber force-place requested: anchor={} origin={} template={} size={}", anchor, origin, structureId, size);
        if (!placeTemplate(level, template, origin)) {
            String message = "Index Chamber force-place failed: template placement returned false at " + origin;
            LOGGER.warn(message);
            return new ForcePlaceResult(false, origin, message);
        }
        IndexPlacementData data = IndexPlacementData.get(level);
        MarkerScanResult markers = scanAndReplaceMarkers(level, data, origin, size, "force_place", structureId);
        String message = "Index Chamber force-placed at " + origin
                + " vault=" + data.vaultPos()
                + " trialSpawner=" + data.trialSpawnerPos()
                + " activeIndex=" + data.activeIndexPos()
                + " markerCounts[vault=" + markers.vaultCount()
                + ", trial=" + markers.trialCount()
                + ", core=" + markers.coreCount()
                + ", barriers=" + markers.barrierCount()
                + ", lights=" + markers.lightCount()
                + ", powers=" + markers.powerCount()
                + "]";
        LOGGER.info(message);
        return new ForcePlaceResult(true, origin, message);
    }

    private static Optional<StructureTemplate> loadTemplate(ServerLevel level, String trigger, ResourceLocation structureId) {
        LOGGER.info(
                "Index Chamber template lookup [{}]: id={} namespace={} path={}",
                trigger,
                structureId,
                structureId.getNamespace(),
                structureId.getPath()
        );
        Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
        if (template.isEmpty()) {
            LOGGER.warn(
                    "Index Chamber template not found [{}]: id={}. StructureTemplateManager did not resolve the resource.",
                    trigger,
                    structureId
            );
            return Optional.empty();
        }
        LOGGER.info("Index Chamber template found [{}]: id={} loadedSize={}", trigger, structureId, template.get().getSize());
        return template;
    }

    private static ResourceLocation chamberStructureId() {
        return isSableLoaded() ? INDEX_CHAMBER_SABLE_STRUCTURE : INDEX_CHAMBER_STRUCTURE;
    }

    private static boolean isSableLoaded() {
        return ModList.get().isLoaded(SABLE_MOD_ID);
    }

    public static boolean isIndexVaultPos(ServerLevel level, BlockPos pos) {
        IndexPlacementData data = IndexPlacementData.get(level);
        return data.chamberPlaced() && data.vaultPos() != null && data.vaultPos().equals(pos);
    }

    public static boolean isIndexTrialSpawnerPos(ServerLevel level, BlockPos pos) {
        IndexPlacementData data = IndexPlacementData.get(level);
        return data.chamberPlaced() && data.trialSpawnerPositions().contains(pos);
    }

    public static boolean isActivated(ServerLevel level) {
        return IndexPlacementData.get(level).chamberActivated();
    }

    public static void syncGuideTarget(ServerPlayer player) {
        if (player == null || player.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        IndexPlacementData data = IndexPlacementData.get(player.serverLevel());
        Network.syncIndexChamberGuideTarget(player, data.dormantCorePos(), data.chamberPlaced() && !data.chamberActivated() && data.dormantCorePos() != null);
    }

    public static void syncGuideTargets(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        IndexPlacementData data = IndexPlacementData.get(level);
        boolean active = data.chamberPlaced() && !data.chamberActivated() && data.dormantCorePos() != null;
        for (ServerPlayer player : level.players()) {
            Network.syncIndexChamberGuideTarget(player, data.dormantCorePos(), active);
        }
    }

    public static void tickPendingSableAssemblies(ServerLevel level) {
        if (PENDING_SABLE_ASSEMBLIES.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        java.util.Iterator<PendingSableAssembly> iterator = PENDING_SABLE_ASSEMBLIES.iterator();
        while (iterator.hasNext()) {
            PendingSableAssembly pending = iterator.next();
            if (!pending.dimension().equals(level.dimension())) {
                continue;
            }
            if (gameTime < pending.dueGameTime()) {
                continue;
            }
            iterator.remove();
            assembleSableTestObjects(level, pending.pairs());
            assembleExperimentalObsidianMarkerObjects(level, pending.experimentalObsidianMarkerAssemblies());
            scheduleCreateKineticRefresh(level, pending.bounds());
        }
    }

    public static void tickPendingKineticRefreshes(ServerLevel level) {
        if (PENDING_KINETIC_REFRESHES.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        java.util.Iterator<PendingKineticRefresh> iterator = PENDING_KINETIC_REFRESHES.iterator();
        while (iterator.hasNext()) {
            PendingKineticRefresh pending = iterator.next();
            if (!pending.dimension().equals(level.dimension())) {
                continue;
            }
            if (gameTime < pending.dueGameTime()) {
                continue;
            }
            iterator.remove();
            CreateKineticCompat.refreshGeneratedKinetics(level, pending.bounds());
        }
    }

    public static void tickVaultActivationObserver(ServerLevel level) {
        IndexPlacementData data = IndexPlacementData.get(level);
        if (!data.chamberPlaced() || data.chamberActivated() || data.vaultPos() == null) {
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        if (!level.getBlockState(data.vaultPos()).is(Blocks.VAULT)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(data.vaultPos());
        if (!(blockEntity instanceof VaultBlockEntity)) {
            return;
        }
        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
        CompoundTag serverData = tag.getCompound("server_data");
        if (serverData.getList("rewarded_players", Tag.TAG_INT_ARRAY).isEmpty()) {
            return;
        }
        if (activate(level, data.vaultPos())) {
            LOGGER.info("Index Chamber activated after vanilla vault rewarded a player at {}", data.vaultPos());
        }
    }

    public static void tickVaultCopperReplacement(ServerLevel level) {
        IndexPlacementData data = IndexPlacementData.get(level);
        if (!data.chamberPlaced() || !data.chamberActivated() || data.vaultPos() == null) {
            return;
        }
        if (level.getGameTime() % 10L != 0L) {
            return;
        }
        BlockState state = level.getBlockState(data.vaultPos());
        if (!state.is(Blocks.VAULT) || !isVaultRewardFinished(level, data.vaultPos(), state)) {
            return;
        }
        level.setBlock(data.vaultPos(), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        LOGGER.info("Replaced completed Index Chamber vault with copper block at {}", data.vaultPos());
    }

    public static void tickTrialKeyRewardProgress(ServerLevel level) {
        IndexPlacementData data = IndexPlacementData.get(level);
        if (!data.chamberPlaced() || data.chamberActivated() || data.trialSpawnerPositions().isEmpty() || data.finalTrialSpawnerPos() != null) {
            return;
        }
        List<BlockPos> unfinished = new ArrayList<>();
        for (BlockPos trialPos : data.trialSpawnerPositions()) {
            if (!level.getBlockState(trialPos).is(Blocks.TRIAL_SPAWNER)) {
                continue;
            }
            if (!isTrialSpawnerCompleted(level, trialPos)) {
                unfinished.add(trialPos);
            }
        }
        BlockPos finalTrialPos = null;
        if (unfinished.size() == 1) {
            finalTrialPos = unfinished.get(0);
        } else if (unfinished.isEmpty()) {
            finalTrialPos = findRewardPendingTrialSpawner(level, data.trialSpawnerPositions());
        }
        if (finalTrialPos != null) {
            applyIndexTrialSpawnerConfig(level, finalTrialPos, true);
            data.markFinalTrialSpawner(finalTrialPos);
            LOGGER.info("Index Chamber final trial spawner selected for Index Key reward at {}", finalTrialPos);
        }
    }

    public static boolean activate(ServerLevel level, BlockPos vaultPos) {
        IndexPlacementData data = IndexPlacementData.get(level);
        if (!data.chamberPlaced() || data.chamberActivated()) {
            return data.chamberActivated();
        }
        if (data.vaultPos() != null && !data.vaultPos().equals(vaultPos)) {
            return false;
        }
        BlockPos activeIndexPos = data.activeIndexPos();
        if (activeIndexPos == null && data.dormantCorePos() != null) {
            activeIndexPos = data.dormantCorePos().below(4);
        }
        if (activeIndexPos == null) {
            return false;
        }
        for (BlockPos barrierPos : data.barrierPositions()) {
            if (level.getBlockState(barrierPos).is(LevelRpgBlocks.ENCHANTMENT_GLYPH_BARRIER.get())) {
                level.setBlock(barrierPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (BlockPos trialPos : data.trialSpawnerPositions()) {
            if (level.getBlockState(trialPos).is(Blocks.TRIAL_SPAWNER)) {
                level.setBlock(trialPos, Blocks.COPPER_BLOCK.defaultBlockState(), 3);
            }
        }
        if (data.dormantCorePos() != null) {
            level.playSound(null, data.dormantCorePos(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.35F, 0.65F);
        }
        if (data.dormantCorePos() != null) {
            removeDormantIndexCrystal(level, data.dormantCorePos());
        }
        if (data.dormantCorePos() != null && level.getBlockState(data.dormantCorePos()).is(LevelRpgBlocks.DORMANT_INDEX_CORE.get())) {
            level.setBlock(data.dormantCorePos(), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(activeIndexPos, LevelRpgBlocks.THE_INDEX.get().defaultBlockState(), 3);
        for (BlockPos lightPos : data.lightMarkerPositions()) {
            placeActivatedTorch(level, lightPos);
        }
        replaceUnrecordedRedstoneTorchMarkers(level, data);
        for (BlockPos powerPos : data.activationPowerMarkerPositions()) {
            BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
            level.setBlock(powerPos, torch.canSurvive(level, powerPos) ? torch : Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        }
        strikeLightningAboveChamber(level, data);
        data.markActivated(activeIndexPos);
        syncGuideTargets(level);
        return true;
    }

    private static void strikeLightningAboveChamber(ServerLevel level, IndexPlacementData data) {
        BlockPos origin = data.chamberOrigin();
        BlockPos size = data.structureSize();
        if (origin == null || size == null) {
            return;
        }
        int centerX = origin.getX() + (size.getX() / 2);
        int centerZ = origin.getZ() + (size.getZ() / 2);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
        BlockPos strikePos = new BlockPos(centerX, surfaceY, centerZ);
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }

    private static void placeActivatedTorch(ServerLevel level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.is(Blocks.REDSTONE_WALL_TORCH) && currentState.hasProperty(WallTorchBlock.FACING)) {
            BlockState wallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, currentState.getValue(WallTorchBlock.FACING));
            if (wallTorch.canSurvive(level, pos)) {
                level.setBlock(pos, wallTorch, 3);
                return;
            }
        }
        BlockState floorTorch = Blocks.TORCH.defaultBlockState();
        if (floorTorch.canSurvive(level, pos)) {
            level.setBlock(pos, floorTorch, 3);
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState wallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction);
            if (wallTorch.canSurvive(level, pos)) {
                level.setBlock(pos, wallTorch, 3);
                return;
            }
        }
        level.setBlock(pos, floorTorch, 3);
    }

    private static void replaceUnrecordedRedstoneTorchMarkers(ServerLevel level, IndexPlacementData data) {
        BlockPos origin = data.chamberOrigin();
        BlockPos size = data.structureSize();
        if (origin == null || size == null) {
            return;
        }
        int replaced = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (isUnrecordedRedstoneLightMarker(state)) {
                        placeActivatedTorch(level, cursor.immutable());
                        replaced++;
                    }
                }
            }
        }
        if (replaced > 0) {
            LOGGER.info("Index Chamber activation converted {} unrecorded redstone torch light marker(s)", replaced);
        }
    }

    private static PlacementResult findPlacement(ServerLevel level, Vec3i size, PlacementStats stats, BlockPos anchor) {
        PlacementResult ideal = searchTier(level, size, stats, PlacementTier.IDEAL, anchor);
        if (ideal != null) {
            return ideal;
        }
        PlacementResult relaxed = searchTier(level, size, stats, PlacementTier.RELAXED, anchor);
        if (relaxed != null) {
            return relaxed;
        }
        PlacementResult fallback = findGuaranteedFallback(level, size, stats, anchor);
        if (fallback != null) {
            return fallback;
        }
        return buildLastResortFallback(level, size, stats, anchor);
    }

    private static PlacementResult searchTier(ServerLevel level, Vec3i size, PlacementStats stats, PlacementTier tier, BlockPos anchor) {
        RandomSource random = RandomSource.create(level.getSeed() ^ tier.seedSalt());
        PlacementResult best = null;
        for (int attempt = 1; attempt <= tier.attempts(); attempt++) {
            stats.attempts++;
            stats.incrementTierAttempt(tier);
            int radius = SEARCH_MIN_RADIUS + random.nextInt(SEARCH_MAX_RADIUS - SEARCH_MIN_RADIUS + 1);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius) - (size.getX() / 2);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius) - (size.getZ() / 2);
            int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
            BlockPos origin = new BlockPos(x, y, z);
            CandidateScan scan = scanCandidate(level, origin, size, stats, tier);
            if (!scan.accepted()) {
                continue;
            }
            PlacementResult result = new PlacementResult(origin, attempt, scan.airBlocks(), tier);
            if (scan.airBlocks() == 0) {
                return result;
            }
            if (best == null || scan.airBlocks() < best.airBlocks()) {
                best = result;
            }
        }
        if (best != null) {
            return best;
        }
        return null;
    }

    private static PlacementResult findGuaranteedFallback(ServerLevel level, Vec3i size, PlacementStats stats, BlockPos anchor) {
        for (int i = 0; i < FALLBACK_OFFSETS.length; i++) {
            int x = anchor.getX() + FALLBACK_OFFSETS[i][0] - (size.getX() / 2);
            int z = anchor.getZ() + FALLBACK_OFFSETS[i][1] - (size.getZ() / 2);
            int centerX = x + (size.getX() / 2);
            int centerZ = z + (size.getZ() / 2);
            level.getChunk(centerX >> 4, centerZ >> 4);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
            int y = clamp(surfaceY - FALLBACK_BURY_DEPTH, MIN_Y, MAX_Y);
            BlockPos origin = new BlockPos(x, y, z);
            stats.attempts++;
            stats.fallbackAttempts++;
            if (!fitsWorldBounds(level, origin, size, stats)) {
                continue;
            }
            CandidateScan scan = scanCandidate(level, origin, size, stats, PlacementTier.FALLBACK);
            if (scan.accepted()) {
                return new PlacementResult(origin, i + 1, scan.airBlocks(), PlacementTier.FALLBACK);
            }
        }
        return null;
    }

    private static PlacementResult buildLastResortFallback(ServerLevel level, Vec3i size, PlacementStats stats, BlockPos anchor) {
        int x = anchor.getX() + 96 - (size.getX() / 2);
        int z = anchor.getZ() - 96 - (size.getZ() / 2);
        int centerX = x + (size.getX() / 2);
        int centerZ = z + (size.getZ() / 2);
        level.getChunk(centerX >> 4, centerZ >> 4);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
        int y = clamp(surfaceY - FALLBACK_BURY_DEPTH, MIN_Y, MAX_Y);
        BlockPos origin = new BlockPos(x, y, z);
        int minBuildY = level.getMinBuildHeight() + 4;
        int maxOriginY = level.getMaxBuildHeight() - size.getY() - 4;
        if (maxOriginY < minBuildY) {
            stats.outOfBounds++;
            return null;
        }
        y = clamp(origin.getY(), minBuildY, maxOriginY);
        origin = new BlockPos(origin.getX(), y, origin.getZ());
        LOGGER.warn("Index Chamber placement using last-resort fallback at origin={}; validation was bypassed except world bounds. {}", origin, stats.summary());
        return new PlacementResult(origin, 1, -1, PlacementTier.FALLBACK);
    }

    private static CandidateScan scanCandidate(ServerLevel level, BlockPos origin, Vec3i size, PlacementStats stats, PlacementTier tier) {
        if (!fitsWorldBounds(level, origin, size, stats)) {
            return CandidateScan.rejected();
        }
        int centerX = origin.getX() + (size.getX() / 2);
        int centerZ = origin.getZ() + (size.getZ() / 2);
        level.getChunk(centerX >> 4, centerZ >> 4);
        int topY = origin.getY() + size.getY();
        if (!hasRequiredSurfaceCover(level, origin, size, topY, tier)) {
            stats.tooCloseToSurface++;
            return CandidateScan.rejected();
        }

        int airBlocks = 0;
        int sampledBlocks = 0;
        int maxAirSamples = Math.max(4, (int) Math.floor(tier.sampleCount(size) * tier.maxAirSampleRatio()));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx += tier.horizontalSampleStep()) {
            for (int dz = 0; dz < size.getZ(); dz += tier.horizontalSampleStep()) {
                for (int dy = 0; dy < size.getY(); dy += tier.verticalSampleStep()) {
                    sampledBlocks++;
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!level.getFluidState(cursor).isEmpty() || state.is(Blocks.LAVA) || state.is(Blocks.WATER)) {
                        stats.fluidFound++;
                        return CandidateScan.rejected();
                    }
                    if (level.canSeeSky(cursor)) {
                        stats.skyExposed++;
                        return CandidateScan.rejected();
                    }
                    if (state.isAir()) {
                        airBlocks++;
                        if (airBlocks > maxAirSamples) {
                            stats.tooMuchAir++;
                            return CandidateScan.rejected();
                        }
                    }
                }
            }
        }
        if (sampledBlocks == 0) {
            stats.invalidSolidVolume++;
            return CandidateScan.rejected();
        }
        return new CandidateScan(true, airBlocks);
    }

    private static boolean fitsWorldBounds(ServerLevel level, BlockPos origin, Vec3i size, PlacementStats stats) {
        int minBuildY = level.getMinBuildHeight() + 4;
        int maxBuildY = level.getMaxBuildHeight() - 4;
        if (origin.getY() < minBuildY || origin.getY() + size.getY() >= maxBuildY) {
            stats.outOfBounds++;
            return false;
        }
        return true;
    }

    private static boolean hasRequiredSurfaceCover(ServerLevel level, BlockPos origin, Vec3i size, int structureTopY, PlacementTier tier) {
        if (tier == PlacementTier.FALLBACK) {
            return true;
        }
        int clearance = tier == PlacementTier.IDEAL ? SURFACE_CLEARANCE : RELAXED_SURFACE_CLEARANCE;
        int centerX = origin.getX() + (size.getX() / 2);
        int centerZ = origin.getZ() + (size.getZ() / 2);
        if (!hasEnoughSurfaceCover(level, centerX, centerZ, structureTopY, clearance)) {
            return false;
        }
        if (tier == PlacementTier.RELAXED) {
            return true;
        }
        return hasEnoughSurfaceCover(level, origin.getX(), origin.getZ(), structureTopY, clearance)
                && hasEnoughSurfaceCover(level, origin.getX() + size.getX() - 1, origin.getZ(), structureTopY, clearance)
                && hasEnoughSurfaceCover(level, origin.getX(), origin.getZ() + size.getZ() - 1, structureTopY, clearance)
                && hasEnoughSurfaceCover(level, origin.getX() + size.getX() - 1, origin.getZ() + size.getZ() - 1, structureTopY, clearance);
    }

    private static boolean hasEnoughSurfaceCover(ServerLevel level, int x, int z, int structureTopY) {
        return hasEnoughSurfaceCover(level, x, z, structureTopY, SURFACE_CLEARANCE);
    }

    private static boolean hasEnoughSurfaceCover(ServerLevel level, int x, int z, int structureTopY, int clearance) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return structureTopY + clearance < surfaceY;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean placeTemplate(ServerLevel level, StructureTemplate template, BlockPos origin) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setKnownShape(true);
        return template.placeInWorld(level, origin, origin, settings, level.getRandom(), 3);
    }

    private static MarkerScanResult scanAndReplaceMarkers(ServerLevel level, IndexPlacementData data, BlockPos origin, Vec3i size, String placementTier, ResourceLocation structureId) {
        BlockPos vaultPos = null;
        BlockPos trialPos = null;
        BlockPos dormantCorePos = null;
        List<BlockPos> barriers = new ArrayList<>();
        List<BlockPos> trialPositions = new ArrayList<>();
        List<BlockPos> lights = new ArrayList<>();
        List<BlockPos> powers = new ArrayList<>();
        List<SableAssemblyPair> sableAssemblyPairs = new ArrayList<>();
        List<BlockPos> experimentalObsidianMarkerAssemblies = new ArrayList<>();
        BlockPos yellowBrownCornerA = null;
        BlockPos yellowBrownCornerB = null;
        BlockPos blackRedCornerA = null;
        BlockPos blackRedCornerB = null;
        BlockPos limeGreenCornerA = null;
        BlockPos limeGreenCornerB = null;
        BlockPos lightBlueBlueCornerA = null;
        BlockPos lightBlueBlueCornerB = null;
        BlockPos pinkMagentaCornerA = null;
        BlockPos pinkMagentaCornerB = null;
        int vaultCount = 0;
        int trialCount = 0;
        int coreCount = 0;
        int waterloggedIronCleanupCount = 0;
        int waterMarkerCount = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockPos pos = cursor.immutable();
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(Blocks.CHAIN) || state.is(Blocks.IRON_BARS))
                            && state.hasProperty(BlockStateProperties.WATERLOGGED)
                            && state.getValue(BlockStateProperties.WATERLOGGED)) {
                        state = state.setValue(BlockStateProperties.WATERLOGGED, false);
                        level.setBlock(pos, state, 3);
                        waterloggedIronCleanupCount++;
                    }
                    if (state.is(Blocks.VAULT)) {
                        vaultCount++;
                        vaultPos = pos;
                        configureIndexVaultMarker(level, pos, state);
                    } else if (state.is(Blocks.TRIAL_SPAWNER)) {
                        trialCount++;
                        if (trialPos == null) {
                            trialPos = pos;
                        }
                        trialPositions.add(pos);
                    } else if (state.is(LevelRpgBlocks.ENCHANTMENT_GLYPH_BARRIER.get())) {
                        barriers.add(pos);
                    } else if (state.is(Blocks.CRYING_OBSIDIAN)) {
                        coreCount++;
                        dormantCorePos = pos;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        spawnDormantIndexCrystal(level, pos);
                    } else if (state.is(Blocks.REDSTONE_WALL_TORCH)) {
                        lights.add(pos);
                    } else if (isHiddenDormantLightMarker(state)) {
                        lights.add(pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    } else if (state.is(Blocks.CHISELED_TUFF_BRICKS)) {
                        powers.add(pos);
                        level.setBlock(pos, Blocks.TUFF_BRICKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.BLUE_WOOL)) {
                        waterMarkerCount++;
                        replaceWaterMarker(level, pos);
                    } else if (EXPERIMENTAL_ASSEMBLE_OBSIDIAN_AS_CRACKED_STONE_BRICKS && state.is(Blocks.OBSIDIAN)) {
                        experimentalObsidianMarkerAssemblies.add(pos);
                        level.setBlock(pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.YELLOW_GLAZED_TERRACOTTA)) {
                        yellowBrownCornerA = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.BROWN_GLAZED_TERRACOTTA)) {
                        yellowBrownCornerB = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.BLACK_GLAZED_TERRACOTTA)) {
                        blackRedCornerA = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.RED_GLAZED_TERRACOTTA)) {
                        blackRedCornerB = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.LIME_GLAZED_TERRACOTTA)) {
                        limeGreenCornerA = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.GREEN_GLAZED_TERRACOTTA)) {
                        limeGreenCornerB = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA)) {
                        lightBlueBlueCornerA = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.BLUE_GLAZED_TERRACOTTA)) {
                        lightBlueBlueCornerB = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.PINK_GLAZED_TERRACOTTA)) {
                        pinkMagentaCornerA = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    } else if (state.is(Blocks.MAGENTA_GLAZED_TERRACOTTA)) {
                        pinkMagentaCornerB = pos;
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                }
            }
        }

        addSableAssemblyPair(sableAssemblyPairs, "yellow_brown", yellowBrownCornerA, yellowBrownCornerB, 0);
        addSableAssemblyPair(sableAssemblyPairs, "black_red", blackRedCornerA, blackRedCornerB, 1);
        addSableAssemblyPair(sableAssemblyPairs, "lime_green", limeGreenCornerA, limeGreenCornerB, 1);
        addSableAssemblyPair(sableAssemblyPairs, "light_blue_blue", lightBlueBlueCornerA, lightBlueBlueCornerB, 1);
        addSableAssemblyPair(sableAssemblyPairs, "pink_magenta", pinkMagentaCornerA, pinkMagentaCornerB, 1);

        trialPositions.sort(IndexChamberManager::compareBlockPositions);
        boolean onlyOneTrialSpawner = trialPositions.size() == 1;
        for (BlockPos recordedTrialPos : trialPositions) {
            configureIndexTrialSpawnerMarker(level, recordedTrialPos, level.getBlockState(recordedTrialPos), onlyOneTrialSpawner);
        }
        BlockPos activeIndexPos = dormantCorePos != null ? dormantCorePos.below(4) : null;
        data.recordChamber(origin, new BlockPos(size.getX(), size.getY(), size.getZ()), vaultPos, trialPos, dormantCorePos, activeIndexPos, barriers, trialPositions, lights, powers, placementTier);
        if (onlyOneTrialSpawner) {
            data.markFinalTrialSpawner(trialPositions.get(0));
        }
        if (INDEX_CHAMBER_SABLE_STRUCTURE.equals(structureId)) {
            BoundingBox placedBounds = new BoundingBox(
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    origin.getX() + size.getX() - 1,
                    origin.getY() + size.getY() - 1,
                    origin.getZ() + size.getZ() - 1
            );
            AeronauticsRopeCompat.scheduleGeneratedRopeConnection(level, placedBounds);
            scheduleSableTestObjects(level, sableAssemblyPairs, experimentalObsidianMarkerAssemblies, placedBounds);
        } else {
            scheduleSableTestObjects(level, sableAssemblyPairs, experimentalObsidianMarkerAssemblies, null);
        }
        LOGGER.info(
                "Index Chamber markers: vault={} trial={} dormantCore={} activeIndex={} counts[vault={}, trial={}, core={}, barriers={}, lights={}, powers={}, waterMarkers={}, waterloggedIronCleanup={}] sableAssemblyPairs={} experimentalObsidianMarkerAssemblies={} finalTrial={}",
                vaultPos,
                trialPos,
                dormantCorePos,
                activeIndexPos,
                vaultCount,
                trialCount,
                coreCount,
                barriers.size(),
                lights.size(),
                powers.size(),
                waterMarkerCount,
                waterloggedIronCleanupCount,
                sableAssemblyPairs.size(),
                experimentalObsidianMarkerAssemblies.size(),
                onlyOneTrialSpawner ? trialPositions.get(0) : null
        );
        return new MarkerScanResult(vaultCount, trialCount, coreCount, barriers.size(), lights.size(), powers.size());
    }

    private static void replaceWaterMarker(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        level.updateNeighborsAt(pos, Blocks.WATER);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            level.updateNeighborsAt(neighbor, level.getBlockState(neighbor).getBlock());
        }
    }

    private static void spawnDormantIndexCrystal(ServerLevel level, BlockPos pos) {
        AABB duplicateCheck = new AABB(pos).inflate(0.25D);
        if (!level.getEntitiesOfClass(EndCrystal.class, duplicateCheck).isEmpty()) {
            return;
        }
        EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
        if (crystal == null) {
            return;
        }
        crystal.setPos(pos.getX() + 0.5D, pos.getY() - 0.5D, pos.getZ() + 0.5D);
        crystal.setShowBottom(false);
        crystal.setInvulnerable(true);
        level.addFreshEntity(crystal);
    }

    private static void addSableAssemblyPair(List<SableAssemblyPair> pairs, String label, BlockPos cornerA, BlockPos cornerB, int yOffset) {
        if (cornerA == null && cornerB == null) {
            return;
        }
        pairs.add(new SableAssemblyPair(label, cornerA, cornerB, yOffset));
    }

    private static void scheduleSableTestObjects(ServerLevel level, List<SableAssemblyPair> pairs, List<BlockPos> experimentalObsidianMarkerAssemblies, BoundingBox placedBounds) {
        if (pairs.isEmpty() && experimentalObsidianMarkerAssemblies.isEmpty()) {
            return;
        }
        PENDING_SABLE_ASSEMBLIES.add(new PendingSableAssembly(
                level.dimension(),
                level.getGameTime() + SABLE_ASSEMBLY_DELAY_AFTER_ROPES_TICKS,
                new ArrayList<>(pairs),
                new ArrayList<>(experimentalObsidianMarkerAssemblies),
                placedBounds
        ));
        LOGGER.info(
                "Scheduled {} Index Chamber Sable assembly pair(s) and {} experimental obsidian marker single-block assembly target(s) {} tick(s) after placement so generated rope compat can run first.",
                pairs.size(),
                experimentalObsidianMarkerAssemblies.size(),
                SABLE_ASSEMBLY_DELAY_AFTER_ROPES_TICKS
        );
    }

    private static void scheduleCreateKineticRefresh(ServerLevel level, BoundingBox bounds) {
        if (bounds == null) {
            return;
        }
        PENDING_KINETIC_REFRESHES.add(new PendingKineticRefresh(
                level.dimension(),
                level.getGameTime() + KINETIC_REFRESH_DELAY_AFTER_ASSEMBLY_TICKS,
                bounds
        ));
        LOGGER.info(
                "Scheduled generated Create kinetic refresh {} tick(s) after Sable assembly for bounds {}.",
                KINETIC_REFRESH_DELAY_AFTER_ASSEMBLY_TICKS,
                bounds
        );
    }

    private static void assembleSableTestObjects(ServerLevel level, List<SableAssemblyPair> pairs) {
        if (pairs.isEmpty()) {
            return;
        }
        if (!isSableLoaded()) {
            LOGGER.info("Index Chamber Sable assembly markers found but Sable is not loaded; skipping {} assembly pair(s).", pairs.size());
            return;
        }
        for (SableAssemblyPair pair : pairs) {
            assembleSableTestObject(level, pair);
        }
    }

    private static void assembleExperimentalObsidianMarkerObjects(ServerLevel level, List<BlockPos> positions) {
        if (!EXPERIMENTAL_ASSEMBLE_OBSIDIAN_AS_CRACKED_STONE_BRICKS || positions.isEmpty()) {
            return;
        }
        if (!isSableLoaded()) {
            LOGGER.info("Experimental obsidian marker Sable assembly found {} target(s), but Sable is not loaded; skipping.", positions.size());
            return;
        }
        int assembled = 0;
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).is(Blocks.CRACKED_STONE_BRICKS)) {
                continue;
            }
            assembleSingleBlockSableObject(level, "experimental_obsidian_marker_cracked_stone_bricks", pos);
            assembled++;
        }
        LOGGER.info(
                "Experimental Index Chamber obsidian marker physics pass assembled {} of {} cracked stone brick replacement block(s). This pass is intentionally isolated for easy removal.",
                assembled,
                positions.size()
        );
    }

    private static void assembleSableTestObject(ServerLevel level, SableAssemblyPair pair) {
        if (pair.cornerA() == null || pair.cornerB() == null) {
            LOGGER.warn("Index Chamber Sable assembly pair '{}' requires both corner markers. corners[{}, {}]", pair.label(), pair.cornerA(), pair.cornerB());
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(pair.cornerA().getX(), pair.cornerB().getX()),
                Math.min(pair.cornerA().getY(), pair.cornerB().getY()),
                Math.min(pair.cornerA().getZ(), pair.cornerB().getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pair.cornerA().getX(), pair.cornerB().getX()),
                Math.max(pair.cornerA().getY(), pair.cornerB().getY()) + pair.yOffset(),
                Math.max(pair.cornerA().getZ(), pair.cornerB().getZ())
        );
        String command = String.format(
                java.util.Locale.ROOT,
                "sable assemble area %d %d %d %d %d %d",
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX(),
                max.getY(),
                max.getZ()
        );
        CommandSourceStack source = level.getServer()
                .createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(min))
                .withPermission(4)
                .withSuppressedOutput();
        try {
            level.getServer().getCommands().performPrefixedCommand(source, command);
            LOGGER.info("Index Chamber Sable assembly command executed for '{}': {}", pair.label(), command);
        } catch (RuntimeException exception) {
            LOGGER.warn("Index Chamber Sable assembly command failed for '{}': {}", pair.label(), command, exception);
        }
    }

    private static void assembleSingleBlockSableObject(ServerLevel level, String label, BlockPos pos) {
        String command = String.format(
                java.util.Locale.ROOT,
                "sable assemble area %d %d %d %d %d %d",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
        CommandSourceStack source = level.getServer()
                .createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(pos))
                .withPermission(4)
                .withSuppressedOutput();
        try {
            level.getServer().getCommands().performPrefixedCommand(source, command);
            LOGGER.info("Index Chamber Sable assembly command executed for '{}': {}", label, command);
        } catch (RuntimeException exception) {
            LOGGER.warn("Index Chamber Sable assembly command failed for '{}': {}", label, command, exception);
        }
    }

    private static void removeDormantIndexCrystal(ServerLevel level, BlockPos pos) {
        AABB removalBox = new AABB(pos).inflate(1.0D);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, removalBox)) {
            crystal.discard();
        }
    }

    private static int compareBlockPositions(BlockPos first, BlockPos second) {
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        int z = Integer.compare(first.getZ(), second.getZ());
        if (z != 0) {
            return z;
        }
        return Integer.compare(first.getX(), second.getX());
    }

    private static boolean isHiddenDormantLightMarker(BlockState state) {
        return state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.WALL_TORCH);
    }

    private static boolean isUnrecordedRedstoneLightMarker(BlockState state) {
        return state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
    }

    private static void configureIndexVaultMarker(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState configuredState = Blocks.VAULT.defaultBlockState();
        if (state.hasProperty(VaultBlock.FACING)) {
            configuredState = configuredState.setValue(VaultBlock.FACING, state.getValue(VaultBlock.FACING));
        }
        configuredState = configuredState.setValue(VaultBlock.STATE, VaultState.INACTIVE);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, configuredState, 3);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof VaultBlockEntity vault) {
            vault.setConfig(new VaultConfig(
                    BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
                    4.0D,
                    4.5D,
                    new ItemStack(LevelRpgBlocks.INDEX_KEY.get()),
                    Optional.empty()
            ));
            vault.setChanged();
        }
    }

    private static void configureIndexTrialSpawnerMarker(ServerLevel level, BlockPos pos, BlockState state) {
        configureIndexTrialSpawnerMarker(level, pos, state, true);
    }

    private static void configureIndexTrialSpawnerMarker(ServerLevel level, BlockPos pos, BlockState state, boolean dropsIndexKey) {
        BlockState configuredState = Blocks.TRIAL_SPAWNER.defaultBlockState().setValue(TrialSpawnerBlock.STATE, TrialSpawnerState.INACTIVE);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, configuredState, 3);
        applyIndexTrialSpawnerConfig(level, pos, dropsIndexKey);
    }

    private static boolean isVaultRewardFinished(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) {
            return false;
        }
        VaultState vaultState = state.hasProperty(VaultBlock.STATE) ? state.getValue(VaultBlock.STATE) : VaultState.INACTIVE;
        if (vaultState == VaultState.UNLOCKING || vaultState == VaultState.EJECTING) {
            return false;
        }
        CompoundTag tag = vault.saveWithFullMetadata(level.registryAccess());
        CompoundTag serverData = tag.getCompound("server_data");
        if (serverData.getList("rewarded_players", Tag.TAG_INT_ARRAY).isEmpty()) {
            return false;
        }
        if (!serverData.getList("items_to_eject", Tag.TAG_COMPOUND).isEmpty()) {
            return false;
        }
        return serverData.getInt("total_ejections_needed") <= 0;
    }

    private static void applyIndexTrialSpawnerConfig(ServerLevel level, BlockPos pos, boolean dropsIndexKey) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TrialSpawnerBlockEntity trialSpawner) {
            ResourceKey<LootTable> rewardTable = dropsIndexKey ? INDEX_KEY_LOOT_TABLE : BuiltInLootTables.TRIAL_CHAMBERS_REWARD;
            TrialSpawnerConfig config = new TrialSpawnerConfig(
                    4,
                    6.0F,
                    2.0F,
                    2.0F,
                    1.0F,
                    40,
                    indexTrialSpawnPotentials(),
                    SimpleWeightedRandomList.<ResourceKey<LootTable>>builder().add(rewardTable).build(),
                    TrialSpawnerConfig.DEFAULT.itemsToDropWhenOminous()
            );
            CompoundTag tag = trialSpawner.saveWithFullMetadata(level.registryAccess());
            Tag encodedConfig = TrialSpawnerConfig.CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), config)
                    .getOrThrow();
            tag.put("normal_config", encodedConfig);
            tag.put("ominous_config", encodedConfig.copy());
            tag.putInt("target_cooldown_length", Integer.MAX_VALUE);
            trialSpawner.loadWithComponents(tag, level.registryAccess());
            trialSpawner.setChanged();
        }
    }

    private static boolean isTrialSpawnerCompleted(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity trialSpawner)) {
            return false;
        }
        TrialSpawnerState state = trialSpawner.getTrialSpawner().getState();
        return state == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION
                || state == TrialSpawnerState.EJECTING_REWARD
                || state == TrialSpawnerState.COOLDOWN;
    }

    private static BlockPos findRewardPendingTrialSpawner(ServerLevel level, List<BlockPos> trialPositions) {
        BlockPos candidate = null;
        for (BlockPos trialPos : trialPositions) {
            if (!(level.getBlockEntity(trialPos) instanceof TrialSpawnerBlockEntity trialSpawner)) {
                continue;
            }
            TrialSpawnerState state = trialSpawner.getTrialSpawner().getState();
            if (state == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION || state == TrialSpawnerState.EJECTING_REWARD) {
                candidate = trialPos;
            }
        }
        return candidate;
    }

    private static SimpleWeightedRandomList<SpawnData> indexTrialSpawnPotentials() {
        return SimpleWeightedRandomList.<SpawnData>builder()
                .add(spawnData("minecraft:zombie"), 3)
                .add(spawnData("minecraft:skeleton"), 2)
                .add(spawnData("minecraft:spider"), 1)
                .build();
    }

    private static SpawnData spawnData(String entityId) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);
        return new SpawnData(entity, Optional.empty(), Optional.empty());
    }

    public static boolean reconfigureVanillaBlocks(ServerLevel level) {
        IndexPlacementData data = IndexPlacementData.get(level);
        if (!data.chamberPlaced()) {
            return false;
        }
        if (data.vaultPos() != null) {
            level.setBlock(data.vaultPos(), Blocks.VAULT.defaultBlockState(), 3);
            configureIndexVaultMarker(level, data.vaultPos(), level.getBlockState(data.vaultPos()));
        }
        if (data.trialSpawnerPos() != null) {
            data.markFinalTrialSpawner(null);
            for (BlockPos trialPos : data.trialSpawnerPositions()) {
                level.setBlock(trialPos, Blocks.TRIAL_SPAWNER.defaultBlockState(), 3);
                configureIndexTrialSpawnerMarker(level, trialPos, level.getBlockState(trialPos), data.trialSpawnerPositions().size() == 1);
            }
            if (data.trialSpawnerPositions().size() == 1) {
                data.markFinalTrialSpawner(data.trialSpawnerPositions().get(0));
            }
        }
        return true;
    }

    public static List<String> dumpVanillaBlockData(ServerLevel level) {
        IndexPlacementData data = IndexPlacementData.get(level);
        List<String> lines = new ArrayList<>();
        lines.add("chamberPlaced=" + data.chamberPlaced());
        lines.add("chamberActivated=" + data.chamberActivated());
        appendBlockDump(level, lines, "vault", data.vaultPos());
        appendBlockDump(level, lines, "trialSpawner", data.trialSpawnerPos());
        int index = 1;
        for (BlockPos trialPos : data.trialSpawnerPositions()) {
            appendBlockDump(level, lines, "trialSpawner[" + index + "]", trialPos);
            index++;
        }
        return lines;
    }

    private static void appendBlockDump(ServerLevel level, List<String> lines, String label, BlockPos pos) {
        lines.add(label + "Pos=" + pos);
        if (pos == null) {
            return;
        }
        lines.add(label + "State=" + level.getBlockState(pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        lines.add(label + "BlockEntity=" + (blockEntity == null ? "null" : blockEntity.getType()));
        if (blockEntity != null) {
            CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
            lines.add(label + "Nbt=" + tag);
        }
    }

    public record ForcePlaceResult(boolean placed, BlockPos origin, String message) {}

    private enum PlacementTier {
        IDEAL(IDEAL_ATTEMPTS, IDEAL_MAX_AIR_SAMPLE_RATIO, 4, 4, 0x1E0EAL),
        RELAXED(RELAXED_ATTEMPTS, RELAXED_MAX_AIR_SAMPLE_RATIO, 6, 6, 0x2E0EAL),
        FALLBACK(FALLBACK_OFFSETS.length, 1.0D, 8, 8, 0x3E0EAL);

        private final int attempts;
        private final double maxAirSampleRatio;
        private final int horizontalSampleStep;
        private final int verticalSampleStep;
        private final long seedSalt;

        PlacementTier(int attempts, double maxAirSampleRatio, int horizontalSampleStep, int verticalSampleStep, long seedSalt) {
            this.attempts = attempts;
            this.maxAirSampleRatio = maxAirSampleRatio;
            this.horizontalSampleStep = horizontalSampleStep;
            this.verticalSampleStep = verticalSampleStep;
            this.seedSalt = seedSalt;
        }

        private int attempts() {
            return attempts;
        }

        private double maxAirSampleRatio() {
            return maxAirSampleRatio;
        }

        private int horizontalSampleStep() {
            return horizontalSampleStep;
        }

        private int verticalSampleStep() {
            return verticalSampleStep;
        }

        private long seedSalt() {
            return seedSalt;
        }

        private int sampleCount(Vec3i size) {
            int xSamples = ((size.getX() - 1) / horizontalSampleStep) + 1;
            int ySamples = ((size.getY() - 1) / verticalSampleStep) + 1;
            int zSamples = ((size.getZ() - 1) / horizontalSampleStep) + 1;
            return xSamples * ySamples * zSamples;
        }
    }

    private record PlacementResult(BlockPos origin, int attempts, int airBlocks, PlacementTier tier) {}

    private record CandidateScan(boolean accepted, int airBlocks) {
        static CandidateScan rejected() {
            return new CandidateScan(false, 0);
        }
    }

    private record MarkerScanResult(int vaultCount, int trialCount, int coreCount, int barrierCount, int lightCount, int powerCount) {
        boolean requiredMarkersPresent() {
            return vaultCount > 0 && trialCount > 0 && coreCount > 0;
        }
    }

    private record SableAssemblyPair(String label, BlockPos cornerA, BlockPos cornerB, int yOffset) {
    }

    private record PendingSableAssembly(ResourceKey<Level> dimension, long dueGameTime, List<SableAssemblyPair> pairs, List<BlockPos> experimentalObsidianMarkerAssemblies, BoundingBox bounds) {
    }

    private record PendingKineticRefresh(ResourceKey<Level> dimension, long dueGameTime, BoundingBox bounds) {
    }

    private static final class PlacementStats {
        private int attempts;
        private int outOfBounds;
        private int tooCloseToSurface;
        private int skyExposed;
        private int fluidFound;
        private int tooMuchAir;
        private int invalidSolidVolume;
        private int placementFailed;
        private int markerScanFailed;
        private int idealAttempts;
        private int relaxedAttempts;
        private int fallbackAttempts;

        private void incrementTierAttempt(PlacementTier tier) {
            if (tier == PlacementTier.IDEAL) {
                idealAttempts++;
            } else if (tier == PlacementTier.RELAXED) {
                relaxedAttempts++;
            } else {
                fallbackAttempts++;
            }
        }

        private String summary() {
            return "attempts=" + attempts
                    + " tierAttempts[ideal=" + idealAttempts
                    + ", relaxed=" + relaxedAttempts
                    + ", fallback=" + fallbackAttempts
                    + "]"
                    + " rejects[outOfBounds=" + outOfBounds
                    + ", tooCloseToSurface=" + tooCloseToSurface
                    + ", skyExposed=" + skyExposed
                    + ", fluidFound=" + fluidFound
                    + ", tooMuchAir=" + tooMuchAir
                    + ", invalidSolidVolume=" + invalidSolidVolume
                    + ", placementFailed=" + placementFailed
                    + ", markerScanFailed=" + markerScanFailed
                    + "] thresholds[y=" + MIN_Y + "-" + MAX_Y
                    + ", radius=" + SEARCH_MIN_RADIUS + "-" + SEARCH_MAX_RADIUS
                    + ", idealAttempts=" + IDEAL_ATTEMPTS
                    + ", relaxedAttempts=" + RELAXED_ATTEMPTS
                    + ", idealAirSampleRatio=" + IDEAL_MAX_AIR_SAMPLE_RATIO
                    + ", relaxedAirSampleRatio=" + RELAXED_MAX_AIR_SAMPLE_RATIO
                    + ", surfaceClearance=" + SURFACE_CLEARANCE
                    + ", relaxedSurfaceClearance=" + RELAXED_SURFACE_CLEARANCE
                    + "]";
        }
    }
}
