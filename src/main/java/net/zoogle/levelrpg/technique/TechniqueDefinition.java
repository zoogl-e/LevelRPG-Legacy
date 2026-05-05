package net.zoogle.levelrpg.technique;

import net.minecraft.resources.ResourceLocation;

public record TechniqueDefinition(
        ResourceLocation id,
        String displayName,
        String description,
        ResourceLocation icon,
        ResourceLocation requiredSkillId,
        String requiredNodeId,
        TechniqueCost cost,
        int cooldownTicks,
        TechniqueValidator validator,
        TechniqueActivator activator
) {
    public TechniqueDefinition {
        displayName = displayName == null ? "" : displayName;
        description = description == null ? "" : description;
        requiredNodeId = requiredNodeId == null ? "" : requiredNodeId;
        cost = cost == null ? TechniqueCost.NONE : cost;
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public boolean hasRequirement() {
        return requiredSkillId != null && !requiredNodeId.isBlank();
    }

    @FunctionalInterface
    public interface TechniqueValidator {
        TechniqueResult validate(TechniqueActivationContext context);
    }

    @FunctionalInterface
    public interface TechniqueActivator {
        TechniqueResult activate(TechniqueActivationContext context);
    }
}
