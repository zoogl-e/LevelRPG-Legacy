package net.zoogle.levelrpg.client.technique;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.Keybinds;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.client.ui.ChargeUiController;
import net.zoogle.levelrpg.client.valor.ClientRecklessChargeState;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;
import java.util.Objects;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class TechniqueHudOverlay {
    private static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final int CHARGING_JUMP_MAX_CHARGE_TICKS = 40;
    private static final double CHARGING_JUMP_COST = 100.0;
    private static int chargingJumpStartTick = -1;
    private static boolean chargingJumpWasSneaking;

    private TechniqueHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHotbarLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldDrawTechniqueHud(minecraft)) {
            return;
        }
        if (!Keybinds.isTechniqueSelectModeActive()) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() / 2) - 91;
        int y = graphics.guiHeight() - 22;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blitSprite(Objects.requireNonNull(HOTBAR_SPRITE), x, y, 182, 22);
        graphics.blitSprite(Objects.requireNonNull(HOTBAR_SELECTION_SPRITE), x - 1 + (Keybinds.pendingSelectedSlotZeroBased() * 20), y - 1, 24, 23);
        for (int i = 0; i < PlayerTechniqueData.SLOT_COUNT; i++) {
            drawSlot(graphics, minecraft, x, y, i);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        if (shouldDrawTechniqueHud(minecraft) && !Keybinds.isTechniqueSelectModeActive()) {
            int hotbarX = (graphics.guiWidth() / 2) - 91;
            int hotbarY = graphics.guiHeight() - 22;
            for (int i = 0; i < PlayerTechniqueData.SLOT_COUNT; i++) {
                drawSelectedTechniqueGlowBacking(graphics, minecraft, hotbarX, hotbarY, i);
                drawTechniqueHintSegment(graphics, hotbarX, hotbarY, i);
            }
        }
        if (minecraft.screen != null) {
            return;
        }
        ChargeUiController.beginFrame();
        registerChargingJumpIndicator(minecraft);
        registerRecklessStrikeIndicator(minecraft);
        ChargeUiController.render(graphics, minecraft);
    }

    /**
     * Hotbar-layer replacement and technique hints should still show when chat or a container screen is open
     * (those UIs keep the hotbar visible; {@code screen == null} alone would hide our overlays).
     */
    private static boolean shouldDrawTechniqueHud(Minecraft minecraft) {
        if (minecraft.options.hideGui || minecraft.player == null) {
            return false;
        }
        Screen screen = minecraft.screen;
        if (screen == null) {
            return true;
        }
        return screen instanceof ChatScreen || screen instanceof AbstractContainerScreen<?>;
    }

    private static void drawSlot(GuiGraphics graphics, Minecraft minecraft, int hotbarX, int hotbarY, int index) {
        var techniqueId = ClientTechniqueCache.slot(index);
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        boolean assigned = technique != null;
        boolean enoughGauge = hasEnoughGauge(technique);
        ClientTechniqueCache.CooldownView cooldown = assigned && technique != null
                ? ClientTechniqueCache.cooldown(technique.id())
                : ClientTechniqueCache.CooldownView.NONE;
        boolean ready = assigned && enoughGauge && !cooldown.active();
        boolean selectedAction = Keybinds.pendingSelectedSlotZeroBased() == index;
        int slotX = hotbarX + index * 20;
        int slotY = hotbarY;
        int contentX = slotX + 3;
        int contentY = slotY + 3;

        int background = 0x00000000;
        if (assigned) {
            if (selectedAction) {
                background = ready ? 0xAA72D6FF : 0xAA18466C;
            } else {
                background = ready ? 0x664FA7E0 : 0x66203D54;
            }
        }
        if (selectedAction) {
            int outline = ready ? 0xFF9EE3FF : 0xFF2A5C89;
            graphics.fill(slotX - 1, slotY - 1, slotX + 23, slotY, outline);
            graphics.fill(slotX - 1, slotY + 22, slotX + 23, slotY + 23, outline);
            graphics.fill(slotX - 1, slotY - 1, slotX, slotY + 23, outline);
            graphics.fill(slotX + 22, slotY - 1, slotX + 23, slotY + 23, outline);
        }
        if (background != 0x00000000) {
            graphics.fill(contentX, contentY, contentX + 16, contentY + 16, background);
        }

        if (assigned && technique != null) {
            ItemStack iconStack = resolveTechniqueIconStack(technique);
            graphics.renderItem(Objects.requireNonNull(iconStack), contentX, contentY);
            if (!enoughGauge) {
                graphics.fill(contentX, contentY, contentX + 16, contentY + 16, 0x66000000);
            }
            if (cooldown.active()) {
                double fraction = cooldown.maxTicks() <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, cooldown.remainingTicks() / (double) cooldown.maxTicks()));
                int overlayHeight = (int) Math.round(16 * fraction);
                graphics.fill(contentX, contentY + 16 - overlayHeight, contentX + 16, contentY + 16, 0xAA000000);
                int seconds = Math.max(1, (int) Math.ceil(cooldown.remainingTicks() / 20.0));
                graphics.drawString(minecraft.font, Integer.toString(seconds), contentX + 7, contentY + 6, 0xFFFFFFFF, false);
            }
        }
    }

    private static ItemStack resolveTechniqueIconStack(TechniqueDefinition technique) {
        if (technique != null && technique.icon() != null) {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(technique.icon()).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return new ItemStack(Objects.requireNonNull(Items.BOOK));
    }

    /**
     * One-pixel pulsing highlight for the server-selected technique column, directly under the readiness line
     * so the stack stays two pixels tall and clear of the XP bar.
     */
    private static void drawSelectedTechniqueGlowBacking(GuiGraphics graphics, Minecraft minecraft, int hotbarX, int hotbarY, int index) {
        if (index != ClientTechniqueCache.selectedSlot()) {
            return;
        }
        int slotX = hotbarX + index * 20;
        int tick = minecraft.player != null ? minecraft.player.tickCount : 0;
        double pulse = 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(tick * 0.09));
        int alpha = (int) (100 * pulse);
        int y = hotbarY - 2;
        // Slightly wider than the readiness strip; single row only
        graphics.fill(slotX + 1, y, slotX + 19, y + 1, (alpha << 24) | 0x0078D0FF);
    }

    private static void drawTechniqueHintSegment(GuiGraphics graphics, int hotbarX, int hotbarY, int index) {
        ResourceLocation techniqueId = ClientTechniqueCache.slot(index);
        if (techniqueId == null) {
            return;
        }
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        if (technique == null) {
            return;
        }
        boolean enoughGauge = hasEnoughGauge(technique);
        ClientTechniqueCache.CooldownView cooldown = ClientTechniqueCache.cooldown(technique.id());
        boolean ready = enoughGauge && !cooldown.active();
        int color = ready ? 0xFF72D6FF : 0xFF365166;
        int slotX = hotbarX + index * 20;
        int top = hotbarY - 1;
        int left = slotX + 2;
        int right = left + 16;
        graphics.fill(left, top, right, top + 1, color);
    }

    private static boolean hasEnoughGauge(TechniqueDefinition technique) {
        if (technique == null || !technique.cost().hasGaugeCost()) {
            return true;
        }
        if (!GaugeRegistry.MOMENTUM.equals(technique.cost().gaugeId())) {
            return true;
        }
        for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
            if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                return gauge.value() + 0.0001 >= ClientTechniqueCostResolver.effectiveCost(technique);
            }
        }
        return false;
    }

    private static void registerChargingJumpIndicator(Minecraft minecraft) {
        if (minecraft.player == null) {
            chargingJumpWasSneaking = false;
            chargingJumpStartTick = -1;
            return;
        }
        if (!hasEnoughMomentumForChargingJump()) {
            chargingJumpWasSneaking = false;
            chargingJumpStartTick = -1;
            return;
        }
        boolean inWater = minecraft.player.isInWaterOrBubble();
        boolean grounded = minecraft.player.onGround();
        boolean sneaking = minecraft.player.isShiftKeyDown();
        if (!inWater || !grounded) {
            chargingJumpWasSneaking = false;
            chargingJumpStartTick = -1;
            return;
        }
        if (sneaking && !chargingJumpWasSneaking) {
            chargingJumpStartTick = minecraft.player.tickCount;
        } else if (!sneaking) {
            chargingJumpStartTick = -1;
        }
        chargingJumpWasSneaking = sneaking;
        if (!sneaking || chargingJumpStartTick < 0) {
            return;
        }
        int chargedTicks = Math.max(0, minecraft.player.tickCount - chargingJumpStartTick);
        double progress = Math.max(0.0, Math.min(1.0, chargedTicks / (double) CHARGING_JUMP_MAX_CHARGE_TICKS));
        ChargeUiController.offer("charging_jump", "Charging Jump", progress, 0x74B6FF);
    }

    private static void registerRecklessStrikeIndicator(Minecraft minecraft) {
        if (minecraft.player == null || !ClientRecklessChargeState.isActive()) {
            return;
        }
        double progress = ClientRecklessChargeState.progress(minecraft.player.tickCount);
        ChargeUiController.offer("reckless_strike", "Reckless Strike", progress, 0xFF5D47);
    }

    private static boolean hasEnoughMomentumForChargingJump() {
        for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
            if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                return gauge.value() + 0.0001 >= CHARGING_JUMP_COST;
            }
        }
        return false;
    }

}
