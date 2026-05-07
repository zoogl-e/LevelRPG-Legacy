package net.zoogle.levelrpg.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProficiencyAwardResult;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.gauge.GaugeDefinition;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.gauge.GaugeModifiers;
import net.zoogle.levelrpg.gauge.DelvingMomentumGaugeModifiers;
import net.zoogle.levelrpg.finesse.FinessePassiveModifiers;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.MasteryLeveling;
import net.zoogle.levelrpg.progression.SkillPointProgression;
import net.zoogle.levelrpg.progression.DisciplineInvestmentProgression;
import net.zoogle.levelrpg.progression.DisciplineInvestmentSource;
import net.zoogle.levelrpg.skilltree.effect.PlayerSkillEffectQuery;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;
import net.zoogle.levelrpg.technique.PlayerTechniques;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;
import net.zoogle.levelrpg.registry.LevelRpgBlocks;
import net.zoogle.levelrpg.world.IndexPlacementData;
import net.zoogle.levelrpg.bounty.BountyService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LevelCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("levelrpg")
                        .requires(src -> src.hasPermission(2))
                        // --- Legacy command aliases (pre-terminology refresh); keep for scripts and muscle memory ---
                        .then(Commands.literal("givemastery")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> giveMastery(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> giveMastery(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // Legacy alias: givexp (same handler as givemastery)
                        .then(Commands.literal("givexp")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> giveMastery(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> giveMastery(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // Legacy alias: set (discipline invested level)
                        .then(Commands.literal("set")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSkill(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> setSkill(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // Legacy alias: get (discipline overview)
                        .then(Commands.literal("get")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .executes(ctx -> getSkill(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> getSkill(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        // --- Modern terminology: Discipline (invested level + overview) ---
                        .then(Commands.literal("discipline")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestSkills)
                                                .executes(ctx -> disciplineGet(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> disciplineGet(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestSkills)
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> disciplineSet(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> disciplineSet(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                                .then(Commands.literal("invest")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestSkills)
                                                .executes(ctx -> disciplineInvest(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> disciplineInvest(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // --- Modern terminology: Potential (practice rank / proficiency; design cap not split in saves yet) ---
                        .then(Commands.literal("potential")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestSkills)
                                                .executes(ctx -> potentialGet(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> potentialGet(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // --- Modern terminology: Practice (proficiency grant; same as givemastery/givexp) ---
                        .then(Commands.literal("practice")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestSkills)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> practiceAdd(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> practiceAdd(ctx, EntityArgument.getPlayer(ctx, "player"))))))))
                        // --- Modern terminology: Insight (tree unlock pool; global only in persistence) ---
                        .then(Commands.literal("insight")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("scope", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestInsightScopes)
                                                .executes(ctx -> insightGet(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> insightGet(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("add")
                                        .then(Commands.literal("global")
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> insightAddGlobal(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> insightAddGlobal(ctx, EntityArgument.getPlayer(ctx, "player"))))))))
                        .then(Commands.literal("essence")
                                .then(Commands.literal("get")
                                        .executes(ctx -> essenceGet(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> essenceGet(ctx, EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("give")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> essenceGive(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> essenceGive(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("take")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> essenceTake(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> essenceTake(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> essenceSet(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> essenceSet(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        // Legacy alias: spend (invest one skill point into discipline level)
                        .then(Commands.literal("spend")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .executes(ctx -> spendPoint(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> spendPoint(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        // Legacy alias: givepoints (available skill points / discipline investment pool)
                        .then(Commands.literal("givepoints")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveSkillPoints(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> giveSkillPoints(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        // Legacy alias: givespecialization (global tree Insight bonus pool)
                        .then(Commands.literal("givespecialization")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveSpecializationPoints(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> giveSpecializationPoints(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        // Legacy alias: givespec
                        .then(Commands.literal("givespec")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveSpecializationPoints(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> giveSpecializationPoints(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        // Legacy alias: unlock (inscribe tree node; spends global Insight)
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestTreeSkills)
                                        .then(Commands.argument("node", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestTreeNodes)
                                                .executes(ctx -> unlockNode(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> unlockNode(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        .then(Commands.literal("tree")
                                // Legacy alias: tree open
                                .then(Commands.literal("open")
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestTreeSkills)
                                                .executes(ctx -> openTree(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> openTree(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("editor")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestTreeSkills)
                                                .executes(ctx -> treeEditor(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> treeEditor(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("unlock")
                                        .then(Commands.argument("discipline", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestTreeSkills)
                                                .then(Commands.argument("node", StringArgumentType.word())
                                                        .suggests(LevelCommands::suggestTreeNodesDiscipline)
                                                        .executes(ctx -> treeUnlock(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> treeUnlock(ctx, EntityArgument.getPlayer(ctx, "player"))))))))
                        .then(Commands.literal("reloadinfo")
                                .executes(ctx -> reloadInfo(ctx)))
                        .then(Commands.literal("sync")
                                .executes(ctx -> sync(ctx, getCallingPlayer(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> sync(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("archetype")
                                .then(Commands.literal("unbind")
                                        .executes(ctx -> unbindArchetype(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> unbindArchetype(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("index")
                                .then(Commands.literal("locate")
                                        .executes(this::indexLocate)))
                        .then(Commands.literal("bounty")
                                .then(Commands.literal("active")
                                        .executes(ctx -> bountyActive(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> bountyActive(ctx, EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> bountyClear(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> bountyClear(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("effects")
                                .executes(ctx -> effects(ctx, getCallingPlayer(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> effects(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("technique")
                                .then(Commands.literal("list")
                                        .executes(ctx -> techniqueList(ctx)))
                                .then(Commands.literal("slots")
                                        .executes(ctx -> techniqueSlots(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> techniqueSlots(ctx, EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("assign")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, PlayerTechniqueData.SLOT_COUNT))
                                                .then(Commands.argument("technique", StringArgumentType.word())
                                                        .suggests(LevelCommands::suggestTechniques)
                                                        .executes(ctx -> techniqueAssign(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> techniqueAssign(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, PlayerTechniqueData.SLOT_COUNT))
                                                .executes(ctx -> techniqueClear(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> techniqueClear(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("cooldowns")
                                        .executes(ctx -> techniqueCooldowns(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> techniqueCooldowns(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("gauge")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("gauge", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestGauges)
                                                .executes(ctx -> gaugeGet(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> gaugeGet(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("gauge", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestGauges)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> gaugeSet(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> gaugeSet(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("gauge", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestGauges)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> gaugeAdd(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> gaugeAdd(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                                .then(Commands.literal("spend")
                                        .then(Commands.argument("gauge", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestGauges)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> gaugeSpend(ctx, getCallingPlayer(ctx)))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> gaugeSpend(ctx, EntityArgument.getPlayer(ctx, "player"))))))))
        );
    }

    private int giveMastery(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return giveProficiency(ctx, target, "skill");
    }

    private int practiceAdd(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return giveProficiency(ctx, target, "discipline");
    }

    /** Legacy {@code givemastery}/{@code givexp} and modern {@code practice add}: awards proficiency toward practice rank. */
    private int giveProficiency(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String disciplineArgName) {
        String raw = StringArgumentType.getString(ctx, disciplineArgName);
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        if (resolved.correctedFrom() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Autocorrected discipline id '" + raw + "' -> '" + skill + "'"), false);
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        ProficiencyAwardResult award = profile.awardProficiency(skill, amount);
        PassiveSkillScalingService.applyIfChanged(target, profile);
        LevelProfile.save(target, profile);
        Network.sendDelta(target, profile, skill);

        // Feedback to command source
        ctx.getSource().sendSuccess(() -> Component.literal("Granted " + amount + " practice (proficiency) to " + displayNameForSkill(skill) + " for " + target.getGameProfile().getName()), true);

        // Feedback to target: action bar with current/needed and level up message
        SkillState spSkill = profile.skills.get(skill);
        if (spSkill != null) {
            long needed = MasteryLeveling.xpToNextLevel(skill, spSkill.rank);
            String name = displayNameForSkill(skill);
            target.displayClientMessage(Component.literal(
                    name + " proficiency " + spSkill.proficiency + "/" + needed + " (practice rank " + spSkill.rank + ")"
            ).withStyle(ChatFormatting.GOLD), true);
            if (award.leveledUp()) {
                target.sendSystemMessage(Component.literal(
                    name + " practice ranked up to rank " + spSkill.rank + ". Potential increased."
                ).withStyle(ChatFormatting.GREEN));
            }
        }
        return (int) Math.max(1L, award.proficiencyAwarded());
    }

    private int disciplineSet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return setDisciplineLevel(ctx, target, StringArgumentType.getString(ctx, "discipline"));
    }

    private int setSkill(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return setDisciplineLevel(ctx, target, StringArgumentType.getString(ctx, "skill"));
    }

    private int setDisciplineLevel(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String raw) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        LevelProfile profile = LevelProfile.get(target);

        // Support special key: all
        if ("all".equalsIgnoreCase(raw)) {
            int clamped = Math.max(0, level);
            for (Map.Entry<ResourceLocation, SkillState> e : profile.skills.entrySet()) {
                SkillState sp = e.getValue();
                if (sp == null) {
                    sp = new SkillState();
                    profile.skills.put(e.getKey(), sp);
                }
                sp.level = clamped;
            }
            PassiveSkillScalingService.applyIfChanged(target, profile);
            LevelProfile.save(target, profile);
            Network.sendSync(target, profile);
            ctx.getSource().sendSuccess(() -> Component.literal("Set ALL disciplines for " + target.getGameProfile().getName() + " to Discipline Level " + clamped), true);
            return profile.skills.size();
        }

        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        if (resolved.correctedFrom() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Autocorrected discipline id '" + raw + "' -> '" + skill + "'"), false);
        }
        SkillState sp = profile.skills.get(skill);
        if (sp == null) {
            sp = new SkillState();
            profile.skills.put(skill, sp);
        }
        sp.level = Math.max(0, level);
        PassiveSkillScalingService.applyIfChanged(target, profile);
        LevelProfile.save(target, profile);
        Network.sendDelta(target, profile, skill);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().getName() + " " + skill + " Discipline Level to " + level), true);
        return 1;
    }

    private int dump(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal("Profile for " + target.getGameProfile().getName()), false);
        for (Map.Entry<ResourceLocation, SkillState> e : profile.skills.entrySet()) {
            SkillState sp = e.getValue();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    displayNameForSkill(e.getKey())
                            + " -> Discipline Level " + sp.level
                            + ", practice rank " + sp.rank
                            + " (" + sp.proficiency + "/" + MasteryLeveling.xpToNextLevel(e.getKey(), sp.rank) + ")"
                            + ", Essence " + profile.essence()
            ), false);
        }
        return profile.skills.size();
    }

    private int disciplineGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return getDisciplineOverview(ctx, target, StringArgumentType.getString(ctx, "discipline"));
    }

    private int getSkill(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return getDisciplineOverview(ctx, target, StringArgumentType.getString(ctx, "skill"));
    }

    private int getDisciplineOverview(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String raw) {
        if ("all".equalsIgnoreCase(raw)) {
            return dump(ctx, target);
        }
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        LevelProfile profile = LevelProfile.get(target);
        SkillState sp = profile.skills.get(skill);
        int level = sp != null ? sp.level : 0;
        int rank = sp != null ? sp.rank : 0;
        long proficiency = sp != null ? sp.proficiency : 0L;
        long needed = MasteryLeveling.xpToNextLevel(skill, rank);
        String name = displayNameForSkill(skill);
        String who = target.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.literal(
                who + " " + name
                        + ": Discipline Level " + level
                        + ", practice rank " + rank
                        + " (" + proficiency + "/" + needed + ")"
                        + ", Essence " + profile.essence()
        ), false);
        return 1;
    }

    private int disciplineInvest(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return spendDisciplinePoint(ctx, target, StringArgumentType.getString(ctx, "discipline"));
    }

    private int spendPoint(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return spendDisciplinePoint(ctx, target, StringArgumentType.getString(ctx, "skill"));
    }

    private int spendDisciplinePoint(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String raw) {
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        LevelProfile profile = LevelProfile.get(target);
        DisciplineInvestmentProgression.InvestmentResult result = profile.spendEssenceForDisciplineLevel(
                skill,
                DisciplineInvestmentSource.COMMAND
        );
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        PassiveSkillScalingService.applyIfChanged(target, profile);
        LevelProfile.save(target, profile);
        Network.sendDelta(target, profile, skill);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Spent " + result.essenceSpent() + " Essence in " + displayNameForSkill(skill) + " for " + target.getGameProfile().getName()
                        + " -> Discipline Level " + result.resultingDisciplineLevel()
                        + ", Essence " + result.essenceRemaining() + " remaining"
        ), true);
        return 1;
    }

    private int potentialGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String raw = StringArgumentType.getString(ctx, "discipline");
        LevelProfile profile = LevelProfile.get(target);
        if ("all".equalsIgnoreCase(raw)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Practice track (maps toward design Potential; no separate Potential cap in player data yet):"
            ), false);
            int n = 0;
            for (ProgressionSkill ps : ProgressionSkill.values()) {
                ResourceLocation id = ps.id();
                SkillState sp = profile.getSkill(id);
                long need = MasteryLeveling.xpToNextLevel(id, sp.rank);
                String name = displayNameForSkill(id);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        name + ": practice rank " + sp.rank + ", proficiency " + sp.proficiency + "/" + need
                ), false);
                n++;
            }
            return n;
        }
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        SkillState sp = profile.getSkill(skill);
        long need = MasteryLeveling.xpToNextLevel(skill, sp.rank);
        String name = displayNameForSkill(skill);
        ctx.getSource().sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + " " + name
                        + " practice track: rank " + sp.rank
                        + ", proficiency " + sp.proficiency + "/" + need
                        + " (Potential cap not yet persisted separately.)"
        ), false);
        return 1;
    }

    private int insightGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String scope = StringArgumentType.getString(ctx, "scope").trim();
        LevelProfile profile = LevelProfile.get(target);
        int earned = profile.globalInsightEarned();
        int spent = profile.globalInsightInscribed();
        int available = profile.globalInsightAvailable();
        if ("global".equalsIgnoreCase(scope)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Global tree Insight for " + target.getGameProfile().getName()
                            + ": available " + available + ", earned " + earned + ", inscribed " + spent
                            + ", bonus Insight grants " + profile.bonusSpecializationPoints
            ), false);
            return 1;
        }
        if ("all".equalsIgnoreCase(scope)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Global Insight: available " + available + " / earned " + earned + " / inscribed " + spent
                            + " (one pool shared by all discipline trees)"
            ), false);
            for (ProgressionSkill ps : ProgressionSkill.values()) {
                ResourceLocation id = ps.id();
                int inscribedHere = profile.getTreePointsSpent(id);
                int unlocked = profile.getUnlockedTreeNodes(id).size();
                ctx.getSource().sendSuccess(() -> Component.literal(
                        displayNameForSkill(id) + ": inscribed " + inscribedHere + " pts, " + unlocked + " node(s)"
                ), false);
            }
            return ProgressionSkill.values().length + 1;
        }
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(scope);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown scope or discipline: " + scope + " (use global, all, or a discipline id)"));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        int inscribedHere = profile.getTreePointsSpent(skill);
        ctx.getSource().sendSuccess(() -> Component.literal(
                displayNameForSkill(skill) + " tree: inscribed " + inscribedHere
                        + " Insight pts on this tree; global pool still available " + available + "/" + earned + " (shared)"
        ), false);
        return 1;
    }

    private int insightAddGlobal(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return giveSpecializationPoints(ctx, target);
    }

    private int essenceGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Essence for " + target.getGameProfile().getName() + ": " + profile.essence()
        ), false);
        return profile.essence();
    }

    private int essenceGive(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        profile.grantEssence(amount);
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Granted " + amount + " Essence to " + target.getGameProfile().getName()
                        + ". Current Essence: " + profile.essence()
        ), true);
        return amount;
    }

    private int essenceTake(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        if (!profile.canSpendEssence(amount)) {
            ctx.getSource().sendFailure(Component.literal(
                    target.getGameProfile().getName() + " only has " + profile.essence() + " Essence"
            ));
            return 0;
        }
        profile.takeEssence(amount);
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Took " + amount + " Essence from " + target.getGameProfile().getName()
                        + ". Current Essence: " + profile.essence()
        ), true);
        return amount;
    }

    private int essenceSet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        profile.setEssence(amount);
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set Essence for " + target.getGameProfile().getName()
                        + " to " + profile.essence()
        ), true);
        return profile.essence();
    }

    private int treeEditor(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return openTreeEditor(ctx, target, StringArgumentType.getString(ctx, "discipline"));
    }

    private int treeUnlock(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return unlockTreeNodes(ctx, target, StringArgumentType.getString(ctx, "discipline"), StringArgumentType.getString(ctx, "node"));
    }

    private int giveSkillPoints(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        SkillPointProgression.grantPoint(profile, amount);
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        int available = profile.uncommittedDisciplineInvestmentPoints();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Granted " + amount + " legacy discipline investment point(s) to " + target.getGameProfile().getName()
                        + ". Legacy pool now: " + available + " (deprecated; normal discipline investment uses Essence)"
        ), true);
        if (target != ctx.getSource().getEntity()) {
            target.displayClientMessage(Component.literal(
                    "You received " + amount + " legacy discipline investment point(s). Legacy pool: " + available
            ).withStyle(ChatFormatting.GREEN), false);
        }
        return amount;
    }

    private int giveSpecializationPoints(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        profile.bonusSpecializationPoints = Math.max(0, profile.bonusSpecializationPoints + amount);
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        int earned = profile.globalInsightEarned();
        int spent = profile.globalInsightInscribed();
        int available = profile.globalInsightAvailable();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Granted " + amount + " Insight to " + target.getGameProfile().getName()
                        + ". Available: " + available
                        + " / Earned: " + earned
                        + " / Inscribed: " + spent
        ), true);
        if (target != ctx.getSource().getEntity()) {
            target.displayClientMessage(Component.literal(
                    "You received " + amount + " Insight. Available: " + available
            ).withStyle(ChatFormatting.GREEN), false);
        }
        return amount;
    }

    private int sync(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile.save(target, LevelProfile.get(target));
        Network.sendSync(target, LevelProfile.get(target));
        ctx.getSource().sendSuccess(() -> Component.literal("Profile saved for " + target.getGameProfile().getName()), true);
        if (target != ctx.getSource().getEntity()) {
            target.displayClientMessage(Component.literal("Your LevelRPG profile was saved."), false);
        }
        return 1;
    }

    private int effects(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        var active = PlayerSkillEffectQuery.activeEffects(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Active LevelRPG discipline tree node effects for " + target.getGameProfile().getName() + ": " + active.size()
        ), false);
        for (PlayerSkillEffectQuery.ActiveSkillNodeEffect effect : active) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "- " + effect.effect().id()
                            + " [" + effect.effect().type() + "]"
                            + " from " + effect.skillId() + "#" + effect.nodeId()
            ), false);
        }
        return active.size();
    }

    private int unbindArchetype(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        boolean changed = profile.clearArchetypeBinding();
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        if (changed) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Cleared archetype binding for " + target.getGameProfile().getName()
            ), true);
            if (target != ctx.getSource().getEntity()) {
                target.displayClientMessage(Component.literal("Your archetype binding was cleared."), true);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    target.getGameProfile().getName() + " had no archetype binding to clear."
            ), false);
        }
        return 1;
    }

    private int indexLocate(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getServer() == null) {
            ctx.getSource().sendFailure(Component.literal("Could not resolve server/Overworld."));
            return 0;
        }
        ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            ctx.getSource().sendFailure(Component.literal("Could not resolve server/Overworld."));
            return 0;
        }

        IndexPlacementData data = IndexPlacementData.get(overworld);
        if (!data.placed() || data.originalIndexPos() == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("No original Index has been placed yet."), false);
            return 0;
        }

        var pos = data.originalIndexPos();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Original Index: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
        ), false);

        BlockState stateAtSavedPos = overworld.getBlockState(pos);
        if (!stateAtSavedPos.is(LevelRpgBlocks.THE_INDEX.get())) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Saved original Index position exists, but the block is missing or changed."
            ), false);
        }
        return 1;
    }

    private int bountyClear(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        if (!profile.hasActiveSoloBounty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    target.getGameProfile().getName() + " has no active solo bounty."
            ), false);
            return 0;
        }
        ResourceLocation previous = profile.activeSoloBountyId();
        profile.clearActiveSoloBounty();
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cleared active solo bounty for " + target.getGameProfile().getName() + ": " + previous
        ), true);
        return 1;
    }

    private int bountyActive(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        if (!profile.hasActiveSoloBounty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No active solo Bounty."), false);
            return 0;
        }

        ResourceLocation bountyId = profile.activeSoloBountyId();
        var definition = BountyService.get(bountyId);
        ctx.getSource().sendSuccess(() -> Component.literal("Active bounty id: " + bountyId), false);
        if (definition != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Bounty title: " + definition.title()), false);
            var spec = definition.objectiveSpec();
            ctx.getSource().sendSuccess(() -> Component.literal("Objective type: " + spec.type()), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Objective implemented: " + (definition.objectiveImplemented() ? "yes" : "no")
            ), false);
            switch (spec.type()) {
                case REACH_Y -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Objective: reach Y <= " + spec.targetY()
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Current Y: " + target.blockPosition().getY()
                    ), false);
                }
                case INDEX_INVEST_ONCE -> ctx.getSource().sendSuccess(() -> Component.literal(
                        "Objective: invest at The Index"
                ), false);
                case KILL_HOSTILE_MOB -> ctx.getSource().sendSuccess(() -> Component.literal(
                        "Objective: slay hostile creatures (" + profile.activeSoloBountyProgress() + "/" + Math.max(1, spec.count()) + ")"
                ), false);
                case MINE_ORE -> {
                    int maxY = spec.targetY();
                    String depth = maxY > 0 ? " at Y " + maxY + " or below" : "";
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Objective: mine ore" + depth + " (" + profile.activeSoloBountyProgress() + "/" + Math.max(1, spec.count()) + ")"
                    ), false);
                }
                default -> {
                }
            }
            boolean completed = profile.hasCompletedBounty(bountyId);
            String bonusStatus = completed
                    ? "First-completion bonus: claimed"
                    : "First-completion bonus: available (+" + definition.firstCompletionBonusEssence() + " Essence)";
            ctx.getSource().sendSuccess(() -> Component.literal(bonusStatus), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("Active bounty definition is missing."), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Objective met: " + profile.activeSoloBountyObjectiveMet()
        ), false);
        return 1;
    }

    private int techniqueList(CommandContext<CommandSourceStack> ctx) {
        for (TechniqueDefinition technique : TechniqueRegistry.entries()) {
            String cost = technique.cost().hasGaugeCost()
                    ? ", cost " + round(technique.cost().amount()) + " " + technique.cost().gaugeId().getPath()
                    : "";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    technique.id() + " - " + technique.displayName()
                            + " [discipline tree " + technique.requiredSkillId() + "#" + technique.requiredNodeId()
                            + ", cooldown " + technique.cooldownTicks() + " ticks"
                            + cost + "]"
            ), false);
        }
        return TechniqueRegistry.entries().size();
    }

    private int techniqueSlots(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        ResourceLocation[] slots = profile.techniques.slotsCopy();
        ctx.getSource().sendSuccess(() -> Component.literal("Technique slots for " + target.getGameProfile().getName()), false);
        for (int i = 0; i < slots.length; i++) {
            ResourceLocation id = slots[i];
            TechniqueDefinition technique = TechniqueRegistry.get(id);
            String label = technique == null ? "empty" : technique.displayName() + " (" + id + ")";
            int slot = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal(slot + ": " + label), false);
        }
        return slots.length;
    }

    private int techniqueAssign(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        ResourceLocation techniqueId = TechniqueRegistry.resolve(StringArgumentType.getString(ctx, "technique"));
        if (techniqueId == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown technique"));
            return 0;
        }
        LevelProfile profile = LevelProfile.get(target);
        var result = PlayerTechniques.assign(target, profile, slot, techniqueId);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result.message() + " for " + target.getGameProfile().getName()), true);
        return 1;
    }

    private int techniqueClear(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        PlayerTechniques.clear(target, LevelProfile.get(target), slot);
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared technique slot " + slot + " for " + target.getGameProfile().getName()), true);
        return 1;
    }

    private int techniqueCooldowns(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal("Technique cooldowns for " + target.getGameProfile().getName()), false);
        int count = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : profile.techniques.cooldownsView().entrySet()) {
            TechniqueDefinition technique = TechniqueRegistry.get(entry.getKey());
            String name = technique == null ? entry.getKey().toString() : technique.displayName();
            ctx.getSource().sendSuccess(() -> Component.literal(name + ": " + entry.getValue() + " ticks"), false);
            count++;
        }
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No active cooldowns"), false);
        }
        return count;
    }

    private int gaugeGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation gaugeId = resolveGauge(ctx);
        if (gaugeId == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gauge"));
            return 0;
        }
        LevelProfile profile = LevelProfile.get(target);
        double value = profile.gauges.getValue(gaugeId);
        double max = profile.gauges.getMax(target, profile, gaugeId);
        GaugeDefinition gauge = GaugeRegistry.get(gaugeId);
        double decay = gauge == null ? 0.0 : GaugeModifiers.computeDecayPerSecond(target, profile, gauge);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " " + gaugeId + ": " + round(value) + "/" + round(max)), false);
        if (gauge != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Base max: " + round(gauge.defaultMax()) + ", computed max: " + round(max) + ", decay/sec: " + round(decay)
            ), false);
            if (GaugeRegistry.MOMENTUM.equals(gaugeId)) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Momentum modifiers: overdrive=" + hasDelving(profile, "overdrive")
                                + ", stone_reservoir=" + hasDelving(profile, "stone_reservoir")
                                + ", momentum_reservoir=" + hasDelving(profile, "momentum_reservoir")
                                + ", deep_delver=" + hasDelving(profile, "deep_delver")
                                + ", momentum_core=" + hasDelving(profile, "momentum_core")
                ), false);
            }
        }
        return 1;
    }

    private int gaugeSet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation gaugeId = resolveGauge(ctx);
        if (gaugeId == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gauge"));
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        PlayerGauges.set(target, gaugeId, amount);
        if (GaugeRegistry.RHYTHM.equals(gaugeId)) {
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(target, LevelProfile.get(target));
        }
        return gaugeGet(ctx, target);
    }

    private int gaugeAdd(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation gaugeId = resolveGauge(ctx);
        if (gaugeId == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gauge"));
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        PlayerGauges.add(target, gaugeId, amount);
        if (GaugeRegistry.RHYTHM.equals(gaugeId)) {
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(target, LevelProfile.get(target));
        }
        return gaugeGet(ctx, target);
    }

    private int gaugeSpend(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ResourceLocation gaugeId = resolveGauge(ctx);
        if (gaugeId == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gauge"));
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        if (!PlayerGauges.spend(target, gaugeId, amount)) {
            ctx.getSource().sendFailure(Component.literal("Not enough " + gaugeId));
            return 0;
        }
        if (GaugeRegistry.RHYTHM.equals(gaugeId)) {
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(target, LevelProfile.get(target));
        }
        return gaugeGet(ctx, target);
    }

    private static ServerPlayer getCallingPlayer(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private int reloadInfo(CommandContext<CommandSourceStack> ctx) {
        int skills = net.zoogle.levelrpg.data.SkillRegistry.size();
        int curves = net.zoogle.levelrpg.data.XpCurves.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Loaded discipline catalog entries: " + skills + ", XP curves: " + curves), false);
        int listed = 0;
        for (Map.Entry<ResourceLocation, net.zoogle.levelrpg.data.SkillDefinition> e : net.zoogle.levelrpg.data.SkillRegistry.entries()) {
            if (listed >= 6) break;
            String name = (e.getValue().display() != null && e.getValue().display().name() != null && !e.getValue().display().name().isEmpty())
                    ? e.getValue().display().name()
                    : e.getKey().toString();
            String line = "- " + e.getKey() + " (" + name + ")";
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            listed++;
        }
        return 1;
    }

    // Suggest plain paths for our namespace (levelrpg) and full ids for external namespaces to avoid duplicates
    public static CompletableFuture<Suggestions> suggestSkills(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> options = new java.util.LinkedHashSet<>();
        options.add("all");
        for (ResourceLocation id : net.zoogle.levelrpg.data.SkillRegistry.ids()) {
            if (id.getNamespace().equals(net.zoogle.levelrpg.LevelRPG.MODID)) {
                options.add(id.getPath());
            } else {
                options.add(id.toString());
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Skill tree suggestions: only skills that have a tree loaded
    public static CompletableFuture<Suggestions> suggestTreeSkills(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> options = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : net.zoogle.levelrpg.data.SkillTreeRegistry.ids()) {
            if (id.getNamespace().equals(net.zoogle.levelrpg.LevelRPG.MODID)) {
                options.add(id.getPath());
            } else {
                options.add(id.toString());
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    public static CompletableFuture<Suggestions> suggestTreeNodes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestTreeNodesForDisciplineArg(ctx, builder, "skill");
    }

    public static CompletableFuture<Suggestions> suggestTreeNodesDiscipline(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestTreeNodesForDisciplineArg(ctx, builder, "discipline");
    }

    private static CompletableFuture<Suggestions> suggestTreeNodesForDisciplineArg(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder,
            String disciplineArgName
    ) {
        java.util.LinkedHashSet<String> options = new java.util.LinkedHashSet<>();
        try {
            String raw = StringArgumentType.getString(ctx, disciplineArgName);
            var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
            if (resolved != null) {
                ResourceLocation skill = resolved.id();
                for (String n : net.zoogle.levelrpg.data.SkillTreeRegistry.nodeIds(skill)) options.add(n);
            }
        } catch (Exception ignored) {
        }
        if (options.isEmpty()) {
            // Fallback: union of all nodes
            for (ResourceLocation id : net.zoogle.levelrpg.data.SkillTreeRegistry.ids()) {
                for (String n : net.zoogle.levelrpg.data.SkillTreeRegistry.nodeIds(id)) options.add(n);
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    /** Suggestions for {@code /levelrpg insight get <scope>}: global pool, all trees summary, or a discipline id. */
    public static CompletableFuture<Suggestions> suggestInsightScopes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        java.util.LinkedHashSet<String> options = new java.util.LinkedHashSet<>();
        options.add("global");
        options.add("all");
        for (ResourceLocation id : net.zoogle.levelrpg.data.SkillRegistry.ids()) {
            if (id.getNamespace().equals(net.zoogle.levelrpg.LevelRPG.MODID)) {
                options.add(id.getPath());
            } else {
                options.add(id.toString());
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    public static CompletableFuture<Suggestions> suggestGauges(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> options = new java.util.LinkedHashSet<>();
        for (GaugeDefinition gauge : GaugeRegistry.entries()) {
            if (gauge.id().getNamespace().equals(net.zoogle.levelrpg.LevelRPG.MODID)) {
                options.add(gauge.id().getPath());
            } else {
                options.add(gauge.id().toString());
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    public static CompletableFuture<Suggestions> suggestTechniques(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> options = new java.util.LinkedHashSet<>();
        for (TechniqueDefinition technique : TechniqueRegistry.entries()) {
            if (technique.id().getNamespace().equals(net.zoogle.levelrpg.LevelRPG.MODID)) {
                options.add(technique.id().getPath());
            } else {
                options.add(technique.id().toString());
            }
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static ResourceLocation resolveGauge(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "gauge");
        ResourceLocation id = raw.indexOf(':') >= 0
                ? ResourceLocation.parse(raw)
                : ResourceLocation.fromNamespaceAndPath(net.zoogle.levelrpg.LevelRPG.MODID, raw);
        return GaugeRegistry.get(id) == null ? null : id;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private int unlockNode(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return unlockTreeNodes(ctx, target, StringArgumentType.getString(ctx, "skill"), StringArgumentType.getString(ctx, "node"));
    }

    private int unlockTreeNodes(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String raw, String nodeId) {
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        var tree = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skill);
        if (tree == null) {
            ctx.getSource().sendFailure(Component.literal("No discipline tree for this id"));
            return 0;
        }
        if (tree.nodes() == null || !tree.nodes().containsKey(nodeId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown node: " + nodeId));
            return 0;
        }
        LevelProfile profile = LevelProfile.get(target);

        ArrayList<String> unlockOrder = new ArrayList<>();
        String planFailure = buildUnlockOrder(
                tree,
                nodeId,
                profile.getUnlockedTreeNodes(skill),
                new HashSet<>(),
                new HashSet<>(),
                unlockOrder
        );
        if (planFailure != null) {
            ctx.getSource().sendFailure(Component.literal(planFailure));
            return 0;
        }
        if (unlockOrder.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("'" + nodeId + "' is already unlocked"));
            return 0;
        }

        ArrayList<String> unlockedNow = new ArrayList<>();
        LevelProfile.UnlockResult lastResult = null;
        for (String plannedNodeId : unlockOrder) {
            LevelProfile.UnlockResult res = profile.unlockNode(skill, plannedNodeId);
            if (!res.success) {
                String detail = "Unlock failed at '" + plannedNodeId + "': " + res.message
                        + " [Discipline Level " + res.rank
                        + ", Insight " + res.insight + "/" + res.gainedInsight + "]";
                if (res.suggestedNextNodeId != null && !res.suggestedNextNodeId.isBlank()) {
                    detail += " Next: " + res.suggestedNextNodeId;
                }
                ctx.getSource().sendFailure(Component.literal(detail));
                return 0;
            }
            unlockedNow.add(plannedNodeId);
            lastResult = res;
        }
        if (lastResult == null) {
            ctx.getSource().sendFailure(Component.literal("Unlock failed"));
            return 0;
        }

        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        PlayerGauges.syncAll(target, profile);
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(target, profile);
        FinessePassiveModifiers.apply(target, profile);
        String name = displayNameForSkill(skill);
        String detail = "Inscribed " + unlockedNow.size() + " node(s) in " + name + " for " + target.getGameProfile().getName()
                + ": " + String.join(", ", unlockedNow)
                + " [Insight " + lastResult.insight + "/" + lastResult.gainedInsight + " remaining]";
        ctx.getSource().sendSuccess(() -> Component.literal(detail), true);
        target.displayClientMessage(Component.literal("Inscribed '" + nodeId + "' in " + name), true);
        return unlockedNow.size();
    }

    private int openTree(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return openTreeEditor(ctx, target, StringArgumentType.getString(ctx, "skill"));
    }

    private int openTreeEditor(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String raw) {
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown discipline: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        var tree = net.zoogle.levelrpg.skilltree.SkillTreeRegistry.get(skill);
        if (tree == null) {
            ctx.getSource().sendFailure(Component.literal("No discipline tree registered for " + skill));
            return 0;
        }
        Network.openSkillTreeEditorScreen(target, skill);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Opening " + displayNameForSkill(skill) + " discipline tree for " + target.getGameProfile().getName()
        ), true);
        return 1;
    }

    private static String buildUnlockOrder(
            net.zoogle.levelrpg.data.SkillTreeCanonicalDefinition tree,
            String nodeId,
            Set<String> alreadyUnlocked,
            Set<String> visiting,
            Set<String> planned,
            ArrayList<String> unlockOrder
    ) {
        if (alreadyUnlocked.contains(nodeId) || planned.contains(nodeId)) {
            return null;
        }
        var node = tree.nodes().get(nodeId);
        if (node == null) {
            return "Unknown prerequisite node: " + nodeId;
        }
        if (!visiting.add(nodeId)) {
            return "Cycle in discipline tree prerequisites at: " + nodeId;
        }
        for (String prerequisiteId : node.requires()) {
            String failure = buildUnlockOrder(tree, prerequisiteId, alreadyUnlocked, visiting, planned, unlockOrder);
            if (failure != null) {
                return failure;
            }
        }
        visiting.remove(nodeId);
        planned.add(nodeId);
        unlockOrder.add(nodeId);
        return null;
    }

    private static boolean hasDelving(LevelProfile profile, String nodeId) {
        return net.zoogle.levelrpg.skilltree.SkillUnlockQuery.hasNode(profile, DelvingMomentumGaugeModifiers.DELVING, nodeId);
    }
}
