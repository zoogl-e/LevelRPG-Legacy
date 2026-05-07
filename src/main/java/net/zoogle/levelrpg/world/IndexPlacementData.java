package net.zoogle.levelrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class IndexPlacementData extends SavedData {
    public static final String DATA_KEY = "levelrpg_index_placement";
    private static final String NBT_PLACED = "placed";
    private static final String NBT_X = "x";
    private static final String NBT_Y = "y";
    private static final String NBT_Z = "z";

    private boolean placed;
    private BlockPos originalIndexPos;

    public static SavedData.Factory<IndexPlacementData> factory() {
        return new SavedData.Factory<>(IndexPlacementData::new, IndexPlacementData::load);
    }

    public static IndexPlacementData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_KEY);
    }

    private static IndexPlacementData load(CompoundTag tag, HolderLookup.Provider registries) {
        IndexPlacementData data = new IndexPlacementData();
        data.placed = tag.getBoolean(NBT_PLACED);
        if (tag.contains(NBT_X) && tag.contains(NBT_Y) && tag.contains(NBT_Z)) {
            data.originalIndexPos = new BlockPos(tag.getInt(NBT_X), tag.getInt(NBT_Y), tag.getInt(NBT_Z));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(NBT_PLACED, placed);
        if (originalIndexPos != null) {
            tag.putInt(NBT_X, originalIndexPos.getX());
            tag.putInt(NBT_Y, originalIndexPos.getY());
            tag.putInt(NBT_Z, originalIndexPos.getZ());
        }
        return tag;
    }

    public boolean placed() {
        return placed;
    }

    public BlockPos originalIndexPos() {
        return originalIndexPos;
    }

    public void markPlaced(BlockPos pos) {
        this.placed = true;
        this.originalIndexPos = pos;
        setDirty();
    }
}

