package net.zoogle.levelrpg.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.MasteryNodeEffects;

public class MasteryEvents {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        MasteryNodeEffects.applyPassiveEffects(player, profile);
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        MasteryNodeEffects.applyIncomingDamageModifiers(event);
    }

    @SubscribeEvent
    public void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MasteryNodeEffects.onDamageTaken(player, event.getNewDamage());
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        MasteryNodeEffects.applyMiningBreakSpeed(event);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        MasteryNodeEffects.afterMiningBlockBreak(player, profile, event.getState());
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        MasteryNodeEffects.afterCraft(player, profile, result, event.getInventory());
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack result = event.getSmelting();
        if (result == null || result.isEmpty()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        MasteryNodeEffects.afterSmelt(player, profile, result);
    }
}
