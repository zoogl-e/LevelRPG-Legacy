package net.zoogle.levelrpg.gauge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerGaugeData {
    private final LinkedHashMap<ResourceLocation, PlayerGaugeInstance> gauges = new LinkedHashMap<>();

    public PlayerGaugeInstance get(ResourceLocation gaugeId) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        double startingValue = definition == null ? 0.0 : definition.defaultStartingValue();
        return gauges.computeIfAbsent(gaugeId, id -> new PlayerGaugeInstance(id, startingValue, 0L));
    }

    public double getValue(ResourceLocation gaugeId) {
        return get(gaugeId).value();
    }

    public double getMax(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        if (definition == null) {
            return 0.0;
        }
        return GaugeModifiers.computeMax(player, profile, definition);
    }

    public boolean setValue(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId, double value) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        if (definition == null) {
            return false;
        }
        return get(gaugeId).setValue(value, definition.min(), getMax(player, profile, gaugeId), player.level().getGameTime());
    }

    public boolean add(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId, double amount) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        if (definition == null || amount == 0.0) {
            return false;
        }
        double modified = amount > 0.0 ? GaugeModifiers.modifyGain(player, profile, definition, amount) : amount;
        return setValue(player, profile, gaugeId, getValue(gaugeId) + modified);
    }

    public boolean canSpend(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId, double amount) {
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        if (definition == null || amount < 0.0) {
            return false;
        }
        double cost = GaugeModifiers.modifyCost(player, profile, definition, amount);
        return getValue(gaugeId) + 0.0001 >= cost;
    }

    public boolean spend(ServerPlayer player, LevelProfile profile, ResourceLocation gaugeId, double amount) {
        if (!canSpend(player, profile, gaugeId, amount)) {
            return false;
        }
        GaugeDefinition definition = GaugeRegistry.get(gaugeId);
        double cost = GaugeModifiers.modifyCost(player, profile, definition, amount);
        return setValue(player, profile, gaugeId, getValue(gaugeId) - cost);
    }

    public Collection<PlayerGaugeInstance> instances() {
        return gauges.values();
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<ResourceLocation, PlayerGaugeInstance> entry : gauges.entrySet()) {
            tag.put(entry.getKey().toString(), entry.getValue().serialize());
        }
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        gauges.clear();
        for (String key : tag.getAllKeys()) {
            try {
                ResourceLocation id = ResourceLocation.parse(key);
                gauges.put(id, PlayerGaugeInstance.deserialize(id, tag.getCompound(key)));
            } catch (Exception ignored) {
            }
        }
    }
}
