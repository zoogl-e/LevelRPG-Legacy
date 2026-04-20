package net.zoogle.levelrpg.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
import net.zoogle.levelrpg.progression.SkillTreeProgression;

import java.util.Map;
import java.util.Set;

public class SkillTreeScreen extends Screen {
    private static final ResourceLocation BOOK_TEX = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "textures/gui/book_gui.png");

    private final ResourceLocation skillId;

    public SkillTreeScreen(ResourceLocation skillId) {
        super(Component.translatable("screen.levelrpg.skill_tree"));
        this.skillId = skillId;
    }

    @Override
    protected void init() {
        net.zoogle.levelrpg.client.ui.BlurUtil.pushNoBlur();
        // no buttons yet; Esc will return
    }

    @Override
    public void onClose() {
        // Return to the game; Level Book is temporarily disabled
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        net.zoogle.levelrpg.client.ui.BlurUtil.popNoBlur();
        super.removed();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Custom dark overlay background instead of vanilla blur
        gfx.fill(0, 0, this.width, this.height, 0xA0000000);

        int texW = 295, texH = 180;
        int availW = Math.max(1, this.width - 80);
        int availH = Math.max(1, this.height - 80);
        double scale = Math.min((double) availW / (double) texW, (double) availH / (double) texH);
        int drawW = Math.max(1, (int) Math.round(texW * scale));
        int drawH = Math.max(1, (int) Math.round(texH * scale));
        int x0 = (this.width - drawW) / 2;
        int y0 = (this.height - drawH) / 2;
        try {
            // Draw full texture region scaled to drawW x drawH to avoid UV issues
            gfx.blit(BOOK_TEX, x0, y0, drawW, drawH, 0, 0, texW, texH, texW, texH);
        } catch (Throwable ignored) {}

        super.render(gfx, mouseX, mouseY, partialTick);

        // Define page bounds using art-safe rectangles (texture-space px scaled)
        // Left page:  (16,12) .. (136,168)
        // Right page: (159,12) .. (279,168)
        int lX1 = (int) Math.round(x0 + 16 * scale);
        int lY1 = (int) Math.round(y0 + 12 * scale);
        int lX2 = (int) Math.round(x0 + 136 * scale);
        int lY2 = (int) Math.round(y0 + 168 * scale);
        int rX1 = (int) Math.round(x0 + 159 * scale);
        int rY1 = (int) Math.round(y0 + 12 * scale);
        int rX2 = (int) Math.round(x0 + 279 * scale);
        int rY2 = (int) Math.round(y0 + 168 * scale);

        int leftPageX = lX1;
        int pageTop = lY1;
        int pageW = Math.max(1, lX2 - lX1);
        int pageH = Math.max(1, lY2 - lY1);
        int rightPageX = rX1;
        int rightPageW = Math.max(1, rX2 - rX1);
        int rightPageH = Math.max(1, rY2 - rY1);

        // Titles
        String skillName = displayNameForSkill(skillId);
        var tree = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skillId);
        String title = tree != null && !tree.title().isBlank() ? tree.title() : skillName + " Mastery";
        gfx.drawString(this.font, title, (this.width - this.font.width(title)) / 2, y0 + 20, 0xFFFFFF, false);

        // Left page: meta
        var skillState = ClientProfileCache.getSkillsView().get(skillId);
        int skillLevel = skillState != null ? Math.max(0, skillState.level) : 0;
        int spentPoints = ClientProfileCache.getTreePointsSpent(skillId);
        Set<String> unlockedNodes = ClientProfileCache.getTreeUnlockedNodes(skillId);
        int earnedPoints = tree != null ? tree.masteryPointsForLevel(skillLevel) : 0;
        int availablePoints = Math.max(0, earnedPoints - spentPoints);
        int ly = pageTop;
        gfx.drawString(this.font, "Skill: " + skillName, leftPageX, ly, 0xFFE0A0, false); ly += 12;
        gfx.drawString(this.font, "Level: " + skillLevel, leftPageX, ly, 0xFFFFFF, false); ly += 12;
        gfx.drawString(this.font, "Mastery: " + availablePoints + " free / " + earnedPoints + " earned", leftPageX, ly, 0xA0FFA0, false); ly += 12;
        gfx.drawString(this.font, "Invested: " + spentPoints, leftPageX, ly, 0xD8C080, false); ly += 12;

        // Right page: nodes list
        int y = pageTop;
        if (tree == null) {
            gfx.drawString(this.font, "No tree defined for this skill.", rightPageX, y, 0xFF6666, false);
            return;
        }
        if (!tree.summary().isBlank()) {
            y = drawWrapped(gfx, tree.summary(), leftPageX, ly, pageW, 0xB8B8B8);
            ly = y + 8;
        }

        var nextThreshold = tree.nextThreshold(skillLevel).orElse(null);
        if (nextThreshold != null) {
            String thresholdLine = "Next threshold: L" + nextThreshold.level() + " +" + nextThreshold.points() + " point";
            if (nextThreshold.points() != 1) {
                thresholdLine += "s";
            }
            ly = drawWrapped(gfx, thresholdLine, leftPageX, ly, pageW, 0x9FD6FF);
            if (!nextThreshold.title().isBlank()) {
                ly = drawWrapped(gfx, nextThreshold.title(), leftPageX, ly, pageW, 0xC7E8FF);
            }
        } else {
            gfx.drawString(this.font, "All mastery thresholds reached.", leftPageX, ly, 0x9FD6FF, false);
            ly += 12;
        }
        gfx.drawString(this.font, "Tip: /levelrpg unlock " + pretty(skillId) + " <node>", leftPageX, ly, 0x888888, false);
        ly += 12;

        if (tree.nodes() == null || tree.nodes().isEmpty()) {
            gfx.drawString(this.font, "This tree has no nodes.", rightPageX, y, 0xAAAAAA, false);
            return;
        }
        for (Map.Entry<String, SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
            SkillTreeDefinition.Node node = e.getValue();
            SkillTreeProgression.NodeStatus status = nodeStatus(tree, node, skillLevel, availablePoints, unlockedNodes);
            String line = statusLabel(status) + " " + nodeDisplayName(node) + " (Cost " + node.normalizedCost() + ")";
            y = drawWrapped(gfx, line, rightPageX, y, rightPageW, colorForStatus(status));
            String requirementText = requirementText(tree, node, skillLevel, unlockedNodes);
            if (!requirementText.isBlank()) {
                y = drawWrapped(gfx, requirementText, rightPageX + 6, y, rightPageW - 6, 0xBBBBBB);
            }
            if (!node.description().isBlank()) {
                y = drawWrapped(gfx, node.description(), rightPageX + 6, y, rightPageW - 6, 0x999999);
            }
            y += 4;
            if (y > pageTop + pageH - 16) break;
        }

        // Footer / Back hint
        String hint = "Esc: Back";
        gfx.drawString(this.font, hint, x0 + 8, y0 + drawH - 14, 0x888888, false);
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        return pretty(skillId);
    }

    private static String pretty(ResourceLocation id) {
        String s = id.getPath().replace('_', ' ');
        return s.isEmpty() ? id.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static SkillTreeProgression.NodeStatus nodeStatus(
            SkillTreeDefinition tree,
            SkillTreeDefinition.Node node,
            int skillLevel,
            int availablePoints,
            Set<String> unlockedNodes
    ) {
        if (unlockedNodes.contains(node.id())) {
            return SkillTreeProgression.NodeStatus.UNLOCKED;
        }
        if (skillLevel < SkillTreeProgression.requiredLevelFor(tree, node)) {
            return SkillTreeProgression.NodeStatus.LOCKED_SKILL_LEVEL;
        }
        for (String requirement : node.requires()) {
            if (!unlockedNodes.contains(requirement)) {
                return SkillTreeProgression.NodeStatus.LOCKED_PREREQUISITE;
            }
        }
        if (availablePoints < node.normalizedCost()) {
            return SkillTreeProgression.NodeStatus.LOCKED_MASTERY_POINTS;
        }
        return SkillTreeProgression.NodeStatus.AVAILABLE;
    }

    private static String requirementText(
            SkillTreeDefinition tree,
            SkillTreeDefinition.Node node,
            int skillLevel,
            Set<String> unlockedNodes
    ) {
        if (unlockedNodes.contains(node.id())) {
            return "";
        }
        if (skillLevel < SkillTreeProgression.requiredLevelFor(tree, node)) {
            return "Requires level " + SkillTreeProgression.requiredLevelFor(tree, node);
        }
        if (!node.requires().isEmpty()) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            for (String requirement : node.requires()) {
                if (!unlockedNodes.contains(requirement)) {
                    missing.add(requirement);
                }
            }
            if (!missing.isEmpty()) {
                return "Requires: " + String.join(", ", missing);
            }
        }
        return "";
    }

    private static String statusLabel(SkillTreeProgression.NodeStatus status) {
        return switch (status) {
            case UNLOCKED -> "[Unlocked]";
            case AVAILABLE -> "[Ready]";
            case LOCKED_SKILL_LEVEL -> "[Level]";
            case LOCKED_PREREQUISITE -> "[Path]";
            case LOCKED_MASTERY_POINTS -> "[Points]";
        };
    }

    private static int colorForStatus(SkillTreeProgression.NodeStatus status) {
        return switch (status) {
            case UNLOCKED -> 0x7DFF9E;
            case AVAILABLE -> 0xFFFFFF;
            case LOCKED_SKILL_LEVEL -> 0xFFCC66;
            case LOCKED_PREREQUISITE -> 0xC7B8FF;
            case LOCKED_MASTERY_POINTS -> 0xFF8F8F;
        };
    }

    private static String nodeDisplayName(SkillTreeDefinition.Node node) {
        if (!node.title().isBlank()) {
            return node.title();
        }
        return prettyNodeId(node.id());
    }

    private static String prettyNodeId(String nodeId) {
        String s = nodeId.replace('_', ' ');
        return s.isEmpty() ? nodeId : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int drawWrapped(GuiGraphics gfx, String text, int x, int y, int maxWidth, int color) {
        for (var line : this.font.split(Component.literal(text), Math.max(1, maxWidth))) {
            gfx.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
