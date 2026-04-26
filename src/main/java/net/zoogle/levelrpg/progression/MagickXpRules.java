package net.zoogle.levelrpg.progression;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical Arcana rules. Scholarly understanding through enchanting,
 * brewing, and curated magical craft outputs — not offensive spellcasting.
 */
public final class MagickXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.ARCANA.id();

    private static final long SAME_RESULT_COOLDOWN_TICKS = 100L;

    private static final Set<String> CURATED_MAGIC_CRAFT_OUTPUTS = Set.of(
            "enchanting_table",
            "brewing_stand",
            "ender_eye",
            "end_crystal",
            "respawn_anchor"
    );

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private MagickXpRules() {}

    public static long xpForCraftedItem(ServerPlayer player, ItemStack result) {
        if (player == null || result == null || result.isEmpty()) {
            return 0L;
        }

        String path = itemPath(result);
        if (path == null || !CURATED_MAGIC_CRAFT_OUTPUTS.contains(path)) {
            return 0L;
        }
        if (isOnCooldown(player, "craft:" + path)) {
            return 0L;
        }

        long perItem = switch (path) {
            case "enchanting_table", "end_crystal", "respawn_anchor" -> 8L;
            case "brewing_stand" -> 6L;
            default -> 4L;
        };
        return perItem * Math.max(1, result.getCount());
    }

    public static boolean isMagicCraftOutput(ItemStack result) {
        String path = itemPath(result);
        return path != null && CURATED_MAGIC_CRAFT_OUTPUTS.contains(path);
    }

    public static long xpForOpenMenu(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return 0L;
        }

        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof EnchantmentMenu enchantmentMenu) {
            tracker.ensureMenu("enchant");
            return xpForEnchantmentMenu(player, tracker, enchantmentMenu);
        }
        if (menu instanceof BrewingStandMenu brewingStandMenu) {
            tracker.ensureMenu("brew");
            return xpForBrewingMenu(player, tracker, brewingStandMenu);
        }

        tracker.clearMenuState();
        return 0L;
    }

    private static long xpForEnchantmentMenu(ServerPlayer player, Tracker tracker, EnchantmentMenu menu) {
        if (menu.slots.isEmpty()) {
            return 0L;
        }

        ItemStack stack = menu.slots.get(0).getItem();
        String signature = enchantmentSignature(stack);
        if (signature == null) {
            tracker.lastEnchantmentSignature = null;
            return 0L;
        }
        if (signature.equals(tracker.lastEnchantmentSignature)) {
            return 0L;
        }

        tracker.lastEnchantmentSignature = signature;
        if (isOnCooldown(player, "enchant:" + signature)) {
            return 0L;
        }
        return 8L;
    }

    private static long xpForBrewingMenu(ServerPlayer player, Tracker tracker, BrewingStandMenu menu) {
        long total = 0L;
        for (int slotIndex = 0; slotIndex < 3 && slotIndex < menu.slots.size(); slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            String signature = brewedPotionSignature(stack);
            String key = "brew_slot_" + slotIndex;
            String previous = tracker.lastBrewingSignatures.get(key);
            if (signature == null) {
                tracker.lastBrewingSignatures.remove(key);
                continue;
            }
            if (signature.equals(previous)) {
                continue;
            }

            tracker.lastBrewingSignatures.put(key, signature);
            if (isOnCooldown(player, "brew:" + signature)) {
                continue;
            }
            total += 3L;
        }
        return total;
    }

    private static String enchantmentSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Object direct = stack.get(DataComponents.ENCHANTMENTS);
        Object stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (direct == null && stored == null) {
            return null;
        }
        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemPath + "|ench=" + String.valueOf(direct) + "|stored=" + String.valueOf(stored);
    }

    private static String brewedPotionSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.POTION_CONTENTS)) {
            return null;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!"potion".equals(itemPath) && !"splash_potion".equals(itemPath) && !"lingering_potion".equals(itemPath)) {
            return null;
        }

        String contents = String.valueOf(stack.get(DataComponents.POTION_CONTENTS));
        String normalized = contents.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("water")
                || normalized.contains("awkward")
                || normalized.contains("mundane")
                || normalized.contains("thick")) {
            return null;
        }
        return itemPath + "|" + contents;
    }

    private static String itemPath(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static boolean isOnCooldown(ServerPlayer player, String key) {
        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
        long now = player.level().getGameTime();
        Long nextAllowed = tracker.nextAllowedTickByKey.get(key);
        if (nextAllowed != null && now < nextAllowed) {
            return true;
        }
        tracker.nextAllowedTickByKey.put(key, now + SAME_RESULT_COOLDOWN_TICKS);
        return false;
    }

    private static final class Tracker {
        private final Map<String, Long> nextAllowedTickByKey = new HashMap<>();
        private final Map<String, String> lastBrewingSignatures = new HashMap<>();
        private String activeMenuKind;
        private String lastEnchantmentSignature;

        private void ensureMenu(String menuKind) {
            if (!menuKind.equals(activeMenuKind)) {
                activeMenuKind = menuKind;
                lastEnchantmentSignature = null;
                lastBrewingSignatures.clear();
            }
        }

        private void clearMenuState() {
            activeMenuKind = null;
            lastEnchantmentSignature = null;
            lastBrewingSignatures.clear();
        }
    }
}
