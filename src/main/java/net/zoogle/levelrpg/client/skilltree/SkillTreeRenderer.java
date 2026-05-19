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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SkillTreeRenderer {
    private static final float TYPE_MARK_MIN_ZOOM = 0.82f;
    private static final float GLOW_MIN_ZOOM = 0.62f;
    private static final int MIN_ICON_SIZE = 5;
    private static final int MAX_ICON_SIZE = 18;
    private static final int MIN_NODE_VISUAL_SIZE = 16;
    private static final int MAX_NODE_VISUAL_SIZE = 54;
    public static final NodeOffset NO_OFFSET = new NodeOffset(0, 0);
    private static final NodeOffsetProvider NO_NODE_OFFSET = node -> NO_OFFSET;

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
        render(graphics, font, definition, nodes, nodeById, viewportX, viewportY, viewportW, viewportH, transform, hovered, selectedNodeId, connectMode, drawGrid, drawFrame, opacity, NO_NODE_OFFSET);
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
            float opacity,
            NodeOffsetProvider offsetProvider
    ) {
        if (drawGrid) {
            drawGrid(graphics, viewportX, viewportY, viewportW, viewportH, transform);
        }
        NodeOffsetProvider offsets = offsetProvider == null ? NO_NODE_OFFSET : offsetProvider;
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        if (definition != null) {
            for (SkillTreeEdge edge : definition.edges()) {
                SkillTreeNodeView parent = nodeById.get(edge.parentId());
                SkillTreeNodeView child = nodeById.get(edge.childId());
                if (parent != null && child != null && parent.rendered() && child.rendered()) {
                    boolean highlighted = isConnectionHighlighted(parent, child, hovered, selectedNodeId);
                    NodeOffset parentOffset = offsetOrNone(offsets.offsetFor(parent));
                    NodeOffset childOffset = offsetOrNone(offsets.offsetFor(child));
                    SkillTreeConnectionRenderer.draw(
                            graphics,
                            parent,
                            child,
                            transform,
                            highlighted,
                            opacity,
                            parentOffset.x(),
                            parentOffset.y(),
                            childOffset.x(),
                            childOffset.y()
                    );
                }
            }
        }
        for (SkillTreeNodeView node : nodes) {
            if (node.rendered()) {
                boolean selected = node.definition().id().equals(selectedNodeId);
                drawNode(graphics, font, node, node == hovered, selected, connectMode, transform, opacity, offsetOrNone(offsets.offsetFor(node)));
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

    public void renderFogHints(
            GuiGraphics graphics,
            SkillTreeVisibilityView.FogHints hints,
            Map<String, SkillTreeNodeView> visibleById,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH,
            SkillTreeCameraTransform transform
    ) {
        if (hints == null || hints.isEmpty()) {
            return;
        }
        LinkedHashMap<String, SkillTreeNodeView> hintedById = new LinkedHashMap<>();
        for (SkillTreeNodeView node : hints.silhouettes()) {
            hintedById.put(node.definition().id(), node);
        }
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        for (SkillTreeEdge edge : hints.edges()) {
            SkillTreeNodeView parent = visibleById.get(edge.parentId());
            SkillTreeNodeView child = hintedById.get(edge.childId());
            if (parent == null || child == null) {
                parent = hintedById.get(edge.parentId());
                child = visibleById.get(edge.childId());
            }
            if (parent != null && child != null) {
                drawFogEdge(graphics, parent, child, transform);
            }
        }
        for (SkillTreeNodeView node : hints.silhouettes()) {
            drawFogNode(graphics, node, transform);
        }
        graphics.disableScissor();
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

    private static void drawFogEdge(GuiGraphics graphics, SkillTreeNodeView parent, SkillTreeNodeView child, SkillTreeCameraTransform transform) {
        int x1 = parent.centerX(transform);
        int y1 = parent.centerY(transform);
        int x2 = child.centerX(transform);
        int y2 = child.centerY(transform);
        int thickness = clamp((int) Math.round(2 * transform.zoom()), 1, 3);
        int midY = y1 + (y2 - y1) / 2;
        drawDashedSegment(graphics, x1, y1, x1, midY, thickness, 0x553D4650);
        drawDashedSegment(graphics, x1, midY, x2, midY, thickness, 0x553D4650);
        drawDashedSegment(graphics, x2, midY, x2, y2, thickness, 0x553D4650);
    }

    private static void drawDashedSegment(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        int dash = Math.max(5, thickness * 4);
        int gap = Math.max(4, thickness * 3);
        for (int i = 0; i <= steps; i++) {
            if ((i % (dash + gap)) >= dash) {
                continue;
            }
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            int half = Math.max(1, thickness / 2);
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    private static void drawFogNode(GuiGraphics graphics, SkillTreeNodeView node, SkillTreeCameraTransform transform) {
        VisualNodeBox box = visualBox(node, transform, 0);
        int left = box.outerX();
        int top = box.outerY();
        int total = box.outerSize();
        int shadowOffset = Math.max(1, Math.min(2, total / 18));
        graphics.fill(left + shadowOffset, top + shadowOffset, left + total + shadowOffset, top + total + shadowOffset, 0x55000000);
        graphics.fill(left, top, left + total, top + total, 0x55202024);
        graphics.fill(left + 2, top + 2, left + total - 2, top + total - 2, 0x6633373D);
        drawFogOutline(graphics, left, top, total, 0x666B7580);
        int cx = box.centerX();
        int cy = box.centerY();
        int mark = Math.max(3, box.innerSize() / 6);
        graphics.fill(cx - 1, cy - mark, cx + 2, cy + mark, 0x665D6670);
        graphics.fill(cx - mark, cy - 1, cx + mark, cy + 2, 0x665D6670);
    }

    private static void drawFogOutline(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y + 1, x + 1, y + size - 1, color);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    private static boolean isConnectionHighlighted(
            SkillTreeNodeView parent,
            SkillTreeNodeView child,
            SkillTreeNodeView hovered,
            String selectedNodeId
    ) {
        String hoveredId = hovered == null ? "" : hovered.definition().id();
        if (parent.definition().id().equals(hoveredId) || child.definition().id().equals(hoveredId)) {
            return true;
        }
        return selectedNodeId != null
                && (parent.definition().id().equals(selectedNodeId) || child.definition().id().equals(selectedNodeId));
    }

    private static void drawNode(GuiGraphics graphics, Font font, SkillTreeNodeView node, boolean hovered, boolean selected, boolean connectMode, SkillTreeCameraTransform transform, float opacity, NodeOffset offset) {
        float zoom = transform.zoom();
        boolean unlocked = node.status() == SkillNodeStatus.INSCRIBED;
        boolean available = node.status() == SkillNodeStatus.AVAILABLE;
        NodeStyle style = styleFor(node.definition(), unlocked);
        VisualNodeBox box = visualBox(node, transform, style.frameInflate(), offset);
        int x = box.innerX();
        int y = box.innerY();
        int size = box.innerSize();
        int visualX = box.outerX();
        int visualY = box.outerY();
        int visualSize = box.outerSize();

        drawNodeBackplate(graphics, visualX, visualY, visualSize, node.status(), opacity);
        if (hovered) {
            drawNodeHighlight(graphics, style, visualX - 3, visualY - 3, visualSize + 6, applyOpacity(connectMode ? 0xCC73B7FF : 0xCCFFF1A8, opacity));
        } else if (selected) {
            drawNodeHighlight(graphics, style, visualX - 4, visualY - 4, visualSize + 8, applyOpacity(connectMode ? 0xCC4C9DDB : 0xCCF6C85B, opacity));
        } else if (available) {
            drawNodeHighlight(graphics, style, visualX - 2, visualY - 2, visualSize + 4, applyOpacity(0xAA78A95E, opacity));
        }

        if (zoom >= GLOW_MIN_ZOOM && visualSize >= 18) {
            drawTypeGlow(graphics, style, visualX, visualY, visualSize, node.status(), opacity);
        }

        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        if (unlocked) {
            r = 1.0f;
            g = 0.96f;
            b = 0.86f;
        } else if (available) {
            r = 0.8f;
            g = 1.0f;
            b = 0.8f;
        } else {
            r = 0.62f;
            g = 0.58f;
            b = 0.52f;
        }

        graphics.setColor(r, g, b, opacity);
        graphics.blitSprite(style.frameSprite(), visualX - 2, visualY - 2, visualSize + 4, visualSize + 4);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (zoom >= TYPE_MARK_MIN_ZOOM && visualSize >= 22) {
            drawTypeMarks(graphics, style, visualX, visualY, visualSize, node.status(), opacity);
        }

        if (node.obfuscated()) {
            drawMysteryGlyph(graphics, x, y, size, applyOpacity(0xFF746A60, opacity), zoom);
        } else if (opacity < 0.75f || !drawIcon(graphics, node.definition(), x, y, size, style, box.visualZoom())) {
            drawFallbackGlyph(graphics, x, y, size, applyOpacity(0xFF9E9487, opacity), box.visualZoom());
        }
        if (!unlocked && !available && !node.obfuscated()) {
            graphics.fill(x + 4, y + 4, x + size - 4, y + size - 4, applyOpacity(0x66000000, opacity));
        }
    }

    private static void drawNodeBackplate(GuiGraphics graphics, int x, int y, int size, SkillNodeStatus status, float opacity) {
        int shadow = applyOpacity(0x66000000, opacity);
        int shadowOffset = Math.max(1, Math.min(2, size / 18));
        graphics.fill(x + shadowOffset, y + shadowOffset, x + size + shadowOffset, y + size + shadowOffset, shadow);
        int inset = Math.max(2, size / 12);
        int fill = status == SkillNodeStatus.INSCRIBED
                ? 0xCC3A3020
                : status == SkillNodeStatus.AVAILABLE ? 0xCC2F3526 : 0xCC242424;
        graphics.fill(x + inset, y + inset, x + size - inset, y + size - inset, applyOpacity(fill, opacity));
    }

    private static void drawNodeHighlight(GuiGraphics graphics, NodeStyle style, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y + 1, x + 1, y + size - 1, color);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
        int dark = (Math.max(0x55, (color >>> 24) / 2) << 24) | 0x000000;
        graphics.fill(x + 1, y + 1, x + size - 1, y + 2, dark);
        graphics.fill(x + 1, y + 2, x + 2, y + size - 1, dark);
    }

    private static void drawTypeGlow(GuiGraphics graphics, NodeStyle style, int x, int y, int size, SkillNodeStatus status, float opacity) {
        if (status == SkillNodeStatus.HIDDEN || style.glowColor() == 0) {
            return;
        }
        float activeOpacity = status == SkillNodeStatus.INSCRIBED || status == SkillNodeStatus.AVAILABLE ? opacity * 0.45f : opacity * 0.18f;
        int glow = applyOpacity(style.glowColor(), activeOpacity);
        int pad = Math.max(2, size / 10);
        graphics.fill(x - pad, y + size / 2 - 1, x + size + pad, y + size / 2 + 2, glow);
        graphics.fill(x + size / 2 - 1, y - pad, x + size / 2 + 2, y + size + pad, glow);
    }

    private static void drawTypeMarks(GuiGraphics graphics, NodeStyle style, int x, int y, int size, SkillNodeStatus status, float opacity) {
        if (status == SkillNodeStatus.HIDDEN || style.markColor() == 0) {
            return;
        }
        int color = applyOpacity(status == SkillNodeStatus.INSCRIBED || status == SkillNodeStatus.AVAILABLE ? style.markColor() : fadeColor(style.markColor(), 0xAA), opacity);
        int tick = Math.max(2, size / 7);
        int thick = Math.max(1, size / 18);
        switch (style.kind()) {
            case TECHNIQUE -> drawCornerTicks(graphics, x, y, size, tick, thick, color);
            case AXIOM -> drawRuneMarks(graphics, x, y, size, thick, color);
            case MANIFESTATION -> drawCapstoneMarks(graphics, x, y, size, tick, thick, color);
            case CORE -> drawCoreMarks(graphics, x, y, size, thick, color);
            default -> {
            }
        }
    }

    private static void drawCornerTicks(GuiGraphics graphics, int x, int y, int size, int tick, int thick, int color) {
        graphics.fill(x, y, x + tick, y + thick, color);
        graphics.fill(x, y, x + thick, y + tick, color);
        graphics.fill(x + size - tick, y, x + size, y + thick, color);
        graphics.fill(x + size - thick, y, x + size, y + tick, color);
        graphics.fill(x, y + size - thick, x + tick, y + size, color);
        graphics.fill(x, y + size - tick, x + thick, y + size, color);
        graphics.fill(x + size - tick, y + size - thick, x + size, y + size, color);
        graphics.fill(x + size - thick, y + size - tick, x + size, y + size, color);
    }

    private static void drawRuneMarks(GuiGraphics graphics, int x, int y, int size, int thick, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int arm = Math.max(4, size / 4);
        graphics.fill(cx - thick, cy - arm, cx + thick + 1, cy - thick, color);
        graphics.fill(cx - thick, cy + thick, cx + thick + 1, cy + arm, color);
        graphics.fill(cx - arm, cy - thick, cx - thick, cy + thick + 1, color);
        graphics.fill(cx + thick, cy - thick, cx + arm, cy + thick + 1, color);
    }

    private static void drawCapstoneMarks(GuiGraphics graphics, int x, int y, int size, int tick, int thick, int color) {
        drawCornerTicks(graphics, x - thick, y - thick, size + thick * 2, tick + thick, thick, color);
        int cx = x + size / 2;
        graphics.fill(cx - thick, y - tick / 2, cx + thick + 1, y, color);
        graphics.fill(cx - thick, y + size, cx + thick + 1, y + size + tick / 2, color);
    }

    private static void drawCoreMarks(GuiGraphics graphics, int x, int y, int size, int thick, int color) {
        int inset = Math.max(3, size / 5);
        graphics.fill(x + inset, y + inset, x + size - inset, y + inset + thick, color);
        graphics.fill(x + inset, y + size - inset - thick, x + size - inset, y + size - inset, color);
        graphics.fill(x + inset, y + inset, x + inset + thick, y + size - inset, color);
        graphics.fill(x + size - inset - thick, y + inset, x + size - inset, y + size - inset, color);
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

    private static void drawFallbackGlyph(GuiGraphics graphics, int x, int y, int size, int color, float zoom) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int radius = Math.max(2, Math.min(size / 3, Math.round(5 * zoom)));
        int thick = Math.max(1, Math.round(2 * zoom));
        if (size < 13) {
            graphics.fill(cx - thick, cy - thick, cx + thick + 1, cy + thick + 1, color);
            return;
        }
        graphics.fill(cx - thick, cy - radius, cx + thick + 1, cy - thick, color);
        graphics.fill(cx - radius, cy - thick, cx - thick, cy + thick + 1, color);
        graphics.fill(cx + thick + 1, cy - thick, cx + radius + 1, cy + thick + 1, color);
        graphics.fill(cx - thick, cy + thick + 1, cx + thick + 1, cy + radius + 1, color);
    }

    private static boolean drawIcon(GuiGraphics graphics, SkillTreeNodeDefinition node, int x, int y, int size, NodeStyle style, float zoom) {
        ItemStack stack = resolveNodeIconStack(node);
        if (stack.isEmpty()) {
            return false;
        }
        int inset = Math.max(1, (int) Math.round(style.iconInset() * zoom));
        int available = size - inset * 2;
        if (available < MIN_ICON_SIZE) {
            return false;
        }
        int iconSize = Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, available));
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

    public static ItemStack resolveNodeIconStack(SkillTreeNodeDefinition node) {
        String iconKey = node.icon() == null ? "" : node.icon();
        if (!iconKey.isBlank()) {
            ItemStack explicit = resolveItemIcon(iconKey);
            if (!explicit.isEmpty()) {
                return explicit;
            }
        }
        return fallbackIconStack(node);
    }

    private static ItemStack resolveItemIcon(String iconKey) {
        ResourceLocation id = ResourceLocation.tryParse(iconKey);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ItemStack fallbackIconStack(SkillTreeNodeDefinition node) {
        String lookup = (node.id() + " " + node.title() + " " + node.description()).toLowerCase();
        if (lookup.contains("shield") || lookup.contains("deflect") || lookup.contains("block")) {
            return new ItemStack(Objects.requireNonNull(Items.SHIELD));
        }
        if (lookup.contains("armor") || lookup.contains("resistance") || lookup.contains("inpenetrable") || lookup.contains("vanguard")) {
            return new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE));
        }
        if (lookup.contains("heal") || lookup.contains("regen") || lookup.contains("hope")) {
            return new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE));
        }
        if (lookup.contains("knock") || lookup.contains("ram") || lookup.contains("immovable") || lookup.contains("steeled")) {
            return new ItemStack(Objects.requireNonNull(Items.ANVIL));
        }
        if (lookup.contains("range") || lookup.contains("reach")) {
            return new ItemStack(Objects.requireNonNull(Items.SPYGLASS));
        }
        if (lookup.contains("dash") || lookup.contains("frenzy") || lookup.contains("speed")) {
            return new ItemStack(Objects.requireNonNull(Items.FEATHER));
        }
        if (lookup.contains("crit") || lookup.contains("combo")) {
            return new ItemStack(Objects.requireNonNull(Items.DIAMOND_SWORD));
        }
        if (lookup.contains("execute") || lookup.contains("mortal")) {
            return new ItemStack(Objects.requireNonNull(Items.NETHERITE_SWORD));
        }
        if (lookup.contains("xp") || lookup.contains("hunter")) {
            return new ItemStack(Objects.requireNonNull(Items.EXPERIENCE_BOTTLE));
        }
        return switch (normalizedType(node.type())) {
            case "core" -> new ItemStack(Objects.requireNonNull(Items.WRITABLE_BOOK));
            case "technique" -> new ItemStack(Objects.requireNonNull(Items.BLAZE_POWDER));
            case "manifestation" -> new ItemStack(Objects.requireNonNull(Items.NETHER_STAR));
            case "axiom" -> new ItemStack(Objects.requireNonNull(Items.TOTEM_OF_UNDYING));
            default -> new ItemStack(Objects.requireNonNull(Items.IRON_SWORD));
        };
    }

    private static NodeStyle styleFor(SkillTreeNodeDefinition node, boolean unlocked) {
        NodeKind kind = kindFor(node.type());
        ResourceLocation sprite = switch (kind) {
            case TECHNIQUE -> unlocked ? ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/goal_frame_unobtained");
            case CORE, AXIOM, MANIFESTATION -> unlocked ? ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/challenge_frame_unobtained");
            default -> unlocked ? ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained") : ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
        };
        ResourceLocation highlight = switch (kind) {
            case TECHNIQUE -> ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained");
            case CORE, AXIOM, MANIFESTATION -> ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained");
            default -> ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
        };
        return switch (kind) {
            case CORE -> new NodeStyle(kind, sprite, highlight, 0x55C9B46D, 0xFFD5BA72, 5, 6);
            case TECHNIQUE -> new NodeStyle(kind, sprite, highlight, 0x445FAFE3, 0xFF74C7F2, 2, 7);
            case AXIOM -> new NodeStyle(kind, sprite, highlight, 0x55C772FF, 0xFFE0A6FF, 3, 8);
            case MANIFESTATION -> new NodeStyle(kind, sprite, highlight, 0x66F2D06A, 0xFFFFDF7A, 6, 5);
            default -> new NodeStyle(kind, sprite, highlight, 0x00000000, 0x00000000, 0, 8);
        };
    }

    private static NodeKind kindFor(String type) {
        return switch (normalizedType(type)) {
            case "core" -> NodeKind.CORE;
            case "technique" -> NodeKind.TECHNIQUE;
            case "axiom" -> NodeKind.AXIOM;
            case "manifestation" -> NodeKind.MANIFESTATION;
            default -> NodeKind.TRAIT;
        };
    }

    private static String normalizedType(String type) {
        return type == null || type.isBlank() ? "trait" : type.trim().toLowerCase();
    }

    private static int fadeColor(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static VisualNodeBox visualBox(SkillTreeNodeView node, SkillTreeCameraTransform transform, int frameInflate) {
        return visualBox(node, transform, frameInflate, NO_OFFSET);
    }

    private static VisualNodeBox visualBox(SkillTreeNodeView node, SkillTreeCameraTransform transform, int frameInflate, NodeOffset offset) {
        int scaledSize = node.scaledSize(transform);
        int innerSize = clamp(scaledSize, MIN_NODE_VISUAL_SIZE, MAX_NODE_VISUAL_SIZE);
        int centerX = node.centerX(transform) + offset.x();
        int centerY = node.centerY(transform) + offset.y();
        int innerX = centerX - innerSize / 2;
        int innerY = centerY - innerSize / 2;
        float visualZoom = node.size() <= 0 ? transform.zoom() : innerSize / (float) node.size();
        int pad = Math.max(0, (int) Math.round(frameInflate * visualZoom));
        return new VisualNodeBox(innerX, innerY, innerSize, pad, visualZoom);
    }

    private static NodeOffset offsetOrNone(NodeOffset offset) {
        return offset == null ? NO_OFFSET : offset;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int applyOpacity(int color, float opacity) {
        if (opacity >= 0.999f) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int fadedAlpha = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0f, Math.min(1.0f, opacity)))));
        return (fadedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private enum NodeKind {
        TRAIT,
        CORE,
        TECHNIQUE,
        AXIOM,
        MANIFESTATION
    }

    private record NodeStyle(
            NodeKind kind,
            ResourceLocation frameSprite,
            ResourceLocation highlightSprite,
            int glowColor,
            int markColor,
            int frameInflate,
            int iconInset
    ) {
    }

    private record VisualNodeBox(int innerX, int innerY, int innerSize, int pad, float visualZoom) {
        int centerX() {
            return innerX + innerSize / 2;
        }

        int centerY() {
            return innerY + innerSize / 2;
        }

        int outerX() {
            return innerX - pad;
        }

        int outerY() {
            return innerY - pad;
        }

        int outerSize() {
            return innerSize + pad * 2;
        }
    }

    @FunctionalInterface
    public interface NodeOffsetProvider {
        NodeOffset offsetFor(SkillTreeNodeView node);
    }

    public record NodeOffset(int x, int y) {
    }
}
