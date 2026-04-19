package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.util.ItemGateUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Earliest client-side veto for container interactions to prevent any local UI mutation (no flicker).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // mouseClicked(double mouseX, double mouseY, int button) -> boolean
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void levelrpg_mouseClicked_blockEquip(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (shouldBlockClick(button)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // mouseReleased(double mouseX, double mouseY, int button) -> boolean
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void levelrpg_mouseReleased_blockEquip(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (shouldBlockClick(button)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) -> boolean
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void levelrpg_mouseDragged_blockEquip(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (shouldBlockCarriedToArmor()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // keyPressed(int keyCode, int scanCode, int modifiers) -> boolean
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void levelrpg_keyPressed_blockSwap(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (shouldBlockNumberSwap(keyCode, scanCode)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // --------------- Helpers ---------------
    private boolean shouldBlockClick(int button) {
        var self = (AbstractContainerScreen)(Object)this;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return false;
        Slot slot = self.getSlotUnderMouse();
        if (slot == null) return false;

        // Shift-left quick move anywhere on an equippable
        if (button == 0 && isShiftDown()) {
            if (!slot.hasItem()) return false;
            ItemStack stack = slot.getItem();
            if (!ItemGateUtil.wouldEquip(stack)) return false;
            GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
            if (gate == null) return false;
            int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
            if (lvl < gate.minLevel) {
                showLocked(mc, gate);
                return true;
            }
        }

        // Right-click auto-equip from a slot
        if (button == 1 && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (ItemGateUtil.wouldEquip(stack)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
                if (gate != null) {
                    int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                    if (lvl < gate.minLevel) {
                        showLocked(mc, gate);
                        return true;
                    }
                }
            }
        }

        // Drag/drop carried item onto armor/offhand
        if (isArmorOrOffhand(slot)) {
            ItemStack carried = self.getMenu().getCarried();
            if (carried != null && !carried.isEmpty() && ItemGateUtil.wouldEquip(carried)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
                if (gate != null) {
                    int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                    if (lvl < gate.minLevel) {
                        showLocked(mc, gate);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldBlockCarriedToArmor() {
        var self = (AbstractContainerScreen)(Object)this;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return false;
        Slot slot = self.getSlotUnderMouse();
        if (slot == null || !isArmorOrOffhand(slot)) return false;
        ItemStack carried = self.getMenu().getCarried();
        if (carried == null || carried.isEmpty() || !ItemGateUtil.wouldEquip(carried)) return false;
        GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
        if (gate == null) return false;
        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
        if (lvl < gate.minLevel) {
            showLocked(mc, gate);
            return true;
        }
        return false;
    }

    private boolean shouldBlockNumberSwap(int keyCode, int scanCode) {
        var self = (AbstractContainerScreen)(Object)this;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return false;
        Slot slot = self.getSlotUnderMouse();
        if (slot == null || !isArmorOrOffhand(slot)) return false;
        // Check hotbar keys 1..9
        for (int i = 0; i < 9; i++) {
            var key = mc.options.keyHotbarSlots[i];
            if (key != null && key.matches(keyCode, scanCode)) {
                ItemStack hotbar = mc.player.getInventory().getItem(i);
                if (!hotbar.isEmpty() && ItemGateUtil.wouldEquip(hotbar)) {
                    GateRules.ItemGate gate = ItemGateUtil.findGateFor(hotbar);
                    if (gate != null) {
                        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                        if (lvl < gate.minLevel) {
                            showLocked(mc, gate);
                            return true;
                        }
                    }
                }
                break;
            }
        }
        return false;
    }

    private static boolean isArmorOrOffhand(Slot slot) {
        try {
            int idx = slot.getSlotIndex();
            return idx >= 36 && idx <= 40; // 36..39 armor, 40 offhand
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isShiftDown() {
        var mc = Minecraft.getInstance();
        return (mc.options != null) && (mc.options.keyShift != null) && mc.options.keyShift.isDown();
        }

    private static void showLocked(Minecraft mc, GateRules.ItemGate gate) {
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
        }
    }
}
