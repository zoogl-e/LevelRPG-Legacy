package net.zoogle.levelrpg.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zoogle.levelrpg.data.SkillRegistry;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
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
    private static final int SLOT = 26;
    private static final int FRAME = 21;
    private static final int FRAME_PAD = (SLOT - FRAME) / 2;
    private static final int ICON = 13;
    private static final int ICON_PAD = FRAME_PAD + (FRAME - ICON) / 2;
    private static final float MIN_ZOOM = 0.55f;
    private static final float MAX_ZOOM = 2.25f;
    private static final float FIT_PADDING = 0.94f;
    private static final int BOUNDS_PADDING = 8;

    private float panX;
    private float panY;
    private float zoom = 1.0f;
    private boolean viewInitialized;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private float dragStartPanX;
    private float dragStartPanY;

    public void resetView() {
        panX = 0;
        panY = 0;
        zoom = 1.0f;
        viewInitialized = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta, int viewX, int viewY, int viewW, int viewH) {
        if (!inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            return false;
        }
        if (Minecraft.getInstance().options.keyShift.isDown()) {
            panY += (float) (scrollDelta * 24);
            return true;
        }

        float oldZoom = zoom;
        float next = Mth.clamp(zoom + (float) (scrollDelta * 0.12f), MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(next - oldZoom) < 0.0001f) {
            return true;
        }

        float localX = (float) (mouseX - viewX);
        float localY = (float) (mouseY - viewY);
        float graphX = (localX - panX) / oldZoom;
        float graphY = (localY - panY) / oldZoom;
        zoom = next;
        panX = localX - graphX * zoom;
        panY = localY - graphY * zoom;
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
            @Nullable EditorOverlay editorOverlay,
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
        gfx.pose().scale(zoom, zoom, 1.0f);

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
            drawEdges(gfx, tree, snap, positions, yShift, unlocked, skillLevel, zoom);
            drawNodes(gfx, section.skillId(), tree, snap, positions, yShift, unlocked, skillLevel, viewX, viewY, viewW, viewH, unlockHitsOut);
        }

        gfx.pose().popPose();
        gfx.disableScissor();

        Optional<HoverInfo> hover = pickForestHover(sections, viewX, viewY, viewW, viewH, mouseX, mouseY);
        if (hover.isPresent()) {
            HoverInfo info = hover.get();
            // Dim everything in the map viewport to emulate advancement hover focus.
            gfx.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x88000000);
            gfx.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
            drawNodeFrameAt(gfx, info.screenX(), info.screenY(), info.status(), info.challengeFrame(), false);
            drawIconAt(gfx, info.screenX(), info.screenY(), info.iconKey(), false);
            gfx.disableScissor();
        }
        if (editorOverlay != null && editorOverlay.enabled()) {
            drawEditorOverlay(gfx, font, sections, viewX, viewY, viewW, viewH, editorOverlay);
        }

        return hover.map(HoverInfo::tooltipLines);
    }

    public Optional<EditorNodePick> pickNodeAt(
            List<CompositeSection> sections,
            int viewX,
            int viewY,
            int viewW,
            int viewH,
            double mouseX,
            double mouseY
    ) {
        if (!inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            return Optional.empty();
        }
        int mx = (int) Math.round(mouseX);
        int my = (int) Math.round(mouseY);
        for (int s = sections.size() - 1; s >= 0; s--) {
            CompositeSection section = sections.get(s);
            SkillTreeDefinition tree = section.tree();
            TreeSnapshot snap = section.snap();
            Map<String, int[]> positions = section.positions();
            if (tree == null || snap.nodes().isEmpty() || positions.isEmpty()) {
                continue;
            }
            int yShift = section.yShift();
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
                int sx = toScreenX(viewX, gx);
                int sy = toScreenY(viewY, gy);
                int ss = Math.max(4, Math.round(SLOT * zoom));
                if (mx >= sx && mx < sx + ss && my >= sy && my < sy + ss) {
                    return Optional.of(new EditorNodePick(section.skillId(), node.id(), gx, gy));
                }
            }
        }
        return Optional.empty();
    }

    public int screenToGraphX(int viewX, double mouseX) {
        return Math.round((((float) mouseX - viewX) - panX) / zoom);
    }

    public int screenToGraphY(int viewY, double mouseY) {
        return Math.round((((float) mouseY - viewY) - panY) / zoom);
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
        float boundsW = Math.max(1.0f, maxX - minX);
        float boundsH = Math.max(1.0f, maxY - minY);
        float fitX = (viewW - BOUNDS_PADDING * 2.0f) / boundsW;
        float fitY = (viewH - BOUNDS_PADDING * 2.0f) / boundsH;
        float fitZoom = Math.min(fitX, fitY) * FIT_PADDING;
        zoom = Mth.clamp(fitZoom, MIN_ZOOM, MAX_ZOOM);

        float cx = (minX + maxX) / 2f;
        float cy = (minY + maxY) / 2f;
        panX = viewW / 2f - cx * zoom;
        panY = viewH / 2f - cy * zoom;
    }

    private static float[] forestBounds(List<CompositeSection> sections, @Nullable ResourceLocation onlySkillId) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        Font font = Minecraft.getInstance().font;
        for (CompositeSection section : sections) {
            if (onlySkillId != null && !onlySkillId.equals(section.skillId())) {
                continue;
            }
            int yShift = section.yShift();
            int ty = section.titleGraphY();
            int titleW = font != null ? font.width(displayNameForSkill(section.skillId())) : 80;
            minX = Math.min(minX, section.titleGraphX());
            minY = Math.min(minY, ty);
            maxX = Math.max(maxX, section.titleGraphX() + titleW);
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
                minX = Math.min(minX, gx - 4);
                minY = Math.min(minY, gy - 4);
                maxX = Math.max(maxX, gx + SLOT + 4);
                maxY = Math.max(maxY, gy + SLOT + 4);
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
            int skillLevel,
            float currentZoom
    ) {
        boolean lowDetail = currentZoom < 0.85f;
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
                SkillTreeProgression.NodeStatus parentStatus = statusFor(snap, req);
                if (lowDetail && ns.status() == SkillTreeProgression.NodeStatus.LOCKED_PREREQUISITE
                        && parentStatus == SkillTreeProgression.NodeStatus.LOCKED_PREREQUISITE) {
                    continue;
                }
                int color = lineColor(ns.status(), parentStatus);
                if (lowDetail && ns.status() != SkillTreeProgression.NodeStatus.UNLOCKED
                        && ns.status() != SkillTreeProgression.NodeStatus.AVAILABLE) {
                    color = withAlpha(color, 0.78f);
                }
                drawLine(gfx, x0, y0, x1, y1, color);
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
            int viewW,
            int viewH,
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
            boolean challenge = node.visibility() == SkillTreeNodeVisibility.OBFUSCATED;
            drawNodeFrame(gfx, gx, gy, ns.status(), challenge, dim);
            drawIcon(gfx, gx, gy, node.iconKey(), dim);

            int screenX = toScreenX(viewX, gx);
            int screenY = toScreenY(viewY, gy);
            int screenSlot = Math.max(4, Math.round(SLOT * zoom));
            if (ns.status() == SkillTreeProgression.NodeStatus.AVAILABLE) {
                if (screenX + screenSlot > viewX && screenX < viewX + viewW && screenY + screenSlot > viewY && screenY < viewY + viewH) {
                    unlockHitsOut.add(new SkillTreeUnlockHit(skillId, screenX, screenY, screenSlot, screenSlot, node.id()));
                }
            }
        }
    }

    private Optional<HoverInfo> pickForestHover(
            List<CompositeSection> sections,
            int viewX,
            int viewY,
            int viewW,
            int viewH,
            int mouseX,
            int mouseY
    ) {
        if (!inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            return Optional.empty();
        }
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
                int screenX = toScreenX(viewX, gx);
                int screenY = toScreenY(viewY, gy);
                int screenSlot = Math.max(4, Math.round(SLOT * zoom));
                if (mouseX >= screenX && mouseX < screenX + screenSlot && mouseY >= screenY && mouseY < screenY + screenSlot) {
                    return Optional.of(new HoverInfo(
                            screenX,
                            screenY,
                            node.iconKey(),
                            ns.status(),
                            node.visibility() == SkillTreeNodeVisibility.OBFUSCATED,
                            tooltipLines(tree, node, ns, skillLevel, unlocked)
                    ));
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
            return 0xE09AEFA6;
        }
        if (parent == SkillTreeProgression.NodeStatus.UNLOCKED) {
            return 0xD08FC0EC;
        }
        return 0x9A5B5B5B;
    }

    private static int withAlpha(int argb, float alphaScale) {
        int alpha = (argb >>> 24) & 0xFF;
        int next = Math.max(0, Math.min(255, Math.round(alpha * alphaScale)));
        return (next << 24) | (argb & 0x00FFFFFF);
    }

    private static void drawNodeFrame(
            GuiGraphics gfx,
            int gx,
            int gy,
            SkillTreeProgression.NodeStatus status,
            boolean challenge,
            boolean dim
    ) {
        drawNodeFrameAt(gfx, gx, gy, status, challenge, dim);
    }

    private static void drawNodeFrameAt(
            GuiGraphics gfx,
            int slotX,
            int slotY,
            SkillTreeProgression.NodeStatus status,
            boolean challenge,
            boolean dim
    ) {
        int x = slotX + FRAME_PAD;
        int y = slotY + FRAME_PAD;
        int outer = dim ? 0xFF2A2A2A : (challenge ? 0xFF7D4AAE : 0xFF3A3A3A);
        int inner = switch (status) {
            case UNLOCKED -> 0xFF1E3D22;
            case AVAILABLE -> 0xFF2A3550;
            default -> 0xFF252525;
        };
        if (status == SkillTreeProgression.NodeStatus.AVAILABLE) {
            outer = 0xFF6BC4FF;
        }
        // Task frame: square border. Challenge frame: add spiked corners.
        gfx.fill(x - 1, y - 1, x + FRAME + 1, y + FRAME + 1, outer);
        gfx.fill(x, y, x + FRAME, y + FRAME, inner);
        if (challenge) {
            int spike = 3;
            gfx.fill(x - spike, y + 4, x, y + 10, outer);
            gfx.fill(x + FRAME, y + 4, x + FRAME + spike, y + 10, outer);
            gfx.fill(x - spike, y + FRAME - 10, x, y + FRAME - 4, outer);
            gfx.fill(x + FRAME, y + FRAME - 10, x + FRAME + spike, y + FRAME - 4, outer);
            gfx.fill(x + 4, y - spike, x + 10, y, outer);
            gfx.fill(x + FRAME - 10, y - spike, x + FRAME - 4, y, outer);
            gfx.fill(x + 4, y + FRAME, x + 10, y + FRAME + spike, outer);
            gfx.fill(x + FRAME - 10, y + FRAME, x + FRAME - 4, y + FRAME + spike, outer);
        }
        if (status == SkillTreeProgression.NodeStatus.AVAILABLE) {
            gfx.fill(x - 1, y - 1, x + FRAME + 1, y, 0x90A8DFFF);
            gfx.fill(x - 1, y + FRAME, x + FRAME + 1, y + FRAME + 1, 0x90A8DFFF);
            gfx.fill(x - 1, y, x, y + FRAME, 0x90A8DFFF);
            gfx.fill(x + FRAME, y, x + FRAME + 1, y + FRAME, 0x90A8DFFF);
        }
        if (dim) {
            gfx.fill(x, y, x + FRAME, y + FRAME, 0x70000000);
        }
    }

    private static void drawIcon(GuiGraphics gfx, int gx, int gy, String iconKey, boolean dim) {
        drawIconAt(gfx, gx, gy, iconKey, dim);
    }

    private static void drawIconAt(GuiGraphics gfx, int slotX, int slotY, String iconKey, boolean dim) {
        int ix = slotX + ICON_PAD;
        int iy = slotY + ICON_PAD;
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

    private int toScreenX(int viewX, int graphX) {
        return viewX + Math.round(panX + graphX * zoom);
    }

    private int toScreenY(int viewY, int graphY) {
        return viewY + Math.round(panY + graphY * zoom);
    }

    private static boolean inside(double mx, double my, int vx, int vy, int vw, int vh) {
        return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }

    private void drawEditorOverlay(
            GuiGraphics gfx,
            Font font,
            List<CompositeSection> sections,
            int viewX,
            int viewY,
            int viewW,
            int viewH,
            EditorOverlay overlay
    ) {
        gfx.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        for (CompositeSection section : sections) {
            if (!section.skillId().equals(overlay.selectedSkillId())) {
                continue;
            }
            int[] selectedPos = section.positions().get(overlay.selectedNodeId());
            if (selectedPos != null) {
                int sx = toScreenX(viewX, selectedPos[0]);
                int sy = toScreenY(viewY, selectedPos[1] + section.yShift());
                int ss = Math.max(4, Math.round(SLOT * zoom));
                gfx.fill(sx - 1, sy - 1, sx + ss + 1, sy, 0xE0FFD56B);
                gfx.fill(sx - 1, sy + ss, sx + ss + 1, sy + ss + 1, 0xE0FFD56B);
                gfx.fill(sx - 1, sy, sx, sy + ss, 0xE0FFD56B);
                gfx.fill(sx + ss, sy, sx + ss + 1, sy + ss, 0xE0FFD56B);
                gfx.drawString(font, "EDIT", sx + ss + 3, sy + 2, 0xFFD56B, false);
            }
            if (overlay.mirrorNodeId() != null) {
                int[] mirrorPos = section.positions().get(overlay.mirrorNodeId());
                if (mirrorPos != null) {
                    int mx = toScreenX(viewX, mirrorPos[0]);
                    int my = toScreenY(viewY, mirrorPos[1] + section.yShift());
                    int ms = Math.max(4, Math.round(SLOT * zoom));
                    gfx.fill(mx - 1, my - 1, mx + ms + 1, my, 0xC06BC4FF);
                    gfx.fill(mx - 1, my + ms, mx + ms + 1, my + ms + 1, 0xC06BC4FF);
                    gfx.fill(mx - 1, my, mx, my + ms, 0xC06BC4FF);
                    gfx.fill(mx + ms, my, mx + ms + 1, my + ms, 0xC06BC4FF);
                }
            }
            break;
        }
        gfx.disableScissor();
    }

    public record SkillTreeUnlockHit(ResourceLocation skillId, int x, int y, int w, int h, String nodeId) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public record EditorNodePick(ResourceLocation skillId, String nodeId, int graphX, int graphY) {}

    public record EditorOverlay(
            boolean enabled,
            @Nullable ResourceLocation selectedSkillId,
            @Nullable String selectedNodeId,
            @Nullable String mirrorNodeId
    ) {}

    private record HoverInfo(
            int screenX,
            int screenY,
            String iconKey,
            SkillTreeProgression.NodeStatus status,
            boolean challengeFrame,
            List<Component> tooltipLines
    ) {}
}
