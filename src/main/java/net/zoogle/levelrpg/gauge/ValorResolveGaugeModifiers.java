package net.zoogle.levelrpg.gauge;

import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;

/**
 * Valor Resolve: max is zero until {@link ValorNodeIds#RESOLVE} is unlocked, then uses the base cap from
 * {@link GaugeRegistry#RESOLVE}. Further Valor nodes can extend this class later.
 */
public final class ValorResolveGaugeModifiers implements GaugeModifierProvider {
    private static final double STEELED_GAIN_MULTIPLIER = 0.60;
    private static final double STEELED_DECAY_MULTIPLIER = 0.45;

    @Override
    public double modifyMax(GaugeComputationContext context, double baseMax) {
        if (!GaugeRegistry.RESOLVE.equals(context.gauge().id())) {
            return baseMax;
        }
        if (!hasResolve(context)) {
            return context.gauge().min();
        }
        return baseMax;
    }

    @Override
    public double modifyDecayPerSecond(GaugeComputationContext context, double baseDecay) {
        if (!GaugeRegistry.RESOLVE.equals(context.gauge().id())) {
            return baseDecay;
        }
        if (!SkillUnlockQuery.hasNode(context.profile(), ValorNodeIds.SKILL, ValorNodeIds.STEELED)) {
            return baseDecay;
        }
        return baseDecay * STEELED_DECAY_MULTIPLIER;
    }

    @Override
    public double modifyGain(GaugeComputationContext context, double baseGain) {
        if (!GaugeRegistry.RESOLVE.equals(context.gauge().id())) {
            return baseGain;
        }
        if (!SkillUnlockQuery.hasNode(context.profile(), ValorNodeIds.SKILL, ValorNodeIds.STEELED)) {
            return baseGain;
        }
        return baseGain * STEELED_GAIN_MULTIPLIER;
    }

    private static boolean hasResolve(GaugeComputationContext context) {
        return SkillUnlockQuery.hasNode(context.profile(), ValorNodeIds.SKILL, ValorNodeIds.RESOLVE);
    }
}
