package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;

/**
 * Explicit per-skill progression state. This is the canonical profile-layer
 * state used by progression, sync, UI, and recipe unlock checks.
 */
public class SkillState {
    public int level;
    public long xp;

    public SkillState() {
        this(0, 0L);
    }

    public SkillState(int level, long xp) {
        this.level = Math.max(0, level);
        this.xp = Math.max(0L, xp);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putLong("xp", xp);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.level = Math.max(0, tag.getInt("level"));
        this.xp = Math.max(0L, tag.getLong("xp"));
    }

    public SkillState copy() {
        return new SkillState(level, xp);
    }
}
