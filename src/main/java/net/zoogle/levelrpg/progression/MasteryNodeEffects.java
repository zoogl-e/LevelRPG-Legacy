package net.zoogle.levelrpg.progression;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.MasteryAwardResult;
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Narrow gameplay layer for data-driven mastery nodes.
 *
 * Unlocked node ids remain the source of truth. This class only translates a
 * selected subset of canonical node ids into gameplay hooks so later rewrite
 * steps can append more effects without altering persistence or sync.
 */
public final class MasteryNodeEffects {
    private static final ResourceLocation MODIFIER_VANGUARD_DAMAGE = id("vanguard_stance_damage");
    private static final ResourceLocation MODIFIER_VANGUARD_KNOCKBACK = id("vanguard_stance_knockback");
    private static final ResourceLocation MODIFIER_DUELIST_SPEED = id("duelist_footwork_speed");
    private static final ResourceLocation MODIFIER_DUELIST_ATTACK_SPEED = id("duelist_footwork_attack_speed");
    private static final ResourceLocation MODIFIER_MAGICK_WILD_CHANNELING = id("wild_channeling_attack_speed");
    private static final ResourceLocation MODIFIER_MAGICK_SURGING_CURRENT = id("surging_current_speed");
    private static final ResourceLocation MODIFIER_EXPLORATION_TRAIL_MARKS = id("trail_marks_speed");
    private static final ResourceLocation MODIFIER_EXPLORATION_CARTOGRAPHERS_FOCUS = id("cartographers_focus_speed");
    private static final ResourceLocation MODIFIER_VITALITY_IRON_RESERVE = id("iron_reserve_health");

    private static final int VEIN_FOLLOWING_HASTE_TICKS = 80;
    private static final int VEIN_FOLLOWING_HASTE_AMPLIFIER = 1;
    private static final int QUARRY_DISCIPLINE_HASTE_TICKS = 50;
    private static final int QUARRY_DISCIPLINE_HASTE_AMPLIFIER = 0;
    private static final int DEEP_SCAN_NIGHT_VISION_TICKS = 220;
    private static final int VOIDWARD_FIRE_RESISTANCE_TICKS = 120;
    private static final int WARLORD_PRESENCE_RESISTANCE_TICKS = 80;
    private static final double WARLORD_PRESENCE_RADIUS = 5.0D;
    private static final int WARLORD_PRESENCE_MIN_HOSTILES = 3;
    private static final int FINISHING_CHAIN_BUFF_TICKS = 140;
    private static final int EXPLORATION_NIGHT_VISION_TICKS = 220;
    private static final int EXPLORATION_HIDDEN_WAYPOINTS_SPEED_TICKS = 90;
    private static final int EXPLORATION_FRONTIER_SENSE_NIGHT_VISION_TICKS = 140;
    private static final int EXPLORATION_FRONTIER_SENSE_GLOW_TICKS = 100;
    private static final double EXPLORATION_FRONTIER_SENSE_RADIUS = 20.0D;
    private static final int EXPLORATION_FRONTIER_SENSE_MAX_TARGETS = 6;
    private static final int VITALITY_LAST_STAND_TICKS = 60;
    private static final int VITALITY_RESTORATIVE_BLOOM_DELAY_TICKS = 160;
    private static final int VITALITY_RAPID_RECOVERY_DELAY_TICKS = 100;
    private static final int VITALITY_RENEWING_CORE_DELAY_TICKS = 60;
    private static final int VITALITY_RESTORATIVE_BLOOM_REGEN_TICKS = 80;
    private static final int VITALITY_RENEWING_CORE_REGEN_TICKS = 100;
    private static final int VITALITY_MIN_FOOD_FOR_RECOVERY = 18;
    private static final float VITALITY_LAST_STAND_HEALTH_RATIO = 0.35F;
    private static final float VITALITY_RENEWING_CORE_HEALTH_RATIO = 0.75F;

    private static final Map<UUID, Long> LAST_DAMAGE_TICK = new HashMap<>();

    private MasteryNodeEffects() {}

    public static void applyPassiveEffects(ServerPlayer player, LevelProfile profile) {
        applyValorPassives(player, profile);
        applyMiningPassives(player, profile);
        applyMagickPassives(player, profile);
        applyExplorationPassives(player, profile);
        applyVitalityPassives(player, profile);
    }

    public static void applyIncomingDamageModifiers(LivingIncomingDamageEvent event) {
        float damage = event.getAmount();

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            if (event.getEntity() instanceof ServerPlayer defender) {
                LevelProfile profile = LevelProfile.get(defender);
                damage = applyDefensiveIncomingDamageModifiers(event, defender, profile, damage);
                if (damage != event.getAmount()) {
                    event.setAmount(damage);
                }
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);

        if (hasNode(profile, ProgressionSkill.VALOR, "shieldbreaker_drive") && target.isBlocking()) {
            damage *= 1.35F;
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
        }
        if (hasNode(profile, ProgressionSkill.VALOR, "execution_window")
                && target.getMaxHealth() > 0.0F
                && target.getHealth() <= target.getMaxHealth() * 0.50F) {
            damage *= 1.35F;
        }
        if (hasNode(profile, ProgressionSkill.MAGICK, "stormcasting") && hasBeneficialEffect(player)) {
            damage *= 1.15F;
        }

        if (event.getEntity() instanceof ServerPlayer defender) {
            LevelProfile defenderProfile = LevelProfile.get(defender);
            damage = applyDefensiveIncomingDamageModifiers(event, defender, defenderProfile, damage);
        }

        if (damage != event.getAmount()) {
            event.setAmount(damage);
        }
    }

    public static void applyMiningBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.PICKAXES)) {
            return;
        }

        LevelProfile profile = LevelProfile.get(player);
        float speedMultiplier = 1.0F;
        if (MiningXpRules.isStoneLike(state) && hasNode(profile, ProgressionSkill.MINING, "clean_cuts")) {
            speedMultiplier += 0.30F;
        }
        if (MiningXpRules.isOre(state) && hasNode(profile, ProgressionSkill.MINING, "prospectors_eye")) {
            speedMultiplier += 0.30F;
        }
        if (MiningXpRules.isStoneLike(state) && hasNode(profile, ProgressionSkill.MINING, "quarry_discipline")) {
            speedMultiplier += 0.25F;
        }

        if (speedMultiplier > 1.0F) {
            event.setNewSpeed(event.getNewSpeed() * speedMultiplier);
        }
    }

    public static void afterMiningBlockBreak(ServerPlayer player, LevelProfile profile, BlockState state) {
        if (state == null) {
            return;
        }
        if (!player.getMainHandItem().is(ItemTags.PICKAXES)) {
            return;
        }
        if (MiningXpRules.isOre(state) && hasNode(profile, ProgressionSkill.MINING, "vein_following")) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, VEIN_FOLLOWING_HASTE_TICKS, VEIN_FOLLOWING_HASTE_AMPLIFIER, true, false, true));
        }
        if (MiningXpRules.isStoneLike(state) && hasNode(profile, ProgressionSkill.MINING, "quarry_discipline")) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, QUARRY_DISCIPLINE_HASTE_TICKS, QUARRY_DISCIPLINE_HASTE_AMPLIFIER, true, false, true));
        }
    }

    public static void afterValorKill(ServerPlayer player, LevelProfile profile, LivingEntity victim) {
        if (player == null || profile == null || victim == null) {
            return;
        }
        if (!hasNode(profile, ProgressionSkill.VALOR, "finishing_chain")) {
            return;
        }
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FINISHING_CHAIN_BUFF_TICKS, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, FINISHING_CHAIN_BUFF_TICKS, 0, true, false, true));
    }

    public static void afterCraft(ServerPlayer player, LevelProfile profile, ItemStack result, Container craftMatrix) {
        if (result == null || result.isEmpty() || craftMatrix == null) {
            return;
        }
        if (!isCraftUnlocked(player, profile, craftMatrix)) {
            return;
        }

        if (ArtificingXpRules.isTechnicalCraftOutput(result)) {
            float chance = 0.0F;
            if (hasNode(profile, ProgressionSkill.ARTIFICING, "field_tinkering")) {
                chance += 0.12F;
            }
            if (hasNode(profile, ProgressionSkill.ARTIFICING, "salvage_loop")) {
                chance += 0.18F;
            }
            if (hasNode(profile, ProgressionSkill.ARTIFICING, "improvised_rigging")
                    && !ArtificingXpRules.isComplexTechnicalCraftOutput(result)) {
                chance += 0.10F;
            }

            grantBonusOutput(player, result, chance, Component.literal("Field Tinkering yielded an extra output."));
        }

        if (MagickXpRules.isMagicCraftOutput(result)) {
            float chance = 0.0F;
            if (hasNode(profile, ProgressionSkill.MAGICK, "ritual_geometry")) {
                chance += 0.10F;
            }
            if (hasNode(profile, ProgressionSkill.MAGICK, "sigil_memory")) {
                chance += 0.14F;
            }

            grantBonusOutput(player, result, chance, Component.literal("Ritual Geometry preserved an extra magical output."));
        }
    }

    public static void afterSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        if (result == null || result.isEmpty() || !ArtificingXpRules.isRefinedMachineOutput(result)) {
            return;
        }

        float chance = 0.0F;
        if (hasNode(profile, ProgressionSkill.ARTIFICING, "precision_fabrication")) {
            chance += 0.12F;
        }
        if (hasNode(profile, ProgressionSkill.ARTIFICING, "clockwork_layouts")) {
            chance += 0.18F;
        }
        if (hasNode(profile, ProgressionSkill.ARTIFICING, "master_mechanisms")) {
            chance += 0.10F;
        }

        grantBonusOutput(player, result, chance, Component.literal("Precision Fabrication refined an extra output."));
    }

    public static void onDamageTaken(ServerPlayer player, float damageTaken) {
        if (player == null || damageTaken <= 0.0F) {
            return;
        }
        LAST_DAMAGE_TICK.put(player.getUUID(), player.level().getGameTime());
    }

    public static void afterExplorationAward(ServerPlayer player, LevelProfile profile, MasteryAwardResult result) {
        if (player == null || profile == null || result == null || !ProgressionSkill.EXPLORATION.id().equals(result.skillId())) {
            return;
        }

        if (hasNode(profile, ProgressionSkill.EXPLORATION, "hidden_waypoints")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EXPLORATION_HIDDEN_WAYPOINTS_SPEED_TICKS, 0, true, false, true));
        }

        if (!hasNode(profile, ProgressionSkill.EXPLORATION, "frontier_sense")) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EXPLORATION_FRONTIER_SENSE_NIGHT_VISION_TICKS, 0, true, false, true));

        var nearbyThreats = player.level().getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(EXPLORATION_FRONTIER_SENSE_RADIUS),
                monster -> monster.isAlive() && !monster.isAlliedTo(player)
        );
        int revealCount = 0;
        for (Monster monster : nearbyThreats) {
            monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, EXPLORATION_FRONTIER_SENSE_GLOW_TICKS, 0, true, false, true));
            revealCount++;
            if (revealCount >= EXPLORATION_FRONTIER_SENSE_MAX_TARGETS) {
                break;
            }
        }
    }

    private static void applyValorPassives(ServerPlayer player, LevelProfile profile) {
        ItemStack held = player.getMainHandItem();
        boolean vanguardActive = held.is(ItemTags.AXES) || held.is(ItemTags.MACE_ENCHANTABLE);
        boolean duelistActive = held.is(ItemTags.SWORDS);

        updateModifier(player, Attributes.ATTACK_DAMAGE, MODIFIER_VANGUARD_DAMAGE, 1.0D,
                AttributeModifier.Operation.ADD_VALUE, vanguardActive && hasNode(profile, ProgressionSkill.VALOR, "vanguard_stance"));
        updateModifier(player, Attributes.KNOCKBACK_RESISTANCE, MODIFIER_VANGUARD_KNOCKBACK, 0.10D,
                AttributeModifier.Operation.ADD_VALUE, vanguardActive && hasNode(profile, ProgressionSkill.VALOR, "warlord_presence"));
        updateModifier(player, Attributes.MOVEMENT_SPEED, MODIFIER_DUELIST_SPEED, 0.07D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, duelistActive && hasNode(profile, ProgressionSkill.VALOR, "duelist_footwork"));
        updateModifier(player, Attributes.ATTACK_SPEED, MODIFIER_DUELIST_ATTACK_SPEED, 0.10D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, duelistActive && hasNode(profile, ProgressionSkill.VALOR, "finishing_chain"));

        boolean surrounded = player.level().getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(WARLORD_PRESENCE_RADIUS),
                monster -> monster.isAlive() && !monster.isAlliedTo(player)
        ).size() >= WARLORD_PRESENCE_MIN_HOSTILES;
        if (vanguardActive && surrounded && hasNode(profile, ProgressionSkill.VALOR, "warlord_presence")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, WARLORD_PRESENCE_RESISTANCE_TICKS, 0, true, false, true));
        }
    }

    private static void applyMiningPassives(ServerPlayer player, LevelProfile profile) {
        boolean deepScanActive = hasNode(profile, ProgressionSkill.MINING, "voidward_prospecting")
                && player.getMainHandItem().is(ItemTags.PICKAXES)
                && player.blockPosition().getY() <= 0;
        if (deepScanActive) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DEEP_SCAN_NIGHT_VISION_TICKS, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, VOIDWARD_FIRE_RESISTANCE_TICKS, 0, true, false, true));
        }
    }

    private static void applyMagickPassives(ServerPlayer player, LevelProfile profile) {
        boolean empowered = hasBeneficialEffect(player);
        updateModifier(player, Attributes.ATTACK_SPEED, MODIFIER_MAGICK_WILD_CHANNELING, 0.10D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, empowered && hasNode(profile, ProgressionSkill.MAGICK, "wild_channeling"));
        updateModifier(player, Attributes.MOVEMENT_SPEED, MODIFIER_MAGICK_SURGING_CURRENT, 0.06D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, empowered && hasNode(profile, ProgressionSkill.MAGICK, "surging_current"));
    }

    private static void applyExplorationPassives(ServerPlayer player, LevelProfile profile) {
        boolean groundedTraversal = player.onGround() && !player.isInWaterOrBubble();
        updateModifier(player, Attributes.MOVEMENT_SPEED, MODIFIER_EXPLORATION_TRAIL_MARKS, 0.04D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, groundedTraversal && hasNode(profile, ProgressionSkill.EXPLORATION, "trail_marks"));
        updateModifier(player, Attributes.MOVEMENT_SPEED, MODIFIER_EXPLORATION_CARTOGRAPHERS_FOCUS, 0.04D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                groundedTraversal && player.isSprinting() && hasNode(profile, ProgressionSkill.EXPLORATION, "cartographers_focus"));

        boolean lowLight = player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 7;
        if (lowLight && hasNode(profile, ProgressionSkill.EXPLORATION, "wanderers_instinct")) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EXPLORATION_NIGHT_VISION_TICKS, 0, true, false, true));
        }
    }

    private static void applyVitalityPassives(ServerPlayer player, LevelProfile profile) {
        updateModifier(player, Attributes.MAX_HEALTH, MODIFIER_VITALITY_IRON_RESERVE, 2.0D,
                AttributeModifier.Operation.ADD_VALUE, hasNode(profile, ProgressionSkill.VITALITY, "iron_reserve"));

        if (hasNode(profile, ProgressionSkill.VITALITY, "last_stand")
                && player.getMaxHealth() > 0.0F
                && player.getHealth() <= player.getMaxHealth() * VITALITY_LAST_STAND_HEALTH_RATIO) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, VITALITY_LAST_STAND_TICKS, 0, true, false, true));
        }

        if (!hasNode(profile, ProgressionSkill.VITALITY, "restorative_bloom")) {
            return;
        }
        if (player.getFoodData().getFoodLevel() < VITALITY_MIN_FOOD_FOR_RECOVERY || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        long recoveryDelay = VITALITY_RESTORATIVE_BLOOM_DELAY_TICKS;
        if (hasNode(profile, ProgressionSkill.VITALITY, "renewing_core")) {
            recoveryDelay = VITALITY_RENEWING_CORE_DELAY_TICKS;
        } else if (hasNode(profile, ProgressionSkill.VITALITY, "rapid_recovery")) {
            recoveryDelay = VITALITY_RAPID_RECOVERY_DELAY_TICKS;
        }

        Long lastDamageTick = LAST_DAMAGE_TICK.get(player.getUUID());
        long now = player.level().getGameTime();
        if (lastDamageTick != null && now - lastDamageTick < recoveryDelay) {
            return;
        }

        boolean renewingCore = hasNode(profile, ProgressionSkill.VITALITY, "renewing_core");
        float recoveryThreshold = renewingCore ? player.getMaxHealth() * VITALITY_RENEWING_CORE_HEALTH_RATIO : player.getMaxHealth();
        if (player.getHealth() > recoveryThreshold) {
            return;
        }

        int amplifier = renewingCore ? 1 : 0;
        int duration = renewingCore ? VITALITY_RENEWING_CORE_REGEN_TICKS : VITALITY_RESTORATIVE_BLOOM_REGEN_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier, true, false, true));
    }

    private static float applyDefensiveIncomingDamageModifiers(
            LivingIncomingDamageEvent event,
            ServerPlayer defender,
            LevelProfile profile,
            float damage
    ) {
        if (hasNode(profile, ProgressionSkill.MAGICK, "sealed_circuit")
                && (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC))) {
            damage *= 0.75F;
        }
        if (hasNode(profile, ProgressionSkill.EXPLORATION, "measured_stride") && event.getSource().is(DamageTypes.FALL)) {
            damage *= 0.80F;
        }
        if (hasNode(profile, ProgressionSkill.VITALITY, "stoneheart") && damage >= 4.0F) {
            damage *= 0.88F;
        }
        if (hasNode(profile, ProgressionSkill.VITALITY, "last_stand")
                && defender.getMaxHealth() > 0.0F
                && defender.getHealth() <= defender.getMaxHealth() * VITALITY_LAST_STAND_HEALTH_RATIO) {
            damage *= 0.90F;
        }
        return damage;
    }

    private static void grantBonusOutput(ServerPlayer player, ItemStack result, float chance, Component feedback) {
        if (chance <= 0.0F || player.getRandom().nextFloat() >= chance) {
            return;
        }
        ItemStack bonus = result.copyWithCount(1);
        boolean added = player.getInventory().add(bonus);
        if (!added) {
            player.drop(bonus, false);
        }
        player.displayClientMessage(feedback, true);
    }

    private static boolean isCraftUnlocked(ServerPlayer player, LevelProfile profile, Container craftMatrix) {
        if (!(craftMatrix instanceof net.minecraft.world.inventory.CraftingContainer craftingContainer)) {
            return true;
        }
        var recipe = player.level().getRecipeManager().getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                craftingContainer.asCraftInput(),
                player.level()
        );
        if (recipe.isEmpty()) {
            return true;
        }
        return RecipeUnlockService.checkAccess(profile, recipe.get().id()).unlocked();
    }

    private static void updateModifier(
            ServerPlayer player,
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation,
            boolean enabled
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (!enabled) {
            instance.removeModifier(modifierId);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, operation));
    }

    private static boolean hasBeneficialEffect(ServerPlayer player) {
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().isBeneficial()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNode(LevelProfile profile, ProgressionSkill skill, String nodeId) {
        return profile.getUnlockedTreeNodes(skill.id()).contains(nodeId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }
}
