package net.zoogle.levelrpg.events;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.zoogle.levelrpg.mixin.AbstractArrowPierceInvoker;
import net.zoogle.levelrpg.finesse.FinesseFlurryTracker;
import net.zoogle.levelrpg.finesse.FinesseGuardState;
import net.zoogle.levelrpg.finesse.FinesseTechniqueActivations;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finesse vertical slice: bow velocity, lucky conservation, melee combo, mitigation, and strike bonuses.
 */
public final class FinesseCombatEvents {
    private static final double PRECISION_VELOCITY_BONUS = 0.14;
    private static final double VERSATILE_BOW_VELOCITY_BONUS = 0.08;
    private static final double VERSATILE_CROSSBOW_VELOCITY_BONUS = 0.06;
    private static final double RHYTHM_MAX_EPSILON = 0.02;
    private static final double LUCKY_SHOT_RHYTHM_COST = 22.0;
    private static final float HANDS_UP_MIN_BLOCKED_DAMAGE = 0.30F;
    private static final float HANDS_UP_MAX_RHYTHM_BLOCKED_DAMAGE_BONUS = 0.60F;
    private static final float HANDS_UP_MAX_BLOCKED_DAMAGE = 0.90F;
    private static final float HANDS_UP_OUTGOING_UNARMED_DAMAGE_MULT = 0.55F;
    private static final double VERSATILE_BRAWLER_DAMAGE_PER_ARMOR_POINT = 0.012;
    private static final double VERSATILE_BRAWLER_DAMAGE_CAP = 4.5;
    private static final double DESPERATE_MEASURE_RANGE = 2.85;
    private static final float DESPERATE_MEASURE_EXTRA_DAMAGE = 5.5F;
    private static final double DESPERATE_MEASURE_RHYTHM_COST = 16.0;
    /** Extra damage multiplier from flight distance: {@code 1 + min(maxBonus, blocks * perBlock)}. */
    private static final double ASSASSIN_BONUS_PER_BLOCK = 0.019;
    private static final double ASSASSIN_MAX_DAMAGE_MULTIPLIER_BONUS = 1.05;
    private static final double RAPID_VOLLEY_EXTRA_VELOCITY_SCALE = 1.34;
    private static final double BLURRED_IMAGE_RHYTHM_COST = 52.0;
    private static final int BLURRED_IMAGE_COOLDOWN_TICKS = 200;
    private static final float DAVID_AND_GOLIATH_PER_HEALTH_ADVANTAGE = 0.064F;
    private static final float DAVID_AND_GOLIATH_MAX_BONUS = 9.0F;

    private static final Map<UUID, Vec3> ASSASSIN_ARROW_SPAWN = new ConcurrentHashMap<>();

    private static boolean hasInfinity(ServerPlayer player, ItemStack bow) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.INFINITY).map(
                holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, bow)
        ).orElse(0) > 0;
    }

    /** Player-caused melee / direct strike (not arrows, trident throws, etc.). */
    private static boolean isDirectPlayerStrike(DamageSource source, ServerPlayer player) {
        return source.getEntity() == player && source.getDirectEntity() == player;
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) {
            ASSASSIN_ARROW_SPAWN.remove(arrow.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.ASSASSIN)) {
            ASSASSIN_ARROW_SPAWN.put(arrow.getUUID(), arrow.position());
        }
        if (SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.PIERCING_SHOT)) {
            int pierce = arrow.getPierceLevel();
            ((AbstractArrowPierceInvoker) (Object) arrow).levelrpg$setPierceLevel((byte) Math.min(127, pierce + 2));
        }
        double velocityMul = 1.0;
        if (SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.PRECISION_SHOT)) {
            velocityMul += PRECISION_VELOCITY_BONUS;
        }
        boolean bowInMain = player.getMainHandItem().getItem() instanceof BowItem;
        boolean crossbowInMain = player.getMainHandItem().getItem() instanceof CrossbowItem;
        if (SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.VERSATILE_SHOT)) {
            if (bowInMain) {
                velocityMul += VERSATILE_BOW_VELOCITY_BONUS;
            } else if (crossbowInMain) {
                velocityMul += VERSATILE_CROSSBOW_VELOCITY_BONUS;
            }
        }
        if (velocityMul > 1.0001) {
            Vec3 v = arrow.getDeltaMovement();
            arrow.setDeltaMovement(v.scale(velocityMul));
        }
        if (FinesseTechniqueActivations.isRapidVolleyActive(player)) {
            Vec3 v = arrow.getDeltaMovement();
            arrow.setDeltaMovement(v.scale(RAPID_VOLLEY_EXTRA_VELOCITY_SCALE));
        }

        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.LUCKY_SHOT) || !bowInMain) {
            return;
        }
        ItemStack bow = player.getMainHandItem();
        if (hasInfinity(player, bow)) {
            return;
        }
        double max = profile.gauges.getMax(player, profile, GaugeRegistry.RHYTHM);
        double rhythm = profile.gauges.getValue(GaugeRegistry.RHYTHM);
        if (max <= 0.0 || rhythm + RHYTHM_MAX_EPSILON < max) {
            return;
        }
        if (!PlayerGauges.canSpend(player, GaugeRegistry.RHYTHM, LUCKY_SHOT_RHYTHM_COST)) {
            return;
        }
        if (!PlayerGauges.spend(player, GaugeRegistry.RHYTHM, LUCKY_SHOT_RHYTHM_COST)) {
            return;
        }
        ItemStack refund = arrow instanceof SpectralArrow
                ? new ItemStack(Items.SPECTRAL_ARROW)
                : new ItemStack(Items.ARROW);
        if (!player.getInventory().add(refund)) {
            player.drop(refund, false);
        }
        profile = LevelProfile.get(player);
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, profile);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamagePreGhostStep(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            if (FinesseTechniqueActivations.isGhostStepping(victim)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamagePreBlurredImage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        LevelProfile profile = LevelProfile.get(victim);
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.BLURRED_IMAGE)) {
            return;
        }
        if (victim.tickCount < FinesseTechniqueActivations.blurredImageCooldownUntil(victim)) {
            return;
        }
        double max = profile.gauges.getMax(victim, profile, GaugeRegistry.RHYTHM);
        double rhythm = profile.gauges.getValue(GaugeRegistry.RHYTHM);
        if (max <= 0.0 || rhythm + RHYTHM_MAX_EPSILON < max) {
            return;
        }
        if (!PlayerGauges.canSpend(victim, GaugeRegistry.RHYTHM, BLURRED_IMAGE_RHYTHM_COST)) {
            return;
        }
        if (!PlayerGauges.spend(victim, GaugeRegistry.RHYTHM, BLURRED_IMAGE_RHYTHM_COST)) {
            return;
        }
        FinesseTechniqueActivations.setBlurredImageCooldown(victim, victim.tickCount + BLURRED_IMAGE_COOLDOWN_TICKS);
        profile = LevelProfile.get(victim);
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(victim, profile);
        event.setNewDamage(0.0F);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (event.getSource().getEntity() instanceof AbstractArrow arrow) {
            if (arrow.getOwner() instanceof ServerPlayer shooter) {
                LevelProfile shooterProfile = LevelProfile.get(shooter);
                if (SkillUnlockQuery.hasNode(shooterProfile, FinesseNodeIds.SKILL, FinesseNodeIds.ASSASSIN)) {
                    Vec3 spawn = ASSASSIN_ARROW_SPAWN.remove(arrow.getUUID());
                    if (spawn != null) {
                        double travelBlocks = spawn.distanceTo(target.position());
                        double bonusMult = Math.min(ASSASSIN_MAX_DAMAGE_MULTIPLIER_BONUS, travelBlocks * ASSASSIN_BONUS_PER_BLOCK);
                        damage *= (float) (1.0 + bonusMult);
                    }
                }
            }
        }
        if (target instanceof ServerPlayer victim) {
            LevelProfile vProfile = LevelProfile.get(victim);
            if (SkillUnlockQuery.hasNode(vProfile, FinesseNodeIds.SKILL, FinesseNodeIds.HANDS_UP)
                    && FinesseGuardState.isGuarding(victim)) {
                damage *= handsUpDamageTakenMultiplier(victim, vProfile);
                playHandsUpBlockSound(victim);
            }
        }

        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) {
            event.setNewDamage(damage);
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            event.setNewDamage(damage);
            return;
        }
        LevelProfile aProfile = LevelProfile.get(attacker);

        boolean unarmedDirectStrike = isDirectPlayerStrike(event.getSource(), attacker)
                && attacker.getMainHandItem().isEmpty()
                && attacker.getOffhandItem().isEmpty();
        boolean attackerGuarding = FinesseGuardState.isGuarding(attacker);
        if (attackerGuarding
                && unarmedDirectStrike) {
            damage *= HANDS_UP_OUTGOING_UNARMED_DAMAGE_MULT;
        }

        if (unarmedDirectStrike) {
            FinesseUnarmedCombat.WeakSpotResult weakSpot =
                    FinesseUnarmedCombat.applyWeakSpotStrike(attacker, aProfile, target, damage, attackerGuarding);
            damage = weakSpot.damage();
            FinesseUnarmedCombat.playUnarmedHitSound(attacker, target, weakSpot.timedCritical());
        }

        if (SkillUnlockQuery.hasNode(aProfile, FinesseNodeIds.SKILL, FinesseNodeIds.VERSATILE_BRAWLER)) {
            int armor = attacker.getArmorValue();
            if (armor > 0) {
                double add = Math.min(VERSATILE_BRAWLER_DAMAGE_CAP, armor * VERSATILE_BRAWLER_DAMAGE_PER_ARMOR_POINT);
                damage += (float) add;
            }
        }

        if (SkillUnlockQuery.hasNode(aProfile, FinesseNodeIds.SKILL, FinesseNodeIds.DAVID_AND_GOLIATH)
                && isDirectPlayerStrike(event.getSource(), attacker)
                && attacker.getMainHandItem().isEmpty()
                && target != attacker) {
            float diff = target.getHealth() - attacker.getHealth();
            if (diff > 0.0F) {
                damage += Math.min(DAVID_AND_GOLIATH_MAX_BONUS, diff * DAVID_AND_GOLIATH_PER_HEALTH_ADVANTAGE);
            }
        }

        if (SkillUnlockQuery.hasNode(aProfile, FinesseNodeIds.SKILL, FinesseNodeIds.DESPERATE_MEASURE)
                && isDirectPlayerStrike(event.getSource(), attacker)
                && target != attacker) {
            ItemStack main = attacker.getMainHandItem();
            if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
                double dist = attacker.distanceTo(target);
                if (dist <= DESPERATE_MEASURE_RANGE) {
                    double maxR = aProfile.gauges.getMax(attacker, aProfile, GaugeRegistry.RHYTHM);
                    if (maxR > 0.0
                            && PlayerGauges.canSpend(attacker, GaugeRegistry.RHYTHM, DESPERATE_MEASURE_RHYTHM_COST)
                            && PlayerGauges.spend(attacker, GaugeRegistry.RHYTHM, DESPERATE_MEASURE_RHYTHM_COST)) {
                        damage += DESPERATE_MEASURE_EXTRA_DAMAGE;
                        aProfile = LevelProfile.get(attacker);
                        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(attacker, aProfile);
                    }
                }
            }
        }

        event.setNewDamage(damage);
    }

    private static float handsUpDamageTakenMultiplier(ServerPlayer victim, LevelProfile profile) {
        double rhythmFraction = 0.0;
        double max = profile.gauges.getMax(victim, profile, GaugeRegistry.RHYTHM);
        if (max > 0.0) {
            rhythmFraction = Math.max(0.0, Math.min(1.0, profile.gauges.getValue(GaugeRegistry.RHYTHM) / max));
        }
        float blocked = HANDS_UP_MIN_BLOCKED_DAMAGE
                + (float) rhythmFraction * HANDS_UP_MAX_RHYTHM_BLOCKED_DAMAGE_BONUS;
        blocked = Math.min(HANDS_UP_MAX_BLOCKED_DAMAGE, blocked);
        return 1.0F - blocked;
    }

    private static void playHandsUpBlockSound(ServerPlayer victim) {
        victim.level().playSound(
                null,
                victim.getX(),
                victim.getY(),
                victim.getZ(),
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS,
                0.8F,
                0.78F + victim.getRandom().nextFloat() * 0.12F
        );
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!isDirectPlayerStrike(event.getSource(), attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == attacker) {
            return;
        }
        LevelProfile profile = LevelProfile.get(attacker);
        FinesseUnarmedCombat.onSuccessfulUnarmedStrike(attacker, profile);
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.FLURRY_OF_BLOWS)) {
            return;
        }
        FinesseFlurryTracker.onMeleeHit(attacker);
    }
}
