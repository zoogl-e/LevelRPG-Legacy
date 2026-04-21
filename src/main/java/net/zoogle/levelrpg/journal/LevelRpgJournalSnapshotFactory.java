package net.zoogle.levelrpg.journal;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.data.RecipeSkillRequirement;
import net.zoogle.levelrpg.data.RecipeUnlockDefinition;
import net.zoogle.levelrpg.data.RecipeUnlockRegistry;
import net.zoogle.levelrpg.data.SkillDefinition;
import net.zoogle.levelrpg.data.SkillRegistry;
import net.zoogle.levelrpg.data.SkillTreeDefinition;
import net.zoogle.levelrpg.profile.ArchetypeDefinition;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillProgressView;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.progression.PassiveSkillSummary;
import net.zoogle.levelrpg.progression.MasteryLeveling;
import net.zoogle.levelrpg.progression.RecipeUnlockService;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import net.zoogle.levelrpg.progression.SkillPointProgression;
import net.zoogle.levelrpg.progression.SkillTreeProgression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Dedicated projection layer that turns raw progression state into a stable
 * journal-facing read model for Enchiridion and similar book UIs.
 */
public final class LevelRpgJournalSnapshotFactory {
    private LevelRpgJournalSnapshotFactory() {}

    public static JournalProfileSnapshot create(LevelProfile profile) {
        Objects.requireNonNull(profile, "profile");
        profile.ensureCanonicalSkills();

        JournalCharacterLedgerSnapshot ledger = buildCharacterLedger(profile);
        ArrayList<JournalSkillSnapshot> skills = new ArrayList<>(ProgressionSkill.values().length);
        for (ProgressionSkill progressionSkill : ProgressionSkill.values()) {
            skills.add(buildSkillSnapshot(profile, progressionSkill));
        }
        return new JournalProfileSnapshot(ledger, List.copyOf(skills));
    }

    private static JournalCharacterLedgerSnapshot buildCharacterLedger(LevelProfile profile) {
        ArchetypeDefinition archetype = profile.getSelectedArchetype();
        String archetypeName = archetype != null ? safe(archetype.displayName()) : "";
        String archetypeDescription = archetype != null ? safe(archetype.description()) : "";
        int totalSkillLevels = SpecializationProgression.totalCanonicalLevels(profile);
        int totalMasteryLevels = totalMasteryLevels(profile);
        int earnedSkillPoints = SkillPointProgression.earnedPoints(profile);
        int spentSkillPoints = SkillPointProgression.spentPoints(profile);
        int availableSkillPoints = SkillPointProgression.availablePoints(profile);
        int earnedSpecializationPoints = SpecializationProgression.earnedPoints(profile);
        int spentSpecializationPoints = SpecializationProgression.spentPoints(profile);
        int availableSpecializationPoints = SpecializationProgression.availablePoints(profile);

        ArrayList<JournalCharacterLedgerSnapshot.Row> rows = new ArrayList<>(ProgressionSkill.values().length);
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            SkillProgressView progress = profile.skillProgress(skill);
            JournalPassiveEffectSnapshot passiveEffects = toJournalPassiveEffects(
                    PassiveSkillScalingService.summarize(profile, skill)
            );
            rows.add(new JournalCharacterLedgerSnapshot.Row(
                    skill.id(),
                    displayNameFor(skill.id()),
                    progress.investedSkillLevel(),
                    0L,
                    0L,
                    progress.masteryLevel(),
                    progress.masteryProgress(),
                    progress.masteryRequiredForNextLevel(),
                    progress.masteryProgress(),
                    progress.masteryRequiredForNextLevel(),
                    progress.masteryProgress() + "/" + progress.masteryRequiredForNextLevel(),
                    progress.canSpendSkillPoint(),
                    passiveEffects
            ));
        }
        return new JournalCharacterLedgerSnapshot(
                archetypeName,
                archetypeDescription,
                profile.hasAppliedStartingArchetype(),
                totalSkillLevels,
                totalMasteryLevels,
                earnedSkillPoints,
                spentSkillPoints,
                availableSkillPoints,
                earnedSpecializationPoints,
                spentSpecializationPoints,
                availableSpecializationPoints,
                List.copyOf(rows)
        );
    }

    private static JournalSkillSnapshot buildSkillSnapshot(LevelProfile profile, ProgressionSkill progressionSkill) {
        ResourceLocation skillId = progressionSkill.id();
        SkillProgressView progress = profile.skillProgress(progressionSkill);
        SkillState state = profile.getSkill(progressionSkill);
        SkillDefinition definition = SkillRegistry.get(skillId);
        String summary = skillSummary(definition);
        JournalPassiveEffectSnapshot passiveEffects = toJournalPassiveEffects(
                PassiveSkillScalingService.summarize(profile, progressionSkill)
        );

        return new JournalSkillSnapshot(
                skillId,
                displayNameFor(skillId),
                definition != null && definition.display() != null ? safe(definition.display().color()) : "",
                definition != null && definition.display() != null ? safe(definition.display().icon()) : "",
                summary,
                progress.investedSkillLevel(),
                0L,
                0L,
                progress.investedSkillLevel(),
                progress.masteryLevel(),
                progress.masteryProgress(),
                progress.masteryRequiredForNextLevel(),
                progress.masteryProgress(),
                progress.masteryRequiredForNextLevel(),
                progress.masteryProgress() + "/" + progress.masteryRequiredForNextLevel(),
                progress.canSpendSkillPoint(),
                passiveEffects,
                buildMasterySnapshot(profile, skillId),
                buildRecipeUnlocks(profile, skillId, progress.investedSkillLevel())
        );
    }

    private static JournalMasterySnapshot buildMasterySnapshot(LevelProfile profile, ResourceLocation skillId) {
        SkillTreeProgression.TreeSnapshot tree = SkillTreeProgression.snapshot(profile, skillId);
        SkillTreeDefinition definition = tree.tree();

        ArrayList<JournalUnlockSnapshot> milestones = new ArrayList<>();
        if (definition != null) {
            for (SkillTreeDefinition.Threshold threshold : definition.thresholds()) {
                milestones.add(new JournalUnlockSnapshot(
                        JournalUnlockSnapshot.Kind.MILESTONE,
                        threshold.id(),
                        fallbackTitle(threshold.title(), "Level " + threshold.level()),
                        safe(threshold.description()),
                        tree.skillLevel(),
                        threshold.level(),
                        tree.skillLevel() >= threshold.level()
                ));
            }
        }

        JournalUnlockSnapshot nextThreshold = tree.nextThreshold()
                .map(threshold -> new JournalUnlockSnapshot(
                        JournalUnlockSnapshot.Kind.MILESTONE,
                        threshold.id(),
                        fallbackTitle(threshold.title(), "Level " + threshold.level()),
                        safe(threshold.description()),
                        tree.skillLevel(),
                        threshold.level(),
                        false
                ))
                .orElse(null);

        ArrayList<JournalMasterySnapshot.NodeSnapshot> nodes = new ArrayList<>(tree.nodes().size());
        for (SkillTreeProgression.NodeSnapshot node : tree.nodes()) {
            nodes.add(new JournalMasterySnapshot.NodeSnapshot(
                    node.node().id(),
                    fallbackTitle(node.node().title(), humanize(node.node().id())),
                    safe(node.node().description()),
                    safe(node.node().branch()),
                    node.node().normalizedCost(),
                    SkillTreeProgression.requiredLevelFor(definition, node.node()),
                    mapStatus(node.status()),
                    describeMissingNodes(definition, node.missingRequirements())
            ));
        }

        return new JournalMasterySnapshot(
                definition != null,
                definition != null ? fallbackTitle(definition.title(), displayNameFor(skillId) + " Mastery") : "",
                definition != null ? safe(definition.summary()) : "",
                tree.skillLevel(),
                tree.unlockedTiers(),
                tree.earnedPoints(),
                tree.spentPoints(),
                tree.availablePoints(),
                nextThreshold,
                List.copyOf(milestones),
                List.copyOf(nodes),
                tree.suggestedNextNode().map(node -> node.node().id()).orElse("")
        );
    }

    private static List<JournalUnlockSnapshot> buildRecipeUnlocks(LevelProfile profile, ResourceLocation skillId, int skillLevel) {
        ArrayList<JournalUnlockSnapshot> unlocks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, RecipeUnlockDefinition> entry : RecipeUnlockRegistry.entries()) {
            RecipeUnlockDefinition definition = entry.getValue();
            if (definition == null) {
                continue;
            }
            RecipeSkillRequirement matchingRequirement = null;
            for (RecipeSkillRequirement requirement : definition.requirements()) {
                if (skillId.equals(requirement.skillId())) {
                    matchingRequirement = requirement;
                    break;
                }
            }
            if (matchingRequirement == null) {
                continue;
            }

            boolean unlocked = RecipeUnlockService.checkAccess(profile, entry.getKey()).unlocked();
            unlocks.add(new JournalUnlockSnapshot(
                    JournalUnlockSnapshot.Kind.RECIPE,
                    entry.getKey().toString(),
                    humanize(entry.getKey().getPath()),
                    describeRecipeRequirements(definition, skillId),
                    skillLevel,
                    matchingRequirement.minLevel(),
                    unlocked
            ));
        }
        unlocks.sort(Comparator
                .comparingInt(JournalUnlockSnapshot::requiredLevel)
                .thenComparing(JournalUnlockSnapshot::title));
        return List.copyOf(unlocks);
    }

    private static JournalMasterySnapshot.Status mapStatus(SkillTreeProgression.NodeStatus status) {
        if (status == null) {
            return JournalMasterySnapshot.Status.LOCKED;
        }
        return switch (status) {
            case UNLOCKED -> JournalMasterySnapshot.Status.UNLOCKED;
            case AVAILABLE -> JournalMasterySnapshot.Status.AVAILABLE;
            case LOCKED_SKILL_LEVEL -> JournalMasterySnapshot.Status.LOCKED_LEVEL;
            case LOCKED_PREREQUISITE -> JournalMasterySnapshot.Status.LOCKED_PREREQUISITE;
            case LOCKED_MASTERY_POINTS -> JournalMasterySnapshot.Status.LOCKED_POINTS;
        };
    }

    private static List<String> describeMissingNodes(SkillTreeDefinition tree, List<String> missingIds) {
        if (tree == null || missingIds == null || missingIds.isEmpty()) {
            return List.of();
        }
        ArrayList<String> labels = new ArrayList<>(missingIds.size());
        for (String missingId : missingIds) {
            SkillTreeDefinition.Node node = tree.nodes().get(missingId);
            if (node != null) {
                labels.add(fallbackTitle(node.title(), humanize(node.id())));
            } else {
                labels.add(humanize(missingId));
            }
        }
        return List.copyOf(labels);
    }

    private static String describeRecipeRequirements(RecipeUnlockDefinition definition, ResourceLocation focusSkillId) {
        if (definition == null || definition.requirements().isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (RecipeSkillRequirement requirement : definition.requirements()) {
            String label = displayNameFor(requirement.skillId()) + " " + requirement.minLevel();
            if (!requirement.skillId().equals(focusSkillId)) {
                label = "Also requires " + label;
            }
            joiner.add(label);
        }
        return joiner.toString();
    }

    private static String skillSummary(SkillDefinition definition) {
        if (definition != null && definition.display() != null) {
            return safe(definition.display().notes());
        }
        return "";
    }

    private static JournalPassiveEffectSnapshot toJournalPassiveEffects(PassiveSkillSummary summary) {
        if (summary == null) {
            return new JournalPassiveEffectSnapshot("", "", false, List.of());
        }
        ArrayList<JournalPassiveEffectSnapshot.Entry> entries = new ArrayList<>(summary.entries().size());
        for (PassiveSkillSummary.Entry entry : summary.entries()) {
            entries.add(new JournalPassiveEffectSnapshot.Entry(
                    entry.label(),
                    entry.valueText(),
                    entry.value(),
                    entry.description()
            ));
        }
        return new JournalPassiveEffectSnapshot(
                summary.title(),
                summary.summary(),
                summary.implemented(),
                List.copyOf(entries)
        );
    }

    private static String displayNameFor(ResourceLocation skillId) {
        String displayName = SkillRegistry.getDisplayName(skillId);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return ProgressionSkill.fromId(skillId)
                .map(ProgressionSkill::displayName)
                .orElseGet(() -> humanize(skillId.getPath()));
    }

    private static String fallbackTitle(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static String humanize(String raw) {
        String normalized = raw == null ? "" : raw.trim().replace('_', ' ').replace('-', ' ');
        if (normalized.isEmpty()) {
            return "";
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int totalMasteryLevels(LevelProfile profile) {
        int total = 0;
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            total += Math.max(0, profile.getSkill(skill).masteryLevel);
        }
        return total;
    }
}
