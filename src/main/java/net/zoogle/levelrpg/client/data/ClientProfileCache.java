package net.zoogle.levelrpg.client.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
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
    private static long lastUpdatedMs;
    private static ResourceLocation lastSkillId; // last skill that received a delta
    private static boolean ready; // true once we have received profile/delta from server
    private static boolean fullProfileSynced; // true once a full canonical profile snapshot has been received

    private ClientProfileCache() {}

    public static void set(
            Map<ResourceLocation, SkillState> map,
            Map<ResourceLocation, Integer> spentMap,
            Map<ResourceLocation, Set<String>> unlockedMap
    ) {
        skills.clear();
        skills.putAll(map);
        treePointsSpent.clear();
        treePointsSpent.putAll(spentMap);
        treeUnlockedNodes.clear();
        for (Map.Entry<ResourceLocation, Set<String>> entry : unlockedMap.entrySet()) {
            treeUnlockedNodes.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
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

    public static void applyDelta(ResourceLocation skillId, int level, long xp) {
        SkillState sp = skills.get(skillId);
        if (sp == null) {
            sp = new SkillState();
            skills.put(skillId, sp);
        }
        sp.level = level;
        sp.xp = xp;
        lastUpdatedMs = System.currentTimeMillis();
        lastSkillId = skillId;
        ready = true;
        LOGGER.info(
                "LevelRPG client profile delta applied: {} -> level {}, xp {}, canonicalReady={}",
                skillId,
                level,
                xp,
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
}
