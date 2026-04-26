package net.zoogle.levelrpg.client.ui;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen view of every canonical specialization tree in one pannable canvas.
 * {@code highlightSkillId} (from the journal) frames that discipline on first open when possible.
 */
public class SkillTreeScreen extends Screen {
    private static final ResourceLocation MENU_TILE =
            ResourceLocation.withDefaultNamespace("textures/gui/options_background.png");

    private final ResourceLocation highlightSkillId;
    private final SkillTreeAdvancementMapPanel mapPanel = new SkillTreeAdvancementMapPanel();
    private final List<SkillTreeAdvancementMapPanel.SkillTreeUnlockHit> unlockHits = new ArrayList<>();
    private int mapViewX;
    private int mapViewY;
    private int mapViewW;
    private int mapViewH;

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
        if (button == 0) {
            for (int i = unlockHits.size() - 1; i >= 0; i--) {
                SkillTreeAdvancementMapPanel.SkillTreeUnlockHit h = unlockHits.get(i);
                if (h.contains(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new UnlockTreeNodeRequestPayload(h.skillId(), h.nodeId()));
                    return true;
                }
            }
            if (insideMap(mouseX, mouseY) && mapPanel.mouseClicked(mouseX, mouseY, button, mapViewX, mapViewY, mapViewW, mapViewH)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mapPanel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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

    private boolean insideMap(double mouseX, double mouseY) {
        return mouseX >= mapViewX && mouseX < mapViewX + mapViewW && mouseY >= mapViewY && mouseY < mapViewY + mapViewH;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        unlockHits.clear();

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

        mapViewX = panelX + 4;
        mapViewY = headerY + 14;
        mapViewW = Math.max(20, panelW - 8);
        mapViewH = Math.max(20, panelY + panelH - mapViewY - 6);

        List<SkillTreeAdvancementMapPanel.CompositeSection> sections = buildCompositeSections(specEarned, specSpent);
        mapPanel.renderForest(gfx, this.font, sections, highlightSkillId, mapViewX, mapViewY, mapViewW, mapViewH, mouseX, mouseY, unlockHits)
                .ifPresent(lines -> {
                    List<FormattedCharSequence> tip = new ArrayList<>();
                    for (Component line : lines) {
                        tip.addAll(this.font.split(line, Math.max(40, mapViewW - 8)));
                    }
                    gfx.renderTooltip(this.font, tip, mouseX, mouseY);
                });

        String esc = "Esc — close · drag pan · scroll";
        gfx.drawString(this.font, esc, panelX + panelW - 6 - this.font.width(esc), panelY + panelH - 12, 0x888888, false);
    }

    private List<SkillTreeAdvancementMapPanel.CompositeSection> buildCompositeSections(int specEarned, int specSpent) {
        List<SkillTreeAdvancementMapPanel.CompositeSection> list = new ArrayList<>();
        final int header = 14;
        final int gap = 20;
        int cursor = 0;
        for (ProgressionSkill ps : ProgressionSkill.values()) {
            ResourceLocation id = ps.id();
            SkillTreeDefinition tree = SkillTreeRegistry.get(id);
            SkillState skillState = ClientProfileCache.getSkillsView().get(id);
            int skillLevel = skillState != null ? Math.max(0, skillState.level) : 0;
            Set<String> unlockedNodes = ClientProfileCache.getTreeUnlockedNodes(id);
            TreeSnapshot snap = SkillTreeProgression.snapshot(id, tree, skillLevel, unlockedNodes, specEarned, specSpent);

            int titleY = cursor;
            cursor += header;

            if (tree == null || snap.nodes().isEmpty()) {
                cursor += 14 + gap;
                list.add(new SkillTreeAdvancementMapPanel.CompositeSection(id, tree, snap, 4, titleY, 0, Map.of()));
                continue;
            }

            Map<String, int[]> positions = SkillTreeGraphLayout.resolve(tree);
            if (positions.isEmpty()) {
                cursor += 14 + gap;
                list.add(new SkillTreeAdvancementMapPanel.CompositeSection(id, tree, snap, 4, titleY, 0, Map.of()));
                continue;
            }

            int[] b = SkillTreeGraphLayout.boundsOf(positions);
            int yShift = cursor - b[1];
            int titleX = b[0];
            cursor += (b[3] - b[1]) + gap;

            list.add(new SkillTreeAdvancementMapPanel.CompositeSection(id, tree, snap, titleX, titleY, yShift, positions));
        }
        return list;
    }

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
