package net.zoogle.levelrpg.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillXpResult;

/**
 * Thin event-facing bridge into the canonical XP award service.
 */
public final class SkillXpRules {
    private SkillXpRules() {}

    public static SkillXpResult awardMiningForBlockBreak(ServerPlayer player, LevelProfile profile, BlockState state) {
        long xp = MiningXpRules.xpForBlockBreak(state);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(MiningXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardValorForDamage(ServerPlayer player, LevelProfile profile, LivingEntity target, float damageDealt) {
        long xp = ValorXpRules.xpForDamage(target, damageDealt);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ValorXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardVitalityForDamageTaken(ServerPlayer player, LevelProfile profile, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        long xp = VitalityXpRules.xpForDamageTaken(player, source, damageTaken);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(VitalityXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardValorForKill(ServerPlayer player, LevelProfile profile, LivingEntity target) {
        long xp = ValorXpRules.xpForKill(target);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ValorXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardCulinaryForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = CulinaryXpRules.xpForCraftedItem(result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(CulinaryXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardCulinaryForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = CulinaryXpRules.xpForSmeltedItem(result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(CulinaryXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardForgingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = ForgingXpRules.xpForCraftedItem(result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ForgingXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardArtificingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = ArtificingXpRules.xpForCraftedItem(player, result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ArtificingXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardArtificingForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = ArtificingXpRules.xpForSmeltedItem(player, result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ArtificingXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardMagickForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long xp = MagickXpRules.xpForCraftedItem(player, result);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(MagickXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardMagickForOpenMenu(ServerPlayer player, LevelProfile profile) {
        long xp = MagickXpRules.xpForOpenMenu(player);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(MagickXpRules.SKILL_ID, xp);
    }

    public static SkillXpResult awardExplorationForMovement(ServerPlayer player, LevelProfile profile) {
        long xp = ExplorationXpRules.xpForMovement(player);
        if (xp <= 0L) {
            return null;
        }
        return profile.awardSkillXp(ExplorationXpRules.SKILL_ID, xp);
    }
}
