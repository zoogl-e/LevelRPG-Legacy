package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.gauge.PlayerGaugeData;
import net.zoogle.levelrpg.progression.MasteryLeveling;
import net.zoogle.levelrpg.progression.ProficiencyProgressionService;
import net.zoogle.levelrpg.progression.DisciplineInvestmentProgression;
import net.zoogle.levelrpg.progression.DisciplineInvestmentSource;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import net.zoogle.levelrpg.progression.SkillPointProgression;
import net.zoogle.levelrpg.progression.SkillTreeProgression;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;

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
 *
 * <p><b>Design-aligned read vocabulary</b> (current persistence still uses legacy field names; see
 * {@link #practiceProgress}, {@link #practiceRank}, {@link #investedDisciplineLevel},
 * {@link #uncommittedDisciplineInvestmentPoints}, {@link #totalDisciplineInvestmentPointsEarned},
 * {@link #totalInvestedDisciplineLevels}, {@link #globalInsightAvailable}, etc.).
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

    /**
     * Legacy NBT/sync name: uncommitted pool that pays for raising {@link SkillState#level} (invested Discipline Level).
     * This is <b>not</b> design Essence (Essence is a separate future currency in {@code LEVELRPG_PROGRESSION_DESIGN.md}).
     * It is also <b>not</b> tree Insight; see {@link #globalInsightAvailable()}.
     * This field is now retained for compatibility/debug paths and is not the active
     * Discipline investment currency.
     *
     * @see #uncommittedDisciplineInvestmentPoints()
     */
    public int availableSkillPoints = 0;

    /**
     * Bookkeeping total for how much of the discipline-investment pool has been spent into {@link SkillState#level}.
     * Retained for compatibility/debug paths while active investment uses Essence.
     *
     * @see #spentDisciplineInvestmentPoints()
     */
    public int spentSkillPoints = 0;

    /**
     * Extra Insight earned toward the <b>global</b> tree pool (legacy name {@code bonusSpecializationPoints} in NBT).
     * Insight is still derived and shared across trees until a per-discipline model exists.
     *
     * @see #globalInsightEarned()
     */
    public int bonusSpecializationPoints = 0;
    /**
     * Global Essence currency.
     *
     * <p>Essence is not Practice, not Potential, and not Insight. In future phases this
     * will pay for Discipline Level investment. For now it is persisted, synced, and
     * testable without replacing the legacy discipline-investment point loop.
     */
    private int essence = 0;
    private ResourceLocation activeSoloBountyId;
    private boolean activeSoloBountyObjectiveMet;
    /** Progress toward countable objectives (kills, ore breaks, etc.); reset when a bounty is claimed or cleared. */
    private int activeSoloBountyProgress;
    private int bountyOfferTier = 1;
    private final LinkedHashSet<ResourceLocation> completedSoloBounties = new LinkedHashSet<>();

    public final ArchetypeState archetype = new ArchetypeState();
    public final PlayerGaugeData gauges = new PlayerGaugeData();
    public final PlayerTechniqueData techniques = new PlayerTechniqueData();
    public final LinkedHashMap<ResourceLocation, SkillState> skills = new LinkedHashMap<>();
    private final LinkedHashSet<ResourceLocation> claimedAdvancementEssenceRewards = new LinkedHashSet<>();
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

    /**
     * Invested Discipline Level for this discipline (legacy accessor name {@code investedSkillLevel}).
     *
     * @see #investedDisciplineLevel(ResourceLocation)
     */
    public int investedSkillLevel(ResourceLocation skillId) {
        return investedDisciplineLevel(skillId);
    }

    /**
     * Practice rank for this discipline (legacy short name {@code rank}); <b>not</b> invested Discipline Level.
     *
     * @see #practiceRank(ResourceLocation)
     */
    public int rank(ResourceLocation skillId) {
        return practiceRank(skillId);
    }

    public long proficiency(ResourceLocation skillId) {
        return practiceProgress(skillId);
    }

    public long proficiencyRequiredForNextRank(ResourceLocation skillId) {
        return MasteryLeveling.xpToNextLevel(skillId, practiceRank(skillId));
    }

    /**
     * Organic practice progress toward the next {@link #practiceRank} for this discipline.
     * Maps to {@link SkillState#proficiency}.
     */
    public long practiceProgress(ResourceLocation skillId) {
        return Math.max(0L, getSkill(skillId).proficiency);
    }

    public long practiceProgress(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return practiceProgress(skill.id());
    }

    /**
     * Current practice rank for this discipline (stand-in for a future explicit Potential cap in design).
     * This is <b>not</b> {@link #investedDisciplineLevel}; maps to {@link SkillState#rank}.
     */
    public int practiceRank(ResourceLocation skillId) {
        return Math.max(0, getSkill(skillId).rank);
    }

    public int practiceRank(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return practiceRank(skill.id());
    }

    /**
     * Invested Discipline Level (chosen build) for this discipline. Maps to {@link SkillState#level}.
     */
    public int investedDisciplineLevel(ResourceLocation skillId) {
        return Math.max(0, getSkill(skillId).level);
    }

    public int investedDisciplineLevel(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return investedDisciplineLevel(skill.id());
    }

    public SkillProgressView skillProgress(ResourceLocation skillId) {
        return new SkillProgressView(
                skillId,
                investedDisciplineLevel(skillId),
                practiceRank(skillId),
                practiceProgress(skillId),
                proficiencyRequiredForNextRank(skillId),
                SkillPointProgression.canSpendPoint(this, skillId)
        );
    }

    public SkillProgressView skillProgress(ProgressionSkill skill) {
        Objects.requireNonNull(skill, "skill");
        return skillProgress(skill.id());
    }

    /**
     * Uncommitted discipline-investment pool (raises {@link SkillState#level} when spent). Same backing field as
     * {@link #availableSkillPoints}; <b>not</b> Essence and <b>not</b> tree Insight.
     */
    public int uncommittedDisciplineInvestmentPoints() {
        return SkillPointProgression.insight(this);
    }

    /**
     * How much of the discipline-investment pool has been committed into levels (bookkeeping).
     */
    public int spentDisciplineInvestmentPoints() {
        return SkillPointProgression.spentPoints(this);
    }

    /**
     * Global tree Insight still available (single derived pool shared by all discipline trees until per-discipline storage exists).
     */
    public int globalInsightAvailable() {
        return SpecializationProgression.insight(this);
    }

    /**
     * Global tree Insight earned from invested Discipline Levels (thresholds) plus {@link #bonusSpecializationPoints}.
     */
    public int globalInsightEarned() {
        return SpecializationProgression.gainedInsight(this);
    }

    /**
     * Global tree Insight already inscribed (sum of per-tree {@link #treePointsSpent} costs).
     */
    public int globalInsightInscribed() {
        return SpecializationProgression.inscribedPoints(this);
    }

    /**
     * Global Essence currently held on this profile.
     */
    public int essence() {
        return Math.max(0, essence);
    }

    /**
     * Adds global Essence.
     */
    public void grantEssence(int amount) {
        if (amount <= 0) {
            return;
        }
        essence = Math.max(0, essence + amount);
    }

    /**
     * Removes up to {@code amount} Essence (clamped at zero).
     */
    public void takeEssence(int amount) {
        if (amount <= 0) {
            return;
        }
        essence = Math.max(0, essence - amount);
    }

    /**
     * Sets global Essence to a non-negative value.
     */
    public void setEssence(int amount) {
        essence = Math.max(0, amount);
    }

    /**
     * Whether at least {@code amount} Essence is available to spend.
     */
    public boolean canSpendEssence(int amount) {
        return amount >= 0 && essence() >= amount;
    }

    /**
     * Attempts to spend {@code amount} Essence.
     *
     * @return true if the full amount was available and spent.
     */
    public boolean spendEssence(int amount) {
        if (amount < 0 || !canSpendEssence(amount)) {
            return false;
        }
        essence = Math.max(0, essence - amount);
        return true;
    }

    public boolean hasClaimedAdvancementEssence(ResourceLocation advancementId) {
        if (advancementId == null) {
            return false;
        }
        return claimedAdvancementEssenceRewards.contains(advancementId);
    }

    public void markAdvancementEssenceClaimed(ResourceLocation advancementId) {
        if (advancementId == null) {
            return;
        }
        claimedAdvancementEssenceRewards.add(advancementId);
    }

    /**
     * Claims one-time Essence for a completed advancement.
     *
     * @return true when reward was newly claimed and granted.
     */
    public boolean claimAdvancementEssenceReward(ResourceLocation advancementId, int amount) {
        if (advancementId == null || amount <= 0 || hasClaimedAdvancementEssence(advancementId)) {
            return false;
        }
        grantEssence(amount);
        markAdvancementEssenceClaimed(advancementId);
        return true;
    }

    public boolean hasActiveSoloBounty() {
        return activeSoloBountyId != null;
    }

    public ResourceLocation activeSoloBountyId() {
        return activeSoloBountyId;
    }

    public boolean activeSoloBountyObjectiveMet() {
        return activeSoloBountyObjectiveMet;
    }

    public int activeSoloBountyProgress() {
        return Math.max(0, activeSoloBountyProgress);
    }

    public void setActiveSoloBounty(ResourceLocation bountyId) {
        this.activeSoloBountyId = bountyId;
        this.activeSoloBountyObjectiveMet = false;
        this.activeSoloBountyProgress = 0;
    }

    public void markActiveSoloBountyObjectiveMet() {
        this.activeSoloBountyObjectiveMet = true;
    }

    /**
     * Increments progress for the active solo bounty (countable objectives). Clamped at zero.
     *
     * @return progress after applying the delta
     */
    public int addActiveSoloBountyProgress(int delta) {
        if (delta <= 0) {
            return activeSoloBountyProgress();
        }
        this.activeSoloBountyProgress = Math.max(0, this.activeSoloBountyProgress + delta);
        return this.activeSoloBountyProgress;
    }

    public void clearActiveSoloBounty() {
        this.activeSoloBountyId = null;
        this.activeSoloBountyObjectiveMet = false;
        this.activeSoloBountyProgress = 0;
    }

    public int bountyOfferTier() {
        return Math.clamp(bountyOfferTier, 1, 3);
    }

    public void setBountyOfferTier(int tier) {
        this.bountyOfferTier = Math.clamp(tier, 1, 3);
    }

    public int increaseBountyOfferTier() {
        this.bountyOfferTier = Math.clamp(this.bountyOfferTier + 1, 1, 3);
        return this.bountyOfferTier;
    }

    public int bountyOfferCount() {
        return switch (bountyOfferTier()) {
            case 3 -> 6;
            case 2 -> 4;
            default -> 2;
        };
    }

    public int bountyOfferSpreadCount() {
        return bountyOfferCount() / 2;
    }

    public boolean hasCompletedBounty(ResourceLocation bountyId) {
        return bountyId != null && completedSoloBounties.contains(bountyId);
    }

    public void markBountyCompleted(ResourceLocation bountyId) {
        if (bountyId == null) {
            return;
        }
        completedSoloBounties.add(bountyId);
    }

    public Set<ResourceLocation> completedBounties() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(completedSoloBounties));
    }

    /**
     * Active Discipline investment path: spends Essence and applies Potential/total-cap checks.
     */
    public DisciplineInvestmentProgression.InvestmentResult spendEssenceForDisciplineLevel(
            ResourceLocation skillId,
            DisciplineInvestmentSource source
    ) {
        return DisciplineInvestmentProgression.investOne(this, skillId, source);
    }

    /**
     * Total discipline-investment points ever earned on this profile (committed + uncommitted) for the legacy pool
     * that raises {@link SkillState#level}. Delegates to {@link SkillPointProgression#earnedPoints(LevelProfile)}.
     *
     * <p><b>Not</b> design Essence. Today this total still reflects the existing practice-rank-up grant loop that
     * feeds {@link #uncommittedDisciplineInvestmentPoints()}.
     */
    public int totalDisciplineInvestmentPointsEarned() {
        return SkillPointProgression.earnedPoints(this);
    }

    /**
     * Sum of invested Discipline Levels ({@link SkillState#level}) across all canonical disciplines. Delegates to
     * {@link SpecializationProgression#totalCanonicalLevels(LevelProfile)}.
     *
     * <p>Used today to derive <b>global</b> tree Insight thresholds. This is <b>not</b> practice rank or Potential;
     * see {@link #practiceRank} per discipline.
     */
    public int totalInvestedDisciplineLevels() {
        return SpecializationProgression.totalCanonicalLevels(this);
    }

    /**
     * Same as {@link #uncommittedDisciplineInvestmentPoints()}; name retained for existing callers and sync payloads.
     */
    public int availableSkillPoints() {
        return uncommittedDisciplineInvestmentPoints();
    }

    /**
     * Same as {@link #spentDisciplineInvestmentPoints()}; name retained for existing callers and sync payloads.
     */
    public int spentSkillPoints() {
        return spentDisciplineInvestmentPoints();
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

    /**
     * Whether this profile currently has an archetype selected/bound.
     */
    public boolean hasBoundArchetype() {
        return archetype.id != null;
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

    public boolean clearArchetypeBinding() {
        boolean changed = archetype.id != null || archetype.startingDistributionApplied;
        archetype.id = null;
        archetype.startingDistributionApplied = false;
        return changed;
    }

    private void applyArchetypeLevels(ArchetypeDefinition definition) {
        ensureCanonicalSkills();
        for (Map.Entry<ProgressionSkill, Integer> entry : definition.startingLevels().entrySet()) {
            SkillState state = getSkill(entry.getKey());
            state.level = Math.max(state.level, entry.getValue());
            // Archetype baseline levels represent starting background/capability.
            // Potential stand-in (rank) must be at least the committed starting Discipline Level.
            state.rank = Math.max(state.rank, state.level);
            state.proficiency = Math.max(0L, state.proficiency);
        }
    }

    /**
     * Canonical proficiency grant path for per-skill practice progression.
     */
    public ProficiencyAwardResult awardProficiency(ResourceLocation skillId, long amount) {
        Objects.requireNonNull(skillId, "skillId");
        return ProficiencyProgressionService.award(this, skillId, amount);
    }

    /**
     * @deprecated Legacy compatibility/debug path only. This spends the deprecated
     * {@link #availableSkillPoints} pool and is not the active Discipline investment
     * flow. New gameplay code should use {@link #spendEssenceForDisciplineLevel(ResourceLocation, DisciplineInvestmentSource)}
     * (or {@link net.zoogle.levelrpg.progression.DisciplineInvestmentProgression} directly).
     */
    @Deprecated
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
        tag.putInt("bonusSpecializationPoints", bonusSpecializationPoints);
        tag.putInt("essence", essence);
        tag.putInt("bountyOfferTier", bountyOfferTier());
        if (activeSoloBountyId != null) {
            tag.putString("activeSoloBountyId", activeSoloBountyId.toString());
            tag.putBoolean("activeSoloBountyObjectiveMet", activeSoloBountyObjectiveMet);
            tag.putInt("activeSoloBountyProgress", activeSoloBountyProgress());
        }
        net.minecraft.nbt.ListTag completedBountyTag = new net.minecraft.nbt.ListTag();
        for (ResourceLocation bountyId : completedSoloBounties) {
            completedBountyTag.add(net.minecraft.nbt.StringTag.valueOf(bountyId.toString()));
        }
        tag.put("completedSoloBounties", completedBountyTag);
        net.minecraft.nbt.ListTag claimedAdvancementEssenceTag = new net.minecraft.nbt.ListTag();
        for (ResourceLocation advancementId : claimedAdvancementEssenceRewards) {
            claimedAdvancementEssenceTag.add(net.minecraft.nbt.StringTag.valueOf(advancementId.toString()));
        }
        tag.put("claimedAdvancementEssenceRewards", claimedAdvancementEssenceTag);
        tag.put("archetype", archetype.serialize());
        tag.put("gauges", gauges.serialize());
        tag.put("techniques", techniques.serialize());
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
        this.bonusSpecializationPoints = Math.max(0, tag.getInt("bonusSpecializationPoints"));
        this.essence = Math.max(0, tag.getInt("essence"));
        this.bountyOfferTier = Math.clamp(tag.getInt("bountyOfferTier"), 1, 3);
        this.activeSoloBountyId = null;
        if (tag.contains("activeSoloBountyId")) {
            try {
                this.activeSoloBountyId = ResourceLocation.parse(tag.getString("activeSoloBountyId"));
                this.activeSoloBountyObjectiveMet = tag.getBoolean("activeSoloBountyObjectiveMet");
                this.activeSoloBountyProgress = Math.max(0, tag.getInt("activeSoloBountyProgress"));
            } catch (Exception ignored) {
                this.activeSoloBountyId = null;
                this.activeSoloBountyObjectiveMet = false;
                this.activeSoloBountyProgress = 0;
            }
        }
        this.completedSoloBounties.clear();
        if (tag.contains("completedSoloBounties")) {
            var completedList = tag.getList("completedSoloBounties", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < completedList.size(); i++) {
                try {
                    this.completedSoloBounties.add(ResourceLocation.parse(completedList.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }
        this.claimedAdvancementEssenceRewards.clear();
        if (tag.contains("claimedAdvancementEssenceRewards")) {
            var claimedList = tag.getList("claimedAdvancementEssenceRewards", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < claimedList.size(); i++) {
                try {
                    this.claimedAdvancementEssenceRewards.add(ResourceLocation.parse(claimedList.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }
        if (tag.contains("archetype")) {
            this.archetype.deserialize(tag.getCompound("archetype"));
        } else {
            this.archetype.id = null;
            this.archetype.startingDistributionApplied = false;
        }
        if (tag.contains("gauges")) {
            this.gauges.deserialize(tag.getCompound("gauges"));
        }
        if (tag.contains("techniques")) {
            this.techniques.deserialize(tag.getCompound("techniques"));
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
        public int gainedInsight;
        public int inscribedPoints;
        public int insight;
        /** Invested Discipline Level from {@link SkillTreeProgression.TreeSnapshot#rank()} at attempt time (not practice rank). */
        public int rank;
        public String suggestedNextNodeId;
    }

    public UnlockResult unlockNode(ResourceLocation skillId, String nodeId) {
        UnlockResult res = new UnlockResult();
        if (skillId == null || nodeId == null || nodeId.isEmpty()) {
            res.success = false;
            res.message = "Invalid discipline or node id";
            return res;
        }
        if (!ProgressionSkill.isCanonicalId(skillId)) {
            res.success = false;
            res.message = "Unknown canonical discipline";
            return res;
        }
        var tree = net.zoogle.levelrpg.data.SkillTreeRegistry.get(skillId);
        if (tree == null) {
            res.success = false;
            res.message = "No discipline tree for this id";
            return res;
        }
        var node = tree.nodes() != null ? tree.nodes().get(nodeId) : null;
        if (node == null) {
            res.success = false;
            res.message = "Unknown node";
            return res;
        }
        SkillTreeProgression.TreeSnapshot snapshot = SkillTreeProgression.snapshot(this, skillId);
        res.rank = snapshot.rank();
        res.gainedInsight = snapshot.gainedInsight();
        res.inscribedPoints = snapshot.inscribedPoints();
        res.insight = snapshot.insight();
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
            case INSCRIBED -> {
                res.success = false;
                res.message = "Already inscribed";
                return res;
            }
            case LOCKED_SKILL_LEVEL -> {
                int requiredLevel = SkillTreeProgression.requiredLevelFor(tree, nodeSnapshot.node());
                res.success = false;
                res.message = "Requires " + skillId.getPath() + " Discipline Level " + requiredLevel;
                return res;
            }
            case LOCKED_PREREQUISITE -> {
                res.success = false;
                res.message = "Missing prerequisite: " + nodeSnapshot.node().requirement().describeForDebug();
                return res;
            }
            case LOCKED_INSIGHT -> {
                res.success = false;
                res.message = "Not enough Insight";
                return res;
            }
            case AVAILABLE -> {
                int cost = nodeSnapshot.node().normalizedCost();
                java.util.HashSet<String> unlocked = treeUnlockedNodes.computeIfAbsent(skillId, key -> new java.util.HashSet<>());
                unlocked.add(nodeId);
                this.treePointsSpent.put(skillId, getTreePointsSpent(skillId) + cost);
                res.success = true;
                res.inscribedPoints = globalInsightInscribed();
                res.insight = globalInsightAvailable();
                res.message = "Inscribed";
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
