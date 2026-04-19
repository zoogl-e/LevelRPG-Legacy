package net.zoogle.levelrpg.client.data;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Very small client-side cache of the synced LevelProfile.
 * This is populated by networking and read by GUI code.
 */
public final class ClientProfileCache {
    private static int playerLevel;
    private static int unspentSkillPoints;
    private static final LinkedHashMap<ResourceLocation, LevelProfile.SkillProgress> skills = new LinkedHashMap<>();
    private static long lastUpdatedMs;
    private static ResourceLocation lastSkillId; // last skill that received a delta
    private static boolean ready; // true once we have received profile/delta from server

    private ClientProfileCache() {}

    public static void set(int playerLevel, int unspent, Map<ResourceLocation, LevelProfile.SkillProgress> map) {
        ClientProfileCache.playerLevel = playerLevel;
        ClientProfileCache.unspentSkillPoints = unspent;
        skills.clear();
        skills.putAll(map);
        lastUpdatedMs = System.currentTimeMillis();
        lastSkillId = null;
        ready = true;
    }

    public static void applyDelta(ResourceLocation skillId, int level, long xp, int newPlayerLevel, int newUnspent) {
        ClientProfileCache.playerLevel = newPlayerLevel;
        ClientProfileCache.unspentSkillPoints = newUnspent;
        LevelProfile.SkillProgress sp = skills.get(skillId);
        if (sp == null) {
            sp = new LevelProfile.SkillProgress();
            skills.put(skillId, sp);
        }
        sp.level = level;
        sp.xp = xp;
        lastUpdatedMs = System.currentTimeMillis();
        lastSkillId = skillId;
        ready = true;
    }

    public static int getPlayerLevel() { return playerLevel; }
    public static int getUnspentSkillPoints() { return unspentSkillPoints; }
    public static Map<ResourceLocation, LevelProfile.SkillProgress> getSkillsView() { return Collections.unmodifiableMap(skills); }
    public static long getLastUpdatedMs() { return lastUpdatedMs; }
    public static ResourceLocation getLastSkillId() { return lastSkillId; }
    public static boolean isReady() { return ready; }
}
