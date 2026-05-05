package net.zoogle.levelrpg.technique;

import net.minecraft.resources.ResourceLocation;

public record TechniqueCost(ResourceLocation gaugeId, double amount) {
    public static final TechniqueCost NONE = new TechniqueCost(null, 0.0);

    public boolean hasGaugeCost() {
        return gaugeId != null && amount > 0.0;
    }
}
