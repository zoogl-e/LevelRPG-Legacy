package net.zoogle.levelrpg.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillXpResult;

/**
 * Deprecated XP-named wrapper over {@link SkillMasteryRules}.
 */
@Deprecated
public final class SkillXpRules {
    private SkillXpRules() {}

    public static SkillXpResult awardDelvingForBlockBreak(ServerPlayer player, LevelProfile profile, BlockState state) {
        return wrap(SkillMasteryRules.awardDelvingForBlockBreak(player, profile, state));
    }

    public static SkillXpResult awardValorForDamage(ServerPlayer player, LevelProfile profile, LivingEntity target, float damageDealt) {
        return wrap(SkillMasteryRules.awardValorForDamage(player, profile, target, damageDealt));
    }

    public static SkillXpResult awardValorGritForDamageTaken(ServerPlayer player, LevelProfile profile, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        return wrap(SkillMasteryRules.awardValorGritForDamageTaken(player, profile, source, damageTaken));
    }

    public static SkillXpResult awardValorForKill(ServerPlayer player, LevelProfile profile, LivingEntity target) {
        return wrap(SkillMasteryRules.awardValorForKill(player, profile, target));
    }

    public static SkillXpResult awardHearthForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardHearthForCraft(player, profile, result));
    }

    public static SkillXpResult awardHearthForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardHearthForSmelt(player, profile, result));
    }

    public static SkillXpResult awardForgingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardForgingForCraft(player, profile, result));
    }

    public static SkillXpResult awardArtificingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardArtificingForCraft(player, profile, result));
    }

    public static SkillXpResult awardArtificingForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardArtificingForSmelt(player, profile, result));
    }

    public static SkillXpResult awardArcanaForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        return wrap(SkillMasteryRules.awardArcanaForCraft(player, profile, result));
    }

    public static SkillXpResult awardArcanaForOpenMenu(ServerPlayer player, LevelProfile profile) {
        return wrap(SkillMasteryRules.awardArcanaForOpenMenu(player, profile));
    }

    public static SkillXpResult awardFinesseForMovement(ServerPlayer player, LevelProfile profile) {
        return wrap(SkillMasteryRules.awardFinesseForMovement(player, profile));
    }

    private static SkillXpResult wrap(net.zoogle.levelrpg.profile.MasteryAwardResult result) {
        return result == null ? null : SkillXpResult.from(result);
    }
}
