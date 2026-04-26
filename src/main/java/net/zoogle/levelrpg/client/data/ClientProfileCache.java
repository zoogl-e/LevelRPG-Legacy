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
    private static ResourceLocation archetypeId;
    private static boolean archetypeApplied;
    private static long lastUpdatedMs;
    private static ResourceLocation lastSkillId; // last skill that received a delta
    private static boolean ready; // true once we have received profile/delta from server
    private static boolean fullProfileSynced; // true once a full canonical profile snapshot has been received

    private ClientProfileCache() {}

    public static void set(
            Map<ResourceLocation, SkillState> map,
            Map<ResourceLocation, Integer> spentMap,
            Map<ResourceLocation, Set<String>> unlockedMap,
            int availablePoints,
            int spentPoints,
            ResourceLocation selectedArchetypeId,
            boolean selectedArchetypeApplied
    ) {
        skills.clear();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillState incoming = map.get(skill.id());
            SkillState state = new SkillState();
            if (incoming != null) {
                state.level = incoming.level;
                state.masteryLevel = incoming.masteryLevel;
                state.masteryXp = incoming.masteryXp;
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
        availableSkillPoints = Math.max(0, availablePoints);
        spentSkillPoints = Math.max(0, spentPoints);
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

    public static void applyDelta(ResourceLocation skillId, int level, int masteryLevel, long masteryXp, int availablePoints, int spentPoints) {
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
        sp.masteryLevel = masteryLevel;
        sp.masteryXp = masteryXp;
        availableSkillPoints = Math.max(0, availablePoints);
        spentSkillPoints = Math.max(0, spentPoints);
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
    public static ResourceLocation getArchetypeId() { return archetypeId; }
    public static boolean isArchetypeApplied() { return archetypeApplied; }

    /** Sum of invested skill levels across all canonical skills (specialization thresholds use this). */
    public static int totalInvestedLevelsAcrossSkills() {
        int total = 0;
        for (SkillState state : skills.values()) {
            total += Math.max(0, state.level);
        }
        return total;
    }

    /** Sum of mastery-tree points spent in every discipline. */
    public static int totalSpecializationSpentAcrossTrees() {
        int spent = 0;
        for (int v : treePointsSpent.values()) {
            spent += Math.max(0, v);
        }
        return spent;
    }
}
