package net.zoogle.levelrpg.events;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
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
import net.zoogle.levelrpg.profile.ProficiencyAwardResult;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.ArtificingXpRules;
import net.zoogle.levelrpg.progression.CulinaryXpRules;
import net.zoogle.levelrpg.progression.ForgingXpRules;
import net.zoogle.levelrpg.progression.MagickXpRules;
import net.zoogle.levelrpg.progression.MiningXpRules;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.SkillProficiencyRules;
import net.zoogle.levelrpg.progression.AdvancementEssenceRewards;
import net.zoogle.levelrpg.progression.ValorXpRules;
import net.zoogle.levelrpg.bounty.BountyProgressionService;

import java.util.HashSet;
import java.util.Set;

/**
 * Gameplay hooks that consult data-driven rules to award proficiency.
 */
public class ActivityEvents {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        LevelProfile profile = LevelProfile.get(sp);
        BountyProgressionService.CompletionResult bountyCompletion =
                BountyProgressionService.evaluateActiveSoloBountyDepthProgress(sp, profile);
        if (bountyCompletion.completed()) {
            LevelProfile.save(sp, profile);
            Network.sendSync(sp, profile);
            String rewardLine = bountyCompletion.firstCompletion()
                    ? bountyCompletion.baseRewardEssence() + " Essence (+" + bountyCompletion.firstCompletionBonusEssence() + " first inscription bonus)"
                    : bountyCompletion.totalRewardEssence() + " Essence";
            sp.displayClientMessage(
                    Component.literal("The Bookmark exhales. Bounty complete. +"
                            + rewardLine + ".")
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        }
        ProficiencyAwardResult arcanaAward = SkillProficiencyRules.awardArcanaForOpenMenu(sp, profile);
        if (arcanaAward != null) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            Network.sendDelta(sp, profile, arcanaAward.skillId());
            sendXpBar(sp, profile, arcanaAward.skillId());
            if (arcanaAward.leveledUp()) {
                sendLevelUp(sp, profile, arcanaAward.skillId());
            }
        }

        ProficiencyAwardResult finesseAward = SkillProficiencyRules.awardFinesseForMovement(sp, profile);
        if (finesseAward == null) return;

        PassiveSkillScalingService.applyIfChanged(sp, profile);
        LevelProfile.save(sp, profile);
        Network.sendDelta(sp, profile, finesseAward.skillId());
        sendXpBar(sp, profile, finesseAward.skillId());
        if (finesseAward.leveledUp()) {
            sendLevelUp(sp, profile, finesseAward.skillId());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        var state = event.getState();

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changedSkills = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        ProficiencyAwardResult delvingAward = SkillProficiencyRules.awardDelvingForBlockBreak(sp, profile, state);
        collectCanonicalAward(delvingAward, changedSkills, leveledUp);

        BountyProgressionService.ObjectiveHookResult bountyMine =
                BountyProgressionService.onOreBlockBrokenByPlayer(sp, profile, state, event.getPos());

        if (ActivityRules.breakBlockRules().isEmpty() && changedSkills.isEmpty() && !bountyMine.needsProfileSync()) {
            return;
        }

        for (ActivityRules.BreakBlockRule rule : ActivityRules.breakBlockRules()) {
            if (MiningXpRules.SKILL_ID.equals(rule.skill) || ArtificingXpRules.SKILL_ID.equals(rule.skill)) continue;
            TagKey<net.minecraft.world.level.block.Block> tag = rule.tagKey();
            if (state.is(tag)) {
                collectCanonicalAward(profile.awardProficiency(rule.skill, rule.xp), changedSkills, leveledUp);
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
        if (bountyMine.needsProfileSync()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            Network.sendSync(sp, profile);
            notifyBountyCompletionIfNeeded(sp, bountyMine);
        }
    }

    @SubscribeEvent
    public void onMobDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer spTarget) {
            LevelProfile profile = LevelProfile.get(spTarget);
            ProficiencyAwardResult valorGritAward = SkillProficiencyRules.awardValorGritForDamageTaken(spTarget, profile, event.getSource(), event.getNewDamage());
            if (valorGritAward != null) {
                PassiveSkillScalingService.applyIfChanged(spTarget, profile);
                LevelProfile.save(spTarget, profile);
                Network.sendDelta(spTarget, profile, valorGritAward.skillId());
                sendXpBar(spTarget, profile, valorGritAward.skillId());
                if (valorGritAward.leveledUp()) {
                    sendLevelUp(spTarget, profile, valorGritAward.skillId());
                }
            }
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity target)) return;

        LevelProfile profile = LevelProfile.get(sp);
        ProficiencyAwardResult valorAward = SkillProficiencyRules.awardValorForDamage(sp, profile, target, event.getNewDamage());
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

        ProficiencyAwardResult valorAward = SkillProficiencyRules.awardValorForKill(sp, profile, victim);
        collectCanonicalAward(valorAward, changed, leveledUp);

        if (ActivityRules.killEntityRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.KillEntityRule rule : ActivityRules.killEntityRules()) {
            if (ValorXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (!type.is(rule.entityTagKey())) continue;
            if (rule.weaponItemTag != null) {
                TagKey<net.minecraft.world.item.Item> wTag = rule.weaponTagKey();
                if (!held.is(wTag)) continue;
            }
            collectCanonicalAward(profile.awardProficiency(rule.skill, rule.xp), changed, leveledUp);
        }
        BountyProgressionService.ObjectiveHookResult bountyKill =
                BountyProgressionService.onHostileMobKilledByPlayer(sp, profile, victim);
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
        if (bountyKill.needsProfileSync()) {
            PassiveSkillScalingService.applyIfChanged(sp, profile);
            LevelProfile.save(sp, profile);
            Network.sendSync(sp, profile);
            notifyBountyCompletionIfNeeded(sp, bountyKill);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();

        collectCanonicalAward(SkillProficiencyRules.awardArtificingForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillProficiencyRules.awardArcanaForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillProficiencyRules.awardHearthForCraft(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillProficiencyRules.awardForgingForCraft(sp, profile, result), changed, leveledUp);

        if (ActivityRules.craftItemRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.CraftItemRule rule : ActivityRules.craftItemRules()) {
            if (ArtificingXpRules.SKILL_ID.equals(rule.skill)
                    || MagickXpRules.SKILL_ID.equals(rule.skill)
                    || CulinaryXpRules.SKILL_ID.equals(rule.skill)
                    || ForgingXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                collectCanonicalAward(profile.awardProficiency(rule.skill, totalXp), changed, leveledUp);
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

        collectCanonicalAward(SkillProficiencyRules.awardArtificingForSmelt(sp, profile, result), changed, leveledUp);
        collectCanonicalAward(SkillProficiencyRules.awardHearthForSmelt(sp, profile, result), changed, leveledUp);

        if (ActivityRules.smeltItemRules().isEmpty() && changed.isEmpty()) return;

        for (ActivityRules.SmeltItemRule rule : ActivityRules.smeltItemRules()) {
            if (ArtificingXpRules.SKILL_ID.equals(rule.skill)
                    || CulinaryXpRules.SKILL_ID.equals(rule.skill)
                    || ForgingXpRules.SKILL_ID.equals(rule.skill)) continue;
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                collectCanonicalAward(profile.awardProficiency(rule.skill, totalXp), changed, leveledUp);
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
    public void onAdvancementEarned(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AdvancementHolder advancement = event.getAdvancement();
        if (advancement == null || advancement.id() == null) return;

        LevelProfile profile = LevelProfile.get(sp);
        AdvancementEssenceRewards.Result result = AdvancementEssenceRewards.claim(profile, advancement);
        if (!result.awarded()) {
            return;
        }

        LevelProfile.save(sp, profile);
        Network.sendSync(sp, profile);
        sp.displayClientMessage(Component.literal(frameFlavorLine(advancement, result.essenceAwarded()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void notifyBountyCompletionIfNeeded(ServerPlayer sp, BountyProgressionService.ObjectiveHookResult hook) {
        if (hook.completion() == null || !hook.completion().completed()) {
            return;
        }
        BountyProgressionService.CompletionResult bountyCompletion = hook.completion();
        String rewardLine = bountyCompletion.firstCompletion()
                ? bountyCompletion.baseRewardEssence() + " Essence (+" + bountyCompletion.firstCompletionBonusEssence() + " first inscription bonus)"
                : bountyCompletion.totalRewardEssence() + " Essence";
        sp.displayClientMessage(
                Component.literal("The Bookmark exhales. Bounty complete. +"
                        + rewardLine + ".")
                        .withStyle(ChatFormatting.AQUA),
                true
        );
    }

    private static void collectCanonicalAward(ProficiencyAwardResult result, Set<ResourceLocation> changedSkills, Set<ResourceLocation> leveledUp) {
        if (result == null) return;
        changedSkills.add(result.skillId());
        if (result.leveledUp()) {
            leveledUp.add(result.skillId());
        }
    }

    private static void sendXpBar(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        SkillState spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        long needed = net.zoogle.levelrpg.progression.MasteryLeveling.xpToNextLevel(skillId, spSkill.rank);
        String name = displayNameForSkill(skillId);
        sp.displayClientMessage(Component.literal(
                name + " proficiency " + spSkill.proficiency + "/" + needed + " (practice rank " + spSkill.rank + ")"
        ).withStyle(ChatFormatting.GOLD), true);
    }

    private static void sendLevelUp(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        SkillState spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        String name = displayNameForSkill(skillId);
        sp.sendSystemMessage(Component.literal(
                name + " practice ranked up to rank " + spSkill.rank + ". Potential increased."
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

    private static String frameFlavorLine(AdvancementHolder advancement, int amount) {
        AdvancementType frameType = advancement.value().display().map(display -> display.getType()).orElse(null);
        if (frameType == AdvancementType.CHALLENGE) {
            return "Challenge inscribed. +" + amount + " Essence.";
        }
        if (frameType == AdvancementType.GOAL) {
            return "Milestone recorded. +" + amount + " Essence.";
        }
        return "The Bookmark stirs. +" + amount + " Essence.";
    }
}
