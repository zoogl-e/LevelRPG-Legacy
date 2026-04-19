package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.util.ItemGateUtil;

/**
 * Client-side guards to prevent local prediction/animation of illegal equips.
 * Cancels shift-left-click, drag/drop into armor/offhand, and number-key swaps before packets are sent.
 */
@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class InventoryClickGuards {
    private InventoryClickGuards() {}

    @SubscribeEvent
    public static void onMouseButtonPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        if (!(screen.getMenu() instanceof net.minecraft.world.inventory.InventoryMenu)) return;
        // Only handle left or right mouse button
        int button = event.getButton();
        if (button != 0 && button != 1) return;

        var mc = Minecraft.getInstance();
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return;

        // Case A: Shift-left on a gated equippable anywhere (prevents quick-move flicker)
        if (button == 0 && isShiftDown()) {
            if (!slot.hasItem()) return;
            ItemStack stack = slot.getItem();
            if (!ItemGateUtil.wouldEquip(stack)) return;
            GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
            if (gate == null) return;
            int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
            if (lvl < gate.minLevel) {
                event.setCanceled(true);
                if (mc.player != null) mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return;
        }

        // Case A2: Right-click auto-equip from slot (vanilla equips armor with right click)
        if (button == 1 && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (ItemGateUtil.wouldEquip(stack)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
                if (gate != null) {
                    int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                    if (lvl < gate.minLevel) {
                        event.setCanceled(true);
                        if (mc.player != null) mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }
                }
            }
        }

        // Case B: Drag/drop carried item onto armor/offhand slot (apply for both buttons)
        ItemStack carried = screen.getMenu().getCarried();
        if (carried != null && !carried.isEmpty() && isArmorOrOffhand(slot) && ItemGateUtil.wouldEquip(carried)) {
            GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
            if (gate != null) {
                int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                if (lvl < gate.minLevel) {
                    event.setCanceled(true);
                    if (mc.player != null) mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                    .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        }
    }

    // Some GUIs act on mouse release; mirror the same guard here
    @SubscribeEvent
    public static void onMouseButtonReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        if (!(screen.getMenu() instanceof net.minecraft.world.inventory.InventoryMenu)) return;
        int button = event.getButton();
        if (button != 0 && button != 1) return;
        var mc = Minecraft.getInstance();
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return;
        // Handle shift-left quick move on release (some UIs may process here)
        if (button == 0 && isShiftDown() && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (ItemGateUtil.wouldEquip(stack)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
                if (gate != null) {
                    int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                    if (lvl < gate.minLevel) {
                        event.setCanceled(true);
                        if (mc.player != null) mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }
                }
            }
        }
        // Handle right-click equip from slot on release (safety)
        if (button == 1 && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (ItemGateUtil.wouldEquip(stack)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
                if (gate != null) {
                    int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                    if (lvl < gate.minLevel) {
                        event.setCanceled(true);
                        if (mc.player != null) mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }
                }
            }
        }
        // Handle drop of carried onto armor slot
        ItemStack carried = screen.getMenu().getCarried();
        if (carried != null && !carried.isEmpty() && isArmorOrOffhand(slot) && ItemGateUtil.wouldEquip(carried)) {
            GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
            if (gate != null) {
                int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                if (lvl < gate.minLevel) {
                    event.setCanceled(true);
                    if (mc.player != null) mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                    .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        }
    }

    // While dragging, prevent placing carried equippable into armor/offhand
    @SubscribeEvent
    public static void onMouseDraggedPre(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        var mc = Minecraft.getInstance();
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return;
        ItemStack carried = screen.getMenu().getCarried();
        if (carried != null && !carried.isEmpty() && isArmorOrOffhand(slot) && ItemGateUtil.wouldEquip(carried)) {
            GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
            if (gate != null) {
                int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                if (lvl < gate.minLevel) {
                    event.setCanceled(true);
                    if (mc.player != null) mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                    .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        if (!(screen.getMenu() instanceof net.minecraft.world.inventory.InventoryMenu)) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !isArmorOrOffhand(slot)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Check hotbar number keys 1..9
        for (int i = 0; i < 9; i++) {
            var key = mc.options.keyHotbarSlots[i];
            if (key != null && key.matches(event.getKeyCode(), event.getScanCode())) {
                ItemStack hotbar = mc.player.getInventory().getItem(i);
                if (!hotbar.isEmpty() && ItemGateUtil.wouldEquip(hotbar)) {
                    GateRules.ItemGate gate = ItemGateUtil.findGateFor(hotbar);
                    if (gate != null) {
                        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                        if (lvl < gate.minLevel) {
                            event.setCanceled(true);
                            mc.player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                        }
                    }
                }
                break;
            }
        }
    }

    private static boolean isShiftDown() {
        var mc = Minecraft.getInstance();
        return (mc.options != null) && (mc.options.keyShift != null) && mc.options.keyShift.isDown();
    }

    private static boolean isArmorOrOffhand(Slot slot) {
        try {
            int idx = slot.getSlotIndex();
            return idx >= 36 && idx <= 40;
        } catch (Throwable t) {
            return false;
        }
    }
}
