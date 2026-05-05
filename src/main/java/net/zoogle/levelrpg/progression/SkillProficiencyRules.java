package net.zoogle.levelrpg.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProficiencyAwardResult;

/**
 * Thin event-facing bridge into the canonical proficiency award path.
 */
public final class SkillProficiencyRules {
    private SkillProficiencyRules() {}

    public static ProficiencyAwardResult awardDelvingForBlockBreak(ServerPlayer player, LevelProfile profile, BlockState state) {
        long proficiency = MiningXpRules.xpForBlockBreak(state);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(MiningXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardValorForDamage(ServerPlayer player, LevelProfile profile, LivingEntity target, float damageDealt) {
        long proficiency = ValorXpRules.xpForDamage(target, damageDealt);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ValorXpRules.SKILL_ID, proficiency);
    }

    /** Awards Valor (Grit branch) proficiency for surviving meaningful incoming damage. */
    public static ProficiencyAwardResult awardValorGritForDamageTaken(ServerPlayer player, LevelProfile profile, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        long proficiency = VitalityXpRules.xpForDamageTaken(player, source, damageTaken);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(VitalityXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardValorForKill(ServerPlayer player, LevelProfile profile, LivingEntity target) {
        long proficiency = ValorXpRules.xpForKill(target);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ValorXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardHearthForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = CulinaryXpRules.xpForCraftedItem(result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(CulinaryXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardHearthForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = CulinaryXpRules.xpForSmeltedItem(result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(CulinaryXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardForgingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = ForgingXpRules.xpForCraftedItem(result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ForgingXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardArtificingForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = ArtificingXpRules.xpForCraftedItem(player, result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ArtificingXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardArtificingForSmelt(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = ArtificingXpRules.xpForSmeltedItem(player, result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ArtificingXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardArcanaForCraft(ServerPlayer player, LevelProfile profile, ItemStack result) {
        long proficiency = MagickXpRules.xpForCraftedItem(player, result);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(MagickXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardArcanaForOpenMenu(ServerPlayer player, LevelProfile profile) {
        long proficiency = MagickXpRules.xpForOpenMenu(player);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(MagickXpRules.SKILL_ID, proficiency);
    }

    public static ProficiencyAwardResult awardFinesseForMovement(ServerPlayer player, LevelProfile profile) {
        long proficiency = ExplorationXpRules.xpForMovement(player);
        if (proficiency <= 0L) {
            return null;
        }
        return profile.awardProficiency(ExplorationXpRules.SKILL_ID, proficiency);
    }
}
