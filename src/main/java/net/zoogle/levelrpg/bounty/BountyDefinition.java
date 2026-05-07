package net.zoogle.levelrpg.bounty;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Minimal v1 bounty definition for Bookmark offer rendering.
 */
public record BountyDefinition(
        ResourceLocation id,
        String title,
        String summary,
        String objective,
        int rewardEssence,
        int firstCompletionBonusEssence,
        BountyObjectiveSpec objectiveSpec,
        List<ResourceLocation> disciplineTags
) {
    public BountyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(objectiveSpec, "objectiveSpec");
        disciplineTags = disciplineTags == null ? List.of() : List.copyOf(disciplineTags);
    }

    /** Claimable only when the objective type has a wired completion path ({@link BountyObjectiveHandlers}). */
    public boolean objectiveImplemented() {
        return BountyObjectiveHandlers.isImplemented(objectiveSpec.type());
    }
}
