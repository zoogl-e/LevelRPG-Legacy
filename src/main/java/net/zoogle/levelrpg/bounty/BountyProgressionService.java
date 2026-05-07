package net.zoogle.levelrpg.bounty;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.zoogle.levelrpg.profile.LevelProfile;
import org.jetbrains.annotations.Nullable;

public final class BountyProgressionService {
    /** @deprecated Retained for callers that key off known ids; progression uses {@link BountyObjectiveSpec}. */
    @Deprecated
    public static final ResourceLocation FIRST_STEPS_ID = ResourceLocation.fromNamespaceAndPath("levelrpg", "first_steps");
    /** @deprecated Retained for callers that key off known ids; use {@link BountyObjectiveType#REACH_Y} and spec {@code targetY}. */
    @Deprecated
    public static final ResourceLocation DEEP_BREATH_ID = ResourceLocation.fromNamespaceAndPath("levelrpg", "deep_breath");
    /** @deprecated Use definition {@link BountyObjectiveSpec#targetY()} for REACH_Y bounties. */
    @Deprecated
    public static final int DEEP_BREATH_Y_THRESHOLD = 50;

    private BountyProgressionService() {}

    public static CompletionResult evaluateActiveSoloBountyDepthProgress(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return CompletionResult.failure(ProgressResult.INVALID_STATE);
        }
        ResourceLocation activeBountyId = profile.activeSoloBountyId();
        if (activeBountyId == null) {
            return CompletionResult.failure(ProgressResult.NO_ACTIVE_BOUNTY);
        }
        BountyDefinition definition = BountyService.get(activeBountyId);
        if (definition == null) {
            return CompletionResult.failure(ProgressResult.UNKNOWN_BOUNTY);
        }
        BountyObjectiveSpec spec = definition.objectiveSpec();
        if (spec.type() != BountyObjectiveType.REACH_Y) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        if (!BountyObjectiveHandlers.isImplemented(BountyObjectiveType.REACH_Y)) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        if (player.getBlockY() > spec.targetY()) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        profile.markActiveSoloBountyObjectiveMet();
        return completeActiveSoloBounty(player, profile, activeBountyId);
    }

    /**
     * Hostile kill credited to the player (server hook). Counts toward {@link BountyObjectiveType#KILL_HOSTILE_MOB}.
     */
    public static ObjectiveHookResult onHostileMobKilledByPlayer(ServerPlayer player, LevelProfile profile, LivingEntity victim) {
        if (player == null || profile == null || victim == null) {
            return ObjectiveHookResult.ignored();
        }
        if (victim instanceof ServerPlayer) {
            return ObjectiveHookResult.ignored();
        }
        if (!(victim instanceof Enemy)) {
            return ObjectiveHookResult.ignored();
        }
        ResourceLocation activeBountyId = profile.activeSoloBountyId();
        if (activeBountyId == null) {
            return ObjectiveHookResult.ignored();
        }
        BountyDefinition definition = BountyService.get(activeBountyId);
        if (definition == null) {
            return ObjectiveHookResult.ignored();
        }
        BountyObjectiveSpec spec = definition.objectiveSpec();
        if (spec.type() != BountyObjectiveType.KILL_HOSTILE_MOB) {
            return ObjectiveHookResult.ignored();
        }
        if (!BountyObjectiveHandlers.isImplemented(BountyObjectiveType.KILL_HOSTILE_MOB)) {
            return ObjectiveHookResult.ignored();
        }
        return advanceCountObjective(player, profile, activeBountyId, spec);
    }

    /**
     * Ore block broken by the player. Counts toward {@link BountyObjectiveType#MINE_ORE} when depth matches spec.
     */
    public static ObjectiveHookResult onOreBlockBrokenByPlayer(ServerPlayer player, LevelProfile profile, BlockState state, BlockPos pos) {
        if (player == null || profile == null || state == null || pos == null) {
            return ObjectiveHookResult.ignored();
        }
        if (!blockCountsAsBountyOre(state)) {
            return ObjectiveHookResult.ignored();
        }
        ResourceLocation activeBountyId = profile.activeSoloBountyId();
        if (activeBountyId == null) {
            return ObjectiveHookResult.ignored();
        }
        BountyDefinition definition = BountyService.get(activeBountyId);
        if (definition == null) {
            return ObjectiveHookResult.ignored();
        }
        BountyObjectiveSpec spec = definition.objectiveSpec();
        if (spec.type() != BountyObjectiveType.MINE_ORE) {
            return ObjectiveHookResult.ignored();
        }
        if (!BountyObjectiveHandlers.isImplemented(BountyObjectiveType.MINE_ORE)) {
            return ObjectiveHookResult.ignored();
        }
        int maxY = spec.targetY();
        if (maxY > 0 && pos.getY() > maxY) {
            return ObjectiveHookResult.ignored();
        }
        return advanceCountObjective(player, profile, activeBountyId, spec);
    }

    private static ObjectiveHookResult advanceCountObjective(
            ServerPlayer player,
            LevelProfile profile,
            ResourceLocation activeBountyId,
            BountyObjectiveSpec spec
    ) {
        int required = Math.max(1, spec.count());
        int progress = profile.addActiveSoloBountyProgress(1);
        if (progress >= required) {
            profile.markActiveSoloBountyObjectiveMet();
            return ObjectiveHookResult.finished(completeActiveSoloBounty(player, profile, activeBountyId));
        }
        return ObjectiveHookResult.progress();
    }

    public static boolean blockCountsAsBountyOre(BlockState state) {
        return state.is(Tags.Blocks.ORES)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES);
    }

    public record ObjectiveHookResult(boolean needsProfileSync, @Nullable CompletionResult completion) {
        public static ObjectiveHookResult ignored() {
            return new ObjectiveHookResult(false, null);
        }

        public static ObjectiveHookResult progress() {
            return new ObjectiveHookResult(true, null);
        }

        public static ObjectiveHookResult finished(CompletionResult completion) {
            return new ObjectiveHookResult(true, completion);
        }
    }

    public static CompletionResult onSuccessfulIndexInvestment(ServerPlayer player, LevelProfile profile) {
        if (player == null || profile == null) {
            return CompletionResult.failure(ProgressResult.INVALID_STATE);
        }
        ResourceLocation activeBountyId = profile.activeSoloBountyId();
        if (activeBountyId == null) {
            return CompletionResult.failure(ProgressResult.NO_ACTIVE_BOUNTY);
        }
        BountyDefinition definition = BountyService.get(activeBountyId);
        if (definition == null) {
            return CompletionResult.failure(ProgressResult.UNKNOWN_BOUNTY);
        }
        BountyObjectiveSpec spec = definition.objectiveSpec();
        if (spec.type() != BountyObjectiveType.INDEX_INVEST_ONCE) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        if (!BountyObjectiveHandlers.isImplemented(BountyObjectiveType.INDEX_INVEST_ONCE)) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        profile.markActiveSoloBountyObjectiveMet();
        return completeActiveSoloBounty(player, profile, activeBountyId);
    }

    public static CompletionResult completeActiveSoloBounty(ServerPlayer player, LevelProfile profile, ResourceLocation bountyId) {
        if (player == null || profile == null || bountyId == null) {
            return CompletionResult.failure(ProgressResult.INVALID_STATE);
        }
        ResourceLocation activeBountyId = profile.activeSoloBountyId();
        if (activeBountyId == null) {
            return CompletionResult.failure(ProgressResult.NO_ACTIVE_BOUNTY);
        }
        if (!bountyId.equals(activeBountyId)) {
            return CompletionResult.failure(ProgressResult.NOT_RELEVANT);
        }
        BountyDefinition definition = BountyService.get(bountyId);
        if (definition == null) {
            return CompletionResult.failure(ProgressResult.UNKNOWN_BOUNTY);
        }
        boolean firstCompletion = !profile.hasCompletedBounty(bountyId);
        int baseReward = Math.max(0, definition.rewardEssence());
        int firstCompletionBonus = firstCompletion ? Math.max(0, definition.firstCompletionBonusEssence()) : 0;
        int totalReward = Math.max(0, baseReward + firstCompletionBonus);
        if (totalReward > 0) {
            profile.grantEssence(totalReward);
        }
        if (firstCompletion) {
            profile.markBountyCompleted(bountyId);
        }
        profile.increaseBountyOfferTier();
        profile.clearActiveSoloBounty();
        return CompletionResult.success(bountyId, totalReward, baseReward, firstCompletionBonus, firstCompletion);
    }

    public record CompletionResult(
            ProgressResult progress,
            ResourceLocation bountyId,
            int totalRewardEssence,
            int baseRewardEssence,
            int firstCompletionBonusEssence,
            boolean firstCompletion
    ) {
        public static CompletionResult failure(ProgressResult progress) {
            return new CompletionResult(progress, null, 0, 0, 0, false);
        }

        public static CompletionResult success(
                ResourceLocation bountyId,
                int totalRewardEssence,
                int baseRewardEssence,
                int firstCompletionBonusEssence,
                boolean firstCompletion
        ) {
            return new CompletionResult(
                    ProgressResult.COMPLETED,
                    bountyId,
                    Math.max(0, totalRewardEssence),
                    Math.max(0, baseRewardEssence),
                    Math.max(0, firstCompletionBonusEssence),
                    firstCompletion
            );
        }

        public boolean completed() {
            return progress == ProgressResult.COMPLETED;
        }
    }

    public enum ProgressResult {
        NO_ACTIVE_BOUNTY,
        NOT_RELEVANT,
        PROGRESS_UPDATED,
        COMPLETED,
        UNKNOWN_BOUNTY,
        INVALID_STATE
    }
}
