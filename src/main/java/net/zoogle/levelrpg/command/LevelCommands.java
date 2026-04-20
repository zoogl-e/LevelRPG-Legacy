package net.zoogle.levelrpg.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.profile.SkillXpResult;
import net.zoogle.levelrpg.net.Network;

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
                        .then(Commands.literal("givexp")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> giveXp(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> giveXp(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSkill(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> setSkill(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestSkills)
                                        .executes(ctx -> getSkill(ctx, getCallingPlayer(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> getSkill(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(LevelCommands::suggestTreeSkills)
                                        .then(Commands.argument("node", StringArgumentType.word())
                                                .suggests(LevelCommands::suggestTreeNodes)
                                                .executes(ctx -> unlockNode(ctx, getCallingPlayer(ctx)))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> unlockNode(ctx, EntityArgument.getPlayer(ctx, "player")))))))
                        .then(Commands.literal("reloadinfo")
                                .executes(ctx -> reloadInfo(ctx)))
                        .then(Commands.literal("sync")
                                .executes(ctx -> sync(ctx, getCallingPlayer(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> sync(ctx, EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    private int giveXp(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String raw = StringArgumentType.getString(ctx, "skill");
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        if (resolved.correctedFrom() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Autocorrected skill '" + raw + "' -> '" + skill + "'"), false);
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LevelProfile profile = LevelProfile.get(target);
        SkillXpResult award = profile.awardSkillXp(skill, amount);
        LevelProfile.save(target, profile);
        Network.sendDelta(target, profile, skill);

        // Feedback to command source
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + amount + " XP to " + skill + " for " + target.getGameProfile().getName()), true);

        // Feedback to target: action bar with current/needed and level up message
        SkillState spSkill = profile.skills.get(skill);
        if (spSkill != null) {
            long needed = LevelProfile.xpToNextLevel(skill, spSkill.level);
            String name = displayNameForSkill(skill);
            target.displayClientMessage(
                    Component.translatable("hud.levelrpg.skill_xp", name, spSkill.xp, needed, spSkill.level)
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
            if (award.leveledUp()) {
                target.sendSystemMessage(
                        Component.translatable("msg.levelrpg.level_up", name, spSkill.level).withStyle(ChatFormatting.GREEN)
                );
            }
        }
        return (int) Math.max(1L, award.xpAwarded());
    }

    private int setSkill(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String raw = StringArgumentType.getString(ctx, "skill");
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
                sp.xp = 0L;
            }
            LevelProfile.save(target, profile);
            Network.sendSync(target, profile);
            ctx.getSource().sendSuccess(() -> Component.literal("Set ALL skills for " + target.getGameProfile().getName() + " to level " + clamped), true);
            return profile.skills.size();
        }

        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        if (resolved.correctedFrom() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Autocorrected skill '" + raw + "' -> '" + skill + "'"), false);
        }
        SkillState sp = profile.skills.get(skill);
        if (sp == null) {
            sp = new SkillState();
            profile.skills.put(skill, sp);
        }
        sp.level = Math.max(0, level);
        sp.xp = 0L;
        LevelProfile.save(target, profile);
        Network.sendDelta(target, profile, skill);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().getName() + " " + skill + " to level " + level), true);
        return 1;
    }

    private int dump(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LevelProfile profile = LevelProfile.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal("Profile for " + target.getGameProfile().getName()), false);
        for (Map.Entry<ResourceLocation, SkillState> e : profile.skills.entrySet()) {
            SkillState sp = e.getValue();
            ctx.getSource().sendSuccess(() -> Component.literal(e.getKey() + " -> Lv " + sp.level + ", XP " + sp.xp), false);
        }
        return profile.skills.size();
    }

    private int getSkill(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String raw = StringArgumentType.getString(ctx, "skill");
        if ("all".equalsIgnoreCase(raw)) {
            return dump(ctx, target);
        }
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        LevelProfile profile = LevelProfile.get(target);
        SkillState sp = profile.skills.get(skill);
        int level = sp != null ? sp.level : 0;
        long xp = sp != null ? sp.xp : 0L;
        long needed = LevelProfile.xpToNextLevel(skill, level);
        String name = displayNameForSkill(skill);
        String who = target.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.literal(who + " " + name + ": Lv " + level + " (" + xp + "/" + needed + " XP)"), false);
        return 1;
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

    private static ServerPlayer getCallingPlayer(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private int reloadInfo(CommandContext<CommandSourceStack> ctx) {
        int skills = net.zoogle.levelrpg.data.SkillRegistry.size();
        int curves = net.zoogle.levelrpg.data.XpCurves.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Loaded skills: " + skills + ", XP curves: " + curves), false);
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
        java.util.LinkedHashSet<String> options = new java.util.LinkedHashSet<>();
        try {
            String raw = StringArgumentType.getString(ctx, "skill");
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

    private int unlockNode(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String raw = StringArgumentType.getString(ctx, "skill");
        String nodeId = StringArgumentType.getString(ctx, "node");
        var resolved = net.zoogle.levelrpg.util.IdUtil.resolveSkill(raw);
        if (resolved == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill: " + raw));
            return 0;
        }
        ResourceLocation skill = resolved.id();
        LevelProfile profile = LevelProfile.get(target);
        LevelProfile.UnlockResult res = profile.unlockNode(skill, nodeId);
        if (!res.success) {
            String detail = "Unlock failed: " + res.message
                    + " [level " + res.skillLevel
                    + ", mastery " + res.availablePoints + "/" + res.earnedPoints + "]";
            if (res.suggestedNextNodeId != null && !res.suggestedNextNodeId.isBlank()) {
                detail += " Next: " + res.suggestedNextNodeId;
            }
            ctx.getSource().sendFailure(Component.literal(detail));
            return 0;
        }
        LevelProfile.save(target, profile);
        Network.sendSync(target, profile);
        String name = displayNameForSkill(skill);
        String detail = "Unlocked '" + nodeId + "' in " + name + " for " + target.getGameProfile().getName()
                + " [mastery " + res.availablePoints + "/" + res.earnedPoints + " remaining]";
        ctx.getSource().sendSuccess(() -> Component.literal(detail), true);
        target.displayClientMessage(Component.literal("Unlocked '" + nodeId + "' in " + name), true);
        return 1;
    }
}
