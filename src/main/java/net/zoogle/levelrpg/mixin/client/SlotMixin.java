package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.util.ItemGateUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only veto at Slot.mayPlace to stop local UI from accepting illegal equips.
 * This prevents flicker/ghosting by making armor/offhand slots simply not accept
 * gated items when the cached level is insufficient.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void levelrpg_blockEquipClient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // Client-side only guard; avoid interfering before cache is ready
        if (stack == null || stack.isEmpty()) return;
        if (!ClientProfileCache.isReady()) return;
        if (!ItemGateUtil.wouldEquip(stack)) return;

        // Determine if this slot is one of the player's armor (36..39) or offhand (40)
        Slot self = (Slot)(Object)this;
        int idx;
        try {
            idx = self.getSlotIndex();
        } catch (Throwable t) {
            return; // can't determine index, do nothing
        }
        if (idx < 36 || idx > 40) return;
        // Only restrict offhand for shields
        if (idx == 40 && !(stack.getItem() instanceof ShieldItem)) return;

        // Find gate and compare to cached level
        GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
        if (gate == null) return;
        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
        if (lvl < gate.minLevel) {
            // Disallow placing into this slot; optional feedback here is omitted to avoid spam.
            cir.setReturnValue(false);
            cir.cancel();
            // Lightweight single feedback: action bar once per click can still be supplied by existing handlers.
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "msg.levelrpg.locked",
                                gate.minLevel,
                                ItemGateUtil.displayNameForSkill(gate.skill)
                        ).withStyle(net.minecraft.ChatFormatting.RED),
                        true
                );
            }
        }
    }
}
