package net.zoogle.levelrpg.client.skilltree;

/**
 * Controls which behavior layer is enabled for the shared LevelRPG skill tree view.
 */
public enum SkillTreeViewMode {
    /** Player-facing tree opened from Enchiridion or other journal/index surfaces. */
    PLAYER_VIEW,
    /** Developer/debug tree with JSON editing controls enabled. */
    EDITOR
}
