package net.zoogle.levelrpg.client.skilltree;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeEdge;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SkillTreeRenderer {
    public void render(
            GuiGraphics graphics,
            Font font,
            SkillTreePresentationDefinition definition,
            List<SkillTreeNodeView> nodes,
            Map<String, SkillTreeNodeView> nodeById,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH,
            SkillTreeCameraTransform transform,
            SkillTreeNodeView hovered,
            String selectedNodeId,
            boolean connectMode
    ) {
        render(graphics, font, definition, nodes, nodeById, viewportX, viewportY, viewportW, viewportH, transform, hovered, selectedNodeId, connectMode, true, true);
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            SkillTreePresentationDefinition definition,
            List<SkillTreeNodeView> nodes,
            Map<String, SkillTreeNodeView> nodeById,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH,
            SkillTreeCameraTransform transform,
            SkillTreeNodeView hovered,
            String selectedNodeId,
            boolean connectMode,
            boolean drawGrid,
            boolean drawFrame
    ) {
        render(graphics, font, definition, nodes, nodeById, viewportX, viewportY, viewportW, viewportH, transform, hovered, selectedNodeId, connectMode, drawGrid, drawFrame, 1.0f);
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            SkillTreePresentationDefinition definition,
            List<SkillTreeNodeView> nodes,
            Map<String, SkillTreeNodeView> nodeById,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH,
            SkillTreeCameraTransform transform,
            SkillTreeNodeView hovered,
            String selectedNodeId,
            boolean connectMode,
            boolean drawGrid,
            boolean drawFrame,
            float opacity
    ) {
        if (drawGrid) {
            drawGrid(graphics, viewportX, viewportY, viewportW, viewportH, transform);
        }
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        if (definition != null) {
            for (SkillTreeEdge edge : definition.edges()) {
                SkillTreeNodeView parent = nodeById.get(edge.parentId());
                SkillTreeNodeView child = nodeById.get(edge.childId());
                if (parent != null && child != null && parent.rendered() && child.rendered()) {
                    drawConnection(graphics, parent, child, transform, opacity);
                }
            }
        }
        for (SkillTreeNodeView node : nodes) {
            if (node.rendered()) {
                boolean selected = node.definition().id().equals(selectedNodeId);
                drawNode(graphics, font, node, node == hovered, selected, connectMode, transform, opacity);
            }
        }
        graphics.disableScissor();
        if (drawFrame) {
            graphics.hLine(viewportX, viewportX + viewportW, viewportY, 0xFF9A7446);
            graphics.hLine(viewportX, viewportX + viewportW, viewportY + viewportH, 0xFF4B3823);
            graphics.vLine(viewportX, viewportY, viewportY + viewportH, 0xFF9A7446);
            graphics.vLine(viewportX + viewportW, viewportY, viewportY + viewportH, 0xFF4B3823);
        }
    }

    private static void drawGrid(GuiGraphics graphics, int x, int y, int w, int h, SkillTreeCameraTransform transform) {
        int spacing = Math.max(8, (int) Math.round(32 * transform.zoom()));
        int startX = x + Math.floorMod((int) Math.round(transform.panX()), spacing);
        int startY = y + Math.floorMod((int) Math.round(transform.panY()), spacing);
        for (int gx = startX; gx < x + w; gx += spacing) {
            graphics.vLine(gx, y, y + h, 0x063E2E1E);
        }
        for (int gy = startY; gy < y + h; gy += spacing) {
            graphics.hLine(x, x + w, gy, 0x063E2E1E);
        }
    }

    private static void drawConnection(GuiGraphics graphics, SkillTreeNodeView parent, SkillTreeNodeView child, SkillTreeCameraTransform transform, float opacity) {
        int x1 = parent.centerX(transform);
        int y1 = parent.centerY(transform);
        int x2 = child.centerX(transform);
        int y2 = child.centerY(transform);
        int color = applyOpacity(parent.status() == SkillNodeStatus.INSCRIBED ? 0xFF6D4F2C : 0xFF75624B, opacity);
        int thickness = Math.max(1, (int) Math.round(2 * transform.zoom()));
        drawLine(graphics, x1, y1, x2, y2, color, thickness);
        drawLine(graphics, x1, y1 + Math.max(1, thickness / 2), x2, y2 + Math.max(1, thickness / 2), applyOpacity(0x66000000, opacity), thickness);
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

    private static void drawNode(GuiGraphics graphics, Font font, SkillTreeNodeView node, boolean hovered, boolean selected, boolean connectMode, SkillTreeCameraTransform transform, float opacity) {
        int x = node.left(transform);
        int y = node.top(transform);
        int size = node.scaledSize(transform);
        ResourceLocation sprite;
        boolean unlocked = node.status() == SkillNodeStatus.INSCRIBED;
        boolean available = node.status() == SkillNodeStatus.AVAILABLE;
        String type = node.definition().type();
        if ("technique".equalsIgnoreCase(type)) {
            sprite = unlocked ? ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/goal_frame_unobtained");
        } else if ("manifestation".equalsIgnoreCase(type) || "axiom".equalsIgnoreCase(type) || "core".equalsIgnoreCase(type)) {
            sprite = unlocked ? ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/challenge_frame_unobtained");
        } else {
            sprite = unlocked ? ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
        }

        if (hovered) {
            drawNodeHighlight(graphics, type, x - 3, y - 3, size + 6, applyOpacity(connectMode ? 0xAA7FCBFF : 0x88FFF0A0, opacity));
        } else if (selected) {
            drawNodeHighlight(graphics, type, x - 4, y - 4, size + 8, applyOpacity(connectMode ? 0xAA42A5F5 : 0xAAFFE081, opacity));
        } else if (available) {
            drawNodeHighlight(graphics, type, x - 2, y - 2, size + 4, applyOpacity(0x6685FF75, opacity));
        }

        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        if (!unlocked && available) {
            r = 0.8f;
            g = 1.0f;
            b = 0.8f;
        }

        graphics.setColor(r, g, b, opacity);
        graphics.blitSprite(sprite, x - 2, y - 2, size + 4, size + 4);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        int glyphHalf = Math.max(1, (int) Math.round(2 * transform.zoom()));
        int glyphPad = Math.max(3, (int) Math.round(7 * transform.zoom()));
        
        if (node.obfuscated()) {
            drawMysteryGlyph(graphics, x, y, size, applyOpacity(0xFF746A60, opacity), transform.zoom());
        } else if (opacity < 0.75f || !drawIcon(graphics, node.definition(), x, y, size, transform.zoom())) {
            graphics.fill(x + size / 2 - glyphHalf, y + glyphPad, x + size / 2 + glyphHalf, y + size - glyphPad, applyOpacity(0xFF9E9487, opacity));
            graphics.fill(x + glyphPad, y + size / 2 - glyphHalf, x + size - glyphPad, y + size / 2 + glyphHalf, applyOpacity(0xFF9E9487, opacity));
        }
    }

    private static void drawNodeHighlight(GuiGraphics graphics, String type, int x, int y, int size, int color) {
        ResourceLocation sprite;
        if ("technique".equalsIgnoreCase(type)) {
            sprite = ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained");
        } else if ("manifestation".equalsIgnoreCase(type) || "axiom".equalsIgnoreCase(type) || "core".equalsIgnoreCase(type)) {
            sprite = ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained");
        } else {
            sprite = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
        }
        
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        graphics.setColor(r, g, b, a);
        graphics.blitSprite(sprite, x, y, size, size);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawMysteryGlyph(GuiGraphics graphics, int x, int y, int size, int color, float zoom) {
        if (zoom >= 0.75f) {
            graphics.drawString(
                    net.minecraft.client.Minecraft.getInstance().font,
                    "?",
                    x + size / 2 - 2,
                    y + size / 2 - 4,
                    color,
                    false
            );
            return;
        }
        int dot = Math.max(2, (int) Math.round(3 * zoom));
        graphics.fill(x + size / 2 - dot, y + size / 2 - dot, x + size / 2 + dot, y + size / 2 + dot, color);
    }

    private static boolean drawIcon(GuiGraphics graphics, SkillTreeNodeDefinition node, int x, int y, int size, float zoom) {
        if (zoom < 0.7f) {
            return false;
        }
        ItemStack stack = resolveNodeIconStack(node);
        if (stack.isEmpty()) {
            return false;
        }
        int iconSize = Math.max(8, Math.min(16, size - 8));
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;
        graphics.pose().pushPose();
        float scale = iconSize / 16.0f;
        graphics.pose().translate(iconX, iconY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        return true;
    }

    private static ItemStack resolveNodeIconStack(SkillTreeNodeDefinition node) {
        String iconKey = node.icon() == null ? "" : node.icon();
        if (!iconKey.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(iconKey);
            if (id != null) {
                ItemStack stack = BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return switch (node.type()) {
            case "core" -> new ItemStack(Objects.requireNonNull(Items.WRITABLE_BOOK));
            case "technique" -> new ItemStack(Objects.requireNonNull(Items.BLAZE_POWDER));
            case "manifestation" -> new ItemStack(Objects.requireNonNull(Items.NETHER_STAR));
            case "axiom" -> new ItemStack(Objects.requireNonNull(Items.TOTEM_OF_UNDYING));
            default -> new ItemStack(Objects.requireNonNull(Items.IRON_SWORD));
        };
    }

    private static int typeAccent(SkillTreeNodeDefinition node) {
        return switch (node.type()) {
            case "core" -> 0xFFBFA45D;
            case "technique" -> 0xFF5FAFE3;
            case "manifestation" -> 0xFFE7D884;
            case "axiom" -> 0xFFFFC857;
            default -> 0xFF9A7446;
        };
    }

    private static boolean isSpecialType(SkillTreeNodeDefinition node) {
        return !"trait".equals(node.type());
    }

    private static int blendFrameForType(int frame, int accent, SkillNodeStatus status) {
        if (status == SkillNodeStatus.HIDDEN || status == SkillNodeStatus.LOCKED_LEVEL || status == SkillNodeStatus.LOCKED_PARENT) {
            return frame;
        }
        int weight = status == SkillNodeStatus.LOCKED_POINTS ? 45 : 70;
        int r = (((frame >> 16) & 0xFF) * (100 - weight) + ((accent >> 16) & 0xFF) * weight) / 100;
        int g = (((frame >> 8) & 0xFF) * (100 - weight) + ((accent >> 8) & 0xFF) * weight) / 100;
        int b = ((frame & 0xFF) * (100 - weight) + (accent & 0xFF) * weight) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int applyOpacity(int color, float opacity) {
        if (opacity >= 0.999f) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int fadedAlpha = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0f, Math.min(1.0f, opacity)))));
        return (fadedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
