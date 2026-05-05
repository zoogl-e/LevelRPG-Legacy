package net.zoogle.levelrpg.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;

/**
 * Valor Resolve: build on outgoing and incoming damage, amplify critical strike damage.
 */
public final class ValorGameplayEvents {
    /** Extra Resolve gained per point of outgoing damage (after armor), before per-hit cap. */
    private static final double RESOLVE_GAIN_PER_DAMAGE = 2.1;
    /** Minimum Resolve granted on a qualifying hit. */
    private static final double RESOLVE_GAIN_FLOOR = 0.6;
    /** Max Resolve from a single outgoing hit as a fraction of current max Resolve. */
    private static final double RESOLVE_GAIN_CAP_FRACTION_OF_MAX = 0.14;
    /** Extra Resolve gained per point of incoming damage (after mitigation), before per-hit cap. */
    private static final double RESOLVE_INCOMING_BASE_GAIN = 2.0;
    /** Damage modulation term uses sqrt so armor doesn't heavily punish Resolve generation. */
    private static final double RESOLVE_INCOMING_SQRT_MOD = 1.35;
    /** Max Resolve from a single incoming hit as a fraction of current max Resolve. */
    private static final double RESOLVE_INCOMING_GAIN_CAP_FRACTION_OF_MAX = 0.12;
    /**
     * Incoming Resolve: linear bonus from missing HP (before this hit). At full HP multiplier is 1.0;
     * at 0 HP it is {@code 1.0 + this}. Applied before the per-hit cap.
     */
    private static final double RESOLVE_INCOMING_LOW_HP_BONUS_MAX = 0.30;
    /** Extra crit damage at empty Resolve (fraction of final damage). */
    private static final double CRIT_BONUS_AT_ZERO = 0.06;
    /** Extra crit damage at full Resolve (added to {@link #CRIT_BONUS_AT_ZERO}). */
    private static final double CRIT_BONUS_SCALE_AT_FULL = 0.34;

    /**
     * Resolve increases the critical damage multiplier (see {@link CriticalHitEvent} — fired from {@code Player#attack}).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
            return;
        }
        if (!event.isCriticalHit()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(sp);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.RESOLVE)) {
            return;
        }
        double max = profile.gauges.getMax(sp, profile, GaugeRegistry.RESOLVE);
        if (max <= 0.0) {
            return;
        }
        double resolve = profile.gauges.getValue(GaugeRegistry.RESOLVE);
        double fraction = Math.max(0.0, Math.min(1.0, resolve / max));
        double bonus = CRIT_BONUS_AT_ZERO + CRIT_BONUS_SCALE_AT_FULL * fraction;
        float mult = event.getDamageMultiplier();
        event.setDamageMultiplier(mult * (float) (1.0 + bonus));
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (victim.isSpectator() || victim.isCreative()) {
            return;
        }
        // Only entity-caused hits should grant incoming Resolve (no fall/environment chip gain).
        if (event.getSource().getEntity() == null) {
            return;
        }
        LevelProfile profile = LevelProfile.get(victim);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.RESOLVE)) {
            return;
        }
        double max = profile.gauges.getMax(victim, profile, GaugeRegistry.RESOLVE);
        if (max <= 0.0) {
            return;
        }
        double gain = RESOLVE_INCOMING_BASE_GAIN + Math.sqrt(Math.max(0.0, damage)) * RESOLVE_INCOMING_SQRT_MOD;
        gain *= incomingResolveThreatMultiplier(victim);
        gain = Math.min(gain, max * RESOLVE_INCOMING_GAIN_CAP_FRACTION_OF_MAX);
        if (gain > 0.0001) {
            PlayerGauges.add(victim, GaugeRegistry.RESOLVE, gain);
        }
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == attacker) {
            return;
        }

        LevelProfile profile = LevelProfile.get(attacker);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.RESOLVE)) {
            return;
        }
        double max = profile.gauges.getMax(attacker, profile, GaugeRegistry.RESOLVE);
        if (max <= 0.0) {
            return;
        }
        double gain = Math.max(RESOLVE_GAIN_FLOOR, damage * RESOLVE_GAIN_PER_DAMAGE);
        gain = Math.min(gain, max * RESOLVE_GAIN_CAP_FRACTION_OF_MAX);
        if (gain > 0.0001) {
            PlayerGauges.add(attacker, GaugeRegistry.RESOLVE, gain);
        }
    }

    /** {@code 1 + RESOLVE_INCOMING_LOW_HP_BONUS_MAX * (missing / max)} from health before the hit. */
    private static double incomingResolveThreatMultiplier(ServerPlayer victim) {
        float maxHp = victim.getMaxHealth();
        if (maxHp <= 1.0e-6f) {
            return 1.0;
        }
        float missingRatio = (maxHp - victim.getHealth()) / maxHp;
        missingRatio = Math.max(0.0f, Math.min(1.0f, missingRatio));
        return 1.0 + RESOLVE_INCOMING_LOW_HP_BONUS_MAX * missingRatio;
    }
}
