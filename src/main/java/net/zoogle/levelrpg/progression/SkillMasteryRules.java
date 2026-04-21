package net.zoogle.levelrpg.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.MasteryAwardResult;

/**
 * Thin event-facing bridge into the canonical mastery award path.
 */
public final class SkillMasteryRules {
    private SkillMasteryRules() {}

    public static MasteryAwardResult awardMiningForBlockBreak(ServerPlayer player, LevelProfile profile, BlockState state) {
        long mastery = MiningXpRules.xpForBlockBreak(state);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(MiningXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardValorForDamage(ServerPlayer player, LevelProfile profile, LivingEntity target, float damageDealt) {
        long mastery = ValorXpRules.xpForDamage(target, damageDealt);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ValorXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardVitalityForDamageTaken(ServerPlayer player, LevelProfile profile, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        long mastery = VitalityXpRules.xpForDamageTaken(player, source, damageTaken);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(VitalityXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardValorForKill(ServerPlayer player, LevelProfile profile, LivingEntity target) {
        long mastery = ValorXpRules.xpForKill(target);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ValorXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardCulinaryForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = CulinaryXpRules.xpForCraftedItem(result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(CulinaryXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardCulinaryForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = CulinaryXpRules.xpForSmeltedItem(result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(CulinaryXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardForgingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = ForgingXpRules.xpForCraftedItem(result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ForgingXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardArtificingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = ArtificingXpRules.xpForCraftedItem(player, result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ArtificingXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardArtificingForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = ArtificingXpRules.xpForSmeltedItem(player, result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ArtificingXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardMagickForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long mastery = MagickXpRules.xpForCraftedItem(player, result);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(MagickXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardMagickForOpenMenu(ServerPlayer player, LevelProfile profile) {
        long mastery = MagickXpRules.xpForOpenMenu(player);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(MagickXpRules.SKILL_ID, mastery);
    }

    public static MasteryAwardResult awardExplorationForMovement(ServerPlayer player, LevelProfile profile) {
        long mastery = ExplorationXpRules.xpForMovement(player);
        if (mastery <= 0L) {
            return null;
        }
        return profile.awardMastery(ExplorationXpRules.SKILL_ID, mastery);
    }
}
