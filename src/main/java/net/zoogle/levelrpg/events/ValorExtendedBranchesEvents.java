package net.zoogle.levelrpg.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.net.payload.CameraShakePayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Next Valor batch: vanguard line ({@link ValorNodeIds#VANGUARD}, {@link ValorNodeIds#DEFLECTION})
 * and duelist continuation ({@link ValorNodeIds#FAR_REACH}, {@link ValorNodeIds#MONSTER_HUNTER}).
 */
public final class ValorExtendedBranchesEvents {
    private static final ResourceLocation IMMOVABLE_KB_RESIST_ID = id("valor_immovable_knockback_resistance");
    private static final ResourceLocation VANGUARD_ARMOR_MULT_ID = id("valor_vanguard_armor_mult");
    /** Per equipped armor piece, {@link AttributeModifier.Operation#ADD_MULTIPLIED_TOTAL} on armor. */
    private static final double VANGUARD_ARMOR_MULT_PER_PIECE = 0.028;
    private static final float DEFLECTION_TRIGGER_CHANCE = 0.14f;
    private static final float DEFLECTION_DAMAGE_REMAINING = 0.42f;
    private static final int DEFLECTION_MIN_ARMOR_PIECES = 2;
    private static final ResourceLocation FAR_REACH_ENTITY_ID = id("valor_far_reach_entity_range");
    private static final ResourceLocation FAR_REACH_BLOCK_ID = id("valor_far_reach_block_range");
    private static final double FAR_REACH_BONUS_BLOCKS = 0.85;
    private static final int MONSTER_HUNTER_DURABILITY_REPAIR = 3;
    private static final int INPENETRABLE_EFFECT_TICKS = 220;
    private static final float INPENETRABLE_MAX_TOLERANCE = 0.05F;
    private static final int BATTERING_RAM_HIT_COOLDOWN_TICKS = 14;
    private static final float BATTERING_RAM_DAMAGE_BASE = 2.0F;
    private static final float BATTERING_RAM_DAMAGE_PER_ARMOR = 0.9F;
    private static final float BATTERING_RAM_KNOCKBACK_BASE = 0.45F;
    private static final float BATTERING_RAM_KNOCKBACK_PER_ARMOR = 0.08F;
    private static final double PROTECTION_AURA_RADIUS = 8.0;
    private static final float PROTECTION_AURA_HEAL_FRACTION = 0.08F;
    private static final float PROTECTION_AURA_HEAL_CAP = 3.0F;
    private static final int BULWARK_COOLDOWN_TICKS = 20 * 300;
    private static final int BULWARK_REGEN_TICKS = 20 * 12;
    private static final int BULWARK_RESIST_TICKS = 20 * 6;
    private static final int BULWARK_REGEN_AMPLIFIER = 2;
    private static final int BULWARK_RESIST_AMPLIFIER = 1;
    private static final double MORTAL_WOUND_THRESHOLD = 0.25;
    private static final double MORTAL_WOUND_RESOLVE_BASE = 18.0;
    private static final double MORTAL_WOUND_RESOLVE_PER_MAX_HP = 0.12;
    private static final double MORTAL_WOUND_RESOLVE_COST_MIN = 24.0;
    private static final double MORTAL_WOUND_RESOLVE_COST_MAX = 95.0;
    private static final float MORTAL_WOUND_BOSS_WEAPON_DAMAGE_MULT = 3.0F;

    private static final Map<UUID, Integer> LAST_BATTERING_RAM_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BULWARK_LAST_TRIGGER_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIncomingDamageBulwark(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (victim.isSpectator() || victim.isCreative()) {
            return;
        }
        float incoming = event.getNewDamage();
        if (incoming <= 0.0f || incoming < victim.getHealth()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(victim);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.BULWARK_OF_HOPE)) {
            return;
        }
        long now = victim.level().getGameTime();
        long last = BULWARK_LAST_TRIGGER_TICK.getOrDefault(victim.getUUID(), Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < BULWARK_COOLDOWN_TICKS) {
            return;
        }
        event.setNewDamage(Math.max(0.0F, victim.getHealth() - 1.0F));
        BULWARK_LAST_TRIGGER_TICK.put(victim.getUUID(), now);
        victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BULWARK_REGEN_TICKS, BULWARK_REGEN_AMPLIFIER, false, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BULWARK_RESIST_TICKS, BULWARK_RESIST_AMPLIFIER, false, true, true));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIncomingDamageDeflection(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (victim.isSpectator() || victim.isCreative()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null || !source.isDirect()) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (event.getNewDamage() <= 0.0f) {
            return;
        }
        LevelProfile profile = LevelProfile.get(victim);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.DEFLECTION)) {
            return;
        }
        if (countArmorPieces(victim) < DEFLECTION_MIN_ARMOR_PIECES) {
            return;
        }
        if (victim.getRandom().nextFloat() >= DEFLECTION_TRIGGER_CHANCE) {
            return;
        }
        event.setNewDamage(event.getNewDamage() * DEFLECTION_DAMAGE_REMAINING);
        victim.connection.send(new ClientboundCustomPayloadPacket(new CameraShakePayload(7, 1.9F, 2.5F)));
    }

    @SubscribeEvent
    public void onPlayerTickValorBranches(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        applyVanguardArmor(player, profile);
        applyFarReach(player, profile);
        applyInpenetrable(player, profile);
        applyImmovableObject(player, profile);
        applyBatteringRam(player, profile);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onOutgoingDamageMortalWound(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            return;
        }
        if (!attacker.isShiftKeyDown()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == attacker || !target.isAlive()) {
            return;
        }
        if (!ValorMeleeWeapons.isValorMelee(attacker.getMainHandItem())) {
            return;
        }
        LevelProfile profile = LevelProfile.get(attacker);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.MORTAL_WOUND)) {
            return;
        }
        float postHitHealth = target.getHealth() - event.getNewDamage();
        float thresholdHealth = (float) (target.getMaxHealth() * MORTAL_WOUND_THRESHOLD);
        if (postHitHealth <= 0.0F || postHitHealth > thresholdHealth) {
            return;
        }
        double resolveCost = mortalWoundResolveCost(target);
        if (!PlayerGauges.spend(attacker, GaugeRegistry.RESOLVE, resolveCost)) {
            return;
        }
        if (isBossStyleMortalWoundTarget(target)) {
            float weapon = mainHandAttackDamage(attacker);
            float bonus = Math.max(1.0F, weapon * MORTAL_WOUND_BOSS_WEAPON_DAMAGE_MULT);
            event.setNewDamage(event.getNewDamage() + bonus);
        } else {
            event.setNewDamage(Math.max(event.getNewDamage(), target.getHealth() + 2.0F));
        }
    }

    private static double mortalWoundResolveCost(LivingEntity target) {
        double raw = MORTAL_WOUND_RESOLVE_BASE + target.getMaxHealth() * MORTAL_WOUND_RESOLVE_PER_MAX_HP;
        return Mth.clamp(raw, MORTAL_WOUND_RESOLVE_COST_MIN, MORTAL_WOUND_RESOLVE_COST_MAX);
    }

    private static float mainHandAttackDamage(ServerPlayer attacker) {
        AttributeInstance inst = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        return inst == null ? 1.0F : (float) Math.max(0.0, inst.getValue());
    }

    /**
     * Boss-style targets: vanilla boss mobs with a global boss bar. Extend later (tags / config) for modded bosses.
     */
    private static boolean isBossStyleMortalWoundTarget(LivingEntity target) {
        return target instanceof WitherBoss || target instanceof EnderDragon;
    }

    @SubscribeEvent
    public void onOutgoingDamageProtectionAura(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == attacker) {
            return;
        }
        float dealt = event.getNewDamage();
        if (dealt <= 0.0F || !ValorMeleeWeapons.isValorMelee(attacker.getMainHandItem())) {
            return;
        }
        LevelProfile profile = LevelProfile.get(attacker);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.PROTECTION_AURA)) {
            return;
        }
        float heal = Math.min(PROTECTION_AURA_HEAL_CAP, Math.max(0.5F, dealt * PROTECTION_AURA_HEAL_FRACTION));
        healLivingAllies(attacker, heal);
    }

    @SubscribeEvent
    public void onLivingDeathMonsterHunter(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (killer.level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Monster) || victim == killer) {
            return;
        }
        LevelProfile profile = LevelProfile.get(killer);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.MONSTER_HUNTER)) {
            return;
        }
        if (!(killer.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = victim.position();
        int reward = victim.getExperienceReward(level, killer);
        if (reward > 0) {
            ExperienceOrb.award(level, pos, reward);
        } else {
            int fallback = Mth.clamp((int) (victim.getMaxHealth() * 0.35f) + 4, 4, 40);
            ExperienceOrb.award(level, pos, fallback);
        }
        ItemStack main = killer.getMainHandItem();
        if (!main.isEmpty() && main.isDamageableItem() && main.getDamageValue() > 0 && ValorMeleeWeapons.isValorMelee(main)) {
            int next = Math.max(0, main.getDamageValue() - MONSTER_HUNTER_DURABILITY_REPAIR);
            main.setDamageValue(next);
        }
    }

    private static void applyVanguardArmor(ServerPlayer player, LevelProfile profile) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            return;
        }
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.VANGUARD)) {
            armor.removeModifier(VANGUARD_ARMOR_MULT_ID);
            return;
        }
        int pieces = countArmorPieces(player);
        if (pieces <= 0) {
            armor.removeModifier(VANGUARD_ARMOR_MULT_ID);
            return;
        }
        double mult = VANGUARD_ARMOR_MULT_PER_PIECE * pieces;
        armor.addOrUpdateTransientModifier(new AttributeModifier(
                VANGUARD_ARMOR_MULT_ID,
                mult,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void applyFarReach(ServerPlayer player, LevelProfile profile) {
        boolean want = SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.FAR_REACH)
                && ValorMeleeWeapons.isValorMelee(player.getMainHandItem());
        AttributeInstance entityReach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (entityReach != null) {
            if (!want) {
                entityReach.removeModifier(FAR_REACH_ENTITY_ID);
            } else {
                entityReach.addOrUpdateTransientModifier(new AttributeModifier(
                        FAR_REACH_ENTITY_ID,
                        FAR_REACH_BONUS_BLOCKS,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
        AttributeInstance blockReach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (blockReach != null) {
            if (!want) {
                blockReach.removeModifier(FAR_REACH_BLOCK_ID);
            } else {
                blockReach.addOrUpdateTransientModifier(new AttributeModifier(
                        FAR_REACH_BLOCK_ID,
                        FAR_REACH_BONUS_BLOCKS,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
    }

    private static void applyInpenetrable(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.INPENETRABLE)) {
            return;
        }
        double max = profile.gauges.getMax(player, profile, GaugeRegistry.RESOLVE);
        if (max <= 0.0) {
            return;
        }
        double resolve = profile.gauges.getValue(GaugeRegistry.RESOLVE);
        if (resolve + INPENETRABLE_MAX_TOLERANCE < max) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, INPENETRABLE_EFFECT_TICKS, 0, false, false, true));
    }

    private static void applyImmovableObject(ServerPlayer player, LevelProfile profile) {
        AttributeInstance kbResist = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        boolean unlocked = SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.IMMOVABLE_OBJECT);
        if (kbResist != null) {
            if (!unlocked) {
                kbResist.removeModifier(IMMOVABLE_KB_RESIST_ID);
            } else {
                kbResist.addOrUpdateTransientModifier(new AttributeModifier(
                        IMMOVABLE_KB_RESIST_ID,
                        1.0,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
        if (!unlocked) {
            return;
        }
        if (player.hasEffect(MobEffects.POISON)) {
            player.removeEffect(MobEffects.POISON);
        }
        if (player.isOnFire()) {
            player.clearFire();
        }
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
    }

    private static void applyBatteringRam(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.BATTERING_RAM)) {
            return;
        }
        if (!player.isSprinting() || !player.horizontalCollision) {
            return;
        }
        int tick = player.tickCount;
        int lastTick = LAST_BATTERING_RAM_HIT_TICK.getOrDefault(player.getUUID(), Integer.MIN_VALUE);
        if (tick - lastTick < BATTERING_RAM_HIT_COOLDOWN_TICKS) {
            return;
        }
        int armorPieces = countArmorPieces(player);
        float damage = BATTERING_RAM_DAMAGE_BASE + armorPieces * BATTERING_RAM_DAMAGE_PER_ARMOR;
        float knockback = BATTERING_RAM_KNOCKBACK_BASE + armorPieces * BATTERING_RAM_KNOCKBACK_PER_ARMOR;
        AABB hitbox = player.getBoundingBox().inflate(0.35);
        boolean hit = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, hitbox, e -> e != player && e.isAlive())) {
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                target.knockback(knockback, player.getX() - target.getX(), player.getZ() - target.getZ());
                hit = true;
            }
        }
        if (hit) {
            LAST_BATTERING_RAM_HIT_TICK.put(player.getUUID(), tick);
        }
    }

    private static void healLivingAllies(ServerPlayer player, float heal) {
        if (heal <= 0.0F) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(PROTECTION_AURA_RADIUS);
        for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isRemoved())) {
            if (ally == player || ally.isAlliedTo(player)) {
                ally.heal(heal);
            }
        }
    }

    private static int countArmorPieces(ServerPlayer player) {
        int n = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }
}
