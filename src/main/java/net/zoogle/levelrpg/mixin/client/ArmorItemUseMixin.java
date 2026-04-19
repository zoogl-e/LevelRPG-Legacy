package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.util.ItemGateUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorItem.class)
public abstract class ArmorItemUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void levelrpg_blockArmorUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        // Client-only prediction guard
        if (player == null || !level.isClientSide) return;
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack == null || stack.isEmpty()) return;
        GateRules.ItemGate gate = ItemGateUtil.findGateFor(stack);
        if (gate == null) return;
        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
        if (lvl < gate.minLevel) {
            // Suppress local auto-equip animation
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            // Immediate feedback
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("msg.levelrpg.locked", gate.minLevel, ItemGateUtil.displayNameForSkill(gate.skill))
                                .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                );
            }
            cir.cancel();
        }
    }
}
