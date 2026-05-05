package net.zoogle.levelrpg.valor;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Melee weapons counted by Valor duelist-style nodes (swords, axes, trident, mace).
 */
public final class ValorMeleeWeapons {
    private ValorMeleeWeapons() {
    }

    public static boolean isValorMelee(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.MACE);
    }
}
