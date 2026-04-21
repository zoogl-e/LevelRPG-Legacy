package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;

/**
 * Explicit per-skill progression state. This is the canonical profile-layer
 * state used by progression, sync, UI, and recipe unlock checks.
 */
public class SkillState {
    /**
     * Build-facing invested level. Players raise this by spending earned skill points.
     */
    public int level;
    /**
     * Practice-facing passive mastery rank. Gameplay actions raise this automatically.
     */
    public int masteryLevel;
    /**
     * Practice-facing mastery progress toward the next mastery level.
     */
    public long masteryXp;

    public SkillState() {
        this(0, 0, 0L);
    }

    public SkillState(int level, int masteryLevel, long masteryXp) {
        this.level = Math.max(0, level);
        this.masteryLevel = Math.max(0, masteryLevel);
        this.masteryXp = Math.max(0L, masteryXp);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("masteryLevel", masteryLevel);
        tag.putLong("masteryXp", masteryXp);
        // Legacy mirror for older readers that still expect xp to exist.
        tag.putLong("xp", masteryXp);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.level = Math.max(0, tag.getInt("level"));
        if (tag.contains("masteryLevel")) {
            this.masteryLevel = Math.max(0, tag.getInt("masteryLevel"));
        } else {
            // Legacy migration: old practiced level becomes mastery level.
            this.masteryLevel = this.level;
        }
        if (tag.contains("masteryXp")) {
            this.masteryXp = Math.max(0L, tag.getLong("masteryXp"));
        } else {
            this.masteryXp = Math.max(0L, tag.getLong("xp"));
        }
    }

    public SkillState copy() {
        return new SkillState(level, masteryLevel, masteryXp);
    }
}
