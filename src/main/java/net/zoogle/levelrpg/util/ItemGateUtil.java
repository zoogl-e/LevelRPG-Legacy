package net.zoogle.levelrpg.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Map;

public final class ItemGateUtil {
    private ItemGateUtil() {}

    public static boolean wouldEquip(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return (item instanceof net.minecraft.world.item.ArmorItem) || (item instanceof net.minecraft.world.item.ShieldItem);
    }

    public static GateRules.ItemGate findGateFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GateRules.ItemGate gate = GateRules.itemIdRules().get(id);
        if (gate != null) return gate;
        for (Map.Entry<ResourceLocation, GateRules.ItemGate> e : GateRules.itemTagRules().entrySet()) {
            ResourceLocation tagId = e.getKey();
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            if (stack.is(tag)) return e.getValue();
        }
        return null;
    }

    public static boolean hasRequiredLevel(ServerPlayer sp, GateRules.ItemGate gate) {
        if (sp == null || gate == null) return true;
        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        return level >= gate.minLevel;
    }

    public static void notifyLocked(ServerPlayer sp, GateRules.ItemGate gate) {
        if (sp == null || gate == null) return;
        sp.displayClientMessage(
                Component.translatable("msg.levelrpg.locked", gate.minLevel, displayNameForSkill(gate.skill))
                        .withStyle(ChatFormatting.RED),
                true
        );
    }

    public static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}