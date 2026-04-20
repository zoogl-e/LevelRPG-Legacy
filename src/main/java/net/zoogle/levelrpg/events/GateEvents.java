package net.zoogle.levelrpg.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.inventory.CraftingContainer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import net.zoogle.levelrpg.util.RecipeUnlockText;

/**
 * Recipe-gate enforcement entrypoint.
 *
 * Legacy item possession/use gates have been retired; ordinary item use and
 * equipment are no longer restricted here. Crafting denial now flows only
 * through the canonical recipe unlock framework.
 */
public class GateEvents {

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        RecipeUnlockService.UnlockCheckResult unlockCheck = resolveRecipeUnlockCheck(sp, profile, event.getInventory());
        if (unlockCheck == null || unlockCheck.unlocked()) {
            return;
        }

        denyCraftedResult(sp, result, lockedRecipeMessage(unlockCheck));
    }

    private static RecipeUnlockService.UnlockCheckResult resolveRecipeUnlockCheck(ServerPlayer player, LevelProfile profile, Container craftMatrix) {
        if (!(craftMatrix instanceof CraftingContainer craftingContainer)) {
            return null;
        }
        var recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingContainer.asCraftInput(), player.level());
        if (recipe.isEmpty()) {
            return null;
        }
        RecipeHolder<CraftingRecipe> holder = recipe.get();
        return RecipeUnlockService.checkAccess(profile, holder.id());
    }

    private static Component lockedRecipeMessage(RecipeUnlockService.UnlockCheckResult unlockCheck) {
        return RecipeUnlockText.deniedCraftMessage(unlockCheck).copy().withStyle(ChatFormatting.RED);
    }

    private static void denyCraftedResult(ServerPlayer player, ItemStack result, Component message) {
        result.setCount(0);
        player.displayClientMessage(message, true);
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}
