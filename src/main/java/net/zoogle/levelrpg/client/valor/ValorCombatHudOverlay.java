package net.zoogle.levelrpg.client.valor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;

import java.util.Set;

/**
 * Valor: crosshair target HP bar (requires {@link ValorNodeIds#RESOLVE}) and Mortal Wound threshold hint
 * (requires {@link ValorNodeIds#MORTAL_WOUND}).
 */
@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class ValorCombatHudOverlay {
    private static final ResourceLocation XP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final int BAR_WIDTH = 110;
    private static final int BAR_HEIGHT = 5;
    private static final double MORTAL_WOUND_THRESHOLD = 0.25;

    private ValorCombatHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        if (!ClientProfileCache.isReady()) {
            return;
        }
        if (!hasValorNode(ValorNodeIds.RESOLVE)) {
            return;
        }
        LivingEntity target = pickCrosshairLiving(mc);
        if (target == null || !target.isAlive() || target == mc.player) {
            return;
        }
        float maxHp = target.getMaxHealth();
        if (maxHp <= 0.0F) {
            return;
        }
        float hp = Mth.clamp(target.getHealth(), 0.0F, maxHp);
        float frac = hp / maxHp;
        boolean mortal = hasValorNode(ValorNodeIds.MORTAL_WOUND);
        boolean inExecuteBand = mortal && hp > 0.0F && hp <= maxHp * (float) MORTAL_WOUND_THRESHOLD;

        GuiGraphics g = event.getGuiGraphics();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int x = sw / 2 - BAR_WIDTH / 2;
        int y = sh / 2 + 18;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.92F);
        g.blitSprite(XP_BAR_BACKGROUND_SPRITE, x, y, BAR_WIDTH, BAR_HEIGHT);
        int filled = (int) Math.round(BAR_WIDTH * frac);
        if (filled > 0) {
            int rgb = hpBarColor(frac);
            float red = ((rgb >> 16) & 0xFF) / 255.0F;
            float green = ((rgb >> 8) & 0xFF) / 255.0F;
            float blue = (rgb & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(red, green, blue, 0.95F);
            g.blitSprite(XP_BAR_PROGRESS_SPRITE, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (inExecuteBand) {
            int markX = x + (int) Math.round(BAR_WIDTH * MORTAL_WOUND_THRESHOLD);
            g.fill(markX, y - 1, markX + 1, y + BAR_HEIGHT + 1, 0xFFFFD700);
            g.drawCenteredString(mc.font, Component.literal("Mortal Wound").withStyle(ChatFormatting.GOLD), sw / 2, y - 12, 0xFFFFD700);
            g.drawCenteredString(mc.font, Component.literal("Sneak + hit").withStyle(ChatFormatting.GRAY), sw / 2, y + BAR_HEIGHT + 2, 0xFFAAAAAA);
        } else {
            String line = String.format("%.0f / %.0f", hp, maxHp);
            g.drawCenteredString(mc.font, line, sw / 2, y - 10, 0xFFFFFFFF);
        }
    }

    private static LivingEntity pickCrosshairLiving(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        Entity e = ((EntityHitResult) hit).getEntity();
        return e instanceof LivingEntity living ? living : null;
    }

    private static boolean hasValorNode(String shortNodeId) {
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(ValorNodeIds.SKILL);
        if (unlocked.isEmpty()) {
            return false;
        }
        String clean = shortNodeId == null ? "" : shortNodeId.trim();
        String prefixed = ValorNodeIds.SKILL.getPath() + "_" + clean;
        return unlocked.contains(clean) || unlocked.contains(prefixed);
    }

    private static int hpBarColor(float healthFraction) {
        float t = Mth.clamp(healthFraction, 0.0F, 1.0F);
        int r = (int) (45 + 210 * (1.0 - t));
        int g = (int) (45 + 210 * t);
        int b = 55;
        return (r << 16) | (g << 8) | b;
    }
}
