package net.zoogle.levelrpg.gauge;

import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

/**
 * Finesse Rhythm: gauge max stays at zero until the {@link FinesseNodeIds#RHYTHM} core node is unlocked.
 */
public final class FinesseRhythmGaugeModifiers implements GaugeModifierProvider {
    @Override
    public double modifyMax(GaugeComputationContext context, double baseMax) {
        if (!GaugeRegistry.RHYTHM.equals(context.gauge().id())) {
            return baseMax;
        }
        if (!hasRhythm(context)) {
            return context.gauge().min();
        }
        return baseMax;
    }

    private static boolean hasRhythm(GaugeComputationContext context) {
        return SkillUnlockQuery.hasNode(context.profile(), FinesseNodeIds.SKILL, FinesseNodeIds.RHYTHM);
    }
}
