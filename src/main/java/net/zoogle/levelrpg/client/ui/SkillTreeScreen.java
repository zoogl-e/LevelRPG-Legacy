package net.zoogle.levelrpg.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
import net.zoogle.levelrpg.data.SkillTreeGraphLayout;
import net.zoogle.levelrpg.data.SkillTreeRegistry;
import net.zoogle.levelrpg.net.payload.RequestProfileSyncPayload;
import net.zoogle.levelrpg.net.payload.UnlockTreeNodeRequestPayload;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.SkillTreeProgression;
import net.zoogle.levelrpg.progression.SkillTreeProgression.TreeSnapshot;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Full-screen view of every canonical specialization tree in one pannable canvas.
 * {@code highlightSkillId} (from the journal) frames that discipline on first open when possible.
 */
public class SkillTreeScreen extends Screen {
    private static final Gson EDITOR_JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation MENU_TILE =
            ResourceLocation.withDefaultNamespace("textures/block/stone.png");
    private static final float NODE_DIAMETER = 26.0f;
    private static final float NODE_PADDING = 18.0f;
    private static final float MIN_CENTER_DISTANCE = NODE_DIAMETER + NODE_PADDING;
    private static final int RELAXATION_STEPS = 10;
    private static final float MAX_RELAX_SHIFT = 50.0f;
    private static final float EXPLICIT_MAX_RELAX_SHIFT = 28.0f;
    private static final float INTER_SKILL_REPULSION = 0.08f;
    private static final float DEPTH_STEP = 78.0f;
    private static final float LANE_BASE = 74.0f;
    private static final float LANE_GROWTH = 24.0f;
    private static final float INTRA_SPACING = 34.0f;

    private final ResourceLocation highlightSkillId;
    private final SkillTreeAdvancementMapPanel mapPanel = new SkillTreeAdvancementMapPanel();
    private final List<SkillTreeAdvancementMapPanel.SkillTreeUnlockHit> unlockHits = new ArrayList<>();
    private int mapViewX;
    private int mapViewY;
    private int mapViewW;
    private int mapViewH;
    private int saveBtnX;
    private int saveBtnY;
    private int saveBtnW;
    private int saveBtnH;
    private int revertBtnX;
    private int revertBtnY;
    private int revertBtnW;
    private int revertBtnH;
    private boolean editorMode;
    private boolean editorDragActive;
    private boolean editorDirty;
    private boolean editorDataLoaded;
    private ResourceLocation selectedSkillId;
    private String selectedNodeId;
    private String mirrorNodeId;
    private boolean dragRootLinked;
    private ResourceLocation dragSkillId;
    private String dragNodeId;
    private int dragNodeStartLocalX;
    private int dragNodeStartLocalY;
    private int dragMouseStartGraphX;
    private int dragMouseStartGraphY;
    private String editorStatusMessage = "";
    private List<SkillTreeAdvancementMapPanel.CompositeSection> lastSections = List.of();
    private final Map<ResourceLocation, Map<String, int[]>> editorPositions = new HashMap<>();
    private final Map<ResourceLocation, Map<String, int[]>> editorBasePositions = new HashMap<>();
    private final Map<ResourceLocation, SkillProjectionContext> projectionBySkill = new HashMap<>();

    public SkillTreeScreen(ResourceLocation highlightSkillId) {
        super(Component.empty());
        this.highlightSkillId = highlightSkillId;
    }

    @Override
    protected void init() {
        BlurUtil.pushNoBlur();
        mapPanel.resetView();
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(RequestProfileSyncPayload.INSTANCE);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        BlurUtil.popNoBlur();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editorMode && button == 0) {
            if (insideButton(mouseX, mouseY, saveBtnX, saveBtnY, saveBtnW, saveBtnH)) {
                saveEditorChanges();
                return true;
            }
            if (insideButton(mouseX, mouseY, revertBtnX, revertBtnY, revertBtnW, revertBtnH)) {
                revertEditorChanges();
                return true;
            }
        }
        if (editorMode && button == 0 && insideMap(mouseX, mouseY)) {
            Optional<SkillTreeAdvancementMapPanel.EditorNodePick> picked = mapPanel.pickNodeAt(
                    lastSections, mapViewX, mapViewY, mapViewW, mapViewH, mouseX, mouseY
            );
            if (picked.isPresent()) {
                SkillTreeAdvancementMapPanel.EditorNodePick pick = picked.get();
                beginEditorDrag(pick, mouseX, mouseY);
                return true;
            }
        }
        if (button == 0 && insideMap(mouseX, mouseY)) {
            if (editorMode && mapPanel.mouseClicked(mouseX, mouseY, button, mapViewX, mapViewY, mapViewW, mapViewH)) {
                return true;
            }
            for (int i = unlockHits.size() - 1; i >= 0; i--) {
                SkillTreeAdvancementMapPanel.SkillTreeUnlockHit h = unlockHits.get(i);
                if (h.contains(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new UnlockTreeNodeRequestPayload(h.skillId(), h.nodeId()));
                    return true;
                }
            }
            if (mapPanel.mouseClicked(mouseX, mouseY, button, mapViewX, mapViewY, mapViewW, mapViewH)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editorMode && button == 0 && editorDragActive) {
            editorDragActive = false;
            dragSkillId = null;
            dragNodeId = null;
            dragRootLinked = false;
            return true;
        }
        mapPanel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editorMode && button == 0 && editorDragActive) {
            updateEditorDrag(mouseX, mouseY);
            return true;
        }
        if (mapPanel.isDragging()) {
            if (mapPanel.mouseDragged(mouseX, mouseY, button, mapViewX, mapViewY, mapViewW, mapViewH)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        double scroll = scrollDeltaY != 0 ? scrollDeltaY : scrollDeltaX;
        if (insideMap(mouseX, mouseY) && mapPanel.mouseScrolled(mouseX, mouseY, scroll, mapViewX, mapViewY, mapViewW, mapViewH)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F6) { // F6 toggles editor mode
            editorMode = !editorMode;
            editorStatusMessage = editorMode ? "Editor enabled" : "Editor disabled";
            if (editorMode) {
                ensureEditorDataLoaded();
            }
            return true;
        }
        if (editorMode && Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_S) { // Ctrl+S
            saveEditorChanges();
            return true;
        }
        if (editorMode && Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_R) { // Ctrl+R
            revertEditorChanges();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean insideMap(double mouseX, double mouseY) {
        return mouseX >= mapViewX && mouseX < mapViewX + mapViewW && mouseY >= mapViewY && mouseY < mapViewY + mapViewH;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        unlockHits.clear();
        if (editorMode) {
            ensureEditorDataLoaded();
        }

        gfx.fill(0, 0, this.width, this.height, 0xC0101010);

        final int margin = 10;
        int panelX = margin;
        int panelY = margin;
        int panelW = Math.max(40, this.width - 2 * margin);
        int panelH = Math.max(40, this.height - 2 * margin);

        blitTiledPanel(gfx, MENU_TILE, panelX, panelY, panelW, panelH);
        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x30000000);

        int headerY = panelY + 5;
        int specEarned = SpecializationProgression.earnedPointsForTotalLevels(ClientProfileCache.totalInvestedLevelsAcrossSkills());
        int specSpent = ClientProfileCache.totalSpecializationSpentAcrossTrees();
        int unspent = Math.max(0, specEarned - specSpent);
        String left = Component.translatable("screen.levelrpg.skill_tree").getString();
        String right = "Unspent: " + unspent;
        gfx.drawString(this.font, left, panelX + 8, headerY, 0xFFFFFF, false);
        gfx.drawString(this.font, right, panelX + panelW - 8 - this.font.width(right), headerY, 0x8CFF8C, false);

        if (editorMode) {
            String tag = "[Editor]";
            gfx.drawString(this.font, tag, panelX + 8 + this.font.width(left) + 8, headerY, 0xFFD56B, false);
            String save = "Save";
            String revert = "Revert";
            saveBtnW = this.font.width(save) + 10;
            saveBtnH = 12;
            revertBtnW = this.font.width(revert) + 10;
            revertBtnH = 12;
            revertBtnX = panelX + panelW - 8 - revertBtnW;
            revertBtnY = headerY - 1;
            saveBtnX = revertBtnX - 6 - saveBtnW;
            saveBtnY = headerY - 1;
            drawEditorButton(gfx, saveBtnX, saveBtnY, saveBtnW, saveBtnH, save, 0xFF3D3D2A, editorDirty ? 0xFFD56B : 0xFF777777);
            drawEditorButton(gfx, revertBtnX, revertBtnY, revertBtnW, revertBtnH, revert, 0xFF2F2F2F, 0xFF9FA7B3);
        } else {
            saveBtnW = 0;
            revertBtnW = 0;
        }

        mapViewX = panelX + 4;
        mapViewY = headerY + 14;
        mapViewW = Math.max(20, panelW - 8);
        mapViewH = Math.max(20, panelY + panelH - mapViewY - 6);

        List<SkillTreeAdvancementMapPanel.CompositeSection> sections = buildCompositeSections(specEarned, specSpent, mapViewW, mapViewH);
        lastSections = sections;
        mapPanel.renderForest(
                        gfx, this.font, sections, highlightSkillId,
                        editorMode ? new SkillTreeAdvancementMapPanel.EditorOverlay(true, selectedSkillId, selectedNodeId, mirrorNodeId) : null,
                        mapViewX, mapViewY, mapViewW, mapViewH, mouseX, mouseY, unlockHits
                )
                .ifPresent(lines -> {
                    List<FormattedCharSequence> tip = new ArrayList<>();
                    for (Component line : lines) {
                        tip.addAll(this.font.split(line, Math.max(40, mapViewW - 8)));
                    }
                    gfx.renderTooltip(this.font, tip, mouseX, mouseY);
                });

        String esc = "Esc — close · drag pan · scroll";
        gfx.drawString(this.font, esc, panelX + panelW - 6 - this.font.width(esc), panelY + panelH - 12, 0x888888, false);
        if (editorMode) {
            String editorHint = "F6 toggle editor · drag node · Ctrl+S save · Ctrl+R revert";
            gfx.drawString(this.font, editorHint, panelX + 8, panelY + panelH - 12, 0xD0C890, false);
            if (!editorStatusMessage.isBlank()) {
                gfx.drawString(this.font, editorStatusMessage, panelX + 8, panelY + panelH - 24, 0xFFC56B, false);
            }
        }
    }

    private List<SkillTreeAdvancementMapPanel.CompositeSection> buildCompositeSections(int specEarned, int specSpent, int viewW, int viewH) {
        List<SkillLayoutDraft> drafts = new ArrayList<>();
        projectionBySkill.clear();
        ProgressionSkill[] skills = ProgressionSkill.values();
        int skillCount = Math.max(1, skills.length);
        float centerX = viewW / 2.0f;
        float centerY = viewH / 2.0f;
        float minDim = Math.max(120.0f, Math.min(viewW, viewH));
        LayoutSizing sizing = resolutionAwareSizing(minDim, skillCount);
        for (int index = 0; index < skills.length; index++) {
            ProgressionSkill ps = skills[index];
            ResourceLocation id = ps.id();
            SkillTreeDefinition tree = SkillTreeRegistry.get(id);
            SkillState skillState = ClientProfileCache.getSkillsView().get(id);
            int skillLevel = skillState != null ? Math.max(0, skillState.level) : 0;
            Set<String> unlockedNodes = ClientProfileCache.getTreeUnlockedNodes(id);
            TreeSnapshot snap = SkillTreeProgression.snapshot(id, tree, skillLevel, unlockedNodes, specEarned, specSpent);

            double theta = -Math.PI / 2.0 + ((Math.PI * 2.0) * index / skillCount);
            float outwardX = (float) Math.cos(theta);
            float outwardY = (float) Math.sin(theta);
            float inwardX = -outwardX;
            float inwardY = -outwardY;
            float perpX = -outwardY;
            float perpY = outwardX;
            int titleX = Math.round(centerX + outwardX * (sizing.outerRadius() + 48.0f));
            int titleY = Math.round(centerY + outwardY * (sizing.outerRadius() + 48.0f));
            projectionBySkill.put(id, new SkillProjectionContext(
                    centerX + outwardX * sizing.outerRadius(),
                    centerY + outwardY * sizing.outerRadius(),
                    perpX, perpY, inwardX, inwardY, sizing.scaleX(), sizing.scaleY()
            ));

            if (tree == null || snap.nodes().isEmpty()) {
                drafts.add(new SkillLayoutDraft(
                        id, tree, snap, titleX, titleY, outwardX, outwardY, centerX, centerY, Map.of(), Map.of()
                ));
                continue;
            }

            Map<String, int[]> positions = organicTemplateLayout(
                    tree,
                    centerX + outwardX * sizing.outerRadius(),
                    centerY + outwardY * sizing.outerRadius(),
                    perpX,
                    perpY,
                    inwardX,
                    inwardY,
                    sizing.scaleX(),
                    sizing.scaleY()
            );
            if (positions.isEmpty()) {
                Map<String, int[]> fallback = SkillTreeGraphLayout.resolve(tree);
                if (fallback.isEmpty()) {
                    drafts.add(new SkillLayoutDraft(
                            id, tree, snap, titleX, titleY, outwardX, outwardY, centerX, centerY, Map.of(), Map.of()
                    ));
                    continue;
                }
                positions = fallback;
            }
            applyEditorOverrides(id, tree, positions);

            Map<String, int[]> base = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> e : positions.entrySet()) {
                base.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
            }
            drafts.add(new SkillLayoutDraft(id, tree, snap, titleX, titleY, outwardX, outwardY, centerX, centerY, positions, base));
        }

        relaxLayout(drafts);

        List<SkillTreeAdvancementMapPanel.CompositeSection> list = new ArrayList<>();
        for (SkillLayoutDraft draft : drafts) {
            if (draft.positions().isEmpty()) {
                list.add(new SkillTreeAdvancementMapPanel.CompositeSection(
                        draft.skillId(),
                        draft.tree(),
                        draft.snap(),
                        draft.titleX(),
                        draft.titleY(),
                        0,
                        Map.of()
                ));
                continue;
            }

            int[] titleAnchor = farthestAlongDirection(
                    draft.positions(),
                    centerX,
                    centerY,
                    draft.outwardX(),
                    draft.outwardY()
            );
            int titleX = draft.titleX();
            int titleY = draft.titleY();
            if (titleAnchor != null) {
                titleX = Math.round(titleAnchor[0] + draft.outwardX() * 42.0f);
                titleY = Math.round(titleAnchor[1] + draft.outwardY() * 42.0f);
            }
            list.add(new SkillTreeAdvancementMapPanel.CompositeSection(draft.skillId(), draft.tree(), draft.snap(), titleX, titleY, 0, draft.positions()));
        }
        return list;
    }

    private static LayoutSizing resolutionAwareSizing(float minDim, int skillCount) {
        float baseWidth = 460.0f;

        float scaleX = clamp(minDim / 390.0f, 0.50f, 1.25f);
        float scaleY = clamp(minDim / 340.0f, 0.48f, 1.35f);
        float clusterWidth = baseWidth * scaleX + NODE_DIAMETER;
        float arcNeededPerSkill = clusterWidth + NODE_PADDING;
        float radiusFromArc = (arcNeededPerSkill * skillCount) / (float) (Math.PI * 2.0);

        float edgeBudget = minDim * 0.5f - 22.0f;
        float maxLateral = (baseWidth * scaleX) * 0.5f + NODE_DIAMETER;
        float maxRadius = Math.max(minDim * 0.42f, edgeBudget - maxLateral);
        float minRadius = minDim * 0.56f;
        float targetRadius = radiusFromArc * 1.10f;
        float outerRadius = clamp(targetRadius, minRadius, maxRadius);
        return new LayoutSizing(outerRadius, scaleX, scaleY);
    }

    private static void relaxLayout(List<SkillLayoutDraft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        for (int step = 0; step < RELAXATION_STEPS; step++) {
            Map<String, float[]> forces = new HashMap<>();
            for (SkillLayoutDraft draft : drafts) {
                for (String id : draft.positions().keySet()) {
                    forces.put(nodeKey(draft.skillId(), id), new float[]{0.0f, 0.0f});
                }
            }

            // Box-like repulsion, constrained to each skill cluster.
            for (SkillLayoutDraft draft : drafts) {
                List<Map.Entry<String, int[]>> nodes = new ArrayList<>(draft.positions().entrySet());
                for (int i = 0; i < nodes.size(); i++) {
                    Map.Entry<String, int[]> an = nodes.get(i);
                    for (int j = i + 1; j < nodes.size(); j++) {
                        Map.Entry<String, int[]> bn = nodes.get(j);
                        float ax = an.getValue()[0];
                        float ay = an.getValue()[1];
                        float bx = bn.getValue()[0];
                        float by = bn.getValue()[1];
                        float dx = ax - bx;
                        float dy = ay - by;
                        float ox = MIN_CENTER_DISTANCE - Math.abs(dx);
                        float oy = MIN_CENTER_DISTANCE - Math.abs(dy);
                        if (ox <= 0.0f || oy <= 0.0f) {
                            continue;
                        }
                        float[] fa = forces.get(nodeKey(draft.skillId(), an.getKey()));
                        float[] fb = forces.get(nodeKey(draft.skillId(), bn.getKey()));
                        boolean explicitA = isExplicitLayoutNode(draft.tree(), an.getKey());
                        boolean explicitB = isExplicitLayoutNode(draft.tree(), bn.getKey());
                        float repulsion = (explicitA || explicitB) ? 0.11f : 0.18f;
                        if (ox < oy) {
                            float push = ox * repulsion * Math.signum(dx == 0.0f ? 1.0f : dx);
                            fa[0] += push;
                            fb[0] -= push;
                        } else {
                            float push = oy * repulsion * Math.signum(dy == 0.0f ? 1.0f : dy);
                            fa[1] += push;
                            fb[1] -= push;
                        }
                    }
                }
            }

            // Cross-skill repulsion so neighboring trees account for each other.
            for (int a = 0; a < drafts.size(); a++) {
                SkillLayoutDraft da = drafts.get(a);
                List<Map.Entry<String, int[]>> nodesA = new ArrayList<>(da.positions().entrySet());
                for (int b = a + 1; b < drafts.size(); b++) {
                    SkillLayoutDraft db = drafts.get(b);
                    List<Map.Entry<String, int[]>> nodesB = new ArrayList<>(db.positions().entrySet());
                    for (Map.Entry<String, int[]> an : nodesA) {
                        for (Map.Entry<String, int[]> bn : nodesB) {
                            float ax = an.getValue()[0];
                            float ay = an.getValue()[1];
                            float bx = bn.getValue()[0];
                            float by = bn.getValue()[1];
                            float dx = ax - bx;
                            float dy = ay - by;
                            float ox = MIN_CENTER_DISTANCE - Math.abs(dx);
                            float oy = MIN_CENTER_DISTANCE - Math.abs(dy);
                            if (ox <= 0.0f || oy <= 0.0f) {
                                continue;
                            }
                            float[] fa = forces.get(nodeKey(da.skillId(), an.getKey()));
                            float[] fb = forces.get(nodeKey(db.skillId(), bn.getKey()));
                            if (ox < oy) {
                                float push = ox * INTER_SKILL_REPULSION * Math.signum(dx == 0.0f ? 1.0f : dx);
                                fa[0] += push;
                                fb[0] -= push;
                            } else {
                                float push = oy * INTER_SKILL_REPULSION * Math.signum(dy == 0.0f ? 1.0f : dy);
                                fa[1] += push;
                                fb[1] -= push;
                            }
                        }
                    }
                }
            }

            // Apply spring back toward organic base.
            for (SkillLayoutDraft draft : drafts) {
                for (Map.Entry<String, int[]> e : draft.positions().entrySet()) {
                    String id = e.getKey();
                    int[] p = e.getValue();
                    int[] base = draft.basePositions().get(id);
                    if (base == null) {
                        continue;
                    }
                    float[] f = forces.get(nodeKey(draft.skillId(), id));
                    float bx = base[0];
                    float by = base[1];
                    float px = p[0];
                    float py = p[1];
                    boolean explicit = isExplicitLayoutNode(draft.tree(), id);
                    float spring = explicit ? 0.16f : 0.10f;
                    f[0] += (bx - px) * spring;
                    f[1] += (by - py) * spring;
                    float nextX = px + f[0];
                    float nextY = py + f[1];
                    float shiftX = nextX - bx;
                    float shiftY = nextY - by;
                    float shiftMag = (float) Math.sqrt(shiftX * shiftX + shiftY * shiftY);
                    float maxShift = explicit ? EXPLICIT_MAX_RELAX_SHIFT : MAX_RELAX_SHIFT;
                    if (shiftMag > maxShift) {
                        float k = maxShift / shiftMag;
                        nextX = bx + shiftX * k;
                        nextY = by + shiftY * k;
                    }
                    p[0] = Math.round(nextX);
                    p[1] = Math.round(nextY);
                }
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void ensureEditorDataLoaded() {
        if (editorDataLoaded) {
            return;
        }
        editorPositions.clear();
        editorBasePositions.clear();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            ResourceLocation id = skill.id();
            SkillTreeDefinition tree = SkillTreeRegistry.get(id);
            if (tree == null) {
                continue;
            }
            Map<String, int[]> perTree = new LinkedHashMap<>();
            for (Map.Entry<String, SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
                SkillTreeDefinition.Node node = e.getValue();
                int lx = node.layoutX() == SkillTreeGraphLayout.AUTO ? 0 : node.layoutX();
                int ly = node.layoutY() == SkillTreeGraphLayout.AUTO ? 0 : node.layoutY();
                perTree.put(e.getKey(), new int[]{lx, ly});
            }
            editorPositions.put(id, deepCopy(perTree));
            editorBasePositions.put(id, deepCopy(perTree));
        }
        editorDataLoaded = true;
    }

    private static Map<String, int[]> deepCopy(Map<String, int[]> src) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : src.entrySet()) {
            out.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
        }
        return out;
    }

    private void applyEditorOverrides(ResourceLocation skillId, SkillTreeDefinition tree, Map<String, int[]> graphPositions) {
        if (!editorMode || tree == null) {
            return;
        }
        Map<String, int[]> local = editorPositions.get(skillId);
        SkillProjectionContext ctx = projectionBySkill.get(skillId);
        if (local == null || ctx == null) {
            return;
        }
        for (Map.Entry<String, int[]> e : local.entrySet()) {
            SkillTreeDefinition.Node node = tree.nodes().get(e.getKey());
            if (node == null) {
                continue;
            }
            int[] projected = projectLocalToGraph(node, e.getValue()[0], e.getValue()[1], ctx);
            graphPositions.put(e.getKey(), projected);
        }
    }

    private int[] projectLocalToGraph(SkillTreeDefinition.Node node, int localLayoutX, int localLayoutY, SkillProjectionContext ctx) {
        float lateral = isCenterBranch(node.branch()) ? 0.50f : 0.58f;
        float inward = isCenterBranch(node.branch()) ? 0.50f : 0.44f;
        float depthNorm = clamp(localLayoutY / 480.0f, 0.0f, 1.0f);
        lateral *= (1.0f - 0.18f * depthNorm);
        inward *= (1.0f + 0.14f * depthNorm);
        float localX = localLayoutX * (ctx.scaleX() * lateral);
        float localY = localLayoutY * (ctx.scaleY() * inward);
        float[] drift = nodeDrift(node.id());
        localX += drift[0];
        localY += drift[1];
        float gx = ctx.anchorX() + ctx.perpX() * localX + ctx.inwardX() * localY;
        float gy = ctx.anchorY() + ctx.perpY() * localX + ctx.inwardY() * localY;
        return new int[]{Math.round(gx), Math.round(gy)};
    }

    private int[] projectGraphToLocal(ResourceLocation skillId, SkillTreeDefinition.Node node, int graphX, int graphY) {
        SkillProjectionContext ctx = projectionBySkill.get(skillId);
        if (ctx == null || node == null) {
            return new int[]{0, 0};
        }
        float dx = graphX - ctx.anchorX();
        float dy = graphY - ctx.anchorY();
        float perp = dx * ctx.perpX() + dy * ctx.perpY();
        float inward = dx * ctx.inwardX() + dy * ctx.inwardY();
        float lateral = isCenterBranch(node.branch()) ? 0.50f : 0.58f;
        float inwardScale = isCenterBranch(node.branch()) ? 0.50f : 0.44f;
        float depthNorm = clamp(Math.abs(inward) / (480.0f * Math.max(0.2f, ctx.scaleY() * inwardScale)), 0.0f, 1.0f);
        lateral *= (1.0f - 0.18f * depthNorm);
        inwardScale *= (1.0f + 0.14f * depthNorm);
        float[] drift = nodeDrift(node.id());
        int localX = Math.round((perp - drift[0]) / Math.max(0.01f, ctx.scaleX() * lateral));
        int localY = Math.round((inward - drift[1]) / Math.max(0.01f, ctx.scaleY() * inwardScale));
        return new int[]{localX, localY};
    }

    private static float[] nodeDrift(String nodeId) {
        float driftX = ((nodeId.hashCode() & 0x7) - 3.5f) * 1.0f;
        float driftY = ((((nodeId.hashCode() >> 3) & 0x7) - 3.5f) * 0.85f);
        return new float[]{driftX, driftY};
    }

    private void beginEditorDrag(SkillTreeAdvancementMapPanel.EditorNodePick pick, double mouseX, double mouseY) {
        ensureEditorDataLoaded();
        Map<String, int[]> local = editorPositions.get(pick.skillId());
        if (local == null) {
            return;
        }
        int[] loc = local.get(pick.nodeId());
        if (loc == null) {
            return;
        }
        editorDragActive = true;
        selectedSkillId = pick.skillId();
        selectedNodeId = pick.nodeId();
        dragSkillId = pick.skillId();
        dragNodeId = pick.nodeId();
        dragNodeStartLocalX = loc[0];
        dragNodeStartLocalY = loc[1];
        dragMouseStartGraphX = mapPanel.screenToGraphX(mapViewX, mouseX);
        dragMouseStartGraphY = mapPanel.screenToGraphY(mapViewY, mouseY);
        SkillTreeDefinition tree = SkillTreeRegistry.get(pick.skillId());
        SkillTreeDefinition.Node node = tree != null ? tree.nodes().get(pick.nodeId()) : null;
        dragRootLinked = node != null && node.requires().isEmpty();
        mirrorNodeId = findMirrorNodeId(pick.skillId(), pick.nodeId());
    }

    private void updateEditorDrag(double mouseX, double mouseY) {
        if (dragSkillId == null || dragNodeId == null) {
            return;
        }
        SkillTreeDefinition tree = SkillTreeRegistry.get(dragSkillId);
        if (tree == null) {
            return;
        }
        SkillTreeDefinition.Node node = tree.nodes().get(dragNodeId);
        if (node == null) {
            return;
        }
        Map<String, int[]> local = editorPositions.get(dragSkillId);
        if (local == null) {
            return;
        }
        int graphX = mapPanel.screenToGraphX(mapViewX, mouseX);
        int graphY = mapPanel.screenToGraphY(mapViewY, mouseY);
        int graphDx = graphX - dragMouseStartGraphX;
        int graphDy = graphY - dragMouseStartGraphY;
        int[] startGraph = projectLocalToGraph(node, dragNodeStartLocalX, dragNodeStartLocalY, projectionBySkill.get(dragSkillId));
        int[] nextLocal = projectGraphToLocal(dragSkillId, node, startGraph[0] + graphDx, startGraph[1] + graphDy);
        local.put(dragNodeId, nextLocal);
        if (dragRootLinked) {
            moveAllRootNodes(nextLocal[0], nextLocal[1]);
        } else {
            moveCorrespondingNodes(dragSkillId, dragNodeId, nextLocal[0], nextLocal[1]);
        }
        if (mirrorNodeId != null) {
            int[] mirror = local.get(mirrorNodeId);
            if (mirror != null) {
                mirror[0] = -nextLocal[0];
                mirror[1] = nextLocal[1];
            }
        }
        editorDirty = true;
        editorStatusMessage = "Dragging " + dragNodeId;
    }

    private void moveAllRootNodes(int localX, int localY) {
        for (Map.Entry<ResourceLocation, Map<String, int[]>> treeEntry : editorPositions.entrySet()) {
            SkillTreeDefinition tree = SkillTreeRegistry.get(treeEntry.getKey());
            if (tree == null) {
                continue;
            }
            Map<String, int[]> local = treeEntry.getValue();
            for (Map.Entry<String, SkillTreeDefinition.Node> nodeEntry : tree.nodes().entrySet()) {
                SkillTreeDefinition.Node node = nodeEntry.getValue();
                if (!node.requires().isEmpty()) {
                    continue;
                }
                int[] p = local.get(nodeEntry.getKey());
                if (p != null) {
                    p[0] = localX;
                    p[1] = localY;
                }
            }
        }
    }

    private void moveCorrespondingNodes(ResourceLocation sourceSkillId, String sourceNodeId, int localX, int localY) {
        Map<String, int[]> sourceLocal = editorPositions.get(sourceSkillId);
        SkillTreeDefinition sourceTree = SkillTreeRegistry.get(sourceSkillId);
        if (sourceLocal == null || sourceTree == null) {
            return;
        }
        SkillTreeDefinition.Node sourceNode = sourceTree.nodes().get(sourceNodeId);
        if (sourceNode == null || sourceNode.requires().isEmpty()) {
            return;
        }
        NodeSignature signature = signatureFor(sourceTree, sourceLocal, sourceNodeId);
        if (signature == null) {
            return;
        }
        for (Map.Entry<ResourceLocation, Map<String, int[]>> e : editorPositions.entrySet()) {
            ResourceLocation skillId = e.getKey();
            if (skillId.equals(sourceSkillId)) {
                continue;
            }
            SkillTreeDefinition tree = SkillTreeRegistry.get(skillId);
            if (tree == null) {
                continue;
            }
            String targetNodeId = findBySignature(tree, e.getValue(), signature);
            if (targetNodeId == null) {
                continue;
            }
            int[] p = e.getValue().get(targetNodeId);
            if (p == null) {
                continue;
            }
            p[0] = localX;
            p[1] = localY;
            String mirror = findMirrorNodeId(skillId, targetNodeId);
            if (mirror != null) {
                int[] mp = e.getValue().get(mirror);
                if (mp != null) {
                    mp[0] = -localX;
                    mp[1] = localY;
                }
            }
        }
    }

    private static NodeSignature signatureFor(SkillTreeDefinition tree, Map<String, int[]> local, String nodeId) {
        SkillTreeDefinition.Node node = tree.nodes().get(nodeId);
        int[] p = local.get(nodeId);
        if (node == null || p == null) {
            return null;
        }
        Map<String, Integer> memoDepth = new HashMap<>();
        int depth = nodeDepth(tree, nodeId, memoDepth);
        int side = Integer.compare(p[0], 0);
        boolean center = isCenterBranch(node.branch());
        int branchIndex = 0;
        if (!center) {
            List<String> same = new ArrayList<>();
            for (Map.Entry<String, SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
                if (!Objects.equals(node.branch(), e.getValue().branch())) {
                    continue;
                }
                int d = nodeDepth(tree, e.getKey(), memoDepth);
                if (d == depth) {
                    same.add(e.getKey());
                }
            }
            same.sort(Comparator.comparingInt(id -> Math.abs(local.getOrDefault(id, new int[]{0, 0})[0])));
            branchIndex = same.indexOf(nodeId);
            if (branchIndex < 0) {
                branchIndex = 0;
            }
        }
        return new NodeSignature(center, depth, side, branchIndex);
    }

    private static String findBySignature(SkillTreeDefinition tree, Map<String, int[]> local, NodeSignature target) {
        String bestId = null;
        int bestScore = Integer.MAX_VALUE;
        for (String id : tree.orderedNodeIds()) {
            NodeSignature sig = signatureFor(tree, local, id);
            if (sig == null) {
                continue;
            }
            int score = 0;
            score += sig.center() == target.center() ? 0 : 100;
            score += Math.abs(sig.depth() - target.depth()) * 20;
            score += sig.side() == target.side() ? 0 : 12;
            score += Math.abs(sig.branchIndex() - target.branchIndex()) * 4;
            if (score < bestScore) {
                bestScore = score;
                bestId = id;
            }
        }
        return bestId;
    }

    private String findMirrorNodeId(ResourceLocation skillId, String nodeId) {
        SkillTreeDefinition tree = SkillTreeRegistry.get(skillId);
        Map<String, int[]> local = editorPositions.get(skillId);
        if (tree == null || local == null) {
            return null;
        }
        SkillTreeDefinition.Node source = tree.nodes().get(nodeId);
        if (source == null || isCenterBranch(source.branch())) {
            return null;
        }
        BranchPair pair = detectBranchPair(tree, local);
        if (pair == null) {
            return null;
        }
        String targetBranch;
        if (Objects.equals(source.branch(), pair.left())) {
            targetBranch = pair.right();
        } else if (Objects.equals(source.branch(), pair.right())) {
            targetBranch = pair.left();
        } else {
            return null;
        }
        int[] src = local.get(nodeId);
        if (src == null) {
            return null;
        }
        String bestId = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map.Entry<String, SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
            if (!Objects.equals(targetBranch, e.getValue().branch())) {
                continue;
            }
            int[] p = local.get(e.getKey());
            if (p == null) {
                continue;
            }
            int score = Math.abs(p[1] - src[1]) * 3 + Math.abs(Math.abs(p[0]) - Math.abs(src[0]));
            if (score < bestScore) {
                bestScore = score;
                bestId = e.getKey();
            }
        }
        return bestId;
    }

    private static BranchPair detectBranchPair(SkillTreeDefinition tree, Map<String, int[]> local) {
        Map<String, Float> avgX = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Map.Entry<String, SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
            SkillTreeDefinition.Node node = e.getValue();
            if (isCenterBranch(node.branch()) || node.branch().isBlank()) {
                continue;
            }
            int[] p = local.get(e.getKey());
            if (p == null) {
                continue;
            }
            avgX.put(node.branch(), avgX.getOrDefault(node.branch(), 0.0f) + p[0]);
            count.put(node.branch(), count.getOrDefault(node.branch(), 0) + 1);
        }
        List<String> branches = new ArrayList<>(avgX.keySet());
        branches.sort(Comparator.comparing(b -> avgX.get(b) / Math.max(1, count.get(b))));
        if (branches.size() < 2) {
            return null;
        }
        return new BranchPair(branches.get(0), branches.get(branches.size() - 1));
    }

    private void saveEditorChanges() {
        if (!editorMode) {
            return;
        }
        ensureEditorDataLoaded();
        int saved = 0;
        for (Map.Entry<ResourceLocation, Map<String, int[]>> e : editorPositions.entrySet()) {
            if (saveTreeLayout(e.getKey(), e.getValue())) {
                saved++;
                editorBasePositions.put(e.getKey(), deepCopy(e.getValue()));
            }
        }
        editorDirty = false;
        editorStatusMessage = "Saved " + saved + " skill tree layout file(s)";
    }

    private void revertEditorChanges() {
        ensureEditorDataLoaded();
        editorPositions.clear();
        for (Map.Entry<ResourceLocation, Map<String, int[]>> e : editorBasePositions.entrySet()) {
            editorPositions.put(e.getKey(), deepCopy(e.getValue()));
        }
        editorDirty = false;
        editorStatusMessage = "Reverted unsaved editor changes";
    }

    private boolean saveTreeLayout(ResourceLocation skillId, Map<String, int[]> local) {
        try {
            Path path = Path.of("src/main/resources/data/levelrpg/skill_trees", skillId.getPath() + ".json");
            if (!Files.exists(path)) {
                return false;
            }
            Path backup = Path.of(path.toString() + ".bak");
            Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = EDITOR_JSON.fromJson(raw, JsonObject.class);
            JsonArray nodes = root.getAsJsonArray("nodes");
            for (JsonElement el : nodes) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject node = el.getAsJsonObject();
                String id = node.has("id") ? node.get("id").getAsString() : "";
                int[] p = local.get(id);
                if (p == null) {
                    continue;
                }
                node.addProperty("layoutX", p[0]);
                node.addProperty("layoutY", p[1]);
            }
            Files.writeString(path, EDITOR_JSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Exception ex) {
            editorStatusMessage = "Failed saving " + skillId.getPath() + ": " + ex.getMessage();
            return false;
        }
    }

    private static boolean insideButton(double mouseX, double mouseY, int x, int y, int w, int h) {
        return w > 0 && h > 0 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawEditorButton(GuiGraphics gfx, int x, int y, int w, int h, String text, int bg, int fg) {
        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, 0xFF555555);
        gfx.fill(x, y + h - 1, x + w, y + h, 0xFF111111);
        gfx.drawString(this.font, text, x + 5, y + 2, fg, false);
    }

    private static String nodeKey(ResourceLocation skillId, String nodeId) {
        return skillId + "|" + nodeId;
    }

    private static Map<String, int[]> organicTemplateLayout(
            SkillTreeDefinition tree,
            float anchorX,
            float anchorY,
            float perpX,
            float perpY,
            float inwardX,
            float inwardY,
            float scaleX,
            float scaleY
    ) {
        if (tree == null || tree.nodes().isEmpty()) {
            return Map.of();
        }

        // Prefer explicit authordata when available; transform into this skill's local frame.
        Map<String, int[]> explicit = new LinkedHashMap<>();
        for (String id : tree.orderedNodeIds()) {
            SkillTreeDefinition.Node node = tree.nodes().get(id);
            if (node == null) {
                continue;
            }
            if (node.layoutX() != SkillTreeGraphLayout.AUTO && node.layoutY() != SkillTreeGraphLayout.AUTO) {
                // Keep authored branch spread while preventing all trees from collapsing into center.
                float lateral = isCenterBranch(node.branch()) ? 0.50f : 0.58f;
                float inward = isCenterBranch(node.branch()) ? 0.50f : 0.44f;
                float depthNorm = clamp(node.layoutY() / 480.0f, 0.0f, 1.0f);
                lateral *= (1.0f - 0.18f * depthNorm);
                inward *= (1.0f + 0.14f * depthNorm);
                float localX = node.layoutX() * (scaleX * lateral);
                float localY = node.layoutY() * (scaleY * inward);
                // Small deterministic drift avoids sterile symmetry while preserving authored topology.
                float driftX = ((node.id().hashCode() & 0x7) - 3.5f) * 1.0f;
                float driftY = ((((node.id().hashCode() >> 3) & 0x7) - 3.5f) * 0.85f);
                localX += driftX;
                localY += driftY;
                float gx = anchorX + perpX * localX + inwardX * localY;
                float gy = anchorY + perpY * localX + inwardY * localY;
                explicit.put(id, new int[]{Math.round(gx), Math.round(gy)});
            }
        }
        if (explicit.size() >= Math.max(3, tree.nodes().size() / 2)) {
            return explicit;
        }

        Map<String, Integer> memoDepth = new HashMap<>();
        Map<Integer, List<String>> idsByDepth = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String id : tree.orderedNodeIds()) {
            SkillTreeDefinition.Node node = tree.nodes().get(id);
            if (node == null) {
                continue;
            }
            for (String req : node.requires()) {
                children.computeIfAbsent(req, unused -> new ArrayList<>()).add(id);
            }
            int depth = nodeDepth(tree, id, memoDepth);
            idsByDepth.computeIfAbsent(depth, unused -> new ArrayList<>()).add(id);
        }

        String leftBranch = "";
        String rightBranch = "";
        List<String> roots = new ArrayList<>();
        for (String id : tree.orderedNodeIds()) {
            SkillTreeDefinition.Node node = tree.nodes().get(id);
            if (node != null && node.requires().isEmpty()) {
                roots.add(id);
            }
        }
        if (!roots.isEmpty()) {
            String root = roots.get(0);
            List<String> firstTier = new ArrayList<>(children.getOrDefault(root, List.of()));
            firstTier.sort(Comparator.comparing(id -> tree.nodes().get(id).id()));
            List<String> nonCenter = new ArrayList<>();
            for (String id : firstTier) {
                SkillTreeDefinition.Node n = tree.nodes().get(id);
                String b = n == null ? "" : n.branch();
                if (!isCenterBranch(b) && !b.isBlank() && !nonCenter.contains(b)) {
                    nonCenter.add(b);
                }
            }
            if (!nonCenter.isEmpty()) {
                leftBranch = nonCenter.get(0);
                rightBranch = nonCenter.size() > 1 ? nonCenter.get(1) : nonCenter.get(0);
            }
        }

        Map<String, Float> localXById = new HashMap<>();
        Map<String, int[]> placed = new LinkedHashMap<>();
        List<Integer> depths = new ArrayList<>(idsByDepth.keySet());
        depths.sort(Integer::compareTo);
        for (int depth : depths) {
            List<String> ids = idsByDepth.getOrDefault(depth, List.of());
            Map<Integer, List<String>> byLane = new HashMap<>();
            for (String id : ids) {
                SkillTreeDefinition.Node node = tree.nodes().get(id);
                if (node == null) {
                    continue;
                }
                int lane = laneFor(node.branch(), leftBranch, rightBranch);
                byLane.computeIfAbsent(lane, unused -> new ArrayList<>()).add(id);
            }
            for (Map.Entry<Integer, List<String>> laneEntry : byLane.entrySet()) {
                int lane = laneEntry.getKey();
                List<String> laneIds = laneEntry.getValue();
                laneIds.sort(Comparator
                        .comparing((String id) -> parentAvgX(tree, id, localXById))
                        .thenComparing(id -> id));
                float laneCenter = laneCenterX(lane, depth);
                float start = laneCenter - ((laneIds.size() - 1) * INTRA_SPACING * 0.5f);
                float localY = depth * DEPTH_STEP;
                for (int i = 0; i < laneIds.size(); i++) {
                    String id = laneIds.get(i);
                    float parentBias = parentAvgX(tree, id, localXById) * 0.20f;
                    float localX = start + (i * INTRA_SPACING) + parentBias;
                    localXById.put(id, localX);
                    float gx = anchorX + perpX * (localX * scaleX) + inwardX * (localY * scaleY);
                    float gy = anchorY + perpY * (localX * scaleX) + inwardY * (localY * scaleY);
                    placed.put(id, new int[]{Math.round(gx), Math.round(gy)});
                }
            }
        }
        return placed;
    }

    private static int nodeDepth(SkillTreeDefinition tree, String id, Map<String, Integer> memoDepth) {
        Integer cached = memoDepth.get(id);
        if (cached != null) {
            return cached;
        }
        SkillTreeDefinition.Node node = tree.nodes().get(id);
        if (node == null || node.requires().isEmpty()) {
            memoDepth.put(id, 0);
            return 0;
        }
        int depth = 0;
        for (String req : node.requires()) {
            depth = Math.max(depth, nodeDepth(tree, req, memoDepth) + 1);
        }
        memoDepth.put(id, depth);
        return depth;
    }

    private static boolean isCenterBranch(String branch) {
        String b = branch == null ? "" : branch.toLowerCase();
        return b.equals("core") || b.equals("cross");
    }

    private static int laneFor(String branch, String left, String right) {
        String b = branch == null ? "" : branch;
        if (isCenterBranch(b)) {
            return 0;
        }
        if (!left.isBlank() && b.equals(left)) {
            return -1;
        }
        if (!right.isBlank() && b.equals(right)) {
            return 1;
        }
        return 0;
    }

    private static float laneCenterX(int lane, int depth) {
        if (lane == 0) {
            return 0.0f;
        }
        float magnitude = LANE_BASE + (Math.max(0, depth - 1) * LANE_GROWTH);
        return lane < 0 ? -magnitude : magnitude;
    }

    private static float parentAvgX(SkillTreeDefinition tree, String id, Map<String, Float> localXById) {
        SkillTreeDefinition.Node node = tree.nodes().get(id);
        if (node == null || node.requires().isEmpty()) {
            return 0.0f;
        }
        float sum = 0.0f;
        int count = 0;
        for (String req : node.requires()) {
            Float x = localXById.get(req);
            if (x != null) {
                sum += x;
                count++;
            }
        }
        return count == 0 ? 0.0f : sum / count;
    }

    private static boolean isExplicitLayoutNode(SkillTreeDefinition tree, String nodeId) {
        if (tree == null) {
            return false;
        }
        SkillTreeDefinition.Node node = tree.nodes().get(nodeId);
        return node != null
                && node.layoutX() != SkillTreeGraphLayout.AUTO
                && node.layoutY() != SkillTreeGraphLayout.AUTO;
    }

    private static int[] farthestAlongDirection(
            Map<String, int[]> positions,
            float centerX,
            float centerY,
            float dirX,
            float dirY
    ) {
        int[] best = null;
        float bestDot = Float.NEGATIVE_INFINITY;
        for (int[] p : positions.values()) {
            float dot = (p[0] - centerX) * dirX + (p[1] - centerY) * dirY;
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    private record LayoutSizing(float outerRadius, float scaleX, float scaleY) {}

    private record SkillProjectionContext(
            float anchorX,
            float anchorY,
            float perpX,
            float perpY,
            float inwardX,
            float inwardY,
            float scaleX,
            float scaleY
    ) {}

    private record BranchPair(String left, String right) {}

    private record NodeSignature(boolean center, int depth, int side, int branchIndex) {}

    private record SkillLayoutDraft(
            ResourceLocation skillId,
            SkillTreeDefinition tree,
            TreeSnapshot snap,
            int titleX,
            int titleY,
            float outwardX,
            float outwardY,
            float centerX,
            float centerY,
            Map<String, int[]> positions,
            Map<String, int[]> basePositions
    ) {}

    private static void blitTiledPanel(GuiGraphics gfx, ResourceLocation texture, int x, int y, int w, int h) {
        final int tw = 16;
        final int th = 16;
        int x1 = x + w;
        int y1 = y + h;
        for (int tx = x; tx < x1; tx += tw) {
            for (int ty = y; ty < y1; ty += th) {
                int bw = Math.min(tw, x1 - tx);
                int bh = Math.min(th, y1 - ty);
                gfx.blit(texture, tx, ty, bw, bh, 0, 0, bw, bh, tw, th);
            }
        }
        int edge = 0xFF2B2B2B;
        gfx.fill(x, y, x + w, y + 1, edge);
        gfx.fill(x, y + h - 1, x + w, y + h, edge);
        gfx.fill(x, y, x + 1, y + h, edge);
        gfx.fill(x + w - 1, y, x + w, y + h, edge);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
