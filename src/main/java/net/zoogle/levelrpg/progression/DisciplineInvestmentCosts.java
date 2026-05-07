package net.zoogle.levelrpg.progression;

/**
 * Shared investment cost helpers used by both server progression logic and
 * client UI display to avoid drift.
 */
public final class DisciplineInvestmentCosts {
    private DisciplineInvestmentCosts() {}

    /**
     * V1 fixed Essence cost for raising a Discipline Level by one.
     */
    public static int fixedV1CostForNextDisciplineLevel() {
        return 1;
    }
}

