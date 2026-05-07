package net.zoogle.levelrpg.bounty;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Parameters for a bounty objective. Intended to become the JSON-facing shape later.
 * TODO(bounties): JSON load — may later add operators, min/max ranges, multi-step arrays, filters.
 */
public record BountyObjectiveSpec(
        BountyObjectiveType type,
        int count,
        int targetY,
        @Nullable ResourceLocation targetId,
        @Nullable String descriptionOverride
) {
    public BountyObjectiveSpec {
        type = Objects.requireNonNullElse(type, BountyObjectiveType.NONE);
    }

    public static BountyObjectiveSpec none() {
        return new BountyObjectiveSpec(BountyObjectiveType.NONE, 0, 0, null, null);
    }

    public static BountyObjectiveSpec indexInvestOnce() {
        return new BountyObjectiveSpec(BountyObjectiveType.INDEX_INVEST_ONCE, 1, 0, null, null);
    }

    public static BountyObjectiveSpec reachY(int y) {
        return new BountyObjectiveSpec(BountyObjectiveType.REACH_Y, 1, y, null, null);
    }

    public static BountyObjectiveSpec killHostileMob(int count) {
        return new BountyObjectiveSpec(BountyObjectiveType.KILL_HOSTILE_MOB, Math.max(1, count), 0, null, null);
    }

    /**
     * @param maxOreY inclusive; ore blocks above this Y do not count. Use {@code 0} for no depth limit.
     */
    public static BountyObjectiveSpec mineOre(int count, int maxOreY) {
        return new BountyObjectiveSpec(BountyObjectiveType.MINE_ORE, Math.max(1, count), maxOreY, null, null);
    }
}
