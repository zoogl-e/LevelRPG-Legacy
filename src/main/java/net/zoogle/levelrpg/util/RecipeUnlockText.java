package net.zoogle.levelrpg.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.progression.RecipeUnlockService;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared text formatting for locked recipe feedback on both server and client.
 */
public final class RecipeUnlockText {
    private RecipeUnlockText() {}

    public static Component deniedCraftMessage(RecipeUnlockService.UnlockCheckResult unlockCheck) {
        return Component.translatable(
                "msg.levelrpg.recipe_locked",
                formatMissingRequirements(unlockCheck)
        ).withStyle(ChatFormatting.RED);
    }

    public static List<Component> tooltipLines(RecipeUnlockService.UnlockCheckResult unlockCheck) {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.levelrpg.recipe_locked").withStyle(ChatFormatting.RED));
        lines.add(Component.translatable(
                "tooltip.levelrpg.recipe_locked.requires",
                formatMissingRequirements(unlockCheck)
        ).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    public static Component formatMissingRequirements(RecipeUnlockService.UnlockCheckResult unlockCheck) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (RecipeUnlockService.MissingRequirement requirement : unlockCheck.missingRequirements()) {
            if (!first) {
                text.append(Component.translatable("msg.levelrpg.recipe_locked.separator"));
            }
            text.append(Component.translatable(
                    "msg.levelrpg.recipe_locked.requirement",
                    displayNameForSkill(requirement.skillId()),
                    requirement.currentLevel(),
                    requirement.requiredLevel()
            ));
            first = false;
        }
        return text;
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
