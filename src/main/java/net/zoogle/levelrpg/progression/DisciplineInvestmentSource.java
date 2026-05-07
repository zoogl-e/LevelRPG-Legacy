package net.zoogle.levelrpg.progression;

/**
 * Origin of a Discipline investment request.
 *
 * <p>This is scaffolding for future Index-gated investment flow. No real
 * Index block/proximity validation is performed yet.
 */
public enum DisciplineInvestmentSource {
    /** Future Index block/station interaction path. */
    INDEX,
    /** Future Enchiridion/book interaction path. */
    BOOK,
    /** Admin/test command path. */
    COMMAND,
    /** Internal scripted/system path. */
    SYSTEM
}

