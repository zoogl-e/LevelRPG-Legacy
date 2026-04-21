package net.zoogle.levelrpg.client.journal;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.journal.JournalProfileSnapshot;
import net.zoogle.levelrpg.journal.LevelRpgJournalSnapshotFactory;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.Set;

/**
 * Client-side adapter that turns the synced profile cache into the same stable
 * journal snapshot used by server-side profile projections.
 */
public final class ClientJournalSnapshotFactory {
    private ClientJournalSnapshotFactory() {}

    public static JournalProfileSnapshot create() {
        LevelProfile profile = new LevelProfile();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillState cached = ClientProfileCache.getSkillsView().get(skill.id());
            SkillState state = profile.getSkill(skill);
            if (cached != null) {
                state.level = cached.level;
                state.masteryLevel = cached.masteryLevel;
                state.masteryXp = cached.masteryXp;
            } else {
                state.level = 0;
                state.masteryLevel = 0;
                state.masteryXp = 0L;
            }
            profile.treePointsSpent.put(skill.id(), ClientProfileCache.getTreePointsSpent(skill.id()));
            profile.treeUnlockedNodes.put(skill.id(), new java.util.HashSet<>(safeSet(ClientProfileCache.getTreeUnlockedNodes(skill.id()))));
        }
        profile.availableSkillPoints = ClientProfileCache.getAvailableSkillPoints();
        profile.spentSkillPoints = ClientProfileCache.getSpentSkillPoints();
        return LevelRpgJournalSnapshotFactory.create(profile);
    }

    private static Set<String> safeSet(Set<String> value) {
        return value == null ? Set.of() : value;
    }
}
