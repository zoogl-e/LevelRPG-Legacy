package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-discipline progression state (Java/API name still “skill” for compatibility). Canonical profile-layer
 * state used by progression, sync, UI, and recipe unlock checks.
 *
 * <p>Semantic mapping (see {@code docs/LEVELRPG_PROGRESSION_DESIGN.md}): {@link #proficiency} is <b>practice
 * progress</b>; {@link #rank} is <b>practice rank</b> (temporary stand-in for future per-discipline
 * <b>Potential</b>); {@link #level} is invested <b>Discipline Level</b>. {@link #rank} is not Discipline Level.
 */
public class SkillState {
    /**
     * Invested Discipline Level (chosen build). Raised by spending the profile’s discipline-investment pool
     * ({@link LevelProfile#availableSkillPoints} / {@link LevelProfile#uncommittedDisciplineInvestmentPoints()}),
     * which is <b>not</b> design Essence.
     */
    public int level;
    /**
     * Practice rank (organic track); NBT mirrors include {@code masteryLevel}. Not {@link #level}.
     */
    public int rank;
    /**
     * Practice progress toward the next practice rank; NBT mirrors include {@code masteryXp}.
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
