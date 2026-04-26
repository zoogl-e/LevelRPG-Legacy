package net.zoogle.levelrpg.data;

/**
 * How a mastery node is presented in the panning skill map (advancement-style).
 */
public enum SkillTreeNodeVisibility {
    /** Always shown with normal title and tooltip. */
    NORMAL,
    /**
     * Hidden until the branch is discovered: shown once any direct prerequisite is unlocked,
     * or immediately for roots once the tree's {@code minSkillLevel} is met.
     */
    HIDDEN,
    /**
     * Frame is visible but the name (and tooltip title) read as {@code ???} until the node is
     * {@link net.zoogle.levelrpg.progression.SkillTreeProgression.NodeStatus#AVAILABLE} or unlocked.
     */
    OBFUSCATED;

    public static SkillTreeNodeVisibility fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "hidden" -> HIDDEN;
            case "obfuscated", "secret" -> OBFUSCATED;
            default -> NORMAL;
        };
    }
}
