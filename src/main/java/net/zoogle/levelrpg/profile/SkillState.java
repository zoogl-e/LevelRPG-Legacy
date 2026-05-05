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
     * Practice-facing passive rank. Gameplay actions raise this automatically.
     */
    public int rank;
    /**
     * Practice-facing proficiency progress toward the next rank.
     */
    public long proficiency;

    public SkillState() {
        this(0, 0, 0L);
    }

    public SkillState(int level, int rank, long proficiency) {
        this.level = Math.max(0, level);
        this.rank = Math.max(0, rank);
        this.proficiency = Math.max(0L, proficiency);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("rank", rank);
        tag.putLong("proficiency", proficiency);
        // Legacy mirrors for older readers.
        tag.putInt("masteryLevel", rank);
        tag.putLong("masteryXp", proficiency);
        tag.putLong("xp", proficiency);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.level = Math.max(0, tag.getInt("level"));
        if (tag.contains("rank")) {
            this.rank = Math.max(0, tag.getInt("rank"));
        } else if (tag.contains("masteryLevel")) {
            // Legacy migration: old masteryLevel becomes rank.
            this.rank = Math.max(0, tag.getInt("masteryLevel"));
        } else {
            this.rank = this.level;
        }
        if (tag.contains("proficiency")) {
            this.proficiency = Math.max(0L, tag.getLong("proficiency"));
        } else if (tag.contains("masteryXp")) {
            this.proficiency = Math.max(0L, tag.getLong("masteryXp"));
        } else {
            this.proficiency = Math.max(0L, tag.getLong("xp"));
        }
    }

    public SkillState copy() {
        return new SkillState(level, rank, proficiency);
    }
}
