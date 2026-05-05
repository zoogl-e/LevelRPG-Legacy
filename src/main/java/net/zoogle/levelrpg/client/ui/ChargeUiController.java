package net.zoogle.levelrpg.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/**
 * Reusable charge-bar HUD utility for any hold-to-charge ability.
 */
public final class ChargeUiController {
    private static final ResourceLocation XP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static ChargeSession activeSession;

    private ChargeUiController() {
    }

    public static void beginFrame() {
        activeSession = null;
    }

    public static void offer(String id, String label, double progress, int rgbColor) {
        if (id == null || id.isBlank() || progress <= 0.0) {
            return;
        }
        ChargeSession candidate = new ChargeSession(
                id,
                label == null || label.isBlank() ? "Charging" : label,
                Math.max(0.0, Math.min(1.0, progress)),
                rgbColor
        );
        if (activeSession == null || candidate.progress > activeSession.progress) {
            activeSession = candidate;
        }
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (activeSession == null || minecraft == null || minecraft.font == null) {
            return;
        }
        int width = 100;
        int height = 5;
        int x = (graphics.guiWidth() - width) / 2;
        int y = (graphics.guiHeight() / 2) + 8;
        int fillWidth = (int) Math.round(width * activeSession.progress);
        graphics.blitSprite(XP_BAR_BACKGROUND_SPRITE, x, y, width, height);
        if (fillWidth > 0) {
            float red = ((activeSession.rgbColor >> 16) & 0xFF) / 255.0F;
            float green = ((activeSession.rgbColor >> 8) & 0xFF) / 255.0F;
            float blue = (activeSession.rgbColor & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(red, green, blue, 1.0F);
            graphics.blitSprite(XP_BAR_PROGRESS_SPRITE, width, height, 0, 0, x, y, fillWidth, height);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        Font font = minecraft.font;
        graphics.drawCenteredString(font, activeSession.label, graphics.guiWidth() / 2, y - 10, 0xFFFFFFFF);
        if (activeSession.progress >= 0.999) {
            graphics.drawString(font, "FULL", x + width + 4, y - 1, 0xFFFFFFFF, false);
        }
    }

    private record ChargeSession(String id, String label, double progress, int rgbColor) {
    }
}
