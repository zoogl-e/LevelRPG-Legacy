package net.zoogle.levelrpg.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.zoogle.levelrpg.data.ActivityRules;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

import java.util.HashSet;
import java.util.Set;

/**
 * Gameplay hooks that consult data-driven ActivityRules to award XP.
 */
public class ActivityEvents {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        var state = event.getState();
        if (ActivityRules.breakBlockRules().isEmpty()) return; // no rules loaded

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changedSkills = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();
        for (ActivityRules.BreakBlockRule rule : ActivityRules.breakBlockRules()) {
            TagKey<net.minecraft.world.level.block.Block> tag = rule.tagKey();
            if (state.is(tag)) {
                LevelProfile.Delta d = profile.addSkillXp(rule.skill, rule.xp);
                changedSkills.add(rule.skill);
                if (d.levelDelta > 0) leveledUp.add(rule.skill);
            }
        }
        if (!changedSkills.isEmpty()) {
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
    public void onMobKilled(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (ActivityRules.killEntityRules().isEmpty()) return;
        var victim = event.getEntity();
        var type = victim.getType();
        ItemStack held = sp.getMainHandItem();

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();
        for (ActivityRules.KillEntityRule rule : ActivityRules.killEntityRules()) {
            if (!type.is(rule.entityTagKey())) continue;
            if (rule.weaponItemTag != null) {
                TagKey<net.minecraft.world.item.Item> wTag = rule.weaponTagKey();
                if (!held.is(wTag)) continue;
            }
            LevelProfile.Delta d = profile.addSkillXp(rule.skill, rule.xp);
            changed.add(rule.skill);
            if (d.levelDelta > 0) leveledUp.add(rule.skill);
        }
        if (!changed.isEmpty()) {
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
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (ActivityRules.craftItemRules().isEmpty()) return;
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();
        for (ActivityRules.CraftItemRule rule : ActivityRules.craftItemRules()) {
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                LevelProfile.Delta d = profile.addSkillXp(rule.skill, totalXp);
                changed.add(rule.skill);
                if (d.levelDelta > 0) leveledUp.add(rule.skill);
            }
        }
        if (!changed.isEmpty()) {
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
        if (ActivityRules.smeltItemRules().isEmpty()) return;
        ItemStack result = event.getSmelting();
        if (result == null || result.isEmpty()) return;

        LevelProfile profile = LevelProfile.get(sp);
        Set<ResourceLocation> changed = new HashSet<>();
        Set<ResourceLocation> leveledUp = new HashSet<>();
        for (ActivityRules.SmeltItemRule rule : ActivityRules.smeltItemRules()) {
            if (result.is(rule.tagKey())) {
                int totalXp = Math.max(1, rule.xp) * Math.max(1, result.getCount());
                LevelProfile.Delta d = profile.addSkillXp(rule.skill, totalXp);
                changed.add(rule.skill);
                if (d.levelDelta > 0) leveledUp.add(rule.skill);
            }
        }
        if (!changed.isEmpty()) {
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

    private static void sendXpBar(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        LevelProfile.SkillProgress spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        long needed = LevelProfile.xpToNextLevel(skillId, spSkill.level);
        String name = displayNameForSkill(skillId);
        sp.displayClientMessage(
                Component.translatable("hud.levelrpg.skill_xp", name, spSkill.xp, needed, spSkill.level)
                        .withStyle(ChatFormatting.GOLD),
                true
        );
    }

    private static void sendLevelUp(ServerPlayer sp, LevelProfile profile, ResourceLocation skillId) {
        LevelProfile.SkillProgress spSkill = profile.skills.get(skillId);
        if (spSkill == null) return;
        String name = displayNameForSkill(skillId);
        sp.sendSystemMessage(
                Component.translatable("msg.levelrpg.level_up", name, spSkill.level).withStyle(ChatFormatting.GREEN)
        );
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
