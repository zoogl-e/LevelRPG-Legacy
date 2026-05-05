package net.zoogle.levelrpg.gauge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlayerGaugeInstance {
    private final ResourceLocation gaugeId;
    private double value;
    private long lastChangedTick;

    public PlayerGaugeInstance(ResourceLocation gaugeId, double value, long lastChangedTick) {
        this.gaugeId = gaugeId;
        this.value = value;
        this.lastChangedTick = lastChangedTick;
    }

    public ResourceLocation gaugeId() {
        return gaugeId;
    }

    public double value() {
        return value;
    }

    public long lastChangedTick() {
        return lastChangedTick;
    }

    public boolean setValue(double value, double min, double max, long tick) {
        double clamped = Math.max(min, Math.min(max, value));
        if (Math.abs(this.value - clamped) < 0.0001) {
            return false;
        }
        this.value = clamped;
        this.lastChangedTick = tick;
        return true;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("value", value);
        tag.putLong("lastChangedTick", lastChangedTick);
        return tag;
    }

    public static PlayerGaugeInstance deserialize(ResourceLocation gaugeId, CompoundTag tag) {
        return new PlayerGaugeInstance(gaugeId, tag.getDouble("value"), tag.getLong("lastChangedTick"));
    }
}
