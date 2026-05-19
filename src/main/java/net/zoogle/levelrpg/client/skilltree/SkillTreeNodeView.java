package net.zoogle.levelrpg.client.skilltree;

import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;

public record SkillTreeNodeView(SkillTreeNodeDefinition definition, SkillNodeStatus status, int x, int y, int size, boolean rendered, boolean revealed, boolean obfuscated) {

    /**
     * Extra pixels the visual frame sprite extends beyond the base node box on each side.
     * Must match the {@code frameInflate} values in {@code SkillTreeRenderer.styleFor}.
     * Adding 2 for the fixed blitSprite inset that the renderer always applies.
     */
    private int frameInflatePx() {
        String type = definition.type() == null ? "" : definition.type().trim().toLowerCase();
        int inflate = switch (type) {
            case "core"          -> 5;
            case "technique"     -> 2;
            case "axiom"         -> 3;
            case "manifestation" -> 6;
            default              -> 0;
        };
        return inflate + 2; // +2 for the fixed blitSprite offset in drawNode
    }

    public int visualPadding() {
        return frameInflatePx();
    }

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

    /**
     * Returns true when the given screen-space point is within the node's
     * visual frame sprite (including the type-specific frame inflation).
     * The hit box exactly matches what the player sees on screen.
     */
    public boolean contains(double mouseX, double mouseY, SkillTreeCameraTransform transform) {
        if (!rendered) {
            return false;
        }
        // Work in screen space so zoom-scaled inflation is naturally handled.
        int screenLeft = left(transform);
        int screenTop  = top(transform);
        int screenSize = scaledSize(transform);

        // The frame sprite is drawn with this many extra pixels on each side.
        int pad = (int) Math.round(frameInflatePx() * transform.zoom());

        int hitLeft  = screenLeft - pad;
        int hitTop   = screenTop  - pad;
        int hitRight  = screenLeft + screenSize + pad;
        int hitBottom = screenTop  + screenSize + pad;

        return mouseX >= hitLeft && mouseX < hitRight
            && mouseY >= hitTop  && mouseY < hitBottom;
    }
}
