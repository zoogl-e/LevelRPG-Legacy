package net.zoogle.levelrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import net.zoogle.levelrpg.util.RecipeUnlockText;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal client-side preview feedback for locked crafting recipes.
 *
 * This does not replace server authority; it only previews lock state in
 * inventory and crafting table screens using the synced client profile cache.
 */
@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class CraftingRecipeLockFeedback {
    private static final int OVERLAY_COLOR = 0xAA7A0000;
    private static final int MARKER_COLOR = 0xFFF5D76E;

    private CraftingRecipeLockFeedback() {}

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        PreviewState preview = resolvePreviewState(screen);
        if (preview == null) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Slot resultSlot = preview.resultSlot();
        int x = screen.getGuiLeft() + resultSlot.x;
        int y = screen.getGuiTop() + resultSlot.y;
        guiGraphics.fill(x, y, x + 16, y + 16, OVERLAY_COLOR);
        guiGraphics.drawString(Minecraft.getInstance().font, "!", x + 6, y + 4, MARKER_COLOR, false);

        if (screen.getSlotUnderMouse() == resultSlot) {
            guiGraphics.renderTooltip(
                    Minecraft.getInstance().font,
                    RecipeUnlockText.tooltipLines(preview.unlockCheck()),
                    java.util.Optional.empty(),
                    event.getMouseX(),
                    event.getMouseY()
            );
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        PreviewState preview = resolvePreviewState(screen);
        if (preview == null) return;

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || hovered != preview.resultSlot()) return;

        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(RecipeUnlockText.deniedCraftMessage(preview.unlockCheck()), true);
        }
        event.setCanceled(true);
    }

    private static PreviewState resolvePreviewState(AbstractContainerScreen<?> screen) {
        if (!ClientProfileCache.isReady()) return null;

        AbstractContainerMenu menu = screen.getMenu();
        CraftingInput input = resolveCraftingInput(menu);
        if (input == null || input.isEmpty()) return null;

        Slot resultSlot = resolveResultSlot(menu);
        if (resultSlot == null || !resultSlot.hasItem()) return null;

        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var recipe = mc.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, mc.level);
        if (recipe.isEmpty()) return null;

        RecipeHolder<CraftingRecipe> holder = recipe.get();
        RecipeUnlockService.UnlockCheckResult unlockCheck = RecipeUnlockService.checkAccess(holder.id(), ClientProfileCache.getSkillsView());
        if (unlockCheck.unlocked()) return null;

        return new PreviewState(resultSlot, unlockCheck);
    }

    private static Slot resolveResultSlot(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu inventoryMenu) {
            return inventoryMenu.slots.get(inventoryMenu.getResultSlotIndex());
        }
        if (menu instanceof CraftingMenu craftingMenu) {
            return craftingMenu.slots.get(craftingMenu.getResultSlotIndex());
        }
        return null;
    }

    private static CraftingInput resolveCraftingInput(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu inventoryMenu) {
            return buildCraftingInput(inventoryMenu, 2, 2, 1, 4);
        }
        if (menu instanceof CraftingMenu craftingMenu) {
            return buildCraftingInput(craftingMenu, 3, 3, 1, 9);
        }
        return null;
    }

    private static CraftingInput buildCraftingInput(AbstractContainerMenu menu, int width, int height, int firstSlot, int count) {
        ArrayList<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(menu.slots.get(firstSlot + i).getItem());
        }
        return CraftingInput.of(width, height, items);
    }

    private record PreviewState(
            Slot resultSlot,
            RecipeUnlockService.UnlockCheckResult unlockCheck
    ) {}
}
