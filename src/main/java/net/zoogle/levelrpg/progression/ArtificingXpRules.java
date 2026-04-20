package net.zoogle.levelrpg.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical Artificing rules. The first migration step stays narrow and
 * testable by rewarding technical crafting outputs and machine-processing
 * completions from furnace-style refinement.
 */
public final class ArtificingXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.ARTIFICING.id();

    private static final long SAME_RESULT_COOLDOWN_TICKS = 40L;

    private static final Set<String> TECHNICAL_CRAFT_OUTPUTS = Set.of(
            "hopper",
            "dropper",
            "dispenser",
            "observer",
            "piston",
            "sticky_piston",
            "crafter",
            "comparator",
            "repeater",
            "daylight_detector",
            "redstone_lamp",
            "target",
            "lever",
            "tripwire_hook",
            "powered_rail",
            "detector_rail",
            "activator_rail",
            "minecart",
            "hopper_minecart",
            "furnace_minecart",
            "tnt_minecart",
            "chest_minecart",
            "furnace",
            "blast_furnace",
            "smoker"
    );

    private static final Set<String> REFINED_MACHINE_OUTPUTS = Set.of(
            "iron_ingot",
            "gold_ingot",
            "copper_ingot",
            "netherite_scrap",
            "glass",
            "smooth_stone"
    );

    private static final Map<UUID, CooldownTracker> COOLDOWNS = new HashMap<>();

    private ArtificingXpRules() {}

    public static long xpForCraftedItem(ServerPlayer player, ItemStack result) {
        if (player == null || result == null || result.isEmpty()) {
            return 0L;
        }

        String path = itemPath(result);
        if (!isTechnicalCraftOutput(path)) {
            return 0L;
        }
        if (isOnCooldown(player, "craft:" + path)) {
            return 0L;
        }

        long perItem = switch (path) {
            case "crafter", "hopper", "observer", "comparator", "blast_furnace", "smoker" -> 6L;
            case "piston", "sticky_piston", "dispenser", "dropper", "powered_rail", "detector_rail", "activator_rail" -> 4L;
            default -> 3L;
        };
        return perItem * Math.max(1, result.getCount());
    }

    public static long xpForSmeltedItem(ServerPlayer player, ItemStack result) {
        if (player == null || result == null || result.isEmpty()) {
            return 0L;
        }

        String path = itemPath(result);
        if (!isRefinedMachineOutput(path)) {
            return 0L;
        }
        if (isOnCooldown(player, "smelt:" + path)) {
            return 0L;
        }

        long perItem = switch (path) {
            case "netherite_scrap" -> 6L;
            case "iron_ingot", "gold_ingot", "copper_ingot" -> 3L;
            default -> 1L;
        };
        return perItem * Math.max(1, result.getCount());
    }

    public static boolean isTechnicalCraftOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isTechnicalCraftOutput(itemPath(stack));
    }

    public static boolean isRefinedMachineOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isRefinedMachineOutput(itemPath(stack));
    }

    public static boolean isComplexTechnicalCraftOutput(ItemStack stack) {
        if (!isTechnicalCraftOutput(stack)) {
            return false;
        }
        String path = itemPath(stack);
        return "crafter".equals(path)
                || "hopper".equals(path)
                || "observer".equals(path)
                || "comparator".equals(path)
                || "blast_furnace".equals(path)
                || "smoker".equals(path);
    }

    private static boolean isTechnicalCraftOutput(String path) {
        return TECHNICAL_CRAFT_OUTPUTS.contains(path);
    }

    private static boolean isRefinedMachineOutput(String path) {
        return REFINED_MACHINE_OUTPUTS.contains(path);
    }

    private static String itemPath(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static boolean isOnCooldown(ServerPlayer player, String key) {
        CooldownTracker tracker = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new CooldownTracker());
        long now = player.level().getGameTime();
        Long nextAllowed = tracker.nextAllowedTickByKey.get(key);
        if (nextAllowed != null && now < nextAllowed) {
            return true;
        }
        tracker.nextAllowedTickByKey.put(key, now + SAME_RESULT_COOLDOWN_TICKS);
        return false;
    }

    private static final class CooldownTracker {
        private final Map<String, Long> nextAllowedTickByKey = new HashMap<>();
    }
}
