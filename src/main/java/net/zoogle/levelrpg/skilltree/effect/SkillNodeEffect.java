package net.zoogle.levelrpg.skilltree.effect;

import net.minecraft.resources.ResourceLocation;

public record SkillNodeEffect(
        ResourceLocation type,
        ResourceLocation id
) {
    public SkillNodeEffect {
        if (type == null) {
            throw new IllegalArgumentException("Skill node effect type cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("Skill node effect id cannot be null");
        }
    }
}
