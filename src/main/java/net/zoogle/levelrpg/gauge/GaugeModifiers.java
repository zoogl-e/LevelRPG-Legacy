package net.zoogle.levelrpg.gauge;

import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.ArrayList;
import java.util.List;

public final class GaugeModifiers {
    private static final ArrayList<GaugeModifierProvider> PROVIDERS = new ArrayList<>();

    private GaugeModifiers() {
    }

    public static void register(GaugeModifierProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static double computeMax(ServerPlayer player, LevelProfile profile, GaugeDefinition gauge) {
        double value = gauge.defaultMax();
        GaugeComputationContext context = new GaugeComputationContext(player, profile, gauge);
        for (GaugeModifierProvider provider : PROVIDERS) {
            value = provider.modifyMax(context, value);
        }
        return Math.max(gauge.min(), value);
    }

    public static double computeDecayPerSecond(ServerPlayer player, LevelProfile profile, GaugeDefinition gauge) {
        GaugeComputationContext context = new GaugeComputationContext(player, profile, gauge);
        for (GaugeModifierProvider provider : PROVIDERS) {
            if (provider.disablesDecay(context)) {
                return 0.0;
            }
        }
        double value = gauge.decayPerSecond();
        for (GaugeModifierProvider provider : PROVIDERS) {
            value = provider.modifyDecayPerSecond(context, value);
        }
        return Math.max(0.0, value);
    }

    public static double modifyGain(ServerPlayer player, LevelProfile profile, GaugeDefinition gauge, double baseGain) {
        double value = baseGain;
        GaugeComputationContext context = new GaugeComputationContext(player, profile, gauge);
        for (GaugeModifierProvider provider : PROVIDERS) {
            value = provider.modifyGain(context, value);
        }
        return value;
    }

    public static double modifyCost(ServerPlayer player, LevelProfile profile, GaugeDefinition gauge, double baseCost) {
        double value = baseCost;
        GaugeComputationContext context = new GaugeComputationContext(player, profile, gauge);
        for (GaugeModifierProvider provider : PROVIDERS) {
            value = provider.modifyCost(context, value);
        }
        return Math.max(0.0, value);
    }

    public static List<GaugeModifierProvider> providers() {
        return List.copyOf(PROVIDERS);
    }
}
