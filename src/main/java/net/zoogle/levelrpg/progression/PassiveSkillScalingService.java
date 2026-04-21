package net.zoogle.levelrpg.progression;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central baseline progression scaling. This is the always-on counterpart to
 * mastery node effects: canonical skill levels grant predictable passive
 * benefits here, while mastery remains in its own dedicated layer.
 */
public final class PassiveSkillScalingService {
    private static final ResourceLocation MODIFIER_VITALITY_MAX_HEALTH = id("baseline_vitality_max_health");
    private static final ResourceLocation MODIFIER_VALOR_ARMOR = id("baseline_valor_armor");
    private static final ResourceLocation MODIFIER_VALOR_KNOCKBACK_RESISTANCE = id("baseline_valor_knockback_resistance");
    private static final ResourceLocation MODIFIER_FORGING_ARMOR_TOUGHNESS = id("baseline_forging_armor_toughness");
    private static final ResourceLocation MODIFIER_EXPLORATION_MOVEMENT_SPEED = id("baseline_exploration_movement_speed");

    // Central tuning constants for the first-pass canonical baseline map.
    private static final double VITALITY_MAX_HEALTH_PER_LEVEL = 1.0D;
    private static final double VALOR_ARMOR_PER_LEVEL = 0.10D;
    private static final double VALOR_KNOCKBACK_RESISTANCE_PER_LEVEL = 0.0025D;
    private static final double FORGING_ARMOR_TOUGHNESS_PER_LEVEL = 0.05D;
    private static final double EXPLORATION_MOVEMENT_SPEED_PER_LEVEL = 0.0025D;
    private static final float MINING_BREAK_SPEED_PER_LEVEL = 0.01F;
    private static final Map<UUID, Long> LAST_APPLIED_SIGNATURES = new ConcurrentHashMap<>();
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("0.##");

    private PassiveSkillScalingService() {}

    public static PassiveSkillSummary summarize(LevelProfile profile, ProgressionSkill skill) {
        if (profile == null || skill == null) {
            return new PassiveSkillSummary(null, "", "", false, List.of());
        }
        return summarize(skill, profile.getSkill(skill).level);
    }

    public static PassiveSkillSummary summarize(ProgressionSkill skill, int level) {
        if (skill == null) {
            return new PassiveSkillSummary(null, "", "", false, List.of());
        }
        int clampedLevel = Math.max(0, level);
        return switch (skill) {
            case VITALITY -> new PassiveSkillSummary(
                    skill.id(),
                    "Vitality Baseline",
                    "Endurance training increases your health pool.",
                    true,
                    List.of(new PassiveSkillSummary.Entry(
                            "Max Health",
                            formatSignedFlat(clampedLevel * VITALITY_MAX_HEALTH_PER_LEVEL),
                            clampedLevel * VITALITY_MAX_HEALTH_PER_LEVEL,
                            "Always-on health granted by Vitality level."
                    ))
            );
            case VALOR -> new PassiveSkillSummary(
                    skill.id(),
                    "Valor Baseline",
                    "Combat discipline hardens your defense and footing.",
                    true,
                    List.of(
                            new PassiveSkillSummary.Entry(
                                    "Armor",
                                    formatSignedFlat(clampedLevel * VALOR_ARMOR_PER_LEVEL),
                                    clampedLevel * VALOR_ARMOR_PER_LEVEL,
                                    "Always-on armor granted by Valor level."
                            ),
                            new PassiveSkillSummary.Entry(
                                    "Knockback Resist",
                                    formatPercent(clampedLevel * VALOR_KNOCKBACK_RESISTANCE_PER_LEVEL),
                                    clampedLevel * VALOR_KNOCKBACK_RESISTANCE_PER_LEVEL,
                                    "Resistance to being shoved back."
                            )
                    )
            );
            case FORGING -> new PassiveSkillSummary(
                    skill.id(),
                    "Forging Baseline",
                    "Forging practice reinforces the armor you wear.",
                    true,
                    List.of(new PassiveSkillSummary.Entry(
                            "Armor Toughness",
                            formatSignedFlat(clampedLevel * FORGING_ARMOR_TOUGHNESS_PER_LEVEL),
                            clampedLevel * FORGING_ARMOR_TOUGHNESS_PER_LEVEL,
                            "Always-on armor toughness granted by Forging level."
                    ))
            );
            case EXPLORATION -> new PassiveSkillSummary(
                    skill.id(),
                    "Exploration Baseline",
                    "Field experience improves your travel pace.",
                    true,
                    List.of(new PassiveSkillSummary.Entry(
                            "Movement Speed",
                            formatPercent(clampedLevel * EXPLORATION_MOVEMENT_SPEED_PER_LEVEL),
                            clampedLevel * EXPLORATION_MOVEMENT_SPEED_PER_LEVEL,
                            "Always-on movement speed granted by Exploration level."
                    ))
            );
            case MINING -> new PassiveSkillSummary(
                    skill.id(),
                    "Mining Baseline",
                    "Mining practice improves your pickaxe work rate.",
                    true,
                    List.of(new PassiveSkillSummary.Entry(
                            "Break Speed",
                            formatPercent(clampedLevel * MINING_BREAK_SPEED_PER_LEVEL),
                            clampedLevel * MINING_BREAK_SPEED_PER_LEVEL,
                            "Applies while using a pickaxe."
                    ))
            );
            case CULINARY -> placeholder(skill, "Culinary baseline scaling is reserved for a future food-efficiency hook.");
            case ARTIFICING -> placeholder(skill, "Artificing baseline scaling is reserved for a future technical-crafting hook.");
            case MAGICK -> placeholder(skill, "Magick baseline scaling is reserved for a future attunement hook.");
        };
    }

    public static boolean applyIfChanged(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return false;
        }
        long signature = computePassiveSignature(profile);
        Long previous = LAST_APPLIED_SIGNATURES.get(player.getUUID());
        if (previous != null && previous.longValue() == signature) {
            return false;
        }
        forceApply(player, profile);
        LAST_APPLIED_SIGNATURES.put(player.getUUID(), signature);
        return true;
    }

    public static void forceApply(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return;
        }
        profile.ensureCanonicalSkills();

        applyVitalityBaseline(player, profile.getSkill(ProgressionSkill.VITALITY));
        applyValorBaseline(player, profile.getSkill(ProgressionSkill.VALOR));
        applyForgingBaseline(player, profile.getSkill(ProgressionSkill.FORGING));
        applyExplorationBaseline(player, profile.getSkill(ProgressionSkill.EXPLORATION));

        // Extension hooks for non-attribute systems that do not yet have a
        // clean vanilla-backed passive implementation.
        applyMiningBaseline(player, profile.getSkill(ProgressionSkill.MINING));
        applyCulinaryBaseline(player, profile.getSkill(ProgressionSkill.CULINARY));
        applyArtificingBaseline(player, profile.getSkill(ProgressionSkill.ARTIFICING));
        applyMagickBaseline(player, profile.getSkill(ProgressionSkill.MAGICK));

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        LAST_APPLIED_SIGNATURES.put(player.getUUID(), computePassiveSignature(profile));
    }

    public static void forget(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LAST_APPLIED_SIGNATURES.remove(player.getUUID());
    }

    public static void applyMiningBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getMainHandItem().is(ItemTags.PICKAXES)) {
            return;
        }

        LevelProfile profile = LevelProfile.get(player);
        int miningLevel = profile.getSkill(ProgressionSkill.MINING).level;
        if (miningLevel <= 0) {
            return;
        }

        float multiplier = 1.0F + (miningLevel * MINING_BREAK_SPEED_PER_LEVEL);
        event.setNewSpeed(event.getNewSpeed() * multiplier);
    }

    private static void applyVitalityBaseline(ServerPlayer player, SkillState state) {
        updateModifier(
                player,
                Attributes.MAX_HEALTH,
                MODIFIER_VITALITY_MAX_HEALTH,
                state.level * VITALITY_MAX_HEALTH_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    private static void applyValorBaseline(ServerPlayer player, SkillState state) {
        updateModifier(
                player,
                Attributes.ARMOR,
                MODIFIER_VALOR_ARMOR,
                state.level * VALOR_ARMOR_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE
        );
        updateModifier(
                player,
                Attributes.KNOCKBACK_RESISTANCE,
                MODIFIER_VALOR_KNOCKBACK_RESISTANCE,
                state.level * VALOR_KNOCKBACK_RESISTANCE_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    private static void applyForgingBaseline(ServerPlayer player, SkillState state) {
        updateModifier(
                player,
                Attributes.ARMOR_TOUGHNESS,
                MODIFIER_FORGING_ARMOR_TOUGHNESS,
                state.level * FORGING_ARMOR_TOUGHNESS_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    private static void applyExplorationBaseline(ServerPlayer player, SkillState state) {
        updateModifier(
                player,
                Attributes.MOVEMENT_SPEED,
                MODIFIER_EXPLORATION_MOVEMENT_SPEED,
                state.level * EXPLORATION_MOVEMENT_SPEED_PER_LEVEL,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    private static void applyMiningBaseline(ServerPlayer player, SkillState state) {
        // Mining currently uses the BreakSpeed hook above because there is no
        // vanilla attribute for baseline tool-gathering speed.
    }

    private static void applyCulinaryBaseline(ServerPlayer player, SkillState state) {
        // Extension point: add food/saturation efficiency once a clean hook is
        // chosen. Kept here so non-attribute passives remain centralized.
    }

    private static void applyArtificingBaseline(ServerPlayer player, SkillState state) {
        // Extension point: add baseline technical/crafting bonuses once a
        // stable gameplay hook is defined outside mastery-specific effects.
    }

    private static void applyMagickBaseline(ServerPlayer player, SkillState state) {
        // Extension point: add baseline magic attunement once the magick layer
        // has a non-mastery passive surface to scale.
    }

    private static void updateModifier(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0D) {
            instance.removeModifier(modifierId);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, operation));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }

    private static PassiveSkillSummary placeholder(ProgressionSkill skill, String summary) {
        return new PassiveSkillSummary(skill.id(), skill.displayName() + " Baseline", summary, false, List.of());
    }

    private static String formatSignedFlat(double value) {
        return value <= 0.0D ? "+0" : "+" + ONE_DECIMAL.format(value);
    }

    private static String formatPercent(double fraction) {
        double percent = fraction * 100.0D;
        return percent <= 0.0D ? "+0%" : "+" + TWO_DECIMALS.format(percent) + "%";
    }

    private static long computePassiveSignature(LevelProfile profile) {
        profile.ensureCanonicalSkills();
        long signature = 17L;
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillState state = profile.getSkill(skill);
            signature = (31L * signature) + skill.ordinal();
            signature = (31L * signature) + Math.max(0, state.level);
        }
        return signature;
    }
}
