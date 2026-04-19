package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.LevelRPG;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal, server-authoritative player profile for the leveling framework.
 * Stores per-skill level/xp and a couple of player-level fields.
 * Persisted in the player's persistent data under key "levelrpg:profile".
 */
public class LevelProfile {
    public static final String NBT_KEY = LevelRPG.MODID + ":profile";


    public int playerLevel = 0;
    public int unspentSkillPoints = 0;
    public final LinkedHashMap<ResourceLocation, SkillProgress> skills = new LinkedHashMap<>();
    // Skill tree state: total points spent per skill and unlocked node ids per skill
    public final LinkedHashMap<ResourceLocation, Integer> treePointsSpent = new LinkedHashMap<>();
    public final LinkedHashMap<ResourceLocation, java.util.HashSet<String>> treeUnlockedNodes = new LinkedHashMap<>();

    public LevelProfile() {
        // Start empty; skills will be created on demand for registered ids only.
    }

    public static LevelProfile get(ServerPlayer player) {
        LevelProfile profile = new LevelProfile();
        CompoundTag root = player.getPersistentData();
        if (root.contains(NBT_KEY)) {
            profile.deserialize(root.getCompound(NBT_KEY));
        } else {
            // ensure default is saved once
            root.put(NBT_KEY, profile.serialize());
        }
        return profile;
    }

    public static void save(ServerPlayer player, LevelProfile profile) {
        CompoundTag root = player.getPersistentData();
        root.put(NBT_KEY, profile.serialize());
    }

    public static void copy(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        CompoundTag oldRoot = oldPlayer.getPersistentData();
        if (oldRoot.contains(NBT_KEY)) {
            newPlayer.getPersistentData().put(NBT_KEY, oldRoot.getCompound(NBT_KEY).copy());
        }
    }

    public Delta addSkillXp(ResourceLocation skillId, int amount) {
        Objects.requireNonNull(skillId, "skillId");
        // Only award XP to registered skills; ignore unknown ids to keep framework clean
        if (net.zoogle.levelrpg.data.SkillRegistry.get(skillId) == null) {
            Delta zero = new Delta();
            zero.skillId = skillId;
            zero.xpDelta = 0;
            zero.levelDelta = 0;
            return zero;
        }
        SkillProgress sp = skills.get(skillId);
        if (sp == null) {
            sp = new SkillProgress();
            skills.put(skillId, sp);
        }
        int beforeLevel = sp.level;
        long beforeXp = sp.xp;
        sp.xp = Math.max(0, sp.xp + amount);
        // Use per-skill curve to determine required XP for next levels
        long needed;
        while (sp.xp >= (needed = xpToNextLevel(skillId, sp.level))) {
            sp.xp -= needed;
            sp.level += 1;
        }
        // If any level was gained, recompute playerLevel and award unspent points accordingly
        int gained = sp.level - beforeLevel;
        if (gained > 0) {
            int old = this.playerLevel;
            int sum = 0;
            for (SkillProgress value : skills.values()) {
                sum += value.level;
            }
            int divider = Math.max(1, net.zoogle.levelrpg.Config.playerLevelDivider);
            this.playerLevel = sum / divider;
            if (this.playerLevel > old) {
                this.unspentSkillPoints += (this.playerLevel - old);
            }
        }
        Delta d = new Delta();
        d.skillId = skillId;
        d.xpDelta = sp.xp - beforeXp; // remaining delta after looping
        d.levelDelta = sp.level - beforeLevel;
        return d;
    }

    public static long xpToNextLevel(ResourceLocation skillId, int level) {
        // Look up curve id from registry; fallback to default behavior if missing
        ResourceLocation curveId = null;
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.xpCurve() != null) {
            curveId = def.xpCurve();
        }
        return net.zoogle.levelrpg.data.XpCurves.computeNeeded(curveId, level);
    }

    public static long xpToNextLevel(int level) {
        // Legacy/default behavior delegates to computeNeeded with no specific curve (uses default curve)
        return net.zoogle.levelrpg.data.XpCurves.computeNeeded(null, level);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 2);
        tag.putInt("playerLevel", playerLevel);
        tag.putInt("unspentSkillPoints", unspentSkillPoints);
        CompoundTag skillsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, SkillProgress> e : skills.entrySet()) {
            skillsTag.put(e.getKey().toString(), e.getValue().serialize());
        }
        tag.put("skills", skillsTag);
        // Serialize trees
        CompoundTag treesTag = new CompoundTag();
        // pointsSpent
        CompoundTag spentTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> e : treePointsSpent.entrySet()) {
            spentTag.putInt(e.getKey().toString(), e.getValue());
        }
        treesTag.put("spent", spentTag);
        // unlocked nodes
        CompoundTag unlockedTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, java.util.HashSet<String>> e : treeUnlockedNodes.entrySet()) {
            var list = new net.minecraft.nbt.ListTag();
            for (String nodeId : e.getValue()) list.add(net.minecraft.nbt.StringTag.valueOf(nodeId));
            unlockedTag.put(e.getKey().toString(), list);
        }
        treesTag.put("unlocked", unlockedTag);
        tag.put("trees", treesTag);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.playerLevel = tag.getInt("playerLevel");
        this.unspentSkillPoints = tag.getInt("unspentSkillPoints");
        this.skills.clear();
        CompoundTag skillsTag = tag.getCompound("skills");
        for (String key : skillsTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            SkillProgress sp = new SkillProgress();
            sp.deserialize(skillsTag.getCompound(key));
            this.skills.put(id, sp);
        }
        // trees
        this.treePointsSpent.clear();
        this.treeUnlockedNodes.clear();
        if (tag.contains("trees")) {
            CompoundTag treesTag = tag.getCompound("trees");
            if (treesTag.contains("spent")) {
                CompoundTag spent = treesTag.getCompound("spent");
                for (String key : spent.getAllKeys()) {
                    try {
                        ResourceLocation id = ResourceLocation.parse(key);
                        this.treePointsSpent.put(id, spent.getInt(key));
                    } catch (Exception ignored) {}
                }
            }
            if (treesTag.contains("unlocked")) {
                CompoundTag unlocked = treesTag.getCompound("unlocked");
                for (String key : unlocked.getAllKeys()) {
                    try {
                        ResourceLocation id = ResourceLocation.parse(key);
                        var list = unlocked.getList(key, net.minecraft.nbt.Tag.TAG_STRING);
                        java.util.HashSet<String> set = new java.util.HashSet<>();
                        for (int i = 0; i < list.size(); i++) {
                            set.add(list.getString(i));
                        }
                        this.treeUnlockedNodes.put(id, set);
                    } catch (Exception ignored) {}
                }
            }
        }
        // Prune any unknown skills not present in registry to enforce data-defined set only
        if (net.zoogle.levelrpg.data.SkillRegistry.size() > 0) {
            java.util.Iterator<ResourceLocation> it = new java.util.LinkedList<>(this.skills.keySet()).iterator();
            while (it.hasNext()) {
                ResourceLocation id = it.next();
                if (net.zoogle.levelrpg.data.SkillRegistry.get(id) == null) {
                    this.skills.remove(id);
                }
            }
            // Ensure all data-driven skills exist (no hardcoded defaults)
            for (ResourceLocation id : net.zoogle.levelrpg.data.SkillRegistry.ids()) {
                this.skills.putIfAbsent(id, new SkillProgress());
            }
        }
    }


    public static class SkillProgress {
        public int level = 0;
        public long xp = 0L;

        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putInt("level", level);
            t.putLong("xp", xp);
            return t;
        }

        public void deserialize(CompoundTag t) {
            this.level = t.getInt("level");
            this.xp = t.getLong("xp");
        }
    }

    public static class Delta {
        public ResourceLocation skillId;
        public int levelDelta;
        public long xpDelta;
    }

    public static class UnlockResult {
        public boolean success;
        public String message;
    }

    public UnlockResult unlockNode(ResourceLocation skillId, String nodeId) {
        UnlockResult res = new UnlockResult();
        if (skillId == null || nodeId == null || nodeId.isEmpty()) {
            res.success = false;
            res.message = "Invalid skill or node id";
            return res;
        }
        var tree = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skillId);
        if (tree == null) {
            res.success = false;
            res.message = "No tree for skill";
            return res;
        }
        var node = tree.nodes() != null ? tree.nodes().get(nodeId) : null;
        if (node == null) {
            res.success = false;
            res.message = "Unknown node";
            return res;
        }
        // Ensure skill exists in profile
        SkillProgress sp = skills.get(skillId);
        if (sp == null) {
            sp = new SkillProgress();
            skills.put(skillId, sp);
        }
        if (sp.level < Math.max(0, tree.minSkillLevel())) {
            res.success = false;
            res.message = "Skill level too low";
            return res;
        }
        // Already unlocked?
        java.util.HashSet<String> unlocked = treeUnlockedNodes.computeIfAbsent(skillId, k -> new java.util.HashSet<>());
        if (unlocked.contains(nodeId)) {
            res.success = false;
            res.message = "Already unlocked";
            return res;
        }
        // Check requirements
        if (node.requires() != null) {
            for (String req : node.requires()) {
                if (!unlocked.contains(req)) {
                    res.success = false;
                    res.message = "Missing prerequisite: " + req;
                    return res;
                }
            }
        }
        int cost = Math.max(1, node.cost());
        if (this.unspentSkillPoints < cost) {
            res.success = false;
            res.message = "Not enough points";
            return res;
        }
        // Spend and unlock
        this.unspentSkillPoints -= cost;
        int spent = this.treePointsSpent.getOrDefault(skillId, 0);
        this.treePointsSpent.put(skillId, spent + cost);
        unlocked.add(nodeId);
        res.success = true;
        res.message = "Unlocked";
        return res;
    }
}
