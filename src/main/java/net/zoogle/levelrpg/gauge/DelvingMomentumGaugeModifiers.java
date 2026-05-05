package net.zoogle.levelrpg.gauge;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.skilltree.DelvingNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

public final class DelvingMomentumGaugeModifiers implements GaugeModifierProvider {
    public static final ResourceLocation DELVING = DelvingNodeIds.SKILL;

    @Override
    public double modifyMax(GaugeComputationContext context, double baseMax) {
        if (!GaugeRegistry.MOMENTUM.equals(context.gauge().id())) {
            return baseMax;
        }
        double result = baseMax;
        if (has(context, DelvingNodeIds.MOMENTUM_RESERVOIR)) {
            result += 50.0;
        }
        if (has(context, DelvingNodeIds.DEEP_DELVER)) {
            result += 50.0;
        }
        return result;
    }

    @Override
    public double modifyDecayPerSecond(GaugeComputationContext context, double baseDecay) {
        if (!GaugeRegistry.MOMENTUM.equals(context.gauge().id())) {
            return baseDecay;
        }
        if (has(context, DelvingNodeIds.DEEP_DELVER)) {
            return baseDecay * 0.5;
        }
        return baseDecay;
    }

    @Override
    public double modifyGain(GaugeComputationContext context, double baseGain) {
        if (!GaugeRegistry.MOMENTUM.equals(context.gauge().id())) {
            return baseGain;
        }
        return has(context, DelvingNodeIds.EARTHBREAKER) ? baseGain * 1.5 : baseGain;
    }

    @Override
    public double modifyCost(GaugeComputationContext context, double baseCost) {
        if (!GaugeRegistry.MOMENTUM.equals(context.gauge().id())) {
            return baseCost;
        }
        double result = baseCost;
        if (has(context, DelvingNodeIds.MOMENTUM_CORE)) {
            result *= 2.0;
        }
        if (has(context, DelvingNodeIds.EARTHBREAKER)) {
            result *= 0.8;
        }
        return result;
    }

    @Override
    public boolean disablesDecay(GaugeComputationContext context) {
        return GaugeRegistry.MOMENTUM.equals(context.gauge().id()) && has(context, DelvingNodeIds.MOMENTUM_CORE);
    }

    public static boolean has(GaugeComputationContext context, String nodeId) {
        return SkillUnlockQuery.hasNode(context.profile(), DELVING, nodeId);
    }
}
