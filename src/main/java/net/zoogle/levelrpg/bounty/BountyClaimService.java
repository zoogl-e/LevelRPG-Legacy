package net.zoogle.levelrpg.bounty;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.profile.LevelProfile;

public final class BountyClaimService {
    private BountyClaimService() {}

    public static ClaimResult claimSoloBounty(LevelProfile profile, ResourceLocation bountyId) {
        if (profile == null || bountyId == null) {
            return ClaimResult.INVALID_STATE;
        }
        BountyDefinition definition = BountyService.get(bountyId);
        if (definition == null) {
            return ClaimResult.UNKNOWN_BOUNTY;
        }
        if (!definition.objectiveImplemented()) {
            return ClaimResult.OBJECTIVE_NOT_IMPLEMENTED;
        }
        if (profile.hasActiveSoloBounty()) {
            return ClaimResult.ALREADY_HAS_ACTIVE_BOUNTY;
        }
        profile.setActiveSoloBounty(bountyId);
        return ClaimResult.SUCCESS;
    }

    public static AbandonResult abandonSoloBounty(LevelProfile profile) {
        if (profile == null) {
            return AbandonResult.INVALID_STATE;
        }
        if (!profile.hasActiveSoloBounty()) {
            return AbandonResult.NO_ACTIVE_BOUNTY;
        }
        profile.clearActiveSoloBounty();
        return AbandonResult.SUCCESS;
    }

    public enum ClaimResult {
        SUCCESS,
        UNKNOWN_BOUNTY,
        OBJECTIVE_NOT_IMPLEMENTED,
        ALREADY_HAS_ACTIVE_BOUNTY,
        INVALID_STATE
    }

    public enum AbandonResult {
        SUCCESS,
        NO_ACTIVE_BOUNTY,
        INVALID_STATE
    }
}

