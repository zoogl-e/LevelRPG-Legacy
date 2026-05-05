package net.zoogle.levelrpg.client.gauge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.Keybinds;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import java.util.Objects;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class GaugeHudOverlay {
    private static final ResourceLocation XP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    // Match the vanilla hunger/air lane width (10 icons * 8px + 1px).
    private static final int STACK_BAR_WIDTH = 81;
    private static final int STACK_BAR_HEIGHT = 5;
    private static final int STACK_ROW_SPACING = 14;
    private static final int STACK_BASE_Y_OFFSET = 46;
    private static final int STACK_BASE_Y_OFFSET_UNDERWATER = 56;
    private static final int MOMENTUM_COOL_BLUE = 0x39E7FF;
    private static final int MOMENTUM_HOT_RED = 0xFF3A2A;
    private static final double MOMENTUM_HOT_START_FRACTION = 0.70;
    private static final float MOMENTUM_TINT_ALPHA = 0.80F;
    private static final float MOMENTUM_LABEL_ALPHA = 0.55F;
    private static final float MOMENTUM_SHAKE_MAX_X = 1.8F;
    private static final float MOMENTUM_SHAKE_MAX_Y = 1.2F;
    private static final float MOMENTUM_SHAKE_FREQ = 0.85F;

    private GaugeHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        var player = Objects.requireNonNull(minecraft.player);
        ClientGaugeCache.tickSmoothing();
        GuiGraphics graphics = event.getGuiGraphics();
        // Anchor to the same right-side lane vanilla uses for hunger/air.
        int x = (graphics.guiWidth() / 2) + 10;
        boolean hasAirRow = player.isEyeInFluid(FluidTags.WATER)
                || player.getAirSupply() < player.getMaxAirSupply();
        int baseYOffset = hasAirRow ? STACK_BASE_Y_OFFSET_UNDERWATER : STACK_BASE_Y_OFFSET;
        int baseY = graphics.guiHeight() - baseYOffset;
        int tickNow = player.tickCount;
        boolean selectModeActive = Keybinds.isTechniqueSelectModeActive();
        int rendered = 0;
        for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
            MomentumHudController.Snapshot momentumHud = null;
            if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                double fraction = gauge.max() <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, gauge.value() / gauge.max()));
                momentumHud = MomentumHudController.update(tickNow, selectModeActive, fraction);
            }
            boolean renderMomentumBar = momentumHud != null && momentumHud.barAlpha() > 0.02;
            if (!gauge.shouldRender() && !renderMomentumBar) {
                continue;
            }
            int y = baseY - rendered * STACK_ROW_SPACING;
            drawGauge(graphics, minecraft, gauge, x, y, STACK_BAR_WIDTH, tickNow, momentumHud);
            rendered++;
            if (rendered >= 4) {
                break;
            }
        }
    }

    private static void drawGauge(
            GuiGraphics graphics,
            Minecraft minecraft,
            ClientGaugeCache.GaugeView gauge,
            int x,
            int y,
            int width,
            int tickNow,
            MomentumHudController.Snapshot momentumHud
    ) {
        float alpha = (float) Math.max(0.0, Math.min(1.0, gauge.alpha()));
        if (momentumHud != null) {
            alpha = (float) Math.max(alpha, momentumHud.barAlpha());
        }
        double fraction = gauge.max() <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, gauge.value() / gauge.max()));
        int filledWidth = (int) Math.round(width * fraction);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blitSprite(XP_BAR_BACKGROUND_SPRITE, x, y, width, STACK_BAR_HEIGHT);
        if (filledWidth > 0) {
            if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                // Pass 1: neutralize into grayscale/white so texture detail stays but hue contamination is removed.
                RenderSystem.setShaderColor(0.86F, 0.86F, 0.86F, alpha * 0.45F);
                graphics.blitSprite(XP_BAR_PROGRESS_SPRITE, width, STACK_BAR_HEIGHT, 0, 0, x, y, filledWidth, STACK_BAR_HEIGHT);
                // Pass 2: apply vibrant momentum heat tint at ~80% opacity.
                int fillColor = resolveMomentumFillColor(fraction);
                float red = ((fillColor >> 16) & 0xFF) / 255.0F;
                float green = ((fillColor >> 8) & 0xFF) / 255.0F;
                float blue = (fillColor & 0xFF) / 255.0F;
                RenderSystem.setShaderColor(red, green, blue, alpha * MOMENTUM_TINT_ALPHA);
                graphics.blitSprite(XP_BAR_PROGRESS_SPRITE, width, STACK_BAR_HEIGHT, 0, 0, x, y, filledWidth, STACK_BAR_HEIGHT);
            } else {
                int fillColor = gauge.primaryColor();
                float red = ((fillColor >> 16) & 0xFF) / 255.0F;
                float green = ((fillColor >> 8) & 0xFF) / 255.0F;
                float blue = (fillColor & 0xFF) / 255.0F;
                RenderSystem.setShaderColor(red, green, blue, alpha);
                graphics.blitSprite(XP_BAR_PROGRESS_SPRITE, width, STACK_BAR_HEIGHT, 0, 0, x, y, filledWidth, STACK_BAR_HEIGHT);
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (momentumHud != null) {
            if (momentumHud.labelAlpha() > 0.02) {
                String text = momentumLabel(gauge, fraction);
                drawEmbeddedBarLabel(
                        graphics,
                        minecraft,
                        text,
                        x,
                        y,
                        width,
                        STACK_BAR_HEIGHT,
                        alpha,
                        fraction,
                        (float) momentumHud.labelAlpha(),
                        (float) momentumHud.shakeIntensity(),
                        tickNow
                );
            }
        } else {
            String text = gauge.displayName() + " " + formatGaugeValue(gauge.value()) + "/" + formatGaugeValue(gauge.max());
            int textAlpha = (int) Math.round(alpha * 255.0);
            int textColor = (textAlpha << 24) | 0x00FFFFFF;
            graphics.drawString(minecraft.font, text, x, y - 10, textColor, false);
        }
    }

    private static long formatGaugeValue(double value) {
        return Math.round(value);
    }

    private static String momentumLabel(ClientGaugeCache.GaugeView gauge, double fraction) {
        if (fraction >= 0.999) {
            return gauge.displayName() + " MAX";
        }
        return gauge.displayName() + " " + formatGaugeValue(gauge.value()) + "/" + formatGaugeValue(gauge.max());
    }

    private static int resolveMomentumFillColor(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        if (clamped <= MOMENTUM_HOT_START_FRACTION) {
            return MOMENTUM_COOL_BLUE;
        }
        // Ramp quickly into hot tones once above 70%.
        double hotRange = (clamped - MOMENTUM_HOT_START_FRACTION) / (1.0 - MOMENTUM_HOT_START_FRACTION);
        double easedHot = Math.pow(Math.max(0.0, Math.min(1.0, hotRange)), 0.72);
        return lerpRgb(MOMENTUM_COOL_BLUE, MOMENTUM_HOT_RED, easedHot);
    }

    private static int lerpRgb(int from, int to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int r = (int) Math.round(fromR + (toR - fromR) * clamped);
        int g = (int) Math.round(fromG + (toG - fromG) * clamped);
        int b = (int) Math.round(fromB + (toB - fromB) * clamped);
        return (r << 16) | (g << 8) | b;
    }

    private static void drawEmbeddedBarLabel(
            GuiGraphics graphics,
            Minecraft minecraft,
            String text,
            int x,
            int y,
            int width,
            int height,
            float alpha,
            double fraction,
            float visibilityAlpha,
            float shakeIntensity,
            int tickNow
    ) {
        if (text == null || text.isBlank()) {
            return;
        }
        int baseWidth = Math.max(1, minecraft.font.width(text));
        float scaleX = (width - 12.0F) / baseWidth;
        float scaleY = 0.56F;
        float textW = baseWidth * scaleX;
        float textH = minecraft.font.lineHeight * scaleY;
        float shake = Math.max(0.0F, Math.min(1.0F, shakeIntensity));
        float wobblePhase = tickNow * MOMENTUM_SHAKE_FREQ;
        float jitterX = (float) Math.sin(wobblePhase * 1.83F) * MOMENTUM_SHAKE_MAX_X * shake;
        float jitterY = (float) Math.cos(wobblePhase * 2.17F) * MOMENTUM_SHAKE_MAX_Y * shake;
        float drawX = x + (width - textW) / 2.0F + jitterX;
        float drawY = y + (height - textH) / 2.0F + jitterY;
        int a = Math.max(0, Math.min(255, (int) Math.round(alpha * MOMENTUM_LABEL_ALPHA * visibilityAlpha * 255.0F)));
        int textRgb = resolveMomentumFillColor(fraction);
        // Slightly brighten the label color so it remains readable over the filled bar.
        int r = Math.min(255, (int) Math.round(((textRgb >> 16) & 0xFF) * 0.82 + 255 * 0.18));
        int g = Math.min(255, (int) Math.round(((textRgb >> 8) & 0xFF) * 0.82 + 255 * 0.18));
        int b = Math.min(255, (int) Math.round((textRgb & 0xFF) * 0.82 + 255 * 0.18));
        int textColor = (a << 24) | (r << 16) | (g << 8) | b;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.drawString(minecraft.font, Component.literal(text).withStyle(ChatFormatting.ITALIC), 0, 0, textColor, false);
        graphics.pose().popPose();
    }

}
