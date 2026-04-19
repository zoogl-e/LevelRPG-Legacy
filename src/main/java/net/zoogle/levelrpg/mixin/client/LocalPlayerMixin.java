package net.zoogle.levelrpg.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.util.ItemGateUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "swapItemInHands", at = @At("HEAD"), cancellable = true)
    private void levelrpg_blockShieldSwap(CallbackInfo ci) {
        if (!net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) return;
        LocalPlayer self = (LocalPlayer)(Object)this;
        ItemStack main = self.getMainHandItem();
        if (!(main.getItem() instanceof ShieldItem)) return; // only care about shields to offhand
        GateRules.ItemGate gate = ItemGateUtil.findGateFor(main);
        if (gate == null) return;
        int lvl = ClientProfileCache.getSkillsView().getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
        if (lvl < gate.minLevel) {
            ci.cancel();
            Minecraft mc = Minecraft.getInstance();
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
