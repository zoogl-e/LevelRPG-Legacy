package net.zoogle.levelrpg.data;

/**
 * Optional semantic layout metadata for a skill-tree node.
 *
 * <p>Manual {@code x/y} values are explicit graph coordinates. {@code branch/tier/lane} are semantic
 * hints used to generate deterministic coordinates when no manual coordinates are present. Legacy
 * top-level {@code layoutX/layoutY} remains supported separately for older datapacks.
 */
public record SkillTreeNodeLayout(
        String branch,
        Integer tier,
        Integer lane,
        Integer x,
        Integer y
) {
    public static final SkillTreeNodeLayout EMPTY = new SkillTreeNodeLayout("", null, null, null, null);

    public SkillTreeNodeLayout {
        branch = branch == null ? "" : branch.trim();
    }

    public boolean hasManualPosition() {
        return x != null && y != null;
    }

    public boolean hasSemanticPosition() {
        return !branch.isBlank() || tier != null || lane != null;
    }

    public int semanticTier() {
        return tier == null ? 0 : tier;
    }

    public int semanticLane() {
        return lane == null ? 0 : lane;
    }
}
