package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

public record SkillDefinition(
        ResourceLocation id,
        Display display,
        ResourceLocation xpCurve
) {
    public record Display(String name, String color, String icon, String notes) {}
}
