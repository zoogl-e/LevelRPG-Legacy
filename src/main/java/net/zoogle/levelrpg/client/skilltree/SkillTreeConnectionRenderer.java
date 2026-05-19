package net.zoogle.levelrpg.client.skilltree;

import net.minecraft.client.gui.GuiGraphics;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;

/**
 * Draws visual-only skill tree connections. Input, unlock logic, and edge topology stay owned by the
 * tree renderer/state resolver.
 */
public final class SkillTreeConnectionRenderer {
    private SkillTreeConnectionRenderer() {
    }

    public static void draw(
            GuiGraphics graphics,
            SkillTreeNodeView parent,
            SkillTreeNodeView child,
            SkillTreeCameraTransform transform,
            boolean highlighted,
            float opacity
    ) {
        draw(graphics, parent, child, transform, highlighted, opacity, 0, 0, 0, 0);
    }

    public static void draw(
            GuiGraphics graphics,
            SkillTreeNodeView parent,
            SkillTreeNodeView child,
            SkillTreeCameraTransform transform,
            boolean highlighted,
            float opacity,
            int parentOffsetX,
            int parentOffsetY,
            int childOffsetX,
            int childOffsetY
    ) {
        int x1 = parent.centerX(transform) + parentOffsetX;
        int y1 = parent.centerY(transform) + parentOffsetY;
        int x2 = child.centerX(transform) + childOffsetX;
        int y2 = child.centerY(transform) + childOffsetY;
        int thickness = clamp((int) Math.round(3 * transform.zoom()), 2, 5);
        int casingThickness = clamp((int) Math.round(5 * transform.zoom()), thickness + 2, 7);

        graphics.pose().pushPose();
        drawBlockPath(graphics, x1 + 2, y1 + 2, x2 + 2, y2 + 2, applyOpacity(0x77000000, opacity), casingThickness);
        drawBlockPath(graphics, x1, y1, x2, y2, applyOpacity(connectionCasingColor(parent, child, highlighted), opacity), casingThickness);
        drawBlockPath(graphics, x1, y1, x2, y2, applyOpacity(connectionCoreColor(parent, child, highlighted), opacity), thickness);
        graphics.pose().popPose();
    }

    private static int connectionCoreColor(SkillTreeNodeView parent, SkillTreeNodeView child, boolean highlighted) {
        if (highlighted) {
            return 0xFFFFF3B8;
        }
        if (parent.status() == SkillNodeStatus.INSCRIBED && child.status() == SkillNodeStatus.INSCRIBED) {
            return 0xFFE6C35A;
        }
        if (parent.status() == SkillNodeStatus.INSCRIBED && child.status() == SkillNodeStatus.AVAILABLE) {
            return 0xFF9EC7DB;
        }
        return 0x88576A78;
    }

    private static int connectionCasingColor(SkillTreeNodeView parent, SkillTreeNodeView child, boolean highlighted) {
        if (highlighted) {
            return 0xCC5B4325;
        }
        if (parent.status() == SkillNodeStatus.INSCRIBED && child.status() == SkillNodeStatus.INSCRIBED) {
            return 0xAA5A4623;
        }
        if (parent.status() == SkillNodeStatus.INSCRIBED && child.status() == SkillNodeStatus.AVAILABLE) {
            return 0xAA273B4A;
        }
        return 0x88202A32;
    }

    private static void drawBlockPath(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (Math.abs(x2 - x1) < 3 || Math.abs(y2 - y1) < 3) {
            drawSegment(graphics, x1, y1, x2, y2, color, thickness);
            return;
        }
        int midY = y1 + (y2 - y1) / 2;
        drawSegment(graphics, x1, y1, x1, midY, color, thickness);
        drawSegment(graphics, x1, midY, x2, midY, color, thickness);
        drawSegment(graphics, x2, midY, x2, y2, color, thickness);
    }

    private static void drawSegment(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            drawCap(graphics, x1, y1, color, thickness);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            drawCap(graphics, x, y, color, thickness);
        }
    }

    private static void drawCap(GuiGraphics graphics, int centerX, int centerY, int color, int thickness) {
        int half = Math.max(1, thickness / 2);
        graphics.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, color);
    }

    private static int applyOpacity(int color, float opacity) {
        if (opacity >= 0.999f) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int fadedAlpha = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0f, Math.min(1.0f, opacity)))));
        return (fadedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
