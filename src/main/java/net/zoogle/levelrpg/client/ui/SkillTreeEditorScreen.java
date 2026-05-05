package net.zoogle.levelrpg.client.ui;

import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.EnchiridionJournalOpener;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.client.ui.skilltree.editor.SkillChamberRenderer;
import net.zoogle.levelrpg.client.ui.skilltree.editor.SkillChamberViewState;
import net.zoogle.levelrpg.client.skilltree.SkillTreeCameraTransform;
import net.zoogle.levelrpg.client.skilltree.SkillTreeEditorDraft;
import net.zoogle.levelrpg.client.skilltree.SkillTreeEditorDraft.NodeType;
import net.zoogle.levelrpg.client.skilltree.SkillTreeEditorDraft.RequirementTarget;
import net.zoogle.levelrpg.client.skilltree.SkillTreeInputState;
import net.zoogle.levelrpg.client.skilltree.SkillTreeNodeView;
import net.zoogle.levelrpg.client.skilltree.SkillTreeRenderer;
import net.zoogle.levelrpg.client.skilltree.SkillTreeTooltipRenderer;
import net.zoogle.levelrpg.net.payload.RequestProfileSyncPayload;
import net.zoogle.levelrpg.net.payload.UnlockTreeNodeRequestPayload;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import net.zoogle.levelrpg.skilltree.NodeVisibilityMode;
import net.zoogle.levelrpg.skilltree.RequirementSpec;
import net.zoogle.levelrpg.skilltree.SkillNodeImplementationRegistry;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeRegistry;
import net.zoogle.levelrpg.skilltree.SkillTreeState;
import net.zoogle.levelrpg.skilltree.SkillTreeStateResolver;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Developer / editor tool</b> – a 2-D, advancement-graph–style view of a single LevelRPG skill
 * tree that doubles as a live JSON editor.
 *
 * <p><b>Entry point:</b> the server-side command {@code /levelrpg tree open <skill>} sends an
 * {@code OpenSkillTreeEditorScreenPayload} to the target client, which calls
 * {@link net.zoogle.levelrpg.net.Network#openSkillTreeEditorScreen} and eventually pushes this screen via
 * {@code Minecraft#setScreen}. Enchiridion also opens it through a reflection bridge in
 * {@code LevelRpgJournalInteractionBridge#openSkillScreen} when the player taps the skill name
 * inside the journal (fallback path, not the primary player-facing flow).
 *
 * <p><b>Editor mode:</b> pressing {@code E} while the screen is open activates an in-GUI editor
 * panel that allows repositioning nodes, editing metadata, wiring requirements, and saving the
 * result back to the skill-tree JSON on disk. This is intentionally kept as a developer tool and
 * should not be shown to normal players.
 *
 * <p><b>Do not rename or move this class</b> without also updating the fully-qualified class name
 * string in {@code LevelRpgJournalInteractionBridge#LEVEL_RPG_SKILL_TREE_SCREEN_CLASS} and the
 * two-argument constructor signature it expects via reflection.
 *
 * @see net.zoogle.levelrpg.client.ui.skilltree.editor.SkillChamberViewState
 * @see net.zoogle.levelrpg.client.ui.skilltree.editor.SkillChamberRenderer
 */
public class SkillTreeEditorScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OUTER_MARGIN = 10;
    private static final int HEADER_HEIGHT = 28;
    private static final int NODE_SIZE = 30;
    private static final int EDITOR_PANEL_WIDTH = 320;
    private static final int EDITOR_CONTENT_HEIGHT = 430;
    private static final int ALIGN_SNAP_DISTANCE = 10;
    private static final float TREE_FADE_OUT_PER_TICK = 0.30f;
    private static final float TREE_FADE_IN_PER_TICK = 0.18f;
    private static final float TREE_FADE_EPSILON = 0.01f;
    private static final Map<ResourceLocation, SkillTreePresentationDefinition> SAVED_PREVIEW_DEFINITIONS = new LinkedHashMap<>();

    private final int returnBookSpreadIndex;
    private final SkillChamberViewState chamber;
    private final SkillChamberRenderer chamberRenderer = new SkillChamberRenderer();
    private final SkillTreeRenderer renderer = new SkillTreeRenderer();
    private final SkillTreeTooltipRenderer tooltipRenderer = new SkillTreeTooltipRenderer();
    private final SkillTreeInputState input = new SkillTreeInputState();
    private final List<SkillTreeNodeView> nodeViews = new ArrayList<>();
    private final Map<String, SkillTreeNodeView> nodeViewById = new LinkedHashMap<>();
    private final Map<ResourceLocation, ChamberTreeView> chamberTreeViews = new LinkedHashMap<>();

    private SkillTreePresentationDefinition definition;
    private SkillTreeState state;
    private SkillTreePresentationDefinition exportedPreviewDefinition;
    private SkillTreePresentationDefinition metadataBaseDefinition;
    private SkillTreePresentationDefinition metadataMergedDefinition;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private boolean centered;
    private SkillTreeNodeView hoveredNode;
    private boolean editorMode;
    private boolean connectMode;
    private boolean draggingNode;
    private boolean syncingEditorFields;
    private int editorScroll;
    private String selectedNodeId;
    private SkillTreeEditorDraft editorDraft;
    private String editorStatus = "";
    private double nodeDragOffsetX;
    private double nodeDragOffsetY;

    /** Rebuild skill definitions / chamber views only when set (avoids clearing maps every render frame). */
    private boolean treeDirty = true;
    private long lastSeenProfileRevision = Long.MIN_VALUE;
    private TreeTransitionState treeTransitionState = TreeTransitionState.STABLE;
    private ChamberTreeView outgoingTreeView;
    private ChamberTreeView incomingTreeView;
    private float outgoingTreeAlpha = 1.0f;
    private float incomingTreeAlpha = 1.0f;

    private Button newNodeButton;
    private Button deleteNodeButton;
    private Button connectButton;
    private Button saveButton;
    private Button discardButton;
    private EditBox idField;
    private EditBox titleField;
    private MultiLineEditBox descriptionField;
    private EditBox costField;
    private EditBox levelField;
    private EditBox iconField;
    private CycleButton<NodeType> typeButton;
    private CycleButton<NodeVisibilityMode> visibilityButton;
    private CycleButton<RequirementTarget> requirementTargetButton;
    private CycleButton<RequirementSpec.Mode> requirementModeButton;
    private EditBox requirementCountField;
    private EditBox requirementNodesField;

    public SkillTreeEditorScreen(ResourceLocation skillId) {
        this(skillId, -1);
    }

    public SkillTreeEditorScreen(ResourceLocation skillId, int returnBookSpreadIndex) {
        super(Component.literal("LevelRPG Skill Tree"));
        this.returnBookSpreadIndex = returnBookSpreadIndex;
        this.chamber = new SkillChamberViewState(skillId);
    }

    @Override
    protected void init() {
        BlurUtil.pushNoBlur();
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(RequestProfileSyncPayload.INSTANCE);
        }
        markTreeDirty();
        flushTreeIfDirty();
        lastSeenProfileRevision = ClientProfileCache.getLastUpdatedMs();
        layoutViewport();
        initEditorWidgets();
        layoutEditorWidgets();
        centerViewOnce();
    }

    @Override
    public void removed() {
        BlurUtil.popNoBlur();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!EnchiridionJournalOpener.openLevelRpgJournal(minecraft)) {
            minecraft.setScreen(null);
            return;
        }
        if (returnBookSpreadIndex >= 0 && minecraft.screen != null) {
            try {
                Method jumpToSpread = minecraft.screen.getClass().getMethod("jumpToSpread", int.class);
                jumpToSpread.invoke(minecraft.screen, returnBookSpreadIndex);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("Unable to return LevelRPG journal to spread {}", returnBookSpreadIndex, exception);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editorMode && insideEditorPanel(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && insideViewport(mouseX, mouseY)) {
            if (isTreeTransitioning() && !editorMode) {
                return true;
            }
            hoveredNode = findHoveredNode(mouseX, mouseY);
            if (editorMode) {
                handleEditorViewportClick(mouseX, mouseY, hoveredNode);
                return true;
            }
            if (hoveredNode != null && hoveredNode.status() == SkillNodeStatus.AVAILABLE) {
                requestUnlockNode(activeSkillId(), hoveredNode.definition().id());
                return true;
            }
            input.beginDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingNode) {
            draggingNode = false;
            return true;
        }
        if (button == 0 && input.isDragging()) {
            input.endDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingNode) {
            dragSelectedNodeTo(mouseX, mouseY);
            return true;
        }
        if (button == 0 && input.isDragging()) {
            input.dragTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editorMode && insideEditorPanel(mouseX, mouseY)) {
            editorScroll = clampEditorScroll(editorScroll - (int) Math.round(scrollY * 24.0));
            layoutEditorWidgets();
            return true;
        }
        if (!insideViewport(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (Screen.hasShiftDown()) {
            input.panVertically(scrollY * 24.0);
        } else {
            input.zoomAround(mouseX, mouseY, viewportX, viewportY, scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (textFieldFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) {
            rotateChamberPrevious();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) {
            rotateChamberNext();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E && canUseEditor()) {
            setEditorMode(!editorMode);
            return true;
        }
        if (editorMode && keyCode == GLFW.GLFW_KEY_N) {
            createNodeAtViewCenter();
            return true;
        }
        if (editorMode && keyCode == GLFW.GLFW_KEY_C) {
            toggleConnectMode();
            return true;
        }
        if (editorMode && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            deleteSelectedNode();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            resetDefaultView();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (chamber.isRotating()) {
            chamber.updateAnimation(1.0f);
        }
        updateTreeTransition();
    }

    private void rotateChamberPrevious() {
        ChamberTreeView previous = chamberTreeViews.get(activeSkillId());
        if (chamber.rotatePrevious()) {
            prepareFocusedSkillChange(previous);
        }
    }

    private void rotateChamberNext() {
        ChamberTreeView previous = chamberTreeViews.get(activeSkillId());
        if (chamber.rotateNext()) {
            prepareFocusedSkillChange(previous);
        }
    }

    private void prepareFocusedSkillChange(ChamberTreeView previousView) {
        hoveredNode = null;
        selectedNodeId = null;
        connectMode = false;
        draggingNode = false;
        input.endDrag();
        exportedPreviewDefinition = null;
        editorDraft = editorMode ? createDraftFromCurrent() : null;
        metadataBaseDefinition = null;
        metadataMergedDefinition = null;
        centered = false;
        markTreeDirty();
        flushTreeIfDirty();
        incomingTreeView = chamberTreeViews.get(activeSkillId());
        beginTreeTransition(previousView, incomingTreeView);
        syncEditorFields();
    }

    private ResourceLocation activeSkillId() {
        return chamber.getFocusedSkill();
    }

    private SkillTreeCameraTransform chamberTransform(ChamberTreeView treeView, SkillTreeCameraTransform focusedCameraTransform, boolean focused) {
        SkillChamberViewState.Viewport viewport = new SkillChamberViewState.Viewport(viewportX, viewportY, viewportW, viewportH);
        SkillChamberViewState.ScreenPosition position = chamber.getSkillTreeScreenPosition(treeView.skillId(), viewport);
        int centerX = viewportX + viewportW / 2;
        int centerY = viewportY + viewportH / 2;
        double[] activeCenterPan = centeredPan(chamberTreeViews.get(activeSkillId()));
        double[] treeCenterPan = centeredPan(treeView);
        double userPanX = focused ? focusedCameraTransform.panX() - activeCenterPan[0] : 0.0;
        double userPanY = focused ? focusedCameraTransform.panY() - activeCenterPan[1] : 0.0;
        return new SkillTreeCameraTransform(
                viewportX,
                viewportY,
                treeCenterPan[0] + userPanX + position.x() - centerX,
                treeCenterPan[1] + userPanY + position.y() - centerY,
                focusedCameraTransform.zoom()
        );
    }

    private SkillTreeCameraTransform activeTreeTransform() {
        return input.transform(viewportX, viewportY);
    }

    private double[] centeredPan(ChamberTreeView treeView) {
        if (treeView == null || treeView.nodes().isEmpty()) {
            return new double[]{viewportW / 2.0, Math.max(54.0, viewportH / 2.0)};
        }
        int minX = treeView.nodes().stream().mapToInt(SkillTreeNodeView::x).min().orElse(0);
        int maxX = treeView.nodes().stream().mapToInt(SkillTreeNodeView::x).max().orElse(0);
        int minY = treeView.nodes().stream().mapToInt(SkillTreeNodeView::y).min().orElse(0);
        int maxY = treeView.nodes().stream().mapToInt(SkillTreeNodeView::y).max().orElse(0);
        int contentCenterX = (minX + maxX) / 2;
        int contentCenterY = (minY + maxY) / 2;
        return new double[]{viewportW / 2.0 - contentCenterX, Math.max(54.0, viewportH / 2.0 - contentCenterY)};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        noteProfileRevision();
        flushTreeIfDirty();
        layoutViewport();
        layoutEditorWidgets();
        centerViewOnce();
        SkillTreeCameraTransform focusedTransform = input.transform(viewportX, viewportY);
        hoveredNode = insideViewport(mouseX, mouseY) && !isTreeTransitioning() ? findHoveredNode(mouseX, mouseY) : null;

        graphics.fill(0, 0, width, height, 0xD0100D0A);
        drawPanel(graphics);
        drawHeader(graphics);
        drawFooter(graphics);
        if (editorMode) {
            drawEditorPanel(graphics);
        }

        if (definition == null) {
            chamberRenderer.render(graphics, font, chamber, viewportX, viewportY, viewportW, viewportH);
            drawMissingTree(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        chamberRenderer.render(graphics, font, chamber, viewportX, viewportY, viewportW, viewportH);
        renderer.render(graphics, font, null, List.of(), Map.of(), viewportX, viewportY, viewportW, viewportH, focusedTransform, null, null, false, true, false);
        ChamberTreeView focusedTreeView = chamberTreeViews.get(activeSkillId());
        if (treeTransitionState == TreeTransitionState.FADING_OUT && outgoingTreeView != null) {
            renderer.render(
                    graphics,
                    font,
                    outgoingTreeView.definition(),
                    outgoingTreeView.nodes(),
                    outgoingTreeView.nodeById(),
                    viewportX,
                    viewportY,
                    viewportW,
                    viewportH,
                    focusedTransform,
                    null,
                    null,
                    false,
                    false,
                    false,
                    outgoingTreeAlpha
            );
        } else if (treeTransitionState == TreeTransitionState.FADING_IN && incomingTreeView != null) {
            renderer.render(
                    graphics,
                    font,
                    incomingTreeView.definition(),
                    incomingTreeView.nodes(),
                    incomingTreeView.nodeById(),
                    viewportX,
                    viewportY,
                    viewportW,
                    viewportH,
                    focusedTransform,
                    null,
                    null,
                    false,
                    false,
                    false,
                    incomingTreeAlpha
            );
        } else if (focusedTreeView != null) {
            renderer.render(
                    graphics,
                    font,
                    focusedTreeView.definition(),
                    focusedTreeView.nodes(),
                    focusedTreeView.nodeById(),
                    viewportX,
                    viewportY,
                    viewportW,
                    viewportH,
                    focusedTransform,
                    hoveredNode,
                    selectedNodeId,
                    connectMode,
                    false,
                    false,
                    1.0f
            );
        }
        if (hoveredNode != null && !isTreeTransitioning()) {
            tooltipRenderer.render(graphics, font, activeSkillId(), hoveredNode, width, height, focusedTransform);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void markTreeDirty() {
        treeDirty = true;
    }

    private void noteProfileRevision() {
        long rev = ClientProfileCache.getLastUpdatedMs();
        if (rev != lastSeenProfileRevision) {
            lastSeenProfileRevision = rev;
            markTreeDirty();
        }
    }

    private void flushTreeIfDirty() {
        if (!treeDirty) {
            return;
        }
        treeDirty = false;
        rebuildTreeData();
    }

    private void rebuildTreeData() {
        ResourceLocation activeSkillId = activeSkillId();
        chamberTreeViews.clear();
        SkillTreePresentationDefinition loaded = exportedPreviewDefinition != null
                ? exportedPreviewDefinition
                : SAVED_PREVIEW_DEFINITIONS.getOrDefault(activeSkillId, SkillTreeRegistry.get(activeSkillId));
        loaded = mergeSourceMetadata(loaded);
        if (editorMode) {
            if (editorDraft == null) {
                editorDraft = createDraftFromCurrent();
            }
            definition = editorDraft.toDefinition();
            LinkedHashMap<String, SkillNodeStatus> statuses = new LinkedHashMap<>();
            for (String nodeId : definition.nodes().keySet()) {
                statuses.put(nodeId, SkillNodeStatus.AVAILABLE);
            }
            LinkedHashMap<String, Boolean> revealed = new LinkedHashMap<>();
            LinkedHashMap<String, Boolean> rendered = new LinkedHashMap<>();
            LinkedHashMap<String, Boolean> obfuscated = new LinkedHashMap<>();
            for (String nodeId : definition.nodes().keySet()) {
                revealed.put(nodeId, true);
                rendered.put(nodeId, true);
                obfuscated.put(nodeId, false);
            }
            state = new SkillTreeState(activeSkillId, 0, 0, Set.of(), statuses, revealed, rendered, obfuscated);
            rebuildViews();
        } else {
            definition = loaded;
            state = resolveTreeState(activeSkillId, definition);
            rebuildViews();
        }
    }

    private void rebuildViews() {
        nodeViews.clear();
        nodeViewById.clear();
        if (definition == null) {
            return;
        }
        populateNodeViews(definition, state, nodeViews, nodeViewById);
        chamberTreeViews.put(activeSkillId(), new ChamberTreeView(activeSkillId(), definition, state, List.copyOf(nodeViews), Map.copyOf(nodeViewById)));
    }

    private ChamberTreeView buildReadonlyTreeView(ResourceLocation skillId) {
        SkillTreePresentationDefinition treeDefinition = SAVED_PREVIEW_DEFINITIONS.getOrDefault(skillId, SkillTreeRegistry.get(skillId));
        if (treeDefinition == null) {
            return null;
        }
        SkillTreeState treeState = resolveTreeState(skillId, treeDefinition);
        ArrayList<SkillTreeNodeView> views = new ArrayList<>();
        LinkedHashMap<String, SkillTreeNodeView> viewsById = new LinkedHashMap<>();
        populateNodeViews(treeDefinition, treeState, views, viewsById);
        return new ChamberTreeView(skillId, treeDefinition, treeState, List.copyOf(views), Map.copyOf(viewsById));
    }

    private SkillTreeState resolveTreeState(ResourceLocation skillId, SkillTreePresentationDefinition treeDefinition) {
        if (treeDefinition == null) {
            return null;
        }
        int rank = 0;
        SkillState skillState = ClientProfileCache.getSkillsView().get(skillId);
        if (skillState != null) {
            rank = Math.max(0, skillState.level);
        }
        int earned = SpecializationProgression.gainedInsightForTotalLevels(ClientProfileCache.totalInvestedLevelsAcrossSkills())
                + ClientProfileCache.getBonusSpecializationPoints();
        int spent = ClientProfileCache.totalSpecializationSpentAcrossTrees();
        int available = Math.max(0, earned - spent);
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(skillId);
        return SkillTreeStateResolver.resolve(skillId, treeDefinition, rank, available, unlocked);
    }

    private void populateNodeViews(
            SkillTreePresentationDefinition treeDefinition,
            SkillTreeState treeState,
            List<SkillTreeNodeView> views,
            Map<String, SkillTreeNodeView> viewsById
    ) {
        for (SkillTreeNodeDefinition node : treeDefinition.nodes().values()) {
            SkillTreeNodeView view = new SkillTreeNodeView(
                    node,
                    treeState.status(node.id()),
                    node.x(),
                    node.y(),
                    NODE_SIZE,
                    treeState.isRendered(node.id()),
                    treeState.isRevealed(node.id()),
                    treeState.isObfuscated(node.id())
            );
            views.add(view);
            viewsById.put(node.id(), view);
        }
        views.sort(Comparator.comparingInt(view -> view.status() == SkillNodeStatus.HIDDEN ? 1 : 0));
    }

    private void layoutViewport() {
        viewportX = OUTER_MARGIN + 6;
        viewportY = OUTER_MARGIN + HEADER_HEIGHT;
        int editorInset = editorMode ? EDITOR_PANEL_WIDTH + 8 : 0;
        viewportW = Math.max(40, width - (OUTER_MARGIN + 6) * 2 - editorInset);
        viewportH = Math.max(40, height - viewportY - OUTER_MARGIN - 6);
    }

    private void centerViewOnce() {
        if (centered || definition == null || nodeViews.isEmpty()) {
            return;
        }
        int minX = nodeViews.stream().mapToInt(SkillTreeNodeView::x).min().orElse(0);
        int maxX = nodeViews.stream().mapToInt(SkillTreeNodeView::x).max().orElse(0);
        int minY = nodeViews.stream().mapToInt(SkillTreeNodeView::y).min().orElse(0);
        int maxY = nodeViews.stream().mapToInt(SkillTreeNodeView::y).max().orElse(0);
        input.centerOn(viewportW, viewportH, minX, minY, maxX, maxY);
        centered = true;
    }

    private void resetDefaultView() {
        layoutViewport();
        centered = false;
        centerViewOnce();
    }

    private void initEditorWidgets() {
        int x = editorPanelX() + 9;
        int y = viewportY + 46;
        int fieldW = EDITOR_PANEL_WIDTH - 18;

        idField = addRenderableWidget(new EditBox(font, x, y, fieldW, 18, Component.literal("Id")));
        idField.setMaxLength(96);
        titleField = addRenderableWidget(new EditBox(font, x, y + 31, fieldW, 18, Component.literal("Title")));
        titleField.setMaxLength(160);
        titleField.setSuggestion("New Node");
        descriptionField = addRenderableWidget(new MultiLineEditBox(
                font,
                x,
                y + 50,
                fieldW,
                58,
                Component.literal("Description"),
                Component.literal("Description")
        ));
        descriptionField.setCharacterLimit(512);
        costField = addRenderableWidget(new EditBox(font, 0, 0, 52, 18, Component.literal("Cost")));
        levelField = addRenderableWidget(new EditBox(font, 0, 0, 52, 18, Component.literal("Level")));
        iconField = addRenderableWidget(new EditBox(font, 0, 0, fieldW, 18, Component.literal("Icon")));
        iconField.setMaxLength(160);
        typeButton = addRenderableWidget(CycleButton.builder((NodeType type) -> Component.literal(type.label()))
                .withValues(NodeType.values())
                .withInitialValue(NodeType.TRAIT)
                .create(0, 0, 10, 20, Component.literal("Type"), (button, type) -> applyEditorFields()));
        visibilityButton = addRenderableWidget(CycleButton.builder((NodeVisibilityMode visibility) -> Component.literal(visibility.jsonName()))
                .withValues(NodeVisibilityMode.values())
                .withInitialValue(NodeVisibilityMode.VISIBLE)
                .create(0, 0, 10, 20, Component.literal("Visibility"), (button, visibility) -> applyEditorFields()));
        requirementTargetButton = addRenderableWidget(CycleButton.builder((RequirementTarget target) -> Component.literal(target.label()))
                .withValues(RequirementTarget.values())
                .withInitialValue(RequirementTarget.UNLOCK)
                .create(0, 0, 10, 20, Component.literal("Editing"), (button, target) -> syncRequirementFields()));
        requirementModeButton = addRenderableWidget(CycleButton.builder((RequirementSpec.Mode mode) -> Component.literal(mode.jsonName()))
                .withValues(RequirementSpec.Mode.values())
                .withInitialValue(RequirementSpec.Mode.ALL)
                .create(0, 0, 10, 20, Component.literal("Mode"), (button, mode) -> applyEditorFields()));
        requirementCountField = addRenderableWidget(new EditBox(font, 0, 0, 48, 18, Component.literal("Count")));
        requirementNodesField = addRenderableWidget(new EditBox(font, 0, 0, fieldW, 18, Component.literal("Requirement Nodes")));
        requirementNodesField.setMaxLength(512);
        idField.setResponder(unused -> applyEditorFields());
        titleField.setResponder(unused -> applyEditorFields());
        descriptionField.setValueListener(unused -> applyEditorFields());
        costField.setResponder(unused -> applyEditorFields());
        levelField.setResponder(unused -> applyEditorFields());
        iconField.setResponder(unused -> applyEditorFields());
        requirementCountField.setResponder(unused -> applyEditorFields());
        requirementNodesField.setResponder(unused -> applyEditorFields());

        newNodeButton = addRenderableWidget(Button.builder(Component.literal("New"), button -> createNodeAtViewCenter()).bounds(0, 0, 78, 20).build());
        deleteNodeButton = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteSelectedNode()).bounds(0, 0, 78, 20).build());
        connectButton = addRenderableWidget(Button.builder(Component.literal("Connect"), button -> toggleConnectMode()).bounds(0, 0, 132, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), button -> exportDraft()).bounds(0, 0, 78, 20).build());
        discardButton = addRenderableWidget(Button.builder(Component.literal("Discard"), button -> discardDraft()).bounds(0, 0, 78, 20).build());
        layoutEditorWidgets();
        setEditorWidgetsVisible(false);
    }

    private void layoutEditorWidgets() {
        if (idField == null) {
            return;
        }
        int x = editorPanelX() + 9;
        int y = viewportY + 48 - editorScroll;
        int fieldW = EDITOR_PANEL_WIDTH - 18;
        idField.setRectangle(fieldW, 18, x, y);
        titleField.setRectangle(fieldW, 18, x, y + 25);
        descriptionField.setX(x);
        descriptionField.setY(y + 50);
        costField.setRectangle(52, 18, x, y + 113);
        levelField.setRectangle(52, 18, x + 64, y + 113);
        typeButton.setRectangle(174, 20, x + 128, y + 112);
        iconField.setRectangle(fieldW, 18, x, y + 145);
        visibilityButton.setRectangle(140, 20, x, y + 178);
        requirementTargetButton.setRectangle(160, 20, x + 144, y + 178);
        requirementModeButton.setRectangle(104, 20, x, y + 211);
        requirementCountField.setRectangle(48, 18, x + 112, y + 212);
        requirementNodesField.setRectangle(fieldW, 18, x, y + 244);

        int buttonY = y + 270;
        newNodeButton.setRectangle(78, 20, x, buttonY);
        deleteNodeButton.setRectangle(78, 20, x + 84, buttonY);
        connectButton.setRectangle(132, 20, x + 168, buttonY);
        saveButton.setRectangle(78, 20, x, buttonY + 26);
        discardButton.setRectangle(78, 20, x + 84, buttonY + 26);
        updateEditorWidgetClipping();
    }

    private void setEditorMode(boolean enabled) {
        editorMode = enabled;
        connectMode = false;
        draggingNode = false;
        if (enabled && editorDraft == null) {
            editorDraft = createDraftFromCurrent();
        }
        if (!enabled) {
            selectedNodeId = null;
        }
        layoutViewport();
        editorScroll = clampEditorScroll(editorScroll);
        setEditorWidgetsVisible(enabled);
        markTreeDirty();
        flushTreeIfDirty();
        syncEditorFields();
        editorStatus = enabled ? "Editor enabled" : "";
    }

    private void setEditorWidgetsVisible(boolean visible) {
        titleField.visible = visible;
        titleField.active = visible;
        idField.visible = visible;
        idField.active = visible;
        descriptionField.visible = visible;
        descriptionField.active = visible;
        costField.visible = visible;
        costField.active = visible;
        levelField.visible = visible;
        levelField.active = visible;
        iconField.visible = visible;
        iconField.active = visible;
        typeButton.visible = visible;
        typeButton.active = visible;
        visibilityButton.visible = visible;
        visibilityButton.active = visible;
        requirementTargetButton.visible = visible;
        requirementTargetButton.active = visible;
        requirementModeButton.visible = visible;
        requirementModeButton.active = visible;
        requirementCountField.visible = visible;
        requirementCountField.active = visible;
        requirementNodesField.visible = visible;
        requirementNodesField.active = visible;
        newNodeButton.visible = visible;
        newNodeButton.active = visible;
        deleteNodeButton.visible = visible;
        connectButton.visible = visible;
        saveButton.visible = visible;
        saveButton.active = visible;
        discardButton.visible = visible;
        discardButton.active = visible;
        updateEditorWidgetState();
    }

    private void updateEditorWidgetState() {
        if (deleteNodeButton != null) {
            boolean hasSelection = editorMode && selectedNodeId != null;
            idField.active = hasSelection && idField.visible;
            titleField.active = hasSelection && titleField.visible;
            descriptionField.active = hasSelection && descriptionField.visible;
            costField.active = hasSelection && costField.visible;
            levelField.active = hasSelection && levelField.visible;
            iconField.active = hasSelection && iconField.visible;
            typeButton.active = hasSelection && typeButton.visible;
            visibilityButton.active = hasSelection && visibilityButton.visible;
            requirementTargetButton.active = hasSelection && requirementTargetButton.visible;
            requirementModeButton.active = hasSelection && requirementModeButton.visible;
            requirementCountField.active = hasSelection && requirementCountField.visible;
            requirementNodesField.active = hasSelection && requirementNodesField.visible;
            deleteNodeButton.active = hasSelection;
            connectButton.active = hasSelection;
            connectButton.setMessage(Component.literal(connectMode ? "Connecting" : "Connect"));
        }
    }

    private void handleEditorViewportClick(double mouseX, double mouseY, SkillTreeNodeView clicked) {
        if (connectMode && selectedNodeId != null && clicked != null) {
            RequirementTarget target = requirementTargetButton == null ? RequirementTarget.UNLOCK : requirementTargetButton.getValue();
            editorDraft.toggleRequirement(selectedNodeId, clicked.definition().id(), target);
            editorStatus = "Toggled " + target.label() + ": " + clicked.definition().id();
            connectMode = false;
            markTreeDirty();
            flushTreeIfDirty();
            syncEditorFields();
            return;
        }
        if (clicked != null) {
            selectNode(clicked.definition().id());
            SkillTreeCameraTransform transform = activeTreeTransform();
            nodeDragOffsetX = transform.screenToGraphX(mouseX) - clicked.x();
            nodeDragOffsetY = transform.screenToGraphY(mouseY) - clicked.y();
            draggingNode = true;
            return;
        }
        input.beginDrag(mouseX, mouseY);
    }

    private void selectNode(String nodeId) {
        selectedNodeId = nodeId;
        connectMode = false;
        syncEditorFields();
        updateEditorWidgetState();
    }

    private void dragSelectedNodeTo(double mouseX, double mouseY) {
        if (editorDraft == null || selectedNodeId == null) {
            return;
        }
        SkillTreeCameraTransform transform = activeTreeTransform();
        int x = (int) Math.round(transform.screenToGraphX(mouseX) - nodeDragOffsetX);
        int y = (int) Math.round(transform.screenToGraphY(mouseY) - nodeDragOffsetY);
        int[] snapped = snapToNearbyAlignment(x, y);
        x = snapped[0];
        y = snapped[1];
        editorDraft.moveNode(selectedNodeId, x, y);
        markTreeDirty();
        flushTreeIfDirty();
    }

    private int[] snapToNearbyAlignment(int x, int y) {
        if (definition == null || selectedNodeId == null) {
            return new int[]{x, y};
        }
        int snappedX = x;
        int snappedY = y;
        int bestDx = ALIGN_SNAP_DISTANCE + 1;
        int bestDy = ALIGN_SNAP_DISTANCE + 1;
        for (SkillTreeNodeDefinition node : definition.nodes().values()) {
            if (node.id().equals(selectedNodeId)) {
                continue;
            }
            int dx = Math.abs(node.x() - x);
            if (dx <= ALIGN_SNAP_DISTANCE && dx < bestDx) {
                snappedX = node.x();
                bestDx = dx;
            }
            int dy = Math.abs(node.y() - y);
            if (dy <= ALIGN_SNAP_DISTANCE && dy < bestDy) {
                snappedY = node.y();
                bestDy = dy;
            }
        }
        return new int[]{snappedX, snappedY};
    }

    private void createNodeAtViewCenter() {
        if (!editorMode || editorDraft == null) {
            return;
        }
        SkillTreeCameraTransform transform = activeTreeTransform();
        int x = editorDraft.isEmpty() ? 0 : (int) Math.round(transform.screenToGraphX(viewportX + viewportW / 2.0));
        int y = editorDraft.isEmpty() ? 0 : (int) Math.round(transform.screenToGraphY(viewportY + viewportH / 2.0));
        selectNode(editorDraft.createNode(x, y));
        editorStatus = "Created " + selectedNodeId;
        markTreeDirty();
        flushTreeIfDirty();
    }

    private void deleteSelectedNode() {
        if (!editorMode || editorDraft == null || selectedNodeId == null) {
            return;
        }
        String deleted = selectedNodeId;
        editorDraft.deleteNode(selectedNodeId);
        selectedNodeId = null;
        connectMode = false;
        editorStatus = "Deleted " + deleted;
        markTreeDirty();
        flushTreeIfDirty();
        syncEditorFields();
    }

    private void toggleConnectMode() {
        if (!editorMode || selectedNodeId == null) {
            return;
        }
        connectMode = !connectMode;
        editorStatus = connectMode ? "Click another node to toggle requirement" : "Connect mode off";
        updateEditorWidgetState();
    }

    private void applyEditorFields() {
        if (syncingEditorFields || !editorMode || editorDraft == null || selectedNodeId == null) {
            return;
        }
        String currentId = selectedNodeId;
        String requestedId = idField.getValue();
        SkillTreeEditorDraft.RenameResult rename = editorDraft.renameNode(currentId, requestedId);
        if (rename.renamed()) {
            selectedNodeId = rename.nodeId();
            editorStatus = "Renamed " + currentId + " to " + selectedNodeId;
        } else if (!rename.warning().isBlank()) {
            editorStatus = rename.warning();
            syncingEditorFields = true;
            idField.setValue(currentId);
            syncingEditorFields = false;
        }
        String icon = iconField.getValue().trim();
        if (!icon.isBlank() && !SkillTreeEditorDraft.isValidResourceLocation(icon)) {
            editorStatus = "Icon must be namespace:path";
            icon = editorDraft.node(selectedNodeId) == null ? "" : editorDraft.node(selectedNodeId).icon();
        }
        editorDraft.updateNode(
                selectedNodeId,
                titleField.getValue(),
                descriptionField.getValue(),
                parseInt(costField.getValue(), 1),
                parseInt(levelField.getValue(), 0),
                icon
        );
        editorDraft.updateNodeType(selectedNodeId, typeButton.getValue());
        editorDraft.updateNodeVisibility(selectedNodeId, visibilityButton.getValue());
        editorDraft.updateRequirement(
                selectedNodeId,
                requirementTargetButton.getValue(),
                requirementModeButton.getValue(),
                parseRequirementNodeList(requirementNodesField.getValue()),
                parseInt(requirementCountField.getValue(), 1)
        );
        updateTitleSuggestion();
        markTreeDirty();
        flushTreeIfDirty();
    }

    private void syncEditorFields() {
        if (titleField == null) {
            return;
        }
        SkillTreeEditorDraft.DraftNode node = editorDraft == null || selectedNodeId == null ? null : editorDraft.node(selectedNodeId);
        syncingEditorFields = true;
        idField.setValue(node == null ? "" : node.id());
        titleField.setValue(node == null ? "" : node.title());
        descriptionField.setValue(node == null ? "" : node.description());
        costField.setValue(node == null ? "" : Integer.toString(node.cost()));
        levelField.setValue(node == null ? "" : Integer.toString(node.requiredRank()));
        iconField.setValue(node == null ? "" : node.icon());
        typeButton.setValue(node == null ? NodeType.TRAIT : node.type());
        visibilityButton.setValue(node == null ? NodeVisibilityMode.VISIBLE : node.visibility());
        syncingEditorFields = false;
        syncRequirementFields();
        updateTitleSuggestion();
        updateEditorWidgetState();
    }

    private void syncRequirementFields() {
        if (syncingEditorFields || requirementModeButton == null) {
            return;
        }
        SkillTreeEditorDraft.DraftNode node = editorDraft == null || selectedNodeId == null ? null : editorDraft.node(selectedNodeId);
        RequirementSpec spec = RequirementSpec.EMPTY;
        if (node != null) {
            spec = requirementTargetButton.getValue() == RequirementTarget.REVEAL
                    ? (node.revealRequirement() == null ? RequirementSpec.EMPTY : node.revealRequirement())
                    : node.requirement();
        }
        syncingEditorFields = true;
        requirementModeButton.setValue(spec.mode());
        requirementCountField.setValue(spec.mode() == RequirementSpec.Mode.AT_LEAST ? Integer.toString(spec.count()) : "");
        requirementNodesField.setValue(String.join(", ", spec.nodes()));
        syncingEditorFields = false;
        updateEditorWidgetState();
    }

    private void exportDraft() {
        if (!editorMode || editorDraft == null) {
            return;
        }
        List<String> errors = editorDraft.validate();
        List<String> warnings = editorDraft.warnings();
        if (!errors.isEmpty()) {
            editorStatus = "Validation failed: " + errors.get(0);
            LOGGER.warn("Skill tree editor validation failed for {}: {}", activeSkillId(), errors);
            return;
        }
        if (!warnings.isEmpty()) {
            LOGGER.warn("Skill tree editor validation warnings for {}: {}", activeSkillId(), warnings);
        }
        Path output = sourceJsonPath();
        try {
            JsonObject existingRoot = readSourceJson();
            String json = editorDraft.toJsonPreserving(existingRoot);
            if (Files.exists(output)) {
                Files.copy(output, Path.of(output.toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(output, json, StandardCharsets.UTF_8);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            exportedPreviewDefinition = editorDraft.toDefinition();
            SAVED_PREVIEW_DEFINITIONS.put(activeSkillId(), exportedPreviewDefinition);
            markTreeDirty();
            flushTreeIfDirty();
            editorStatus = warnings.isEmpty() ? "Saved " + output.getFileName() : "Saved with warning: " + warnings.get(0);
            LOGGER.info("Saved LevelRPG skill tree draft for {} to {}\n{}", activeSkillId(), output.toAbsolutePath(), json);
        } catch (IOException ex) {
            editorStatus = "Save failed: " + ex.getMessage();
            LOGGER.warn("Failed to save LevelRPG skill tree draft {}", activeSkillId(), ex);
        }
    }

    private void discardDraft() {
        exportedPreviewDefinition = null;
        editorDraft = createDraftFromCurrent();
        selectedNodeId = null;
        connectMode = false;
        editorStatus = "Discarded draft changes";
        markTreeDirty();
        flushTreeIfDirty();
        syncEditorFields();
    }

    private boolean canUseEditor() {
        return SharedConstants.IS_RUNNING_IN_IDE || !LevelRPG.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar");
    }

    private boolean textFieldFocused() {
        return titleField != null && (idField.isFocused() || titleField.isFocused() || descriptionField.isFocused() || costField.isFocused() || levelField.isFocused() || iconField.isFocused() || requirementCountField.isFocused() || requirementNodesField.isFocused());
    }

    private static List<String> parseRequirementNodeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        for (String part : value.split("[,\\s]+")) {
            String nodeId = part.trim();
            if (!nodeId.isBlank() && !result.contains(nodeId)) {
                result.add(nodeId);
            }
        }
        return List.copyOf(result);
    }

    private boolean insideEditorPanel(double mouseX, double mouseY) {
        return mouseX >= editorPanelX() && mouseX < width - OUTER_MARGIN && mouseY >= viewportY && mouseY < viewportY + viewportH;
    }

    private int clampEditorScroll(int value) {
        int max = Math.max(0, EDITOR_CONTENT_HEIGHT - viewportH + 18);
        return Math.max(0, Math.min(value, max));
    }

    private void updateEditorWidgetClipping() {
        if (!editorMode || idField == null) {
            return;
        }
        clipEditorWidget(idField, true);
        clipEditorWidget(titleField, true);
        clipEditorWidget(descriptionField, true);
        clipEditorWidget(costField, true);
        clipEditorWidget(levelField, true);
        clipEditorWidget(iconField, true);
        clipEditorWidget(typeButton, true);
        clipEditorWidget(visibilityButton, true);
        clipEditorWidget(requirementTargetButton, true);
        clipEditorWidget(requirementModeButton, true);
        clipEditorWidget(requirementCountField, true);
        clipEditorWidget(requirementNodesField, true);
        clipEditorWidget(newNodeButton, false);
        clipEditorWidget(deleteNodeButton, false);
        clipEditorWidget(connectButton, false);
        clipEditorWidget(saveButton, false);
        clipEditorWidget(discardButton, false);
        updateEditorWidgetState();
    }

    private void clipEditorWidget(net.minecraft.client.gui.components.AbstractWidget widget, boolean requiresSelection) {
        boolean inPanel = widget.getY() + widget.getHeight() >= viewportY + 28 && widget.getY() <= viewportY + viewportH - 8;
        widget.visible = editorMode && inPanel;
        widget.active = widget.visible && (!requiresSelection || selectedNodeId != null);
    }

    private int editorPanelX() {
        return width - OUTER_MARGIN - EDITOR_PANEL_WIDTH;
    }

    private SkillTreeEditorDraft createDraftFromCurrent() {
        ResourceLocation activeSkillId = activeSkillId();
        SkillTreePresentationDefinition current = exportedPreviewDefinition != null
                ? exportedPreviewDefinition
                : SAVED_PREVIEW_DEFINITIONS.getOrDefault(activeSkillId, SkillTreeRegistry.get(activeSkillId));
        current = mergeSourceMetadata(current);
        SkillTreeEditorDraft draft = SkillTreeEditorDraft.copyOf(current, activeSkillId);
        draft.applyJsonMetadata(readSourceJson());
        return draft;
    }

    private SkillTreePresentationDefinition mergeSourceMetadata(SkillTreePresentationDefinition base) {
        if (base == null) {
            return null;
        }
        if (base == metadataBaseDefinition && metadataMergedDefinition != null) {
            return metadataMergedDefinition;
        }
        SkillTreeEditorDraft draft = SkillTreeEditorDraft.copyOf(base, activeSkillId());
        draft.applyJsonMetadata(readSourceJson());
        metadataBaseDefinition = base;
        metadataMergedDefinition = draft.toDefinition();
        return metadataMergedDefinition;
    }

    private JsonObject readSourceJson() {
        Path path = sourceJsonPath();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            LOGGER.warn("Failed to read existing skill tree json {}", path, ex);
            return null;
        }
    }

    private Path sourceJsonPath() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path projectRoot = cwd;
        if (!Files.exists(projectRoot.resolve("src/main/resources")) && cwd.getParent() != null) {
            Path parent = cwd.getParent();
            if (Files.exists(parent.resolve("src/main/resources"))) {
                projectRoot = parent;
            }
        }
        return projectRoot.resolve(Path.of("src", "main", "resources", "data", LevelRPG.MODID, "skill_trees", activeSkillId().getPath() + ".json"));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private SkillTreeNodeView findHoveredNode(double mouseX, double mouseY) {
        var transform = activeTreeTransform();
        for (int i = nodeViews.size() - 1; i >= 0; i--) {
            SkillTreeNodeView view = nodeViews.get(i);
            if (view.status() != SkillNodeStatus.HIDDEN
                    && view.contains(mouseX, mouseY, transform)) {
                return view;
            }
        }
        return null;
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportW && mouseY >= viewportY && mouseY < viewportY + viewportH;
    }

    private void requestUnlockNode(ResourceLocation skillId, String nodeId) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new UnlockTreeNodeRequestPayload(skillId, nodeId));
            return;
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("Unlock requested: " + nodeId), true);
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = OUTER_MARGIN;
        int y = OUTER_MARGIN;
        int w = width - OUTER_MARGIN * 2;
        int h = height - OUTER_MARGIN * 2;
        graphics.fill(x, y, x + w, y + h, 0xEE211810);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xEE3A2A18);
        graphics.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xCC221B15);
        graphics.hLine(x, x + w, y, 0xFFE0B86A);
        graphics.hLine(x, x + w, y + h, 0xFF4F3923);
        graphics.vLine(x, y, y + h, 0xFFE0B86A);
        graphics.vLine(x + w, y, y + h, 0xFF4F3923);
    }

    private void drawHeader(GuiGraphics graphics) {
        String title = definition == null ? "Skill Tree: " + activeSkillId() : definition.title();
        String subtitle = "A/D rotate  |  Drag to pan  |  Esc to close";
        int available = state == null ? 0 : state.insight();
        String points = "Unspent: " + available;
        graphics.drawString(font, title, OUTER_MARGIN + 12, OUTER_MARGIN + 8, 0xFFFFE0A3, false);
        graphics.drawString(font, subtitle, OUTER_MARGIN + 12 + font.width(title) + 14, OUTER_MARGIN + 8, 0xFF9F9587, false);
        graphics.drawString(font, points, width - OUTER_MARGIN - 12 - font.width(points), OUTER_MARGIN + 8, 0xFF8DFF8D, false);
    }

    private void drawFooter(GuiGraphics graphics) {
        String zoom = "Zoom: " + Math.round(input.zoom() * 100.0f) + "%";
        if (canUseEditor()) {
            zoom += editorMode ? "  |  E: editor on" : "  |  E: editor";
        }
        graphics.drawString(font, zoom, OUTER_MARGIN + 12, height - OUTER_MARGIN - 14, 0xFFC8B58E, false);
    }

    private void drawEditorPanel(GuiGraphics graphics) {
        int x = editorPanelX();
        int y = viewportY;
        int h = viewportH;
        graphics.fill(x, y, x + EDITOR_PANEL_WIDTH, y + h, 0xEE171A1E);
        graphics.fill(x + 1, y + 1, x + EDITOR_PANEL_WIDTH - 1, y + h - 1, connectMode ? 0xEE162B3A : 0xEE25221D);
        graphics.hLine(x, x + EDITOR_PANEL_WIDTH, y, connectMode ? 0xFF62B7FF : 0xFFE0B86A);
        graphics.hLine(x, x + EDITOR_PANEL_WIDTH, y + h, 0xFF4F3923);
        graphics.vLine(x, y, y + h, connectMode ? 0xFF62B7FF : 0xFFE0B86A);
        graphics.vLine(x + EDITOR_PANEL_WIDTH, y, y + h, 0xFF4F3923);

        int tx = x + 9;
        graphics.drawString(font, "Dev Tree Editor", tx, y + 6, connectMode ? 0xFF90D2FF : 0xFFFFE0A3, false);
        graphics.drawString(font, connectMode ? "Connect mode: click target" : "E toggles  R resets", tx, y + 18, 0xFFB8A98E, false);

        String selected = selectedNodeId == null ? "Selected: none" : "Selected: " + selectedNodeId;
        graphics.drawString(font, trimToWidth(selected, EDITOR_PANEL_WIDTH - 18), tx, y + 33, 0xFFE7D8B2, false);
        SkillTreeEditorDraft.DraftNode node = editorDraft == null || selectedNodeId == null ? null : editorDraft.node(selectedNodeId);
        String implementation = node == null ? "" : "  " + (SkillNodeImplementationRegistry.isImplemented(activeSkillId(), node.id()) ? "Implemented" : "Unimplemented");
        String pos = node == null ? "Pos: -" : "Pos: " + node.x() + ", " + node.y() + implementation;
        graphics.drawString(font, trimToWidth(pos, 127), tx + 184, y + 33, 0xFF9F9587, false);

        graphics.enableScissor(x + 1, y + 28, x + EDITOR_PANEL_WIDTH - 1, y + h - 36);
        int fieldY = y + 48 - editorScroll;
        graphics.drawString(font, "Id", tx, fieldY - 10, 0xFFB8A98E, false);
        graphics.drawString(font, "Title", tx, fieldY + 15, 0xFFB8A98E, false);
        graphics.drawString(font, "Description", tx, fieldY + 40, 0xFFB8A98E, false);
        graphics.drawString(font, "Cost", tx, fieldY + 103, 0xFFB8A98E, false);
        graphics.drawString(font, "Level", tx + 64, fieldY + 103, 0xFFB8A98E, false);
        graphics.drawString(font, "Node Type", tx + 128, fieldY + 103, 0xFFB8A98E, false);
        graphics.drawString(font, "Icon", tx, fieldY + 135, 0xFFB8A98E, false);
        graphics.drawString(font, "Visibility / Requirement Target", tx, fieldY + 168, 0xFFB8A98E, false);
        graphics.drawString(font, "Mode", tx, fieldY + 201, 0xFFB8A98E, false);
        graphics.drawString(font, "Count", tx + 112, fieldY + 201, 0xFFB8A98E, false);
        graphics.drawString(font, "Requirement Node IDs", tx, fieldY + 234, 0xFFB8A98E, false);

        int infoY = fieldY + 322;
        if (node != null && infoY < y + h - 56) {
            graphics.drawString(font, "Requires:", tx, infoY, 0xFFB8A98E, false);
            int lineY = infoY + 12;
            if (node.requires().isEmpty()) {
                graphics.drawString(font, "(none)", tx, lineY, 0xFF8C8378, false);
            } else {
                for (String requirement : node.requires()) {
                    graphics.drawString(font, trimToWidth("- " + requirement, EDITOR_PANEL_WIDTH - 18), tx, lineY, 0xFFD7C797, false);
                    lineY += 10;
                    if (lineY > y + h - 46) {
                        break;
                    }
                }
            }
        }
        graphics.disableScissor();
        drawEditorScrollbar(graphics, x, y, h);
        if (editorDraft != null) {
            List<String> warnings = editorDraft.warnings();
            if (!warnings.isEmpty()) {
                graphics.drawString(font, trimToWidth(warnings.get(0), EDITOR_PANEL_WIDTH - 18), tx, y + h - 30, 0xFFFFC857, false);
            }
        }
        if (!editorStatus.isBlank()) {
            graphics.drawString(font, trimToWidth(editorStatus, EDITOR_PANEL_WIDTH - 18), tx, y + h - 18, 0xFF8DFF8D, false);
        }
    }

    private void drawEditorScrollbar(GuiGraphics graphics, int x, int y, int h) {
        int maxScroll = clampEditorScroll(Integer.MAX_VALUE);
        if (maxScroll <= 0) {
            return;
        }
        int trackX = x + EDITOR_PANEL_WIDTH - 7;
        int trackTop = y + 30;
        int trackBottom = y + h - 38;
        int trackH = Math.max(20, trackBottom - trackTop);
        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0x66362B20);
        int thumbH = Math.max(18, trackH * Math.max(1, viewportH - 48) / EDITOR_CONTENT_HEIGHT);
        int thumbTravel = Math.max(1, trackH - thumbH);
        int thumbY = trackTop + (editorScroll * thumbTravel / maxScroll);
        graphics.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbH, 0xFFBFA45D);
    }

    private void updateTitleSuggestion() {
        if (titleField != null) {
            titleField.setSuggestion(titleField.getValue().isBlank() ? "New Node" : null);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        String result = value;
        while (!result.isEmpty() && font.width(result) > allowed) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private void drawMissingTree(GuiGraphics graphics) {
        String message = "No advancement-style tree is registered for " + activeSkillId() + ".";
        int x = viewportX + Math.max(0, (viewportW - font.width(message)) / 2);
        int y = viewportY + viewportH / 2 - font.lineHeight / 2;
        graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, 0xD0181511);
        graphics.drawString(font, message, x, y, 0xFFFFD2A0, false);
    }

    private record ChamberTreeView(
            ResourceLocation skillId,
            SkillTreePresentationDefinition definition,
            SkillTreeState state,
            List<SkillTreeNodeView> nodes,
            Map<String, SkillTreeNodeView> nodeById
    ) {
    }

    private boolean isTreeTransitioning() {
        return treeTransitionState != TreeTransitionState.STABLE;
    }

    private void beginTreeTransition(ChamberTreeView previousView, ChamberTreeView nextView) {
        if (previousView == null || nextView == null || previousView.skillId().equals(nextView.skillId())) {
            treeTransitionState = TreeTransitionState.STABLE;
            outgoingTreeView = null;
            incomingTreeView = nextView;
            outgoingTreeAlpha = 0.0f;
            incomingTreeAlpha = 1.0f;
            return;
        }
        outgoingTreeView = previousView;
        incomingTreeView = nextView;
        outgoingTreeAlpha = 1.0f;
        incomingTreeAlpha = 0.0f;
        treeTransitionState = TreeTransitionState.FADING_OUT;
    }

    private void updateTreeTransition() {
        if (treeTransitionState == TreeTransitionState.FADING_OUT) {
            outgoingTreeAlpha = Math.max(0.0f, outgoingTreeAlpha - TREE_FADE_OUT_PER_TICK);
            if (outgoingTreeAlpha <= TREE_FADE_EPSILON) {
                outgoingTreeAlpha = 0.0f;
                treeTransitionState = TreeTransitionState.FADING_IN;
            }
            return;
        }
        if (treeTransitionState == TreeTransitionState.FADING_IN) {
            incomingTreeAlpha = Math.min(1.0f, incomingTreeAlpha + TREE_FADE_IN_PER_TICK);
            if (incomingTreeAlpha >= 1.0f - TREE_FADE_EPSILON) {
                incomingTreeAlpha = 1.0f;
                outgoingTreeView = null;
                treeTransitionState = TreeTransitionState.STABLE;
            }
        }
    }

    private enum TreeTransitionState {
        STABLE,
        FADING_OUT,
        FADING_IN
    }
}
