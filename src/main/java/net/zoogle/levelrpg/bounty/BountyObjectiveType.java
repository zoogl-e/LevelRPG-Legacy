package net.zoogle.levelrpg.bounty;

/**
 * Bounty objective discriminator. Future JSON/datapack will deserialize into {@link BountyObjectiveSpec}.
 */
public enum BountyObjectiveType {
    NONE,
    INDEX_INVEST_ONCE,
    REACH_Y,
    KILL_HOSTILE_MOB,
    MINE_ORE,
    CRAFT_ITEM,
    PLACE_BLOCKS,
    REPAIR_ITEM,
    ENCHANT_ITEM,
    COOK_ITEMS
}
