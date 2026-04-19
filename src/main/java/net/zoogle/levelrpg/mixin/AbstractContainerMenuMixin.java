package net.zoogle.levelrpg.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.util.ItemGateUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow public abstract Slot getSlot(int slotId);
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void levelrpg_blockEquipPaths(int slotId, int button, ClickType type, net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;

        // Guard: outside click (e.g., -999) -> do nothing
        if (slotId < 0) return;

        // 1) QUICK_MOVE (shift-click) from any source when item would equip
        if (type == ClickType.QUICK_MOVE) {
            Slot src = getSlot(slotId);
            if (src != null && src.hasItem()) {
                ItemStack stack = src.getItem();
                if (ItemGateUtil.wouldEquip(stack)) {
                    GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
                    if (gate != null && !ItemGateUtil.hasRequiredLevel(sp, gate)) {
                        ci.cancel();
                        ItemGateUtil.notifyLocked(sp, gate);
                        return;
                    }
                }
            }
        }

        // Resolve destination slot (if any)
        Slot dst = getSlot(slotId);
        if (dst == null) return;
        boolean isArmorSlot = isArmorOrOffhand(dst);
        if (!isArmorSlot) return;

        // 2) SWAP (number keys) into armor/offhand
        if (type == ClickType.SWAP) {
            // button is hotbar index 0..8
            if (button >= 0 && button <= 8) {
                ItemStack hotbar = sp.getInventory().getItem(button);
                if (!hotbar.isEmpty() && ItemGateUtil.wouldEquip(hotbar)) {
                    GateRules.ItemGate gate = ItemGateUtil.findGateFor(hotbar);
                    if (gate != null && !ItemGateUtil.hasRequiredLevel(sp, gate)) {
                        ci.cancel();
                        ItemGateUtil.notifyLocked(sp, gate);
                        return;
                    }
                }
            }
        }

        // 3) Direct place of carried item into armor/offhand (drag-and-drop)
        if (type == ClickType.PICKUP || type == ClickType.PICKUP_ALL) {
            ItemStack carried = getCarried();
            if (carried != null && !carried.isEmpty() && ItemGateUtil.wouldEquip(carried)) {
                GateRules.ItemGate gate = ItemGateUtil.findGateFor(carried);
                if (gate != null && !ItemGateUtil.hasRequiredLevel(sp, gate)) {
                    ci.cancel();
                    ItemGateUtil.notifyLocked(sp, gate);
                }
            }
        }
    }

    private static boolean isArmorOrOffhand(Slot slot) {
        try {
            // Player inventory armor indices are typically 36..39; offhand is 40
            int idx = slot.getSlotIndex();
            // The offhand slot sometimes reports -1 from getSlotIndex; additionally use mayPlace semantics
            // We approximate: if slot index >= 36 or slot mayPlace only armor/shield when empty.
            if (idx >= 36 && idx <= 40) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
