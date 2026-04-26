package net.zoogle.levelrpg.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zoogle.levelrpg.data.SkillRegistry;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
import net.zoogle.levelrpg.data.SkillTreeGraphLayout;
import net.zoogle.levelrpg.data.SkillTreeNodeVisibility;
import net.zoogle.levelrpg.progression.SkillTreeProgression;
import net.zoogle.levelrpg.progression.SkillTreeProgression.NodeSnapshot;
import net.zoogle.levelrpg.progression.SkillTreeProgression.TreeSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Advancement-style panning canvas for skill trees: edges, framed icons, tooltips,
 * and unlock hit targets. Supports a vertical stack of all canonical trees in one viewport.
 */
public final class SkillTreeAdvancementMapPanel {
    private static final int SLOT = SkillTreeGraphLayout.NODE_SLOT;
    private static final int FRAME = 26;
    private static final int FRAME_PAD = (SLOT - FRAME) / 2;
    private static final int ICON = 16;
    private static final int ICON_PAD = FRAME_PAD + (FRAME - ICON) / 2;

    private float panX;
    private float panY;
    private boolean viewInitialized;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private float dragStartPanX;
    private float dragStartPanY;

    public void resetView() {
        panX = 0;
        panY = 0;
        viewInitialized = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta, int viewX, int viewY, int viewW, int viewH) {
        if (!inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            return false;
        }
        if (Minecraft.getInstance().options.keyShift.isDown()) {
            panX += (float) (scrollDelta * 24);
        } else {
            panY += (float) (scrollDelta * 24);
        }
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int viewX, int viewY, int viewW, int viewH) {
        if (button != 0 || !inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            return false;
        }
        dragging = true;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartPanX = panX;
        dragStartPanY = panY;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int viewX, int viewY, int viewW, int viewH) {
        if (button != 0 || !dragging) {
            return false;
        }
        if (!inside(mouseX, mouseY, viewX, viewY, viewW, viewH) && !inside(dragStartMouseX, dragStartMouseY, viewX, viewY, viewW, viewH)) {
            return false;
        }
        panX = dragStartPanX + (float) (mouseX - dragStartMouseX);
        panY = dragStartPanY + (float) (mouseY - dragStartMouseY);
        return true;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * One vertical band in the combined graph (all canonical skills stacked).
     *
     * @param titleGraphX left edge for the section title (graph space)
     * @param titleGraphY top of the title row (graph space)
     * @param yShift        added to each node's layout Y so sections stack without overlapping
     */
    public record CompositeSection(
            ResourceLocation skillId,
            @Nullable SkillTreeDefinition tree,
            TreeSnapshot snap,
            int titleGraphX,
            int titleGraphY,
            int yShift,
            Map<String, int[]> positions
    ) {}

    public Optional<List<Component>> renderForest(
            GuiGraphics gfx,
            Font font,
            List<CompositeSection> sections,
            @Nullable ResourceLocation highlightSkillId,
            int viewX,
            int viewY,
            int viewW,
            int viewH,
            int mouseX,
            int mouseY,
            List<SkillTreeUnlockHit> unlockHitsOut
    ) {
        if (sections.isEmpty()) {
            gfx.drawString(font, "No specialization maps.", viewX + 4, viewY + 4, 0xAAAAAA, false);
            return Optional.empty();
        }

        if (!viewInitialized) {
            initPanForForest(sections, highlightSkillId, viewW, viewH);
            viewInitialized = true;
        }

        gfx.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x78080808);
        int border = 0xFF3A3A3A;
        gfx.fill(viewX, viewY, viewX + viewW, viewY + 1, border);
        gfx.fill(viewX, viewY + viewH - 1, viewX + viewW, viewY + viewH, border);
        gfx.fill(viewX, viewY, viewX + 1, viewY + viewH, border);
        gfx.fill(viewX + viewW - 1, viewY, viewX + viewW, viewY + viewH, border);

        gfx.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        gfx.pose().pushPose();
        gfx.pose().translate(viewX + panX, viewY + panY, 0);

        for (CompositeSection section : sections) {
            drawSectionTitle(font, gfx, section);
            SkillTreeDefinition tree = section.tree();
            TreeSnapshot snap = section.snap();
            Map<String, int[]> positions = section.positions();
            int yShift = section.yShift();
            if (tree == null || snap.nodes().isEmpty() || positions.isEmpty()) {
                continue;
            }
            Set<String> unlocked = snap.unlockedNodes();
            int skillLevel = snap.skillLevel();
            drawEdges(gfx, tree, snap, positions, yShift, unlocked, skillLevel);
            drawNodes(gfx, section.skillId(), tree, snap, positions, yShift, unlocked, skillLevel, viewX, viewY, unlockHitsOut);
        }

        gfx.pose().popPose();
        gfx.disableScissor();

        return pickForestHover(sections, viewX, viewY, mouseX, mouseY);
    }

    private void initPanForForest(
            List<CompositeSection> sections,
            @Nullable ResourceLocation highlightSkillId,
            int viewW,
            int viewH
    ) {
        ResourceLocation frameOn = null;
        if (highlightSkillId != null) {
            for (CompositeSection s : sections) {
                if (highlightSkillId.equals(s.skillId()) && s.tree() != null && !s.positions().isEmpty()) {
                    frameOn = highlightSkillId;
                    break;
                }
            }
        }
        float[] box = forestBounds(sections, frameOn);
        float minX = box[0];
        float minY = box[1];
        float maxX = box[2];
        float maxY = box[3];
        if (minX > maxX) {
            minX = 0;
            minY = 0;
            maxX = viewW;
            maxY = viewH;
        }
        float cx = (minX + maxX) / 2f;
        float cy = (minY + maxY) / 2f;
        panX = viewW / 2f - cx;
        panY = viewH / 2f - cy;
    }

    private static float[] forestBounds(List<CompositeSection> sections, @Nullable ResourceLocation onlySkillId) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (CompositeSection section : sections) {
            if (onlySkillId != null && !onlySkillId.equals(section.skillId())) {
                continue;
            }
            int yShift = section.yShift();
            int ty = section.titleGraphY();
            minX = Math.min(minX, section.titleGraphX());
            minY = Math.min(minY, ty);
            maxX = Math.max(maxX, section.titleGraphX() + 160);
            maxY = Math.max(maxY, ty + 12);
            SkillTreeDefinition tree = section.tree();
            TreeSnapshot snap = section.snap();
            Map<String, int[]> positions = section.positions();
            if (tree == null || positions.isEmpty()) {
                maxY = Math.max(maxY, ty + 28);
                continue;
            }
            Set<String> unlocked = snap.unlockedNodes();
            int skillLevel = snap.skillLevel();
            for (NodeSnapshot ns : snap.nodes()) {
                SkillTreeDefinition.Node node = ns.node();
                if (!shouldDrawNode(tree, node, ns.status(), unlocked, skillLevel)) {
                    continue;
                }
                int[] p = positions.get(node.id());
                if (p == null) {
                    continue;
                }
                int gx = p[0];
                int gy = p[1] + yShift;
                minX = Math.min(minX, gx);
                minY = Math.min(minY, gy);
                maxX = Math.max(maxX, gx + SLOT);
                maxY = Math.max(maxY, gy + SLOT);
            }
        }
        if (minX > maxX) {
            return new float[]{0, 0, 400, 300};
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private static void drawSectionTitle(Font font, GuiGraphics gfx, CompositeSection section) {
        String title = displayNameForSkill(section.skillId());
        gfx.drawString(font, title, section.titleGraphX(), section.titleGraphY(), 0xFFEEDD, false);
        SkillTreeDefinition tree = section.tree();
        TreeSnapshot snap = section.snap();
        if (tree == null || snap.nodes().isEmpty()) {
            gfx.drawString(font, "No mastery map", section.titleGraphX(), section.titleGraphY() + 11, 0x888888, false);
        }
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        String n = SkillRegistry.getDisplayName(skillId);
        if (n != null && !n.isBlank()) {
            return n;
        }
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void drawEdges(
            GuiGraphics gfx,
            SkillTreeDefinition tree,
            TreeSnapshot snap,
            Map<String, int[]> positions,
            int yShift,
            Set<String> unlocked,
            int skillLevel
    ) {
        for (NodeSnapshot ns : snap.nodes()) {
            SkillTreeDefinition.Node node = ns.node();
            if (!shouldDrawNode(tree, node, ns.status(), unlocked, skillLevel)) {
                continue;
            }
            int[] p = positions.get(node.id());
            if (p == null) {
                continue;
            }
            int gx = p[0];
            int gy = p[1] + yShift;
            for (String req : node.requires()) {
                if (!shouldDrawNode(tree, tree.nodes().get(req), statusFor(snap, req), unlocked, skillLevel)) {
                    continue;
                }
                int[] rp = positions.get(req);
                if (rp == null) {
                    continue;
                }
                int x0 = gx + SLOT / 2;
                int y0 = gy + SLOT / 2;
                int x1 = rp[0] + SLOT / 2;
                int y1 = rp[1] + yShift + SLOT / 2;
                drawLine(gfx, x0, y0, x1, y1, lineColor(ns.status(), statusFor(snap, req)));
            }
        }
    }

    private void drawNodes(
            GuiGraphics gfx,
            ResourceLocation skillId,
            SkillTreeDefinition tree,
            TreeSnapshot snap,
            Map<String, int[]> positions,
            int yShift,
            Set<String> unlocked,
            int skillLevel,
            int viewX,
            int viewY,
            List<SkillTreeUnlockHit> unlockHitsOut
    ) {
        for (NodeSnapshot ns : snap.nodes()) {
            SkillTreeDefinition.Node node = ns.node();
            if (!shouldDrawNode(tree, node, ns.status(), unlocked, skillLevel)) {
                continue;
            }
            int[] p = positions.get(node.id());
            if (p == null) {
                continue;
            }
            int gx = p[0];
            int gy = p[1] + yShift;
            boolean dim = ns.status() != SkillTreeProgression.NodeStatus.UNLOCKED
                    && ns.status() != SkillTreeProgression.NodeStatus.AVAILABLE;
            drawNodeFrame(gfx, gx, gy, ns.status(), dim);
            drawIcon(gfx, gx, gy, node.iconKey(), dim);

            int screenX = viewX + Math.round(panX) + gx;
            int screenY = viewY + Math.round(panY) + gy;
            if (ns.status() == SkillTreeProgression.NodeStatus.AVAILABLE) {
                unlockHitsOut.add(new SkillTreeUnlockHit(skillId, screenX, screenY, SLOT, SLOT, node.id()));
            }
        }
    }

    private Optional<List<Component>> pickForestHover(
            List<CompositeSection> sections,
            int viewX,
            int viewY,
            int mouseX,
            int mouseY
    ) {
        for (int s = sections.size() - 1; s >= 0; s--) {
            CompositeSection section = sections.get(s);
            SkillTreeDefinition tree = section.tree();
            TreeSnapshot snap = section.snap();
            Map<String, int[]> positions = section.positions();
            int yShift = section.yShift();
            if (tree == null || snap.nodes().isEmpty() || positions.isEmpty()) {
                continue;
            }
            Set<String> unlocked = snap.unlockedNodes();
            int skillLevel = snap.skillLevel();
            for (int i = snap.nodes().size() - 1; i >= 0; i--) {
                NodeSnapshot ns = snap.nodes().get(i);
                SkillTreeDefinition.Node node = ns.node();
                if (!shouldDrawNode(tree, node, ns.status(), unlocked, skillLevel)) {
                    continue;
                }
                int[] p = positions.get(node.id());
                if (p == null) {
                    continue;
                }
                int gx = p[0];
                int gy = p[1] + yShift;
                int screenX = viewX + Math.round(panX) + gx;
                int screenY = viewY + Math.round(panY) + gy;
                if (mouseX >= screenX && mouseX < screenX + SLOT && mouseY >= screenY && mouseY < screenY + SLOT) {
                    return Optional.of(tooltipLines(tree, node, ns, skillLevel, unlocked));
                }
            }
        }
        return Optional.empty();
    }

    private static SkillTreeProgression.NodeStatus statusFor(TreeSnapshot snap, String nodeId) {
        for (NodeSnapshot n : snap.nodes()) {
            if (n.node().id().equals(nodeId)) {
                return n.status();
            }
        }
        return SkillTreeProgression.NodeStatus.LOCKED_PREREQUISITE;
    }

    private static boolean shouldDrawNode(
            SkillTreeDefinition tree,
            SkillTreeDefinition.Node node,
            SkillTreeProgression.NodeStatus status,
            Set<String> unlocked,
            int skillLevel
    ) {
        if (node == null) {
            return false;
        }
        if (node.visibility() != SkillTreeNodeVisibility.HIDDEN) {
            return true;
        }
        if (status == SkillTreeProgression.NodeStatus.UNLOCKED || status == SkillTreeProgression.NodeStatus.AVAILABLE) {
            return true;
        }
        for (String req : node.requires()) {
            if (unlocked.contains(req)) {
                return true;
            }
        }
        return node.requires().isEmpty() && skillLevel >= tree.minSkillLevel();
    }

    private static boolean obfuscateTitle(SkillTreeDefinition.Node node, SkillTreeProgression.NodeStatus status) {
        return node.visibility() == SkillTreeNodeVisibility.OBFUSCATED
                && status != SkillTreeProgression.NodeStatus.UNLOCKED
                && status != SkillTreeProgression.NodeStatus.AVAILABLE;
    }

    private static List<Component> tooltipLines(
            SkillTreeDefinition tree,
            SkillTreeDefinition.Node node,
            NodeSnapshot ns,
            int skillLevel,
            Set<String> unlocked
    ) {
        List<Component> lines = new ArrayList<>();
        String title = obfuscateTitle(node, ns.status()) ? "???" : (node.title().isBlank() ? node.id() : node.title());
        lines.add(Component.literal(title).withStyle(ChatFormatting.GOLD));
        if (!obfuscateTitle(node, ns.status()) && !node.description().isBlank()) {
            lines.add(Component.literal(node.description()).withStyle(ChatFormatting.GRAY));
        } else if (obfuscateTitle(node, ns.status())) {
            lines.add(Component.literal("Hidden specialization").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        lines.add(Component.literal("Cost: " + node.normalizedCost() + " specialization point(s)").withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("State: " + humanStatus(ns.status())).withStyle(ChatFormatting.WHITE));
        if (!obfuscateTitle(node, ns.status()) && !ns.missingRequirements().isEmpty()) {
            lines.add(Component.literal("Requires: " + String.join(", ", ns.missingRequirements())).withStyle(ChatFormatting.RED));
        }
        if (skillLevel < SkillTreeProgression.requiredLevelFor(tree, node)) {
            lines.add(Component.literal("Needs invested level " + SkillTreeProgression.requiredLevelFor(tree, node)).withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static String humanStatus(SkillTreeProgression.NodeStatus s) {
        return switch (s) {
            case UNLOCKED -> "Unlocked";
            case AVAILABLE -> "Ready to unlock";
            case LOCKED_SKILL_LEVEL -> "Locked (level)";
            case LOCKED_PREREQUISITE -> "Locked (path)";
            case LOCKED_MASTERY_POINTS -> "Locked (points)";
        };
    }

    private static int lineColor(SkillTreeProgression.NodeStatus child, SkillTreeProgression.NodeStatus parent) {
        if (parent == SkillTreeProgression.NodeStatus.UNLOCKED && child == SkillTreeProgression.NodeStatus.UNLOCKED) {
            return 0xFF8CE997;
        }
        if (parent == SkillTreeProgression.NodeStatus.UNLOCKED) {
            return 0xFF6BA3D6;
        }
        return 0xFF444444;
    }

    private static void drawNodeFrame(GuiGraphics gfx, int gx, int gy, SkillTreeProgression.NodeStatus status, boolean dim) {
        int x = gx + FRAME_PAD;
        int y = gy + FRAME_PAD;
        int outer = dim ? 0xFF2A2A2A : 0xFF3A3A3A;
        int inner = switch (status) {
            case UNLOCKED -> 0xFF1E3D22;
            case AVAILABLE -> 0xFF2A3550;
            default -> 0xFF252525;
        };
        if (status == SkillTreeProgression.NodeStatus.AVAILABLE) {
            outer = 0xFF6BC4FF;
        }
        gfx.fill(x - 1, y - 1, x + FRAME + 1, y + FRAME + 1, outer);
        gfx.fill(x, y, x + FRAME, y + FRAME, inner);
    }

    private static void drawIcon(GuiGraphics gfx, int gx, int gy, String iconKey, boolean dim) {
        int ix = gx + ICON_PAD;
        int iy = gy + ICON_PAD;
        if (iconKey != null && iconKey.contains("textures/")) {
            ResourceLocation tex = ResourceLocation.tryParse(iconKey);
            if (tex != null) {
                try {
                    gfx.blit(tex, ix, iy, 0, 0, ICON, ICON, 256, 256);
                } catch (Throwable ignored) {
                    gfx.renderItem(new ItemStack(Items.BOOK), ix, iy);
                }
            } else {
                gfx.renderItem(new ItemStack(Items.BOOK), ix, iy);
            }
        } else {
            gfx.renderItem(resolveIconStack(iconKey), ix, iy);
        }
        if (dim) {
            gfx.fill(ix, iy, ix + ICON, iy + ICON, 0x99000000);
        }
    }

    private static ItemStack resolveIconStack(String iconKey) {
        if (iconKey == null || iconKey.isBlank()) {
            return new ItemStack(Items.BOOK);
        }
        ResourceLocation id = ResourceLocation.tryParse(iconKey);
        if (id == null) {
            return new ItemStack(Items.BOOK);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return new ItemStack(Items.BOOK);
        }
        return new ItemStack(item);
    }

    private static void drawLine(GuiGraphics gfx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            gfx.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static boolean inside(double mx, double my, int vx, int vy, int vw, int vh) {
        return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }

    public record SkillTreeUnlockHit(ResourceLocation skillId, int x, int y, int w, int h, String nodeId) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
