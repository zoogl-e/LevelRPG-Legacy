package net.zoogle.levelrpg.client.input.technique;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.client.Keybinds;
import net.zoogle.levelrpg.client.gauge.MomentumHudController;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCache;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCostResolver;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.net.payload.ActivateTechniqueSlotPayload;
import net.zoogle.levelrpg.net.payload.SelectTechniqueSlotPayload;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;

import java.util.Objects;

public final class TechniqueInputController {
    private static final boolean[] TECHNIQUE_SLOT_WAS_DOWN = new boolean[PlayerTechniqueData.SLOT_COUNT];
    private static boolean techniqueSelectWasDown;
    private static int pendingSelectedSlot = 1;

    private TechniqueInputController() {}

    public static boolean isTechniqueSelectModeActive() {
        return techniqueSelectWasDown;
    }

    public static int pendingSelectedSlotZeroBased() {
        return Math.max(0, Math.min(PlayerTechniqueData.SLOT_COUNT - 1, pendingSelectedSlot - 1));
    }

    public static void tick(Minecraft mc) {
        boolean selectDown = (Keybinds.TECHNIQUE_SELECT_MODIFIER != null && Keybinds.TECHNIQUE_SELECT_MODIFIER.isDown())
                || InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_R);
        if (selectDown && !techniqueSelectWasDown) {
            pendingSelectedSlot = ClientTechniqueCache.selectedSlot() + 1;
            showPendingTechniquePopup(mc);
        }
        for (int i = 0; i < Keybinds.TECHNIQUE_SLOTS.length; i++) {
            KeyMapping mapping = Keybinds.TECHNIQUE_SLOTS[i];
            if (mapping == null) {
                continue;
            }
            boolean down = mapping.isDown();
            if (down && !TECHNIQUE_SLOT_WAS_DOWN[i]) {
                if (selectDown) {
                    pendingSelectedSlot = i + 1;
                    showPendingTechniquePopup(mc);
                    revealMomentumForTechniqueAttempt(i);
                    PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(i + 1));
                } else {
                    revealMomentumForTechniqueAttempt(i);
                    PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(i + 1));
                }
            }
            TECHNIQUE_SLOT_WAS_DOWN[i] = down;
            while (mapping.consumeClick()) {
                // drain queued clicks so hold/repeat does not re-trigger activations
            }
        }
        if (!selectDown && techniqueSelectWasDown) {
            PacketDistributor.sendToServer(new SelectTechniqueSlotPayload(pendingSelectedSlot));
        }
        if (Keybinds.TECHNIQUE_TRIGGER != null) {
            while (Keybinds.TECHNIQUE_TRIGGER.consumeClick()) {
                int selected = ClientTechniqueCache.selectedSlot() + 1;
                revealMomentumForTechniqueAttempt(selected - 1);
                PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(selected));
            }
        }
        if (selectDown) {
            showPendingTechniquePopup(mc);
        }
        techniqueSelectWasDown = selectDown;
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!techniqueSelectWasDown) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (Math.abs(delta) < 0.0001) {
            return;
        }
        int step = delta > 0.0 ? -1 : 1;
        int zeroBased = pendingSelectedSlot - 1;
        int next = (zeroBased + step) % PlayerTechniqueData.SLOT_COUNT;
        if (next < 0) {
            next += PlayerTechniqueData.SLOT_COUNT;
        }
        pendingSelectedSlot = next + 1;
        showPendingTechniquePopup(Minecraft.getInstance());
        event.setCanceled(true);
    }

    private static void showPendingTechniquePopup(Minecraft minecraft) {
        if (minecraft.gui == null) {
            return;
        }
        ResourceLocation techniqueId = ClientTechniqueCache.slot(Math.max(0, pendingSelectedSlot - 1));
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        if (technique != null) {
            String label = technique.displayName().isBlank() ? technique.id().getPath() : technique.displayName();
            double effectiveCost = ClientTechniqueCostResolver.effectiveCost(technique);
            String subtitle = technique.cost().hasGaugeCost()
                    ? " Cost: " + (int) Math.round(effectiveCost)
                    : " Cost: None";
            MutableComponent message = Component.literal(Objects.requireNonNull(label))
                    .append(Component.literal(subtitle).withStyle(ChatFormatting.AQUA));
            minecraft.gui.setOverlayMessage(message, false);
        } else {
            minecraft.gui.setOverlayMessage(Component.literal("Empty Technique Slot"), false);
        }
    }

    private static void revealMomentumForTechniqueAttempt(int zeroBasedSlot) {
        if (!PlayerTechniqueData.isValidSlot(zeroBasedSlot)) {
            return;
        }
        ResourceLocation techniqueId = ClientTechniqueCache.slot(zeroBasedSlot);
        TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
        if (technique == null || !technique.cost().hasGaugeCost()) {
            return;
        }
        if (!GaugeRegistry.MOMENTUM.equals(technique.cost().gaugeId())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int tickNow = mc.player != null ? mc.player.tickCount : 0;
        MomentumHudController.onTechniqueAttempt(ClientTechniqueCostResolver.effectiveCost(technique), tickNow);
    }
}
