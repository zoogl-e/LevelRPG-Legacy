package net.zoogle.levelrpg.client.ui.skilltree.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>Editor-only presentation logic for skill tree navigation.</b>
 *
 * <p>Pure-draw companion to {@link SkillChamberViewState}: renders the revolving chamber
 * background, skill-name labels, directional hints, and spoke lines for the wheel UI embedded
 * inside {@link net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen}.
 *
 * <p>This renderer has no state of its own; it receives a {@code SkillChamberViewState} snapshot
 * on every {@link #render} call. It is used exclusively by the developer/editor tool and is
 * <em>not</em> part of the player-facing Enchiridion projection pipeline.
 *
 * @see SkillChamberViewState
 * @see net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen
 */
public final class SkillChamberRenderer {
    public void render(
            GuiGraphics graphics,
            Font font,
            SkillChamberViewState chamber,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH
    ) {
        SkillChamberViewState.Viewport viewport = new SkillChamberViewState.Viewport(viewportX, viewportY, viewportW, viewportH);
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        drawChamberBackground(graphics, viewport);
        drawRotationHints(graphics, font, chamber, viewport);
        graphics.disableScissor();
    }

    private static void drawChamberBackground(GuiGraphics graphics, SkillChamberViewState.Viewport viewport) {
        int x = viewport.x();
        int y = viewport.y();
        int w = viewport.width();
        int h = viewport.height();
        // Warm parchment substrate (ink-on-paper direction).
        graphics.fill(x, y, x + w, y + h, 0xE8D8C1A0);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xEFDCC8AE);

        // Intentionally no inner translucent frame; keep viewport open for tree/grid readability.
    }

    private static void drawSkillSpokes(GuiGraphics graphics, SkillChamberViewState chamber, SkillChamberViewState.Viewport viewport) {
        int centerX = SkillChamberViewState.wheelCenterScreenX(viewport);
        int centerY = SkillChamberViewState.wheelCenterScreenY(viewport);
        int radius = SkillChamberViewState.wheelRadiusPixels(viewport);
        for (ResourceLocation skill : chamber.orderedSkills()) {
            double angle = chamber.getSkillAngle(skill);
            int endX = centerX + (int) Math.round(Math.sin(angle) * radius);
            int endY = centerY + (int) Math.round(Math.cos(angle) * radius);
            int color = skill.equals(chamber.getFocusedSkill()) ? 0xCCFFD66E : 0x665F4A31;
            drawLine(graphics, centerX, centerY, endX, endY, color, skill.equals(chamber.getFocusedSkill()) ? 2 : 1);
        }
    }

    private static void drawSkillLabels(GuiGraphics graphics, Font font, SkillChamberViewState chamber, SkillChamberViewState.Viewport viewport) {
        for (ResourceLocation skill : chamber.orderedSkills()) {
            SkillChamberViewState.ScreenPosition position = chamber.getSkillScreenPosition(skill, viewport);
            double centerWeight = Math.max(0.0, 1.0 - Math.min(1.0, Math.abs(position.angle()) / chamber.sectorAngle()));
            boolean focused = skill.equals(chamber.getFocusedSkill());
            String label = label(skill);
            int labelW = font.width(label);
            int x = position.x() - labelW / 2;
            int y = position.y() - 5;
            int pad = focused ? 7 : 5;
            int frame = focused ? 0xCCFFD66E : 0x775F4A31;
            int fill = focused ? 0xCC3D2B18 : 0x88201916;
            int color = focused ? 0xFFFFE0A3 : lerpColor(0xFF8C8378, 0xFFD9C18C, centerWeight);
            graphics.fill(x - pad, y - 4, x + labelW + pad, y + 14, frame);
            graphics.fill(x - pad + 1, y - 3, x + labelW + pad - 1, y + 13, fill);
            graphics.drawString(font, label, x, y, color, false);
        }
    }

    private static void drawFocusedSkillFrame(GuiGraphics graphics, SkillChamberViewState.Viewport viewport, boolean rotating) {
        int x = viewport.x() + 22;
        int y = viewport.y() + 30;
        int w = viewport.width() - 44;
        int h = viewport.height() - 52;
        int color = rotating ? 0xFF7E5A2A : 0xFF5B3F1D;
        graphics.hLine(x, x + w, y, color);
        graphics.hLine(x, x + w, y + h, 0xFF735633);
        graphics.vLine(x, y, y + h, color);
        graphics.vLine(x + w, y, y + h, 0xFF735633);
    }

    private static void drawRotationHints(GuiGraphics graphics, Font font, SkillChamberViewState chamber, SkillChamberViewState.Viewport viewport) {
        String left = "< A " + label(chamber.getPreviousSkill());
        String right = label(chamber.getNextSkill()) + " D >";
        int y = viewport.y() + viewport.height() - 17;
        graphics.drawString(font, left, viewport.x() + 28, y, 0xFF5A4020, false);
        graphics.drawString(font, right, viewport.x() + viewport.width() - 28 - font.width(right), y, 0xFF5A4020, false);
    }

    private static void drawOctagon(GuiGraphics graphics, int centerX, int centerY, int radius, int color, int thickness) {
        int diagonal = (int) Math.round(radius * 0.707);
        int[] xs = {centerX, centerX + diagonal, centerX + radius, centerX + diagonal, centerX, centerX - diagonal, centerX - radius, centerX - diagonal};
        int[] ys = {centerY - radius, centerY - diagonal, centerY, centerY + diagonal, centerY + radius, centerY + diagonal, centerY, centerY - diagonal};
        for (int i = 0; i < xs.length; i++) {
            int next = (i + 1) % xs.length;
            drawLine(graphics, xs[i], ys[i], xs[next], ys[next], color, thickness);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    private static int lerpColor(int from, int to, double amount) {
        int weight = (int) Math.round(Math.max(0.0, Math.min(1.0, amount)) * 100.0);
        int r = (((from >> 16) & 0xFF) * (100 - weight) + ((to >> 16) & 0xFF) * weight) / 100;
        int g = (((from >> 8) & 0xFF) * (100 - weight) + ((to >> 8) & 0xFF) * weight) / 100;
        int b = ((from & 0xFF) * (100 - weight) + (to & 0xFF) * weight) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String label(ResourceLocation skill) {
        String path = skill.getPath();
        return path.isEmpty() ? skill.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
