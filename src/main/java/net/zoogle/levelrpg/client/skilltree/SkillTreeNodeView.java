package net.zoogle.levelrpg.client.skilltree;

import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;

public record SkillTreeNodeView(SkillTreeNodeDefinition definition, SkillNodeStatus status, int x, int y, int size, boolean rendered, boolean revealed, boolean obfuscated) {
    public int left(SkillTreeCameraTransform transform) {
        return transform.graphToScreenX(x - size / 2.0);
    }

    public int top(SkillTreeCameraTransform transform) {
        return transform.graphToScreenY(y - size / 2.0);
    }

    public int centerX(SkillTreeCameraTransform transform) {
        return transform.graphToScreenX(x);
    }

    public int centerY(SkillTreeCameraTransform transform) {
        return transform.graphToScreenY(y);
    }

    public int scaledSize(SkillTreeCameraTransform transform) {
        return transform.scaledSize(size);
    }

    public boolean contains(double mouseX, double mouseY, SkillTreeCameraTransform transform) {
        if (!rendered) {
            return false;
        }
        double graphX = transform.screenToGraphX(mouseX);
        double graphY = transform.screenToGraphY(mouseY);
        double half = size / 2.0;
        return graphX >= x - half && graphX < x + half && graphY >= y - half && graphY < y + half;
    }
}
