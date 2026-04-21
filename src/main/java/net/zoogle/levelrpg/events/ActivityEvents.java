package net.zoogle.levelrpg.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.zoogle.levelrpg.data.ActivityRules;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.MasteryAwardResult;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.ArtificingXpRules;
import net.zoogle.levelrpg.progression.CulinaryXpRules;
import net.zoogle.levelrpg.progression.ForgingXpRules;
import net.zoogle.levelrpg.progression.MagickXpRules;
import net.zoogle.levelrpg.progression.MasteryNodeEffects;
import net.zoogle.levelrpg.progression.MiningXpRules;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.SkillMasteryRules;
import net.zoogle.levelrpg.progression.VitalityXpRules;
import net.zoogle.levelrpg.progression.ValorXpRules;

import java.util.HashSet;
import java.util.Set;

/**
 * Gameplay hooks that consult data-driven rules to award mastery.
 */
public class ActivityEvents {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        LevelProfile profile = LevelProfile.get(sp);
        MasteryAwardResult magickAward = SkillMasteryRules.awardMagickForOpenMenu(sp, profile);
        if (magickAward != null) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            Network.sendDelta(sp, profile, magickAward.skillId());
            sendXpBar(sp, profile, magickAward.skillId());
            if (magickAward.leveledUp()) {
                sendLevelUp(sp, profile, magickAward.skillId());
            }
        }

        MasteryAwardResult explorationAward = SkillMasteryRules.awardExplorationForMovement(sp, profile);
        if (explorationAward == null) return;

        PassiveSkillScalingService.applyIfChanged(sp, profile);
        LevelProfile.save(sp, profile);
        Network.sendDelta(sp, profile, explorationAward.skillId());
        MasteryNodeEffects.afterExplorationAward(sp, profile, explorationAward);
        sendXpBar(sp, profile, explorationAward.skillId());
        if (explorationAward.leveledUp()) {
            sendLevelUp(sp, profile, explorationAward.skillId());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        var state = event.getState();

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changedSkills = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        MasteryAwardResult miningAward = SkillMasteryRules.awardMiningForBlockBreak(sp, profile, state);
        collectCanonicalAward(miningAward, changedSkills, leveledUp);

        if (ActivityRules.breakBlockRules().isEmpty() && changedSkills.isEmpty()) return;

        for (ActivityRules.BreakBlockRule rule : ActivityRules.breakBlockRules()) {
            if (MiningXpRules.SKILL_ID.equals(rule.skill) || ArtificingXpRules.SKILL_ID.equals(rule.skill)) continue;
            TagKey<net.minecraft.world.level.block.Block> tag = rule.tagKey();
            if (state.is(tag)) {
                collectCanonicalAward(profile.awardMastery(rule.skill, rule.xp), changedSkills, leveledUp);
            }
        }
        if (!changedSkills.isEmpty()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            for (ResourceLocation id : changedSkills) {
                Network.sendDelta(sp, profile, id);
                sendXpBar(sp, profile, id);
            }
            for (ResourceLocation id : leveledUp) {
                sendLevelUp(sp, profile, id);
            }
        }
    }

    @SubscribeEvent
    public void onMobDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer spTarget) {
            LevelProfile profile = LevelProfile.get(spTarget);
        MasteryAwardResult vitalityAward = SkillMasteryRules.awardVitalityForDamageTaken(spTarget, profile, event.getSource(), event.getNewDamage());
        if (vitalityAward != null) {
            PassiveSkillScalingService.applyIfChanged(spTarget, profile);
            LevelProfile.save(spTarget, profile);
            Network.sendDelta(spTarget, profile, vitalityAward.skillId());
            sendXpBar(spTarget, profile, vitalityAward.skillId());
                if (vitalityAward.leveledUp()) {
                    sendLevelUp(spTarget, profile, vitalityAward.skillId());
                }
            }
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity target)) return;

        LevelProfile profile = LevelProfile.get(sp);
        MasteryAwardResult valorAward = SkillMasteryRules.awardValorForDamage(sp, profile, target, event.getNewDamage());
        if (valorAward == null) return;

        PassiveSkillScalingService.applyIfChanged(sp, profile);
        LevelProfile.save(sp, profile);
        Network.sendDelta(sp, profile, valorAward.skillId());
        sendXpBar(sp, profile, valorAward.skillId());
        if (valorAward.leveledUp()) {
            sendLevelUp(sp, profile, valorAward.skillId());
        }
    }

    @SubscribeEvent
    public void onMobKilled(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        var victim = event.getEntity();
        var type = victim.getType();
        ItemStack held = sp.getMainHandItem();

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        MasteryAwardResult valorAward = SkillMasteryRules.awardValorForKill(sp, profile, victim);
        collectCanonicalAward(valorAward, changed, leveledUp);

        if (ActivityRules.killEntityRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.KillEntityRule rule : ActivityRules.killEntityRules()) {
            if (ValorXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (!type.is(rule.entityTagKey())) continue;
            if (rule.weaponItemTag != null) {
                TagKey<net.minecraft.world.item.Item> wTag = rule.weaponTagKey();
                if (!held.is(wTag)) continue;
            }
            collectCanonicalAward(profile.awardMastery(rule.skill, rule.xp), changed, leveledUp);
        }
        if (!changed.isEmpty()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            for (ResourceLocation id : changed) {
                Network.sendDelta(sp, profile, id);
                sendXpBar(sp, profile, id);
            }
            for (ResourceLocation id : leveledUp) {
                sendLevelUp(sp, profile, id);
            }
        }
        MasteryNodeEffects.afterValorKill(sp, profile, victim);
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        collectCanonicalAward(SkillMasteryRules.awardArtificingForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillMasteryRules.awardMagickForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillMasteryRules.awardCulinaryForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillMasteryRules.awardForgingForCraft(sp, profile, result), changed, leveledUp);

        if (ActivityRules.craftItemRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.CraftItemRule rule : ActivityRules.craftItemRules()) {
            if (ArtificingXpRules.SKILL_ID.equals(rule.skill)
                    || MagickXpRules.SKILL_ID.equals(rule.skill)
                    || CulinaryXpRules.SKILL_ID.equals(rule.skill)
                    || ForgingXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                collectCanonicalAward(profile.awardMastery(rule.skill, totalXp), changed, leveledUp);
            }
        }
        if (!changed.isEmpty()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            for (ResourceLocation id : changed) {
                Network.sendDelta(sp, profile, id);
                sendXpBar(sp, profile, id);
            }
            for (ResourceLocation id : leveledUp) {
                sendLevelUp(sp, profile, id);
            }
        }
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack result = event.getSmelting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        collectCanonicalAward(SkillMasteryRules.awardArtificingForSmelt(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillMasteryRules.awardCulinaryForSmelt(sp, profile, result), changed, leveledUp);

        if (ActivityRules.smeltItemRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.SmeltItemRule rule : ActivityRules.smeltItemRules()) {
            if (ArtificingXpRules.SKILL_ID.equals(rule.skill)
                    || CulinaryXpRules.SKILL_ID.equals(rule.skill)
                    || ForgingXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                collectCanonicalAward(profile.awardMastery(rule.skill, totalXp), changed, leveledUp);
            }
        }
        if (!changed.isEmpty()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            for (ResourceLocation id : changed) {
                Network.sendDelta(sp, profile, id);
                sendXpBar(sp, profile, id);
            }
            for (ResourceLocation id : leveledUp) {
                sendLevelUp(sp, profile, id);
            }
        }
    }

    private static void collectCanonicalAward(MasteryAwardResult result, Set<ResourceLocation> changedSkills, Set<ResourceLocation> leveledUp) {
        if (result == null) return;
        changedSkills.add(result.skillId());
        if (result.leveledUp()) {
            leveledUp.add(result.skillId());
        }
    }

    private static void sendXpBar(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        SkillState spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        long needed = net.zoogle.levelrpg.progression.MasteryLeveling.xpToNextLevel(skillId, spSkill.masteryLevel);
        String name = displayNameForSkill(skillId);
        sp.displayClientMessage(Component.literal(
                name + " mastery " + spSkill.masteryXp + "/" + needed + " (M" + spSkill.masteryLevel + ")"
        ).withStyle(ChatFormatting.GOLD), true);
    }

    private static void sendLevelUp(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        SkillState spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        String name = displayNameForSkill(skillId);
        sp.sendSystemMessage(Component.literal(
                name + " mastery reached " + spSkill.masteryLevel + ". Gained 1 Skill Point."
        ).withStyle(ChatFormatting.GREEN));
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
