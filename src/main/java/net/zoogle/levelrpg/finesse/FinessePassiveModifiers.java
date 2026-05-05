package net.zoogle.levelrpg.finesse;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

/**
 * Transient attribute modifiers for Finesse mastery nodes (held-item and combo state).
 */
public final class FinessePassiveModifiers {
    private static final ResourceLocation MOD_HAND = id("finesse_hand_to_hand_damage");
    private static final ResourceLocation MOD_FLURRY = id("finesse_flurry_attack_speed");
    private static final ResourceLocation MOD_SMOOTH_MOVES = id("finesse_smooth_moves_damage");

    private static final double HAND_TO_HAND_DAMAGE = 2.25;
    private static final double FLURRY_ATTACK_SPEED_PER_STACK = 0.045;
    /** Extra attack damage at full Rhythm when unarmed ({@link FinesseNodeIds#SMOOTH_MOVES}). */
    private static final double SMOOTH_MOVES_DAMAGE_AT_FULL_RHYTHM = 5.25;

    private FinessePassiveModifiers() {
    }

    public static void apply(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return;
        }
        FinesseFlurryTracker.tick(player);
        applyHandToHand(player, profile);
        applySmoothMoves(player, profile);
        applyFlurry(player, profile);
    }

    private static void applyHandToHand(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.HAND_TO_HAND_COMBAT)) {
            updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_HAND, 0.0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_HAND, 0.0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_HAND, HAND_TO_HAND_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applySmoothMoves(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.SMOOTH_MOVES)) {
            updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_SMOOTH_MOVES, 0.0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_SMOOTH_MOVES, 0.0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        double max = profile.gauges.getMax(player, profile, GaugeRegistry.RHYTHM);
        if (max <= 0.0) {
            updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_SMOOTH_MOVES, 0.0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        double frac = Math.max(0.0, Math.min(1.0, profile.gauges.getValue(GaugeRegistry.RHYTHM) / max));
        double bonus = frac * SMOOTH_MOVES_DAMAGE_AT_FULL_RHYTHM;
        updateModifier(player, Attributes.ATTACK_DAMAGE, MOD_SMOOTH_MOVES, bonus, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applyFlurry(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.FLURRY_OF_BLOWS)) {
            updateModifier(player, Attributes.ATTACK_SPEED, MOD_FLURRY, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            return;
        }
        int stacks = FinesseFlurryTracker.stacks(player);
        if (stacks <= 0) {
            updateModifier(player, Attributes.ATTACK_SPEED, MOD_FLURRY, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            return;
        }
        double bonus = FLURRY_ATTACK_SPEED_PER_STACK * stacks;
        updateModifier(player, Attributes.ATTACK_SPEED, MOD_FLURRY, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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
}
