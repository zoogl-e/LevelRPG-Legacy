package net.zoogle.levelrpg.bounty;

/**
 * Which objective types have server-side completion hooks wired. Expand as handlers are added.
 */
public final class BountyObjectiveHandlers {
    private BountyObjectiveHandlers() {}

    public static boolean isImplemented(BountyObjectiveType type) {
        if (type == null || type == BountyObjectiveType.NONE) {
            return false;
        }
        return switch (type) {
            case INDEX_INVEST_ONCE, REACH_Y, KILL_HOSTILE_MOB, MINE_ORE -> true;
            case CRAFT_ITEM,
                    PLACE_BLOCKS,
                    REPAIR_ITEM,
                    ENCHANT_ITEM,
                    COOK_ITEMS,
                    NONE -> false;
        };
    }
}
