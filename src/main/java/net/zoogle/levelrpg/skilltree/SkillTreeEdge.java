package net.zoogle.levelrpg.skilltree;

public record SkillTreeEdge(String parentId, String childId) {
    public SkillTreeEdge {
        parentId = parentId == null ? "" : parentId.trim();
        childId = childId == null ? "" : childId.trim();
    }
}
