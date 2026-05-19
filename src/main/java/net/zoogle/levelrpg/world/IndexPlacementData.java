package net.zoogle.levelrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class IndexPlacementData extends SavedData {
    public static final String DATA_KEY = "levelrpg_index_placement";
    private static final String NBT_CHAMBER_PLACED = "chamberPlaced";
    private static final String NBT_CHAMBER_ACTIVATED = "chamberActivated";
    private static final String NBT_TRIAL_COMPLETED = "trialCompleted";
    private static final String NBT_TRIAL_REWARD_DROPPED = "trialRewardDropped";
    private static final String NBT_ORIGIN = "chamberOrigin";
    private static final String NBT_SIZE = "structureSize";
    private static final String NBT_VAULT = "vaultPos";
    private static final String NBT_TRIAL = "trialSpawnerPos";
    private static final String NBT_TRIALS = "trialSpawnerPositions";
    private static final String NBT_FINAL_TRIAL = "finalTrialSpawnerPos";
    private static final String NBT_DORMANT_CORE = "dormantCorePos";
    private static final String NBT_ACTIVE_INDEX = "activeIndexPos";
    private static final String NBT_PLACEMENT_TIER = "placementTier";
    private static final String NBT_BARRIERS = "barrierPositions";
    private static final String NBT_LIGHTS = "lightMarkerPositions";
    private static final String NBT_POWER = "activationPowerMarkerPositions";
    private static final String NBT_LEGACY_PLACED = "placed";
    private static final String NBT_LEGACY_X = "x";
    private static final String NBT_LEGACY_Y = "y";
    private static final String NBT_LEGACY_Z = "z";

    private boolean chamberPlaced;
    private boolean chamberActivated;
    private boolean trialCompleted;
    private boolean trialRewardDropped;
    private BlockPos chamberOrigin;
    private BlockPos structureSize;
    private BlockPos vaultPos;
    private BlockPos trialSpawnerPos;
    private BlockPos finalTrialSpawnerPos;
    private BlockPos dormantCorePos;
    private BlockPos activeIndexPos;
    private String placementTier = "unknown";
    private final List<BlockPos> barrierPositions = new ArrayList<>();
    private final List<BlockPos> trialSpawnerPositions = new ArrayList<>();
    private final List<BlockPos> lightMarkerPositions = new ArrayList<>();
    private final List<BlockPos> activationPowerMarkerPositions = new ArrayList<>();

    public static SavedData.Factory<IndexPlacementData> factory() {
        return new SavedData.Factory<>(IndexPlacementData::new, IndexPlacementData::load);
    }

    public static IndexPlacementData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_KEY);
    }

    private static IndexPlacementData load(CompoundTag tag, HolderLookup.Provider registries) {
        IndexPlacementData data = new IndexPlacementData();
        data.chamberPlaced = tag.getBoolean(NBT_CHAMBER_PLACED) || tag.getBoolean(NBT_LEGACY_PLACED);
        data.chamberActivated = tag.getBoolean(NBT_CHAMBER_ACTIVATED);
        data.trialCompleted = tag.getBoolean(NBT_TRIAL_COMPLETED);
        data.trialRewardDropped = tag.getBoolean(NBT_TRIAL_REWARD_DROPPED);
        data.chamberOrigin = readPos(tag, NBT_ORIGIN);
        data.structureSize = readPos(tag, NBT_SIZE);
        data.vaultPos = readPos(tag, NBT_VAULT);
        data.trialSpawnerPos = readPos(tag, NBT_TRIAL);
        data.finalTrialSpawnerPos = readPos(tag, NBT_FINAL_TRIAL);
        data.dormantCorePos = readPos(tag, NBT_DORMANT_CORE);
        data.activeIndexPos = readPos(tag, NBT_ACTIVE_INDEX);
        data.placementTier = tag.getString(NBT_PLACEMENT_TIER);
        if (data.placementTier == null || data.placementTier.isBlank()) {
            data.placementTier = "unknown";
        }
        data.barrierPositions.addAll(readPosList(tag, NBT_BARRIERS));
        data.trialSpawnerPositions.addAll(readPosList(tag, NBT_TRIALS));
        if (data.trialSpawnerPositions.isEmpty() && data.trialSpawnerPos != null) {
            data.trialSpawnerPositions.add(data.trialSpawnerPos);
        }
        data.lightMarkerPositions.addAll(readPosList(tag, NBT_LIGHTS));
        data.activationPowerMarkerPositions.addAll(readPosList(tag, NBT_POWER));
        if (data.chamberOrigin == null && tag.contains(NBT_LEGACY_X) && tag.contains(NBT_LEGACY_Y) && tag.contains(NBT_LEGACY_Z)) {
            data.chamberOrigin = new BlockPos(tag.getInt(NBT_LEGACY_X), tag.getInt(NBT_LEGACY_Y), tag.getInt(NBT_LEGACY_Z));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(NBT_CHAMBER_PLACED, chamberPlaced);
        tag.putBoolean(NBT_CHAMBER_ACTIVATED, chamberActivated);
        tag.putBoolean(NBT_TRIAL_COMPLETED, trialCompleted);
        tag.putBoolean(NBT_TRIAL_REWARD_DROPPED, trialRewardDropped);
        writePos(tag, NBT_ORIGIN, chamberOrigin);
        writePos(tag, NBT_SIZE, structureSize);
        writePos(tag, NBT_VAULT, vaultPos);
        writePos(tag, NBT_TRIAL, trialSpawnerPos);
        writePos(tag, NBT_FINAL_TRIAL, finalTrialSpawnerPos);
        writePos(tag, NBT_DORMANT_CORE, dormantCorePos);
        writePos(tag, NBT_ACTIVE_INDEX, activeIndexPos);
        tag.putString(NBT_PLACEMENT_TIER, placementTier);
        tag.put(NBT_BARRIERS, writePosList(barrierPositions));
        tag.put(NBT_TRIALS, writePosList(trialSpawnerPositions));
        tag.put(NBT_LIGHTS, writePosList(lightMarkerPositions));
        tag.put(NBT_POWER, writePosList(activationPowerMarkerPositions));
        return tag;
    }

    public boolean placed() {
        return chamberPlaced;
    }

    public boolean chamberPlaced() {
        return chamberPlaced;
    }

    public boolean chamberActivated() {
        return chamberActivated;
    }

    public boolean trialCompleted() {
        return trialCompleted;
    }

    public boolean trialRewardDropped() {
        return trialRewardDropped;
    }

    public BlockPos originalIndexPos() {
        return locatePos();
    }

    public BlockPos chamberOrigin() {
        return chamberOrigin;
    }

    public BlockPos structureSize() {
        return structureSize;
    }

    public BlockPos vaultPos() {
        return vaultPos;
    }

    public BlockPos trialSpawnerPos() {
        return trialSpawnerPos;
    }

    public List<BlockPos> trialSpawnerPositions() {
        return List.copyOf(trialSpawnerPositions);
    }

    public BlockPos finalTrialSpawnerPos() {
        return finalTrialSpawnerPos;
    }

    public BlockPos dormantCorePos() {
        return dormantCorePos;
    }

    public BlockPos activeIndexPos() {
        return activeIndexPos;
    }

    public String placementTier() {
        return placementTier;
    }

    public List<BlockPos> barrierPositions() {
        return List.copyOf(barrierPositions);
    }

    public List<BlockPos> lightMarkerPositions() {
        return List.copyOf(lightMarkerPositions);
    }

    public List<BlockPos> activationPowerMarkerPositions() {
        return List.copyOf(activationPowerMarkerPositions);
    }

    public BlockPos locatePos() {
        if (chamberActivated && activeIndexPos != null) {
            return activeIndexPos;
        }
        if (vaultPos != null) {
            return vaultPos;
        }
        return chamberOrigin;
    }

    public void markPlaced(BlockPos pos) {
        this.chamberPlaced = true;
        this.chamberOrigin = pos;
        setDirty();
    }

    public void recordChamber(
            BlockPos origin,
            BlockPos size,
            BlockPos vaultPos,
            BlockPos trialSpawnerPos,
            BlockPos dormantCorePos,
            BlockPos activeIndexPos,
            List<BlockPos> barrierPositions,
            List<BlockPos> lightMarkerPositions,
            List<BlockPos> activationPowerMarkerPositions
    ) {
        List<BlockPos> trialPositions = trialSpawnerPos == null ? List.of() : List.of(trialSpawnerPos);
        recordChamber(origin, size, vaultPos, trialSpawnerPos, dormantCorePos, activeIndexPos, barrierPositions, trialPositions, lightMarkerPositions, activationPowerMarkerPositions, "unknown");
    }

    public void recordChamber(
            BlockPos origin,
            BlockPos size,
            BlockPos vaultPos,
            BlockPos trialSpawnerPos,
            BlockPos dormantCorePos,
            BlockPos activeIndexPos,
            List<BlockPos> barrierPositions,
            List<BlockPos> trialSpawnerPositions,
            List<BlockPos> lightMarkerPositions,
            List<BlockPos> activationPowerMarkerPositions,
            String placementTier
    ) {
        this.chamberPlaced = true;
        this.chamberActivated = false;
        this.trialCompleted = false;
        this.trialRewardDropped = false;
        this.chamberOrigin = origin;
        this.structureSize = size;
        this.vaultPos = vaultPos;
        this.trialSpawnerPos = trialSpawnerPos;
        this.finalTrialSpawnerPos = null;
        this.dormantCorePos = dormantCorePos;
        this.activeIndexPos = activeIndexPos;
        this.placementTier = placementTier == null || placementTier.isBlank() ? "unknown" : placementTier;
        this.barrierPositions.clear();
        this.barrierPositions.addAll(barrierPositions);
        this.trialSpawnerPositions.clear();
        this.trialSpawnerPositions.addAll(trialSpawnerPositions);
        if (this.trialSpawnerPositions.isEmpty() && trialSpawnerPos != null) {
            this.trialSpawnerPositions.add(trialSpawnerPos);
        }
        this.lightMarkerPositions.clear();
        this.lightMarkerPositions.addAll(lightMarkerPositions);
        this.activationPowerMarkerPositions.clear();
        this.activationPowerMarkerPositions.addAll(activationPowerMarkerPositions);
        setDirty();
    }

    public void markActivated(BlockPos activeIndexPos) {
        this.chamberActivated = true;
        this.activeIndexPos = activeIndexPos;
        setDirty();
    }

    public void markTrialCompleted() {
        this.trialCompleted = true;
        setDirty();
    }

    public void markTrialRewardDropped() {
        this.trialRewardDropped = true;
        setDirty();
    }

    public void markFinalTrialSpawner(BlockPos finalTrialSpawnerPos) {
        this.finalTrialSpawnerPos = finalTrialSpawnerPos;
        setDirty();
    }

    private static void writePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private static ListTag writePosList(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            list.add(entry);
        }
        return list;
    }

    private static List<BlockPos> readPosList(CompoundTag tag, String key) {
        List<BlockPos> positions = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("pos", Tag.TAG_LONG)) {
                positions.add(BlockPos.of(entry.getLong("pos")));
            }
        }
        return positions;
    }
}
