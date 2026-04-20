package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.zoogle.levelrpg.profile.ProgressionSkill;

/**
 * Canonical Valor rules. Prefer direct player combat against combat-relevant
 * living mobs.
 */
public final class ValorXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.VALOR.id();

    private ValorXpRules() {}

    public static long xpForDamage(LivingEntity target, float damageDealt) {
        if (!isCombatRelevantTarget(target) || damageDealt <= 0.0F) {
            return 0L;
        }
        return Math.max(1L, Math.min(4L, (long) Math.floor(damageDealt / 3.0F)));
    }

    public static long xpForKill(LivingEntity target) {
        if (!isCombatRelevantTarget(target)) {
            return 0L;
        }
        return 10L;
    }

    private static boolean isCombatRelevantTarget(LivingEntity target) {
        return target instanceof Mob && !(target instanceof net.minecraft.world.entity.player.Player);
    }
}
