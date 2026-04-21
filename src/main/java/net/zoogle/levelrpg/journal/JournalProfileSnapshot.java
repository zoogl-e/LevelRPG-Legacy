package net.zoogle.levelrpg.journal;

import java.util.List;

/**
 * Root journal-facing snapshot for Enchiridion integration.
 */
public record JournalProfileSnapshot(
        JournalCharacterLedgerSnapshot characterLedger,
        List<JournalSkillSnapshot> skills
) {
    public JournalProfileSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
