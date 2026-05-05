package net.zoogle.levelrpg.gauge;

import net.minecraft.resources.ResourceLocation;

public record GaugeDefinition(
        ResourceLocation id,
        String displayName,
        String description,
        double min,
        double defaultMax,
        double defaultStartingValue,
        double decayPerSecond,
        int decayDelayTicks,
        boolean hiddenWhenEmpty,
        int primaryColor,
        int backgroundColor,
        ResourceLocation icon
) {
    public GaugeDefinition {
        if (id == null) {
            throw new IllegalArgumentException("Gauge id cannot be null");
        }
        displayName = displayName == null || displayName.isBlank() ? id.getPath() : displayName;
        description = description == null ? "" : description;
        defaultMax = Math.max(min, defaultMax);
        defaultStartingValue = Math.max(min, Math.min(defaultMax, defaultStartingValue));
        decayPerSecond = Math.max(0.0, decayPerSecond);
        decayDelayTicks = Math.max(0, decayDelayTicks);
    }
}
