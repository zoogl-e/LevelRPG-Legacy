package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.zoogle.levelrpg.profile.ProgressionSkill;

/**
 * Canonical Forging rules. Favor gear-oriented production and keep this
 * separate from gathering-oriented Mining signals.
 */
public final class ForgingXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.FORGING.id();

    private ForgingXpRules() {}

    public static long xpForCraftedItem(ItemStack result) {
        if (result == null || result.isEmpty()) {
            return 0L;
        }

        Item item = result.getItem();
        long perItem = 0L;
        if (item instanceof ArmorItem || item instanceof ShieldItem) {
            perItem = 8L;
        } else if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof MaceItem) {
            perItem = 7L;
        } else if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FishingRodItem || item instanceof FlintAndSteelItem) {
            perItem = 5L;
        }

        return perItem * Math.max(1, result.getCount());
    }
}
