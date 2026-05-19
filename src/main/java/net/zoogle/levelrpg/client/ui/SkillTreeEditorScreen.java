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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
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
import net.zoogle.levelrpg.client.skilltree.SkillTreeScene;
import net.zoogle.levelrpg.client.skilltree.SkillTreeSceneFactory;
import net.zoogle.levelrpg.client.skilltree.SkillTreeTooltipRenderer;
import net.zoogle.levelrpg.client.skilltree.SkillTreeVisibilityView;
import net.zoogle.levelrpg.client.skilltree.SkillTreeViewportController;
import net.zoogle.levelrpg.client.skilltree.SkillTreeViewMode;
import net.zoogle.levelrpg.net.payload.RequestProfileSyncPayload;
import net.zoogle.levelrpg.net.payload.UnlockTreeNodeRequestPayload;
import net.zoogle.levelrpg.net.payload.AssignTechniqueSlotPayload;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCache;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.zoogle.levelrpg.skilltree.NodeVisibilityMode;
import net.zoogle.levelrpg.skilltree.RequirementSpec;
import net.zoogle.levelrpg.skilltree.SkillNodeImplementationRegistry;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeEdge;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeState;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Developer / editor tool</b> â€“ a 2-D, advancement-graphâ€“style view of a single LevelRPG skill
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
    private static final int PLAYER_FOOTER_HEIGHT = 34;
    private static final int PLAYER_TREE_TOP_PADDING = 84;
    private static final int PLAYER_TREE_RIGHT_PADDING = 60;
    private static final int PLAYER_TREE_BOTTOM_PADDING = 92;
    private static final int PLAYER_TREE_LEFT_PADDING = 60;
    private static final int PLAYER_ZOOM_OVERVIEW = 1;
    private static final int PLAYER_ZOOM_BROWSING = 2;
    private static final int PLAYER_ZOOM_SELECTED = 3;
    private static final float PLAYER_OVERVIEW_MAX_ZOOM = 0.92f;
    private static final float PLAYER_BROWSING_ZOOM = 1.0f;
    private static final float PLAYER_SELECTED_MIN_ZOOM = 1.18f;
    private static final float PLAYER_SELECTED_MAX_ZOOM = 1.48f;
    private static final int NODE_SIZE = 30;
    private static final int EDITOR_PANEL_WIDTH = 320;
    private static final int EDITOR_CONTENT_HEIGHT = 430;
    private static final int ALIGN_SNAP_DISTANCE = 10;
    private static final float TREE_FADE_OUT_PER_TICK = 0.30f;
    private static final float TREE_FADE_IN_PER_TICK = 0.18f;
    private static final float TREE_FADE_EPSILON = 0.01f;
    private static final int REVEAL_PULSE_TICKS = 34;
    private static final int INSCRIBE_HOLD_TICKS = 22;
    private static final int UNLOCK_BURST_TICKS = 28;
    private static final int HOVER_SOUND_COOLDOWN_TICKS = 3;
    private static final int PAN_SOUND_INTERVAL_TICKS = 8;
    private static final Map<ResourceLocation, SkillTreePresentationDefinition> SAVED_PREVIEW_DEFINITIONS = new LinkedHashMap<>();

    private final SkillTreeViewMode viewMode;
    private final int returnBookSpreadIndex;
    private final SkillChamberViewState chamber;
    private final SkillChamberRenderer chamberRenderer = new SkillChamberRenderer();
    private final SkillTreeRenderer renderer = new SkillTreeRenderer();
    private final SkillTreeTooltipRenderer tooltipRenderer = new SkillTreeTooltipRenderer();
    private final SkillTreeViewportController viewport = new SkillTreeViewportController();
    private final SkillTreeSceneFactory sceneFactory = new SkillTreeSceneFactory(NODE_SIZE);
    private final Map<ResourceLocation, SkillTreeScene> chamberTreeViews = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<String>> lastVisibleNodeIdsBySkill = new LinkedHashMap<>();
    private final Map<String, Integer> revealPulseTicks = new LinkedHashMap<>();
    private final Map<String, Integer> unlockBurstTicks = new LinkedHashMap<>();

    private SkillTreeScene activeScene;
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
    private int playerZoomLevel = PLAYER_ZOOM_OVERVIEW;
    private int hoverSoundCooldown;
    private int panSoundCooldown;
    private String selectedNodeId;
    private String lastHoveredSoundNodeId;
    private SkillTreeEditorDraft editorDraft;
    private String editorStatus = "";
    private double nodeDragOffsetX;
    private double nodeDragOffsetY;
    private String draggingTechniqueId;
    private ItemStack draggingTechniqueIcon = ItemStack.EMPTY;
    private int draggingTechniqueSourceSlot = -1;

    /** Rebuild skill definitions / chamber views only when set (avoids clearing maps every render frame). */
    private boolean treeDirty = true;
    private long lastSeenProfileRevision = Long.MIN_VALUE;
    private TreeTransitionState treeTransitionState = TreeTransitionState.STABLE;
    private SkillTreeScene outgoingTreeView;
    private SkillTreeScene incomingTreeView;
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
    private Button inscribeButton;

    public SkillTreeEditorScreen(ResourceLocation skillId) {
        this(skillId, -1, SkillTreeViewMode.EDITOR);
    }

    public SkillTreeEditorScreen(ResourceLocation skillId, int returnBookSpreadIndex) {
        this(skillId, returnBookSpreadIndex, SkillTreeViewMode.PLAYER_VIEW);
    }

    public SkillTreeEditorScreen(ResourceLocation skillId, int returnBookSpreadIndex, SkillTreeViewMode viewMode) {
        super(Component.literal("LevelRPG Discipline Tree"));
        this.viewMode = viewMode == null ? SkillTreeViewMode.PLAYER_VIEW : viewMode;
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

    private static final int PLAYER_PANEL_W = 220;
    private static final int PLAYER_PANEL_MIN_H = 86;
    private static final int PLAYER_PANEL_MAX_H = 172;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Widgets are checked first, but PLAYER_VIEW only allows actual visible
        // player controls to consume input. Hidden/stale editor widgets must not
        // create dead zones over the tree.
        if (playerWidgetClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!isPlayerView() && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (editorMode && insideEditorPanel(mouseX, mouseY)) {
            return true;
        }
        if (button != 0 || !insideViewport(mouseX, mouseY)) {
            return false;
        }

        // Always record the press so drag can start if the mouse moves.
        viewport.press(mouseX, mouseY);

        if (isPlayerView() && button == 0) {
            int hoveredSlot = getHoveredHotbarSlot(mouseX, mouseY);
            if (hoveredSlot >= 0) {
                ResourceLocation techId = ClientTechniqueCache.slot(hoveredSlot);
                if (techId != null) {
                    draggingTechniqueId = techId.toString();
                    draggingTechniqueSourceSlot = hoveredSlot;
                    TechniqueDefinition def = TechniqueRegistry.get(techId);
                    if (def != null && def.icon() != null) {
                        draggingTechniqueIcon = new ItemStack(BuiltInRegistries.ITEM.get(def.icon()));
                    } else {
                        draggingTechniqueIcon = ItemStack.EMPTY;
                    }
                }
                return true;
            }
        }

        // Node hit test â€” runs before the panel check so nodes panned under
        // the panel area are still selectable.
        hoveredNode = hoveredNode(mouseX, mouseY);
        if (editorMode) {
            handleEditorViewportClick(mouseX, mouseY, hoveredNode);
            return true;
        }
        if (hoveredNode != null) {
            String clickedNodeId = hoveredNode.definition().id();
            
            if (isPlayerView()) {
                SkillTreeState state = state();
                if (state != null && state.status(clickedNodeId) == SkillNodeStatus.INSCRIBED) {
                    ResourceLocation techId = resolveNodeTechniqueId(clickedNodeId);
                    if (techId != null) {
                        draggingTechniqueId = techId.toString();
                        draggingTechniqueSourceSlot = -1;
                        draggingTechniqueIcon = SkillTreeRenderer.resolveNodeIconStack(hoveredNode.definition());
                    }
                }
            }

            boolean alreadySelected = clickedNodeId.equals(selectedNodeId);
            selectNode(clickedNodeId);
            if (isPlayerView()) {
                playNodeClickSound(alreadySelected);
            }
            if (isPlayerView()) {
                int clickZoomLevel = alreadySelected || playerZoomLevel == PLAYER_ZOOM_SELECTED
                        ? PLAYER_ZOOM_SELECTED
                        : PLAYER_ZOOM_BROWSING;
                applyPlayerZoomLevel(clickZoomLevel, hoveredNode);
            }
        } else if (isPlayerView()) {
            selectNode(null);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingTechniqueId != null) {
            handleTechniqueDrop(mouseX, mouseY);
            draggingTechniqueId = null;
            draggingTechniqueSourceSlot = -1;
            draggingTechniqueIcon = ItemStack.EMPTY;
            viewport.release();
            return true;
        }
        if (button == 0 && draggingNode) {
            draggingNode = false;
            viewport.release();
            return true;
        }
        if (button == 0) {
            viewport.release();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingTechniqueId != null) {
            return true;
        }
        if (button == 0 && draggingNode) {
            dragSelectedNodeTo(mouseX, mouseY);
            return true;
        }
        if (button == 0) {
            // onDrag returns true only after the threshold is crossed.
            if (viewport.drag(mouseX, mouseY)) {
                playPanSound();
                return true;
            }
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
        if (isPlayerView()) {
            handlePlayerViewScroll(mouseX, mouseY, scrollY);
            return true;
        }
        if (Screen.hasShiftDown()) {
            viewport.panVertically(scrollY * 24.0);
        } else {
            viewport.zoomAround(mouseX, mouseY, scrollY);
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
        if (hoverSoundCooldown > 0) {
            hoverSoundCooldown--;
        }
        if (panSoundCooldown > 0) {
            panSoundCooldown--;
        }
        viewport.tickCameraAnimation();
        tickRevealPulses();
        tickUnlockBursts();
        if (inscribeButton instanceof InscribeButton holdButton) {
            holdButton.tickHold();
        }
        updateTreeTransition();
    }

    private void rotateChamberPrevious() {
        SkillTreeScene previous = chamberTreeViews.get(activeSkillId());
        if (chamber.rotatePrevious()) {
            prepareFocusedSkillChange(previous);
        }
    }

    private void rotateChamberNext() {
        SkillTreeScene previous = chamberTreeViews.get(activeSkillId());
        if (chamber.rotateNext()) {
            prepareFocusedSkillChange(previous);
        }
    }

    private void prepareFocusedSkillChange(SkillTreeScene previousView) {
        hoveredNode = null;
        selectedNodeId = null;
        playerZoomLevel = PLAYER_ZOOM_OVERVIEW;
        connectMode = false;
        draggingNode = false;
        revealPulseTicks.clear();
        unlockBurstTicks.clear();
        viewport.release();
        viewport.cancelCameraAnimation();
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

    private SkillTreePresentationDefinition definition() {
        return activeScene == null ? null : activeScene.definition();
    }

    private SkillTreeState state() {
        return activeScene == null ? null : activeScene.state();
    }

    private SkillTreeCameraTransform chamberTransform(SkillTreeScene treeView, SkillTreeCameraTransform focusedCameraTransform, boolean focused) {
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
        return viewport.transform();
    }

    private double[] centeredPan(SkillTreeScene treeView) {
        if (treeView == null || treeView.nodeViews().isEmpty()) {
            return new double[]{viewportW / 2.0, Math.max(54.0, viewportH / 2.0)};
        }
        SkillTreeScene.Bounds bounds = treeView.bounds();
        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minY = bounds.minY();
        int maxY = bounds.maxY();
        int contentCenterX = (minX + maxX) / 2;
        int contentCenterY = (minY + maxY) / 2;
        return new double[]{viewportW / 2.0 - contentCenterX, Math.max(54.0, viewportH / 2.0 - contentCenterY)};
    }

    private int[] getDisciplineVoidColors() {
        String discipline = activeSkillId() == null ? "" : activeSkillId().getPath();
        return switch (discipline) {
            case "valor" -> new int[]{0xDD1A0505, 0xEE331010};
            case "finesse" -> new int[]{0xDD051A0A, 0xEE0D3314};
            case "arcana" -> new int[]{0xDD140520, 0xEE2A1040};
            case "delving" -> new int[]{0xDD181005, 0xEE30200A};
            case "forging" -> new int[]{0xDD200A05, 0xEE40140A};
            case "artificing" -> new int[]{0xDD051A20, 0xEE0A3340};
            case "hearth" -> new int[]{0xDD1A1810, 0xEE333020};
            default -> new int[]{0xDD04060A, 0xEE101625};
        };
    }

    private void drawPlayerViewBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x33000000);
        graphics.fill(0, 0, width, viewportY + 4, 0x66000000);
        graphics.fill(0, viewportY + viewportH - 32, width, height, 0x55000000);
        graphics.fill(0, 0, viewportX + 32, height, 0x33000000);
        graphics.fill(viewportX + viewportW - 32, 0, width, height, 0x33000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        noteProfileRevision();
        flushTreeIfDirty();
        layoutViewport();
        layoutEditorWidgets();
        layoutPlayerWidgets();
        centerViewOnce();
        SkillTreeCameraTransform focusedTransform = viewport.transform();
        hoveredNode = hoveredNode(mouseX, mouseY);
        updateHoverSound();

        if (isPlayerView()) {
            int[] voidColors = getDisciplineVoidColors();
            graphics.fillGradient(0, 0, width, height, voidColors[0], voidColors[1]);
            drawPlayerViewBackdrop(graphics);
        } else {
            graphics.fill(0, 0, width, height, 0xD0100D0A);
        }
        if (!isPlayerView()) {
            drawPanel(graphics);
        }
        drawHeader(graphics);
        drawFooter(graphics);
        if (editorMode && !isPlayerView()) {
            drawEditorPanel(graphics);
        }

        if (definition() == null) {
            chamberRenderer.render(graphics, font, chamber, viewportX, viewportY, viewportW, viewportH, isPlayerView());
            drawMissingTree(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        chamberRenderer.render(graphics, font, chamber, viewportX, viewportY, viewportW, viewportH, isPlayerView());
        renderer.render(graphics, font, null, List.of(), Map.of(), viewportX, viewportY, viewportW, viewportH, focusedTransform, null, null, false, true, false);
        SkillTreeScene focusedTreeView = chamberTreeViews.get(activeSkillId());
        if (treeTransitionState == TreeTransitionState.FADING_OUT && outgoingTreeView != null) {
            renderer.render(
                    graphics,
                    font,
                    outgoingTreeView.definition(),
                    outgoingTreeView.nodeViews(),
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
                    incomingTreeView.nodeViews(),
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
            if (isPlayerView()) {
                renderer.renderFogHints(
                        graphics,
                        SkillTreeVisibilityView.fogHints(focusedTreeView),
                        focusedTreeView.nodeById(),
                        viewportX,
                        viewportY,
                        viewportW,
                        viewportH,
                        focusedTransform
                );
            }
            renderer.render(
                    graphics,
                    font,
                    focusedTreeView.definition(),
                    focusedTreeView.nodeViews(),
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
                    1.0f,
                    node -> shockwaveNodeOffset(focusedTreeView, node, focusedTransform)
            );
        }
        drawRevealPulses(graphics, focusedTreeView, focusedTransform);
        drawUnlockBursts(graphics, focusedTreeView, focusedTransform);
        drawInscribeChargePulse(graphics, focusedTransform);
        if (hoveredNode != null && (!isPlayerView() || !hoveredNode.definition().id().equals(selectedNodeId))) {
            tooltipRenderer.render(graphics, font, activeSkillId(), hoveredNode, width, height, focusedTransform);
        }
        drawPlayerNodeDetailsPanel(graphics);
        if (draggingTechniqueId != null && !draggingTechniqueIcon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.renderItem(draggingTechniqueIcon, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
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
        activeScene = null;
        chamberTreeViews.clear();
        SkillTreePresentationDefinition loaded = sceneFactory.resolveDefinition(
                activeSkillId,
                exportedPreviewDefinition,
                SAVED_PREVIEW_DEFINITIONS
        );
        loaded = mergeSourceMetadata(loaded);
        if (editorMode) {
            if (editorDraft == null) {
                editorDraft = createDraftFromCurrent();
            }
            activeScene = sceneFactory.createEditorScene(activeSkillId, editorDraft);
        } else {
            activeScene = sceneFactory.createPlayerScene(activeSkillId, loaded);
            activeScene = SkillTreeVisibilityView.localReveal(activeScene);
        }
        if (activeScene == null) {
            return;
        }
        updateRevealPulses(activeSkillId, activeScene);
        chamberTreeViews.put(activeSkillId, activeScene);
    }

    private SkillTreeScene buildReadonlyTreeView(ResourceLocation skillId) {
        SkillTreePresentationDefinition treeDefinition = sceneFactory.resolveDefinition(skillId, null, SAVED_PREVIEW_DEFINITIONS);
        return SkillTreeVisibilityView.localReveal(sceneFactory.createPlayerScene(skillId, treeDefinition));
    }

    private void updateRevealPulses(ResourceLocation skillId, SkillTreeScene scene) {
        if (!isPlayerView() || skillId == null || scene == null) {
            return;
        }
        ArrayList<String> current = visibleNodeIds(scene);
        List<String> previous = lastVisibleNodeIdsBySkill.put(skillId, List.copyOf(current));
        if (previous == null) {
            return;
        }
        for (String nodeId : current) {
            if (!previous.contains(nodeId)) {
                revealPulseTicks.put(nodeId, REVEAL_PULSE_TICKS);
            }
        }
    }

    private static ArrayList<String> visibleNodeIds(SkillTreeScene scene) {
        ArrayList<String> result = new ArrayList<>();
        for (SkillTreeNodeView view : scene.nodeViews()) {
            result.add(view.definition().id());
        }
        return result;
    }

    private void tickRevealPulses() {
        if (revealPulseTicks.isEmpty()) {
            return;
        }
        for (String nodeId : new ArrayList<>(revealPulseTicks.keySet())) {
            int next = revealPulseTicks.getOrDefault(nodeId, 0) - 1;
            if (next <= 0) {
                revealPulseTicks.remove(nodeId);
            } else {
                revealPulseTicks.put(nodeId, next);
            }
        }
    }

    private void tickUnlockBursts() {
        if (unlockBurstTicks.isEmpty()) {
            return;
        }
        for (String nodeId : new ArrayList<>(unlockBurstTicks.keySet())) {
            int next = unlockBurstTicks.getOrDefault(nodeId, 0) - 1;
            if (next <= 0) {
                unlockBurstTicks.remove(nodeId);
            } else {
                unlockBurstTicks.put(nodeId, next);
            }
        }
    }

    private void layoutViewport() {
        viewportX = OUTER_MARGIN + 6;
        viewportY = OUTER_MARGIN + HEADER_HEIGHT;
        int editorInset = editorMode ? EDITOR_PANEL_WIDTH + 8 : 0;
        int footerInset = isPlayerView() ? PLAYER_FOOTER_HEIGHT : 0;
        viewportW = Math.max(40, width - (OUTER_MARGIN + 6) * 2 - editorInset);
        viewportH = Math.max(40, height - viewportY - OUTER_MARGIN - 6 - footerInset);
        viewport.setViewport(viewportX, viewportY, viewportW, viewportH);
    }

    private void centerViewOnce() {
        if (centered || activeScene == null || !activeScene.hasNodes()) {
            return;
        }
        if (isPlayerView()) {
            playerZoomLevel = PLAYER_ZOOM_OVERVIEW;
            viewport.fitToScene(
                    activeScene,
                    PLAYER_TREE_TOP_PADDING,
                    PLAYER_TREE_RIGHT_PADDING,
                    PLAYER_TREE_BOTTOM_PADDING,
                    PLAYER_TREE_LEFT_PADDING,
                    PLAYER_OVERVIEW_MAX_ZOOM
            );
        } else {
            viewport.fitToScene(activeScene);
        }
        centered = true;
    }

    private void applyPlayerZoomLevel(int level, SkillTreeNodeView target) {
        if (!isPlayerView()) {
            return;
        }
        int previousLevel = playerZoomLevel;
        playerZoomLevel = Math.max(PLAYER_ZOOM_OVERVIEW, Math.min(PLAYER_ZOOM_SELECTED, level));
        if (playerZoomLevel != previousLevel) {
            playZoomLevelSound(playerZoomLevel);
        }
        if (playerZoomLevel == PLAYER_ZOOM_OVERVIEW) {
            focusPlayerOverview();
            return;
        }
        SkillTreeNodeView focusTarget = target == null ? selectedNodeView() : target;
        if (focusTarget == null && activeScene != null) {
            focusTarget = activeScene.rootNode();
        }
        if (focusTarget == null) {
            return;
        }
        if (playerZoomLevel == PLAYER_ZOOM_BROWSING) {
            focusPlayerBrowsing(focusTarget);
        } else {
            focusPlayerSelected(focusTarget);
        }
    }

    private void focusPlayerOverview() {
        if (!isPlayerView() || activeScene == null || !activeScene.hasNodes()) {
            return;
        }
        viewport.animateToNodes(
                activeScene.nodeViews(),
                PLAYER_TREE_TOP_PADDING,
                PLAYER_TREE_RIGHT_PADDING,
                PLAYER_TREE_BOTTOM_PADDING,
                PLAYER_TREE_LEFT_PADDING,
                SkillTreeInputState.MIN_ZOOM,
                PLAYER_OVERVIEW_MAX_ZOOM
        );
    }

    private void focusPlayerBrowsing(SkillTreeNodeView node) {
        if (!isPlayerView() || node == null) {
            return;
        }
        viewport.animateToNodes(
                playerFocusNodes(node),
                PLAYER_TREE_TOP_PADDING,
                PLAYER_TREE_RIGHT_PADDING,
                PLAYER_TREE_BOTTOM_PADDING,
                PLAYER_TREE_LEFT_PADDING,
                PLAYER_BROWSING_ZOOM,
                PLAYER_BROWSING_ZOOM
        );
    }

    private void focusPlayerSelected(SkillTreeNodeView node) {
        if (!isPlayerView() || node == null) {
            return;
        }
        viewport.animateToNodes(
                List.of(node),
                PLAYER_TREE_TOP_PADDING,
                PLAYER_TREE_RIGHT_PADDING,
                PLAYER_TREE_BOTTOM_PADDING,
                PLAYER_TREE_LEFT_PADDING,
                PLAYER_SELECTED_MIN_ZOOM,
                PLAYER_SELECTED_MAX_ZOOM
        );
    }

    private void handlePlayerViewScroll(double mouseX, double mouseY, double scrollY) {
        SkillTreeNodeView target = selectedNodeView();
        if (target == null) {
            target = hoveredNode(mouseX, mouseY);
        }
        if (target == null && activeScene != null) {
            target = activeScene.rootNode();
        }
        if (scrollY > 0.0) {
            applyPlayerZoomLevel(playerZoomLevel + 1, target);
        } else if (scrollY < 0.0) {
            applyPlayerZoomLevel(playerZoomLevel - 1, target);
        }
    }

    private List<SkillTreeNodeView> playerFocusNodes(SkillTreeNodeView node) {
        if (activeScene == null || activeScene.definition() == null || node == null) {
            return node == null ? List.of() : List.of(node);
        }
        LinkedHashMap<String, SkillTreeNodeView> focusNodes = new LinkedHashMap<>();
        String nodeId = node.definition().id();
        focusNodes.put(nodeId, node);
        for (SkillTreeEdge edge : activeScene.definition().edges()) {
            if (edge.parentId().equals(nodeId)) {
                addVisibleFocusNode(focusNodes, edge.childId());
            } else if (edge.childId().equals(nodeId)) {
                addVisibleFocusNode(focusNodes, edge.parentId());
            }
        }
        return List.copyOf(focusNodes.values());
    }

    private void addVisibleFocusNode(Map<String, SkillTreeNodeView> focusNodes, String nodeId) {
        if (activeScene == null || nodeId == null || focusNodes.containsKey(nodeId)) {
            return;
        }
        SkillTreeNodeView view = activeScene.nodeById(nodeId);
        if (view != null && view.rendered() && view.status() != SkillNodeStatus.HIDDEN) {
            focusNodes.put(nodeId, view);
        }
    }

    private void playZoomLevelSound(int level) {
        if (!isPlayerView()) {
            return;
        }
        switch (level) {
            case PLAYER_ZOOM_OVERVIEW -> playUiSound(SoundEvents.SPYGLASS_USE, 0.62f, 0.24f);
            case PLAYER_ZOOM_BROWSING -> playUiSound(SoundEvents.SPYGLASS_USE, 0.95f, 0.30f);
            case PLAYER_ZOOM_SELECTED -> {
                playUiSound(SoundEvents.SPYGLASS_USE, 1.18f, 0.34f);
                playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.45f, 0.18f);
            }
            default -> {
            }
        }
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
        levelField = addRenderableWidget(new EditBox(font, 0, 0, 52, 18, Component.literal("Disc. Lv.")));
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

        inscribeButton = addRenderableWidget(new InscribeButton(Component.literal("Hold to Inscribe"), button -> {
            SkillTreeState state = state();
            if (selectedNodeId != null && state != null && state.status(selectedNodeId) == SkillNodeStatus.AVAILABLE) {
                requestUnlockNode(activeSkillId(), selectedNodeId);
            }
        }));
        inscribeButton.visible = false;
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
        revealPulseTicks.clear();
        unlockBurstTicks.clear();
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
        visible = visible && !isPlayerView();
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
        viewport.press(mouseX, mouseY);
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
        SkillTreePresentationDefinition definition = definition();
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
        int x = editorDraft.isEmpty() ? 0 : (int) Math.round(viewport.screenToGraphX(viewportX + viewportW / 2.0));
        int y = editorDraft.isEmpty() ? 0 : (int) Math.round(viewport.screenToGraphY(viewportY + viewportH / 2.0));
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
        return !isPlayerView()
                && (SharedConstants.IS_RUNNING_IN_IDE
                || !LevelRPG.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
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
        SkillTreePresentationDefinition current = sceneFactory.resolveDefinition(
                activeSkillId,
                exportedPreviewDefinition,
                SAVED_PREVIEW_DEFINITIONS
        );
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

    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private SkillTreeNodeView hoveredNode(double mouseX, double mouseY) {
        return viewport.hoveredNode(activeScene, mouseX, mouseY);
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return viewport.contains(mouseX, mouseY);
    }

    private boolean playerWidgetClicked(double mouseX, double mouseY, int button) {
        if (!isPlayerView() || button != 0 || inscribeButton == null) {
            return false;
        }
        layoutPlayerWidgets();
        return inscribeButton.visible
                && inscribeButton.active
                && inscribeButton.isMouseOver(mouseX, mouseY)
                && inscribeButton.mouseClicked(mouseX, mouseY, button);
    }

    private void updateHoverSound() {
        if (!isPlayerView() || viewport.isDragging() || hoveredNode == null) {
            lastHoveredSoundNodeId = hoveredNode == null ? null : lastHoveredSoundNodeId;
            return;
        }
        String hoveredId = hoveredNode.definition().id();
        if (!hoveredId.equals(lastHoveredSoundNodeId) && hoverSoundCooldown <= 0) {
            playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.62f, 0.16f);
            playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.28f, 0.08f);
            hoverSoundCooldown = HOVER_SOUND_COOLDOWN_TICKS;
        }
        lastHoveredSoundNodeId = hoveredId;
    }

    private void playNodeClickSound(boolean alreadySelected) {
        playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, alreadySelected ? 1.14f : 0.96f, alreadySelected ? 0.22f : 0.16f);
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, alreadySelected ? 1.38f : 1.12f, alreadySelected ? 0.12f : 0.08f);
    }

    private void playPanSound() {
        // Deliberately silent for now. Repeated vanilla click/plate-style ticks
        // read as placeholder UI noise during continuous camera movement.
    }

    private void requestUnlockNode(ResourceLocation skillId, String nodeId) {
        if (Minecraft.getInstance().getConnection() != null) {
            playUnlockFeedback(nodeId);
            PacketDistributor.sendToServer(new UnlockTreeNodeRequestPayload(skillId, nodeId));
            return;
        }
        if (Minecraft.getInstance().player != null) {
            playUnlockFeedback(nodeId);
            Minecraft.getInstance().player.displayClientMessage(Component.literal("Unlock requested: " + nodeId), true);
        }
    }

    private void playUnlockFeedback(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            unlockBurstTicks.put(nodeId, UNLOCK_BURST_TICKS);
            revealPulseTicks.put(nodeId, REVEAL_PULSE_TICKS);
        }
        playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.95f, 0.65f);
        playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.45f);
        playUiSound(SoundEvents.BEACON_POWER_SELECT, 1.35f, 0.35f);
        playUiSound(SoundEvents.WIND_CHARGE_BURST.value(), 0.9f, 0.6f);
    }

    private void playUiSound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private boolean isPlayerView() {
        return viewMode == SkillTreeViewMode.PLAYER_VIEW;
    }

    private boolean shouldRenderTreeGrid() {
        return !isPlayerView();
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = OUTER_MARGIN;
        int y = OUTER_MARGIN;
        int w = width - OUTER_MARGIN * 2;
        int h = height - OUTER_MARGIN * 2;
        graphics.fill(x, y, x + w, y + h, 0xEE211810);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xEE3A2A18);
        graphics.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xCC221B15);
        graphics.hLine(x + 8, x + w - 8, y + 8, 0x99A89154);
        graphics.hLine(x + 8, x + w - 8, y + h, 0x664F3923);
        graphics.vLine(x, y + 8, y + h - 8, 0x88BFA45D);
        graphics.vLine(x + w, y + 8, y + h - 8, 0x55372D21);
    }

    private void drawRevealPulses(GuiGraphics graphics, SkillTreeScene scene, SkillTreeCameraTransform transform) {
        if (!isPlayerView() || scene == null || revealPulseTicks.isEmpty()) {
            return;
        }
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        for (String nodeId : new ArrayList<>(revealPulseTicks.keySet())) {
            SkillTreeNodeView node = scene.nodeById(nodeId);
            if (node == null) {
                continue;
            }
            int ticks = revealPulseTicks.getOrDefault(nodeId, 0);
            float age = 1.0f - ticks / (float) REVEAL_PULSE_TICKS;
            int alpha = Math.max(0, Math.min(180, Math.round((1.0f - age) * 180.0f)));
            int x = node.left(transform);
            int y = node.top(transform);
            int size = node.scaledSize(transform);
            int basePad = Math.max(4, (int) Math.round((node.visualPadding() + 5) * transform.zoom()));
            int outerPad = basePad + Math.max(1, Math.round(age * 20.0f * transform.zoom()));
            int innerPad = Math.max(2, basePad / 2);
            drawRectOutline(graphics, x - outerPad, y - outerPad, size + outerPad * 2, withAlpha(0xFFDCC76B, alpha));
            drawRectOutline(graphics, x - innerPad, y - innerPad, size + innerPad * 2, withAlpha(0xFF9F7DFF, alpha / 2));
        }
        graphics.disableScissor();
    }

    private void drawUnlockBursts(GuiGraphics graphics, SkillTreeScene scene, SkillTreeCameraTransform transform) {
        if (!isPlayerView() || scene == null || unlockBurstTicks.isEmpty()) {
            return;
        }
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        for (String nodeId : new ArrayList<>(unlockBurstTicks.keySet())) {
            SkillTreeNodeView node = scene.nodeById(nodeId);
            if (node == null) {
                continue;
            }
            int ticks = unlockBurstTicks.getOrDefault(nodeId, 0);
            float age = 1.0f - ticks / (float) UNLOCK_BURST_TICKS;
            int alpha = Math.max(0, Math.min(210, Math.round((1.0f - age) * 210.0f)));
            int cx = node.centerX(transform);
            int cy = node.centerY(transform);
            int size = node.scaledSize(transform);
            drawUnlockBeam(graphics, cx, age);
            drawUnlockImpactFlash(graphics, node, transform, age);
            drawUnlockShockwave(graphics, cx, cy, size, age, transform.zoom());
            int radius = Math.max(size / 2 + 8, Math.round((size / 2.0f + 30.0f) * age));
            drawRectOutline(graphics, cx - radius, cy - radius, radius * 2, withAlpha(0xFFFFE6A3, alpha));
            drawUnlockRay(graphics, cx, cy, radius, 0, withAlpha(0xFFFFF3C4, alpha));
            drawUnlockRay(graphics, cx, cy, radius, 1, withAlpha(0xFFA785FF, alpha / 2));
            drawUnlockRay(graphics, cx, cy, radius, 2, withAlpha(0xFFFFF3C4, alpha / 2));
            drawUnlockRay(graphics, cx, cy, radius, 3, withAlpha(0xFFA785FF, alpha / 3));
        }
        graphics.disableScissor();
    }

    private void drawUnlockBeam(GuiGraphics graphics, int cx, float age) {
        if (age > 0.34f) {
            return;
        }
        float fade = 1.0f - age / 0.34f;
        float split = age / 0.34f;
        int centerAlpha = Math.max(0, Math.min(245, Math.round(fade * fade * 245.0f)));
        int echoAlpha = Math.max(0, Math.min(170, Math.round(fade * 170.0f)));
        int halfHeightTop = viewportY;
        int halfHeightBottom = viewportY + viewportH;
        int centerWidth = age < 0.08f ? 5 : 3;
        int spread = Math.round((6.0f + split * 34.0f) * Math.max(0.7f, viewport.zoom()));

        drawVerticalBeam(graphics, cx, halfHeightTop, halfHeightBottom, centerWidth + 10, withAlpha(0xFFFFFFFF, centerAlpha / 5));
        drawVerticalBeam(graphics, cx, halfHeightTop, halfHeightBottom, centerWidth + 4, withAlpha(0xFFFFF6D6, centerAlpha / 2));
        drawVerticalBeam(graphics, cx, halfHeightTop, halfHeightBottom, centerWidth, withAlpha(0xFFFFFFFF, centerAlpha));

        if (age > 0.04f) {
            drawVerticalBeam(graphics, cx - spread, halfHeightTop, halfHeightBottom, 2, withAlpha(0xFFFFE6A3, echoAlpha));
            drawVerticalBeam(graphics, cx + spread, halfHeightTop, halfHeightBottom, 2, withAlpha(0xFFFFE6A3, echoAlpha));
            drawVerticalBeam(graphics, cx - spread / 2, halfHeightTop, halfHeightBottom, 1, withAlpha(0xFFA785FF, echoAlpha / 2));
            drawVerticalBeam(graphics, cx + spread / 2, halfHeightTop, halfHeightBottom, 1, withAlpha(0xFFA785FF, echoAlpha / 2));
        }
    }

    private static void drawVerticalBeam(GuiGraphics graphics, int cx, int top, int bottom, int width, int color) {
        if (((color >>> 24) & 0xFF) <= 0 || width <= 0) {
            return;
        }
        int half = Math.max(0, width / 2);
        graphics.fill(cx - half, top, cx + half + 1, bottom, color);
    }

    private SkillTreeRenderer.NodeOffset shockwaveNodeOffset(SkillTreeScene scene, SkillTreeNodeView node, SkillTreeCameraTransform transform) {
        if (!isPlayerView() || scene == null || node == null || unlockBurstTicks.isEmpty()) {
            return SkillTreeRenderer.NO_OFFSET;
        }
        double offsetX = 0.0;
        double offsetY = 0.0;
        int nodeCenterX = node.centerX(transform);
        int nodeCenterY = node.centerY(transform);
        for (Map.Entry<String, Integer> entry : unlockBurstTicks.entrySet()) {
            SkillTreeNodeView source = scene.nodeById(entry.getKey());
            if (source == null) {
                continue;
            }
            float age = 1.0f - entry.getValue() / (float) UNLOCK_BURST_TICKS;
            float eased = 1.0f - (1.0f - age) * (1.0f - age);
            int sourceX = source.centerX(transform);
            int sourceY = source.centerY(transform);
            double dx = nodeCenterX - sourceX;
            double dy = nodeCenterY - sourceY;
            double distance = Math.hypot(dx, dy);
            int sourceSize = source.scaledSize(transform);
            double startRadius = Math.max(10.0, sourceSize / 2.0 + 8.0 * transform.zoom());
            double endRadius = startRadius + Math.max(56.0, 116.0 * transform.zoom());
            double radius = startRadius + (endRadius - startRadius) * eased;
            double bandWidth = Math.max(18.0, 34.0 * transform.zoom());
            double wave = 1.0 - Math.abs(distance - radius) / bandWidth;
            if (wave <= 0.0) {
                continue;
            }
            double fade = (1.0 - age) * (1.0 - age);
            double strength = Math.min(9.0, 5.5 + 2.0 * transform.zoom()) * wave * fade;
            if (distance < 0.001) {
                offsetY -= strength * 0.45;
            } else {
                offsetX += dx / distance * strength;
                offsetY += dy / distance * strength;
            }
        }
        int roundedX = (int) Math.round(offsetX);
        int roundedY = (int) Math.round(offsetY);
        if (roundedX == 0 && roundedY == 0) {
            return SkillTreeRenderer.NO_OFFSET;
        }
        return new SkillTreeRenderer.NodeOffset(roundedX, roundedY);
    }

    private void drawUnlockImpactFlash(GuiGraphics graphics, SkillTreeNodeView node, SkillTreeCameraTransform transform, float age) {
        if (age > 0.18f) {
            return;
        }
        float flash = 1.0f - age / 0.18f;
        int alpha = Math.max(0, Math.min(210, Math.round(flash * 210.0f)));
        int x = node.left(transform);
        int y = node.top(transform);
        int size = node.scaledSize(transform);
        int pad = Math.max(5, Math.round((node.visualPadding() + 8) * transform.zoom()));
        int outer = size + pad * 2;
        graphics.fill(x - pad, y - pad, x + size + pad, y + size + pad, withAlpha(0xFFFFFFFF, alpha / 3));
        drawRectOutline(graphics, x - pad, y - pad, outer, withAlpha(0xFFFFFFFF, alpha));
        drawRectOutline(graphics, x - pad - 3, y - pad - 3, outer + 6, withAlpha(0xFFFFE6A3, alpha / 2));
    }

    private void drawUnlockShockwave(GuiGraphics graphics, int cx, int cy, int nodeSize, float age, float zoom) {
        float eased = 1.0f - (1.0f - age) * (1.0f - age);
        int startRadius = Math.max(10, nodeSize / 2 + Math.round(8.0f * zoom));
        int endRadius = startRadius + Math.max(56, Math.round(116.0f * zoom));
        int radius = Math.round(startRadius + (endRadius - startRadius) * eased);
        int alpha = Math.max(0, Math.min(190, Math.round((1.0f - age) * (1.0f - age) * 190.0f)));
        int thickness = Math.max(1, Math.min(3, Math.round(2.0f * zoom)));

        drawCircularRing(graphics, cx, cy, radius, thickness, withAlpha(0xFFFFF8DD, alpha));
        if (age > 0.12f) {
            int echoAlpha = Math.max(0, alpha / 3);
            int echoRadius = Math.max(startRadius, radius - Math.round(14.0f * zoom));
            drawCircularRing(graphics, cx, cy, echoRadius, Math.max(1, thickness - 1), withAlpha(0xFFA785FF, echoAlpha));
        }
    }

    private void drawInscribeChargePulse(GuiGraphics graphics, SkillTreeCameraTransform transform) {
        if (!isPlayerView() || !(inscribeButton instanceof InscribeButton holdButton) || !holdButton.isHolding()) {
            return;
        }
        SkillTreeNodeView node = selectedNodeView();
        if (node == null) {
            return;
        }
        float progress = holdButton.holdProgress();
        int shake = holdButton.shakeOffset();
        int x = node.left(transform) + shake;
        int y = node.top(transform) - shake / 2;
        int size = node.scaledSize(transform);
        int basePad = Math.max(4, Math.round((node.visualPadding() + 5) * transform.zoom()));
        int pulsePad = basePad + Math.round((4.0f + progress * 18.0f) * transform.zoom());
        int alpha = Math.max(70, Math.min(220, Math.round(95.0f + progress * 125.0f)));
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        drawRectOutline(graphics, x - pulsePad, y - pulsePad, size + pulsePad * 2, withAlpha(0xFFFFF7D6, alpha));
        drawRectOutline(graphics, x - basePad, y - basePad, size + basePad * 2, withAlpha(0xFFA785FF, alpha / 2));
        int cx = x + size / 2;
        int cy = y + size / 2;
        int ray = Math.max(size / 2 + basePad + 3, Math.round(size / 2.0f + 12.0f + progress * 18.0f));
        drawUnlockRay(graphics, cx, cy, ray, 0, withAlpha(0xFFFFF3C4, alpha / 2));
        drawUnlockRay(graphics, cx, cy, ray, 1, withAlpha(0xFFA785FF, alpha / 3));
        drawUnlockRay(graphics, cx, cy, ray, 2, withAlpha(0xFFFFF3C4, alpha / 3));
        drawUnlockRay(graphics, cx, cy, ray, 3, withAlpha(0xFFA785FF, alpha / 4));
        graphics.disableScissor();
    }

    private static void drawUnlockRay(GuiGraphics graphics, int cx, int cy, int radius, int direction, int color) {
        int thickness = 2;
        switch (direction) {
            case 0 -> graphics.fill(cx - thickness, cy - radius, cx + thickness + 1, cy - 5, color);
            case 1 -> graphics.fill(cx + 5, cy - thickness, cx + radius, cy + thickness + 1, color);
            case 2 -> graphics.fill(cx - thickness, cy + 5, cx + thickness + 1, cy + radius, color);
            case 3 -> graphics.fill(cx - radius, cy - thickness, cx - 5, cy + thickness + 1, color);
            default -> {
            }
        }
    }

    private static void drawRectOutline(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y + 1, x + 1, y + size - 1, color);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    private static void drawCircularRing(GuiGraphics graphics, int cx, int cy, int radius, int thickness, int color) {
        if (((color >>> 24) & 0xFF) <= 0 || radius <= 0) {
            return;
        }
        int samples = Math.max(28, Math.min(128, Math.round(radius * 1.35f)));
        int half = Math.max(1, thickness / 2);
        for (int i = 0; i < samples; i++) {
            double angle = Math.PI * 2.0 * i / samples;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private void layoutPlayerWidgets() {
        if (inscribeButton == null) return;
        SkillTreePresentationDefinition definition = definition();
        SkillTreeState state = state();
        if (!isPlayerView() || selectedNodeId == null || state == null || definition == null) {
            inscribeButton.visible = false;
            inscribeButton.active = false;
            if (inscribeButton instanceof InscribeButton holdButton) {
                holdButton.cancelHold();
            }
            return;
        }
        SkillTreeNodeDefinition node = definition.nodes().get(selectedNodeId);
        if (node == null) {
            inscribeButton.visible = false;
            inscribeButton.active = false;
            if (inscribeButton instanceof InscribeButton holdButton) {
                holdButton.cancelHold();
            }
            return;
        }
        SkillNodeStatus status = state.status(node.id());
        inscribeButton.visible = status == SkillNodeStatus.AVAILABLE;
        inscribeButton.active = status == SkillNodeStatus.AVAILABLE;
        if (!inscribeButton.visible && inscribeButton instanceof InscribeButton holdButton) {
            holdButton.cancelHold();
        }
        int panelH = playerPopoverHeight(node, status);
        int[] bounds = selectedNodePopoverBounds(PLAYER_PANEL_W, panelH);
        int px = bounds[0];
        int py = bounds[1];
        int btnPad = 10;
        int btnW = PLAYER_PANEL_W - btnPad * 2;
        int btnH = 22;
        inscribeButton.setX(px + btnPad);
        inscribeButton.setY(py + panelH - btnH - 8);
        inscribeButton.setWidth(btnW);
        inscribeButton.setHeight(btnH);
    }

    private void drawPlayerNodeDetailsPanel(GuiGraphics graphics) {
        SkillTreePresentationDefinition definition = definition();
        SkillTreeState state = state();
        if (!isPlayerView() || selectedNodeId == null || state == null || definition == null) {
            return;
        }
        SkillTreeNodeDefinition node = definition.nodes().get(selectedNodeId);
        if (node == null) return;
        
        int panelW = PLAYER_PANEL_W;
        SkillNodeStatus status = state.status(node.id());
        int panelH = playerPopoverHeight(node, status);
        int[] bounds = selectedNodePopoverBounds(panelW, panelH);
        int px = bounds[0];
        int py = bounds[1];

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 280.0f);
        if (inscribeButton instanceof InscribeButton holdButton && holdButton.isHolding()) {
            int shake = holdButton.shakeOffset();
            graphics.pose().translate(shake, shake == 0 ? 0 : -shake / 2.0f, 0.0f);
        }
        drawPopoverPointer(graphics, px, py, panelW, panelH);
        drawMinecraftPanel(graphics, px, py, panelW, panelH);

        int tx = px + 10;
        int ty = py + 10;
        drawPopoverIcon(graphics, node, tx, ty);
        int textX = tx + 24;
        graphics.drawString(font, trimToWidth(node.title(), panelW - 44), textX, ty + 1, 0xFFFFEAA3, true);
        graphics.drawString(font, typeLabel(node.type()) + " - " + statusLabel(status), textX, ty + 13, statusColor(status), false);
        ty += 32;
        graphics.fill(px + 8, ty, px + panelW - 8, ty + 1, 0x88584B34);
        ty += 8;

        int descBottom = status == SkillNodeStatus.AVAILABLE ? py + panelH - 42 : py + panelH - 12;
        for (FormattedCharSequence line : wrappedDescription(node, panelW - 20)) {
            if (ty + font.lineHeight > descBottom) {
                break;
            }
            graphics.drawString(font, line, tx, ty, 0xFFD0D7E0, true);
            ty += 12;
        }
        ty = Math.max(ty + 2, descBottom - 18);
        if (status == SkillNodeStatus.INSCRIBED) {
            graphics.drawString(font, "Inscribed", tx, ty, 0xFF7FD888, true);
        } else {
            int insightAvailable = state.insight();
            String cost = "Cost: " + node.cost();
            String insight = "Insight: " + insightAvailable;
            graphics.drawString(font, cost, tx, ty, status == SkillNodeStatus.AVAILABLE ? 0xFF9EE6D8 : 0xFF8F938C, true);
            graphics.drawString(font, insight, px + panelW - 10 - font.width(insight), ty, insightAvailable >= node.cost() ? 0xFF7FD888 : 0xFFE05B5B, true);
        }
        graphics.pose().popPose();
    }

    private int playerPopoverHeight(SkillTreeNodeDefinition node, SkillNodeStatus status) {
        int descLines = wrappedDescription(node, PLAYER_PANEL_W - 20).size();
        int buttonSpace = status == SkillNodeStatus.AVAILABLE ? 32 : 0;
        int contentH = 10 + 24 + 9 + Math.min(4, descLines) * 12 + 17 + buttonSpace;
        return clampInt(contentH, PLAYER_PANEL_MIN_H, PLAYER_PANEL_MAX_H);
    }

    private List<FormattedCharSequence> wrappedDescription(SkillTreeNodeDefinition node, int width) {
        if (node.description() == null || node.description().isBlank()) {
            return List.of();
        }
        return font.split(Component.literal(node.description()), width);
    }

    private void drawPopoverIcon(GuiGraphics graphics, SkillTreeNodeDefinition node, int x, int y) {
        ItemStack stack = SkillTreeRenderer.resolveNodeIconStack(node);
        graphics.fill(x, y, x + 18, y + 18, 0xCC2D2A22);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xCC111111);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
        }
    }

    private static String typeLabel(String type) {
        if (type == null || type.isBlank()) {
            return "Trait";
        }
        return switch (type.trim().toLowerCase()) {
            case "core" -> "Core";
            case "technique" -> "Technique";
            case "manifestation" -> "Manifestation";
            case "axiom" -> "Axiom";
            default -> "Trait";
        };
    }

    private static String statusLabel(SkillNodeStatus status) {
        return switch (status) {
            case INSCRIBED -> "Inscribed";
            case AVAILABLE -> "Ready to inscribe";
            case LOCKED_POINTS -> "Needs more Insight";
            case LOCKED_PARENT -> "Requires connected skill";
            case LOCKED_LEVEL -> "Requires more discipline";
            case HIDDEN -> "Unknown";
        };
    }

    private static int statusColor(SkillNodeStatus status) {
        return switch (status) {
            case INSCRIBED -> 0xFF7FD888;
            case AVAILABLE -> 0xFFFFD25E;
            case LOCKED_POINTS -> 0xFFE4A94F;
            case LOCKED_PARENT, LOCKED_LEVEL, HIDDEN -> 0xFFE05B5B;
        };
    }

    private int[] selectedNodePopoverBounds(int panelW, int panelH) {
        SkillTreeNodeView selected = selectedNodeView();
        if (selected == null) {
            return new int[]{viewportX + viewportW - panelW - 10, viewportY + 10};
        }
        SkillTreeCameraTransform transform = activeTreeTransform();
        int nodeSize = selected.scaledSize(transform);
        int preferredX = selected.centerX(transform) + nodeSize / 2 + 18;
        int x = preferredX;
        if (x + panelW > viewportX + viewportW - 8) {
            x = selected.left(transform) - panelW - 18;
        }
        x = clampInt(x, viewportX + 8, viewportX + viewportW - panelW - 8);
        int y = selected.centerY(transform) - panelH / 2;
        y = clampInt(y, viewportY + 8, viewportY + viewportH - panelH - 8);
        return new int[]{x, y};
    }

    private SkillTreeNodeView selectedNodeView() {
        return activeScene == null || selectedNodeId == null ? null : activeScene.nodeById(selectedNodeId);
    }

    private void drawPopoverPointer(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        SkillTreeNodeView selected = selectedNodeView();
        if (selected == null) {
            return;
        }
        SkillTreeCameraTransform transform = activeTreeTransform();
        int fromX = selected.centerX(transform);
        int fromY = selected.centerY(transform);
        int toX = fromX < panelX ? panelX : panelX + panelW;
        int toY = clampInt(fromY, panelY + 12, panelY + panelH - 12);
        graphics.fill(Math.min(fromX, toX), fromY - 1, Math.max(fromX, toX) + 1, fromY + 2, 0xBB000000);
        graphics.fill(Math.min(fromX, toX), fromY - 2, Math.max(fromX, toX) + 1, fromY, 0xCC8B7C55);
        if (toY != fromY) {
            graphics.fill(toX - 1, Math.min(fromY, toY), toX + 2, Math.max(fromY, toY) + 1, 0xBB000000);
            graphics.fill(toX - 2, Math.min(fromY, toY), toX, Math.max(fromY, toY) + 1, 0xCC8B7C55);
        }
    }

    private void drawMinecraftPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x99000000);
        graphics.fill(x, y, x + w, y + h, 0xF01F211D);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xF03B3325);
        graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xE9161818);
        graphics.hLine(x + 1, x + w - 2, y + 1, 0xFF8B7C55);
        graphics.vLine(x + 1, y + 1, y + h - 2, 0xFF8B7C55);
        graphics.hLine(x + 1, x + w - 2, y + h - 2, 0xFF242018);
        graphics.vLine(x + w - 2, y + 1, y + h - 2, 0xFF242018);
    }

    private void drawHeader(GuiGraphics graphics) {
        if (isPlayerView()) {
            drawPlayerViewHeader(graphics);
            return;
        }
        SkillTreePresentationDefinition definition = definition();
        SkillTreeState state = state();
        String title = definition == null ? "Discipline Tree: " + activeSkillId() : definition.title();
        String subtitle = "A/D rotate  |  Drag to pan  |  Esc to close";
        int available = state == null ? 0 : state.insight();
        String points = "Unspent Insight: " + available;
        graphics.drawString(font, title, OUTER_MARGIN + 12, OUTER_MARGIN + 8, 0xFFFFE0A3, false);
        graphics.drawString(font, subtitle, OUTER_MARGIN + 12 + font.width(title) + 14, OUTER_MARGIN + 8, 0xFF9F9587, false);
        graphics.drawString(font, points, width - OUTER_MARGIN - 12 - font.width(points), OUTER_MARGIN + 8, 0xFF8DFF8D, false);
    }

    private void drawPlayerViewHeader(GuiGraphics graphics) {
        SkillTreePresentationDefinition definition = definition();
        SkillTreeState state = state();
        String title = definition == null ? "Discipline: " + activeSkillId().getPath() : definition.title();
        graphics.drawString(font, title, viewportX + 10, viewportY - 20, 0xFFFFEAA3, true);
        int available = state == null ? 0 : state.insight();
        String points = "Insight: " + available;
        graphics.drawString(font, points, viewportX + viewportW - font.width(points) - 10, viewportY - 20, 0xFF9FF0B0, true);
    }

    private void drawFooter(GuiGraphics graphics) {
        if (isPlayerView()) {
            drawPlayerViewFooter(graphics);
            return;
        }
        String zoom = "Zoom: " + Math.round(viewport.zoom() * 100.0f) + "%";
        if (canUseEditor()) {
            zoom += editorMode ? "  |  E: editor on" : "  |  E: editor";
        }
        graphics.drawString(font, zoom, OUTER_MARGIN + 12, height - OUTER_MARGIN - 14, 0xFFC8B58E, false);
    }

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 2;

    private void drawPlayerViewFooter(GuiGraphics graphics) {
        String subtitle = "Drag to pan  |  Scroll zoom levels  |  Esc to close";
        int y = height - OUTER_MARGIN - font.lineHeight - 4;
        graphics.drawString(font, subtitle, viewportX + 10, y, 0xFFAAB5C4, true);

        int totalWidth = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        int startX = width / 2 - totalWidth / 2;
        int startY = height - OUTER_MARGIN - SLOT_SIZE - 2;

        graphics.fill(startX - 2, startY - 2, startX + totalWidth + 2, startY + SLOT_SIZE + 2, 0x88000000);

        for (int i = 0; i < 9; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            boolean isSelected = i == ClientTechniqueCache.selectedSlot();
            graphics.fill(slotX, startY, slotX + SLOT_SIZE, startY + SLOT_SIZE, isSelected ? 0xCCB8860B : 0xCC111111);
            graphics.fill(slotX + 1, startY + 1, slotX + SLOT_SIZE - 1, startY + SLOT_SIZE - 1, isSelected ? 0xCC3D2E04 : 0xCC2D2A22);
            String label = Integer.toString(i + 1);
            graphics.drawString(font, label, slotX + 2, startY + 2, isSelected ? 0xFFFFE566 : 0xFF888888, false);

            ResourceLocation techId = ClientTechniqueCache.slot(i);
            if (techId != null && (draggingTechniqueId == null || !draggingTechniqueId.equals(techId.toString()) || draggingTechniqueSourceSlot != i)) {
                TechniqueDefinition def = TechniqueRegistry.get(techId);
                if (def != null && def.icon() != null) {
                    ItemStack iconStack = new ItemStack(BuiltInRegistries.ITEM.get(def.icon()));
                    graphics.renderItem(iconStack, slotX + 3, startY + 3);
                }
            }
        }
    }

    private int getHoveredHotbarSlot(double mouseX, double mouseY) {
        int totalWidth = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        int startX = width / 2 - totalWidth / 2;
        int startY = height - OUTER_MARGIN - SLOT_SIZE - 2;
        
        if (mouseY >= startY && mouseY <= startY + SLOT_SIZE) {
            for (int i = 0; i < 9; i++) {
                int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
                if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void handleTechniqueDrop(double mouseX, double mouseY) {
        int slot = getHoveredHotbarSlot(mouseX, mouseY);
        if (slot >= 0) {
            ResourceLocation techId = ResourceLocation.parse(draggingTechniqueId);
            PacketDistributor.sendToServer(new AssignTechniqueSlotPayload(slot, techId.toString()));
        } else if (draggingTechniqueSourceSlot >= 0) {
            PacketDistributor.sendToServer(new AssignTechniqueSlotPayload(draggingTechniqueSourceSlot, ""));
        }
    }


    /** Returns the technique ResourceLocation whose required node matches nodeId under the active skill. */
    private ResourceLocation resolveNodeTechniqueId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return null;
        ResourceLocation skillId = activeSkillId();
        String skillPath = skillId == null ? "" : skillId.getPath();
        String bareNodeId = nodeId;
        if (!skillPath.isEmpty() && nodeId.startsWith(skillPath + "_")) {
            bareNodeId = nodeId.substring(skillPath.length() + 1);
        }
        for (TechniqueDefinition def : TechniqueRegistry.entries()) {
            if (skillId != null && skillId.equals(def.requiredSkillId())) {
                if (nodeId.equals(def.requiredNodeId()) || bareNodeId.equals(def.requiredNodeId())) {
                    return def.id();
                }
            }
        }
        return null;
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
        graphics.drawString(font, "Disc. Lv.", tx + 64, fieldY + 103, 0xFFB8A98E, false);
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

    private boolean isTreeTransitioning() {
        return treeTransitionState != TreeTransitionState.STABLE;
    }

    private void beginTreeTransition(SkillTreeScene previousView, SkillTreeScene nextView) {
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

    private class InscribeButton extends Button {
        private boolean holding;
        private boolean completedHold;
        private int holdTicks;

        public InscribeButton(Component message, OnPress onPress) {
            super(0, 0, 100, 20, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
                return false;
            }
            holding = true;
            completedHold = false;
            holdTicks = 0;
            playUiSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.35f, 0.25f);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && holding) {
                cancelHold();
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        public void tickHold() {
            if (!holding || !this.active || !this.visible || completedHold) {
                return;
            }
            holdTicks++;
            if (holdTicks % 4 == 1) {
                float progress = holdTicks / (float) INSCRIBE_HOLD_TICKS;
                playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.75f + progress * 0.7f, 0.28f);
            }
            if (holdTicks >= INSCRIBE_HOLD_TICKS) {
                completedHold = true;
                holding = false;
                holdTicks = 0;
                this.onPress();
            }
        }

        public void cancelHold() {
            holding = false;
            completedHold = false;
            holdTicks = 0;
        }

        public boolean isHolding() {
            return holding;
        }

        public float holdProgress() {
            return holding ? Math.min(1.0f, holdTicks / (float) INSCRIBE_HOLD_TICKS) : 0.0f;
        }

        public int shakeOffset() {
            if (!holding) {
                return 0;
            }
            int amplitude = holdTicks > INSCRIBE_HOLD_TICKS * 2 / 3 ? 2 : 1;
            return switch (holdTicks % 4) {
                case 1 -> amplitude;
                case 3 -> -amplitude;
                default -> 0;
            };
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 320.0f);
            int shake = shakeOffset();
            if (shake != 0) {
                graphics.pose().translate(shake, -shake / 2.0f, 0.0f);
            }
            int alpha = this.active ? (this.isHovered ? 0xDD : 0x77) : 0x33;
            int textColor = this.active ? (this.isHovered ? 0xFFFFFFFF : 0xFFBDE8E0) : 0xFF5A6B80;
            int borderColor = this.active ? (this.isHovered ? 0xFFEAD089 : 0xFF4A6B8C) : 0xFF3D4958;

            int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;

            // Background fill
            graphics.fillGradient(x0, y0, x1, y1, (alpha << 24) | 0x0A0F1A, (alpha << 24) | 0x0A1F1A);
            if (holding) {
                int progressW = Math.round(width * Math.min(1.0f, holdTicks / (float) INSCRIBE_HOLD_TICKS));
                graphics.fill(x0 + 1, y0 + 1, x0 + 1 + progressW, y1 - 1, 0x885FBF7A);
            }

            // Border drawn with fill() so it stays exactly within the widget bounds
            // (hLine/vLine overshoot by 1px and cause visual vs. hit-box mismatch)
            graphics.fill(x0, y0, x1, y0 + 1, borderColor);         // top
            graphics.fill(x0, y1 - 1, x1, y1, borderColor);         // bottom
            graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, borderColor); // left
            graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, borderColor); // right

            graphics.drawCenteredString(font, this.getMessage(), x0 + width / 2, y0 + (height - 8) / 2, textColor);
            graphics.pose().popPose();
        }
    }
}
