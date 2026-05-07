package net.zoogle.levelrpg.client.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Very small client-side cache of the synced LevelProfile.
 * This is populated by networking and read by GUI code.
 */
public final class ClientProfileCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LinkedHashMap<ResourceLocation, SkillState> skills = new LinkedHashMap<>();
    private static final LinkedHashMap<ResourceLocation, Integer> treePointsSpent = new LinkedHashMap<>();
    private static final LinkedHashMap<ResourceLocation, Set<String>> treeUnlockedNodes = new LinkedHashMap<>();
    private static int availableSkillPoints;
    private static int spentSkillPoints;
    private static int bonusSpecializationPoints;
    private static int essence;
    private static ResourceLocation activeSoloBountyId;
    private static boolean activeSoloBountyObjectiveMet;
    private static int activeSoloBountyProgress;
    private static int bountyOfferTier = 1;
    private static final java.util.LinkedHashSet<ResourceLocation> completedBounties = new java.util.LinkedHashSet<>();
    private static ResourceLocation archetypeId;
    private static boolean archetypeApplied;
    private static long lastUpdatedMs;
    private static ResourceLocation lastSkillId; // last skill that received a delta
    private static boolean ready; // true once we have received profile/delta from server
    private static boolean fullProfileSynced; // true once a full canonical profile snapshot has been received
    private static long bindResultVersion;
    private static boolean lastBindSuccess;
    private static String lastBindMessage = "";
    private static ResourceLocation lastBindArchetypeId;

    private ClientProfileCache() {}

    public static void set(
            Map<ResourceLocation, SkillState> map,
            Map<ResourceLocation, Integer> spentMap,
            Map<ResourceLocation, Set<String>> unlockedMap,
            int insight,
            int spentPoints,
            int bonusSpecPoints,
            int syncedEssence,
            ResourceLocation syncedActiveSoloBountyId,
            boolean syncedActiveSoloBountyObjectiveMet,
            int syncedActiveSoloBountyProgress,
            int syncedBountyOfferTier,
            Set<ResourceLocation> syncedCompletedBounties,
            ResourceLocation selectedArchetypeId,
            boolean selectedArchetypeApplied
    ) {
        skills.clear();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillState incoming = map.get(skill.id());
            SkillState state = new SkillState();
            if (incoming != null) {
                state.level = incoming.level;
                state.rank = incoming.rank;
                state.proficiency = incoming.proficiency;
            }
            skills.put(skill.id(), state);
        }
        treePointsSpent.clear();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            treePointsSpent.put(skill.id(), Math.max(0, spentMap.getOrDefault(skill.id(), 0)));
        }
        treeUnlockedNodes.clear();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            treeUnlockedNodes.put(skill.id(), Set.copyOf(unlockedMap.getOrDefault(skill.id(), Set.of())));
        }
        availableSkillPoints = Math.max(0, insight);
        spentSkillPoints = Math.max(0, spentPoints);
        bonusSpecializationPoints = Math.max(0, bonusSpecPoints);
        essence = Math.max(0, syncedEssence);
        activeSoloBountyId = syncedActiveSoloBountyId;
        activeSoloBountyObjectiveMet = syncedActiveSoloBountyObjectiveMet;
        activeSoloBountyProgress = Math.max(0, syncedActiveSoloBountyProgress);
        bountyOfferTier = Math.clamp(syncedBountyOfferTier, 1, 3);
        completedBounties.clear();
        if (syncedCompletedBounties != null) {
            completedBounties.addAll(syncedCompletedBounties);
        }
        archetypeId = selectedArchetypeId;
        archetypeApplied = selectedArchetypeApplied;
        lastUpdatedMs = System.currentTimeMillis();
        lastSkillId = null;
        ready = true;
        fullProfileSynced = !skills.isEmpty();
        LOGGER.info(
                "LevelRPG client profile cache synced: {} skills, {} trees with spent points, {} trees with unlocked nodes, canonicalReady={}",
                skills.size(),
                treePointsSpent.size(),
                treeUnlockedNodes.size(),
                fullProfileSynced
        );
    }

    public static void applyDelta(ResourceLocation skillId, int level, int masteryLevel, long masteryXp, int insight, int spentPoints, int syncedEssence) {
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            LOGGER.warn("Ignoring non-canonical LevelRPG client profile delta for {}", skillId);
            return;
        }
        SkillState sp = skills.get(skillId);
        if (sp == null) {
            sp = new SkillState();
            skills.put(skillId, sp);
        }
        sp.level = level;
        sp.rank = masteryLevel;
        sp.proficiency = masteryXp;
        availableSkillPoints = Math.max(0, insight);
        spentSkillPoints = Math.max(0, spentPoints);
        essence = Math.max(0, syncedEssence);
        lastUpdatedMs = System.currentTimeMillis();
        lastSkillId = skillId;
        ready = true;
        LOGGER.info(
                "LevelRPG client profile delta applied: {} -> level {}, xp {}, canonicalReady={}",
                skillId,
                level,
                masteryXp,
                hasCanonicalProfileData()
        );
    }

    public static Map<ResourceLocation, SkillState> getSkillsView() { return Collections.unmodifiableMap(skills); }
    public static int getTreePointsSpent(ResourceLocation skillId) { return treePointsSpent.getOrDefault(skillId, 0); }
    public static Set<String> getTreeUnlockedNodes(ResourceLocation skillId) { return treeUnlockedNodes.getOrDefault(skillId, Set.of()); }
    public static long getLastUpdatedMs() { return lastUpdatedMs; }
    public static ResourceLocation getLastSkillId() { return lastSkillId; }
    public static boolean isReady() { return ready; }
    public static boolean hasCanonicalProfileData() { return fullProfileSynced && !skills.isEmpty(); }
    public static int getAvailableSkillPoints() { return availableSkillPoints; }
    public static int getSpentSkillPoints() { return spentSkillPoints; }
    public static int getBonusSpecializationPoints() { return bonusSpecializationPoints; }
    public static int getEssence() { return essence; }
    public static ResourceLocation getActiveSoloBountyId() { return activeSoloBountyId; }
    public static boolean isActiveSoloBountyObjectiveMet() { return activeSoloBountyObjectiveMet; }
    public static int getActiveSoloBountyProgress() { return Math.max(0, activeSoloBountyProgress); }
    public static int getBountyOfferTier() { return Math.clamp(bountyOfferTier, 1, 3); }
    public static boolean hasCompletedBounty(ResourceLocation bountyId) {
        return bountyId != null && completedBounties.contains(bountyId);
    }
    public static Set<ResourceLocation> getCompletedBounties() {
        return Collections.unmodifiableSet(completedBounties);
    }
    public static ResourceLocation getArchetypeId() { return archetypeId; }
    public static boolean isArchetypeApplied() { return archetypeApplied; }
    public static long getBindResultVersion() { return bindResultVersion; }
    public static boolean wasLastBindSuccess() { return lastBindSuccess; }
    public static String getLastBindMessage() { return lastBindMessage; }
    public static ResourceLocation getLastBindArchetypeId() { return lastBindArchetypeId; }

    public static void recordBindResult(boolean success, String message, ResourceLocation archetypeId) {
        lastBindSuccess = success;
        lastBindMessage = message == null ? "" : message;
        lastBindArchetypeId = archetypeId;
        bindResultVersion++;
    }

    /** Sum of Discipline Levels across all canonical disciplines (Insight thresholds use this). */
    public static int totalInvestedLevelsAcrossSkills() {
        int total = 0;
        for (SkillState state : skills.values()) {
            total += Math.max(0, state.level);
        }
        return total;
    }

    /** Sum of Insight spent (inscribed) across every discipline tree. */
    public static int totalSpecializationSpentAcrossTrees() {
        int spent = 0;
        for (int v : treePointsSpent.values()) {
            spent += Math.max(0, v);
        }
        return spent;
    }
}
