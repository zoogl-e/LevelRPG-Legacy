package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.progression.MasteryLeveling;
import net.zoogle.levelrpg.progression.MasteryProgressionService;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import net.zoogle.levelrpg.progression.SkillPointProgression;
import net.zoogle.levelrpg.progression.SkillTreeProgression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-authoritative player progression profile.
 *
 * The rewrite keeps persistence and sync responsibilities here, but no longer
 * treats summed player level or generic unspent points as core progression.
 * Those legacy fields remain only as migration state for older saves.
 */
public class LevelProfile {
    public static final String NBT_KEY = LevelRPG.MODID + ":profile";

    /**
     * Legacy compatibility field. The rewrite does not derive a global player
     * level from summed skill levels anymore.
     */
    @Deprecated
    public int playerLevel = 0;

    /**
     * Legacy compatibility field. Generic unspent points are no longer a core
     * progression concept, but some existing tree code still references it.
     */
    @Deprecated
    public int unspentSkillPoints = 0;

    public int availableSkillPoints = 0;
    public int spentSkillPoints = 0;

    public final ArchetypeState archetype = new ArchetypeState();
    public final LinkedHashMap<ResourceLocation, SkillState> skills = new LinkedHashMap<>();
    // Legacy tree state retained for compile-safe compatibility with old systems.
    public final LinkedHashMap<ResourceLocation, Integer> treePointsSpent = new LinkedHashMap<>();
    public final LinkedHashMap<ResourceLocation, java.util.HashSet<String>> treeUnlockedNodes = new LinkedHashMap<>();

    public LevelProfile() {
        ensureCanonicalSkills();
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

    public SkillState getSkill(ResourceLocation skillId) {
        Objects.requireNonNull(skillId, "skillId");
        return skills.computeIfAbsent(skillId, id -> new SkillState());
    }

    public SkillState getSkill(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return getSkill(skill.id());
    }

    public int investedSkillLevel(ResourceLocation skillId) {
        return Math.max(0, getSkill(skillId).level);
    }

    public int masteryLevel(ResourceLocation skillId) {
        return Math.max(0, getSkill(skillId).masteryLevel);
    }

    public long masteryProgress(ResourceLocation skillId) {
        return Math.max(0L, getSkill(skillId).masteryXp);
    }

    public long masteryRequiredForNextLevel(ResourceLocation skillId) {
        return MasteryLeveling.xpToNextLevel(skillId, masteryLevel(skillId));
    }

    public SkillProgressView skillProgress(ResourceLocation skillId) {
        return new SkillProgressView(
                skillId,
                investedSkillLevel(skillId),
                masteryLevel(skillId),
                masteryProgress(skillId),
                masteryRequiredForNextLevel(skillId),
                SkillPointProgression.canSpendPoint(this, skillId)
        );
    }

    public SkillProgressView skillProgress(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return skillProgress(skill.id());
    }

    public int availableSkillPoints() {
        return SkillPointProgression.availablePoints(this);
    }

    public int spentSkillPoints() {
        return SkillPointProgression.spentPoints(this);
    }

    public Map<ResourceLocation, SkillState> canonicalSkillsView() {
        LinkedHashMap<ResourceLocation, SkillState> ordered = new LinkedHashMap<>();
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            ordered.put(skill.id(), getSkill(skill));
        }
        return java.util.Collections.unmodifiableMap(ordered);
    }

    public int getTreePointsSpent(ResourceLocation skillId) {
        return Math.max(0, treePointsSpent.getOrDefault(skillId, 0));
    }

    public Set<String> getUnlockedTreeNodes(ResourceLocation skillId) {
        java.util.HashSet<String> unlocked = treeUnlockedNodes.get(skillId);
        if (unlocked == null || unlocked.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(unlocked));
    }

    public Map<ResourceLocation, Integer> treePointsSpentView() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(treePointsSpent));
    }

    public Map<ResourceLocation, Set<String>> treeUnlockedNodesView() {
        LinkedHashMap<ResourceLocation, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, java.util.HashSet<String>> entry : treeUnlockedNodes.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public void ensureCanonicalSkills() {
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            skills.putIfAbsent(skill.id(), new SkillState());
        }
    }

    public void setArchetype(ResourceLocation archetypeId, boolean startingDistributionApplied) {
        this.archetype.id = archetypeId;
        this.archetype.startingDistributionApplied = startingDistributionApplied;
    }

    public ArchetypeDefinition getSelectedArchetype() {
        if (archetype.id == null) {
            return null;
        }
        return ArchetypeRegistry.get(archetype.id);
    }

    public boolean hasAppliedStartingArchetype() {
        return archetype.startingDistributionApplied;
    }

    public ArchetypeApplyResult selectArchetype(ResourceLocation archetypeId) {
        ArchetypeDefinition definition = ArchetypeRegistry.get(archetypeId);
        if (definition == null) {
            return ArchetypeApplyResult.failure("Unknown archetype");
        }
        if (archetype.startingDistributionApplied) {
            if (definition.id().equals(archetype.id)) {
                return ArchetypeApplyResult.alreadyApplied(definition.id());
            }
            return ArchetypeApplyResult.failure("Archetype already locked in");
        }
        this.archetype.id = definition.id();
        this.archetype.startingDistributionApplied = false;
        return ArchetypeApplyResult.selected(definition.id());
    }

    public ArchetypeApplyResult ensureDefaultArchetypeSelected() {
        if (archetype.id != null) {
            return ArchetypeApplyResult.selected(archetype.id);
        }
        ArchetypeDefinition definition = ArchetypeRegistry.defaultArchetype();
        if (definition == null) {
            return ArchetypeApplyResult.failure("No default archetype is registered");
        }
        this.archetype.id = definition.id();
        this.archetype.startingDistributionApplied = false;
        return ArchetypeApplyResult.selected(definition.id());
    }

    public ArchetypeApplyResult applySelectedArchetypeIfNeeded() {
        if (archetype.startingDistributionApplied) {
            return ArchetypeApplyResult.alreadyApplied(archetype.id);
        }
        ArchetypeDefinition definition = getSelectedArchetype();
        if (definition == null) {
            return ArchetypeApplyResult.failure("No archetype selected");
        }
        applyArchetypeLevels(definition);
        archetype.id = definition.id();
        archetype.startingDistributionApplied = true;
        return ArchetypeApplyResult.applied(definition.id());
    }

    public ArchetypeApplyResult selectAndApplyArchetype(ResourceLocation archetypeId) {
        ArchetypeApplyResult selection = selectArchetype(archetypeId);
        if (!selection.success) {
            return selection;
        }
        return applySelectedArchetypeIfNeeded();
    }

    private void applyArchetypeLevels(ArchetypeDefinition definition) {
        ensureCanonicalSkills();
        for (Map.Entry<ProgressionSkill, Integer> entry : definition.startingLevels().entrySet()) {
            SkillState state = getSkill(entry.getKey());
            state.level = Math.max(state.level, entry.getValue());
            state.masteryXp = Math.max(0L, state.masteryXp);
        }
    }

    /**
     * Canonical mastery grant path for per-skill practice progression.
     */
    public MasteryAwardResult awardMastery(ResourceLocation skillId, long amount) {
        Objects.requireNonNull(skillId, "skillId");
        return MasteryProgressionService.award(this, skillId, amount);
    }

    /**
     * Legacy compatibility alias for older XP-named award code.
     */
    @Deprecated
    public SkillXpResult awardSkillXp(ResourceLocation skillId, long amount) {
        return SkillXpResult.from(awardMastery(skillId, amount));
    }

    public SkillPointProgression.SkillPointSpendResult spendSkillPoint(ResourceLocation skillId) {
        return SkillPointProgression.spendPoint(this, skillId);
    }

    /**
     * Legacy skill-threshold helper retained while older systems still refer to
     * skill-level curves. New earned progression should use {@link MasteryLeveling}.
     */
    @Deprecated
    public static long xpToNextLevel(ResourceLocation skillId, int level) {
        return SkillLeveling.xpToNextLevel(skillId, level);
    }

    @Deprecated
    public static long xpToNextLevel(int level) {
        return SkillLeveling.xpToNextLevel(level);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 4);
        tag.putInt("playerLevel", playerLevel);
        tag.putInt("unspentSkillPoints", unspentSkillPoints);
        tag.putInt("availableSkillPoints", availableSkillPoints);
        tag.putInt("spentSkillPoints", spentSkillPoints);
        tag.put("archetype", archetype.serialize());
        CompoundTag skillsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, SkillState> e : skills.entrySet()) {
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
        this.availableSkillPoints = Math.max(0, tag.getInt("availableSkillPoints"));
        this.spentSkillPoints = Math.max(0, tag.getInt("spentSkillPoints"));
        if (tag.contains("archetype")) {
            this.archetype.deserialize(tag.getCompound("archetype"));
        } else {
            this.archetype.id = null;
            this.archetype.startingDistributionApplied = false;
        }
        this.skills.clear();
        CompoundTag skillsTag = tag.getCompound("skills");
        for (String key : skillsTag.getAllKeys()) {
            try {
                ResourceLocation id = ResourceLocation.parse(key);
                if (!ProgressionSkill.isCanonicalId(id)) {
                    continue;
                }
                SkillState sp = new SkillState();
                sp.deserialize(skillsTag.getCompound(key));
                this.skills.put(id, sp);
            } catch (Exception ignored) {
            }
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
                        if (!ProgressionSkill.isCanonicalId(id)) {
                            continue;
                        }
                        this.treePointsSpent.put(id, spent.getInt(key));
                    } catch (Exception ignored) {}
                }
            }
            if (treesTag.contains("unlocked")) {
                CompoundTag unlocked = treesTag.getCompound("unlocked");
                for (String key : unlocked.getAllKeys()) {
                    try {
                        ResourceLocation id = ResourceLocation.parse(key);
                        if (!ProgressionSkill.isCanonicalId(id)) {
                            continue;
                        }
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
        ensureCanonicalSkills();
        if (!tag.contains("availableSkillPoints") && unspentSkillPoints > 0) {
            this.availableSkillPoints = Math.max(this.availableSkillPoints, unspentSkillPoints);
        }
    }

    public static class ArchetypeApplyResult {
        public boolean success;
        public boolean applied;
        public ResourceLocation archetypeId;
        public String message;

        public static ArchetypeApplyResult selected(ResourceLocation archetypeId) {
            ArchetypeApplyResult result = new ArchetypeApplyResult();
            result.success = true;
            result.applied = false;
            result.archetypeId = archetypeId;
            result.message = "Archetype selected";
            return result;
        }

        public static ArchetypeApplyResult applied(ResourceLocation archetypeId) {
            ArchetypeApplyResult result = new ArchetypeApplyResult();
            result.success = true;
            result.applied = true;
            result.archetypeId = archetypeId;
            result.message = "Archetype starting distribution applied";
            return result;
        }

        public static ArchetypeApplyResult alreadyApplied(ResourceLocation archetypeId) {
            ArchetypeApplyResult result = new ArchetypeApplyResult();
            result.success = true;
            result.applied = false;
            result.archetypeId = archetypeId;
            result.message = "Archetype starting distribution already applied";
            return result;
        }

        public static ArchetypeApplyResult failure(String message) {
            ArchetypeApplyResult result = new ArchetypeApplyResult();
            result.success = false;
            result.applied = false;
            result.message = message;
            return result;
        }
    }

    public static class UnlockResult {
        public boolean success;
        public String message;
        public int earnedPoints;
        public int spentPoints;
        public int availablePoints;
        public int skillLevel;
        public String suggestedNextNodeId;
    }

    public UnlockResult unlockNode(ResourceLocation skillId, String nodeId) {
        UnlockResult res = new UnlockResult();
        if (skillId == null || nodeId == null || nodeId.isEmpty()) {
            res.success = false;
            res.message = "Invalid skill or node id";
            return res;
        }
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            res.success = false;
            res.message = "Unknown canonical skill";
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
        SkillTreeProgression.TreeSnapshot snapshot = SkillTreeProgression.snapshot(this, skillId);
        res.skillLevel = snapshot.skillLevel();
        res.earnedPoints = snapshot.earnedPoints();
        res.spentPoints = snapshot.spentPoints();
        res.availablePoints = snapshot.availablePoints();
        res.suggestedNextNodeId = snapshot.suggestedNextNode().map(nodeSnapshot -> nodeSnapshot.node().id()).orElse(null);

        SkillTreeProgression.NodeSnapshot nodeSnapshot = snapshot.nodes().stream()
                .filter(candidate -> candidate.node().id().equals(nodeId))
                .findFirst()
                .orElse(null);
        if (nodeSnapshot == null) {
            res.success = false;
            res.message = "Unknown node";
            return res;
        }

        switch (nodeSnapshot.status()) {
            case UNLOCKED -> {
                res.success = false;
                res.message = "Already unlocked";
                return res;
            }
            case LOCKED_SKILL_LEVEL -> {
                int requiredLevel = SkillTreeProgression.requiredLevelFor(tree, nodeSnapshot.node());
                res.success = false;
                res.message = "Requires " + skillId.getPath() + " level " + requiredLevel;
                return res;
            }
            case LOCKED_PREREQUISITE -> {
                res.success = false;
                res.message = "Missing prerequisite: " + String.join(", ", nodeSnapshot.missingRequirements());
                return res;
            }
            case LOCKED_MASTERY_POINTS -> {
                res.success = false;
                res.message = "Not enough specialization points";
                return res;
            }
            case AVAILABLE -> {
                int cost = nodeSnapshot.node().normalizedCost();
                java.util.HashSet<String> unlocked = treeUnlockedNodes.computeIfAbsent(skillId, key -> new java.util.HashSet<>());
                unlocked.add(nodeId);
                this.treePointsSpent.put(skillId, getTreePointsSpent(skillId) + cost);
                res.success = true;
                res.spentPoints = SpecializationProgression.spentPoints(this);
                res.availablePoints = SpecializationProgression.availablePoints(this);
                res.message = "Unlocked";
                res.suggestedNextNodeId = SkillTreeProgression.snapshot(this, skillId)
                        .suggestedNextNode()
                        .map(next -> next.node().id())
                        .orElse(null);
                return res;
            }
        }

        res.success = false;
        res.message = "Unlock failed";
        return res;
    }
}
