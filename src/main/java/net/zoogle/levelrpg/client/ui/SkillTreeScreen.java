package net.zoogle.levelrpg.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;

import java.util.Map;

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
        String title = skillName + " Tree";
        gfx.drawString(this.font, title, (this.width - this.font.width(title)) / 2, y0 + 20, 0xFFFFFF, false);

        // Left page: meta
        int ly = pageTop;
        gfx.drawString(this.font, "Skill: " + skillName, leftPageX, ly, 0xFFE0A0, false); ly += 12;
        gfx.drawString(this.font, "Unspent Points: " + ClientProfileCache.getUnspentSkillPoints(), leftPageX, ly, 0xA0FFA0, false); ly += 12;
        gfx.drawString(this.font, "Tip: use /levelrpg unlock " + pretty(skillId) + " <node>", leftPageX, ly, 0x888888, false); ly += 12;

        // Right page: nodes list
        int y = pageTop;
        var tree = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skillId);
        if (tree == null) {
            gfx.drawString(this.font, "No tree defined for this skill.", rightPageX, y, 0xFF6666, false);
            return;
        }
        if (tree.nodes() == null || tree.nodes().isEmpty()) {
            gfx.drawString(this.font, "This tree has no nodes.", rightPageX, y, 0xAAAAAA, false);
            return;
        }
        for (Map.Entry<String, net.zoogle.levelrpg.data.SkillTreeDefinition.Node> e : tree.nodes().entrySet()) {
            var n = e.getValue();
            String line = n.id() + "  (Cost " + Math.max(1, n.cost()) + ")";
            gfx.drawString(this.font, line, rightPageX, y, 0xFFFFFF, false);
            y += 10;
            if (n.requires() != null && !n.requires().isEmpty()) {
                String req = "  requires: " + String.join(", ", n.requires());
                gfx.drawString(this.font, req, rightPageX, y, 0xBBBBBB, false);
                y += 10;
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

    @Override
    public boolean isPauseScreen() { return false; }
}
