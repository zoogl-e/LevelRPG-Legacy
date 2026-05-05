package net.zoogle.levelrpg.gauge;

public interface GaugeModifierProvider {
    default double modifyMax(GaugeComputationContext context, double baseMax) {
        return baseMax;
    }

    default double modifyDecayPerSecond(GaugeComputationContext context, double baseDecay) {
        return baseDecay;
    }

    default double modifyGain(GaugeComputationContext context, double baseGain) {
        return baseGain;
    }

    default double modifyCost(GaugeComputationContext context, double baseCost) {
        return baseCost;
    }

    default boolean disablesDecay(GaugeComputationContext context) {
        return false;
    }
}
