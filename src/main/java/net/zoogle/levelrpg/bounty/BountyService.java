package net.zoogle.levelrpg.bounty;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny hardcoded v1 bounty catalog.
 * TODO(bounties): Move to datapack JSON loading once the offer pipeline stabilizes.
 */
public final class BountyService {
    private static final Map<ResourceLocation, BountyDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "first_steps"),
                "First Inscription",
                "The Bookmark urges your first true commitment.",
                "Commit Essence at The Index.",
                1,
                1,
                BountyObjectiveSpec.indexInvestOnce(),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "valor"))
        ));
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "deep_breath"),
                "A Deep Breath",
                "Descend, endure, and return to the light.",
                "Venture below the surface and return.",
                1,
                1,
                BountyObjectiveSpec.reachY(50),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "finesse"))
        ));
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "embers_of_valor"),
                "Embers of Valor",
                "Courage glows brightest after the clash.",
                "Slay a hostile creature.",
                1,
                1,
                BountyObjectiveSpec.killHostileMob(1),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "valor"))
        ));
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "steady_hands"),
                "Steady Hands",
                "Stillness sharpens the path before commitment.",
                "Practice precision before committing your next path.",
                1,
                1,
                BountyObjectiveSpec.none(),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "finesse"))
        ));
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "stone_memory"),
                "Stone Memory",
                "The earth keeps counsel for patient listeners.",
                "Mine 3 ore blocks at Y 32 or below.",
                1,
                1,
                BountyObjectiveSpec.mineOre(3, 32),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "delving"))
        ));
        register(new BountyDefinition(
                ResourceLocation.fromNamespaceAndPath("levelrpg", "warm_hearth"),
                "Warm Hearth",
                "Preparation is its own quiet vow.",
                "Prepare yourself or another before the next journey.",
                1,
                1,
                BountyObjectiveSpec.none(),
                List.of(ResourceLocation.fromNamespaceAndPath("levelrpg", "hearth"))
        ));
    }

    private BountyService() {}

    public static List<BountyDefinition> firstOfferSpread() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static boolean isKnownBounty(ResourceLocation bountyId) {
        return bountyId != null && DEFINITIONS.containsKey(bountyId);
    }

    public static BountyDefinition get(ResourceLocation bountyId) {
        return bountyId == null ? null : DEFINITIONS.get(bountyId);
    }

    private static void register(BountyDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }
}
