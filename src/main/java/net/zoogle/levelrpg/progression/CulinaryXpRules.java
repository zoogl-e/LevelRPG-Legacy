package net.zoogle.levelrpg.progression;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.profile.ProgressionSkill;

/**
 * Canonical Culinary rules. Keep the signal focused on prepared food outputs,
 * not generic crafting throughput.
 */
public final class CulinaryXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.CULINARY.id();

    private CulinaryXpRules() {}

    public static long xpForCraftedItem(ItemStack result) {
        if (!isCraftedFood(result)) {
            return 0L;
        }
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food == null) {
            return 0L;
        }
        long perItem = 2L + Math.min(4L, Math.max(0L, food.nutrition() / 2L));
        return perItem * Math.max(1, result.getCount());
    }

    public static long xpForSmeltedItem(ItemStack result) {
        if (!isCookedFood(result)) {
            return 0L;
        }
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food == null) {
            return 0L;
        }
        long perItem = 3L + Math.min(4L, Math.max(0L, food.nutrition() / 3L));
        return perItem * Math.max(1, result.getCount());
    }

    private static boolean isCraftedFood(ItemStack result) {
        return result != null && !result.isEmpty() && result.has(DataComponents.FOOD);
    }

    private static boolean isCookedFood(ItemStack result) {
        if (!isCraftedFood(result)) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        return path.startsWith("cooked_")
                || path.startsWith("baked_")
                || "dried_kelp".equals(path);
    }
}
