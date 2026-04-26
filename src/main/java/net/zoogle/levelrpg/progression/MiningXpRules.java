package net.zoogle.levelrpg.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.profile.ProgressionSkill;

/**
 * Canonical Delving rules. Underground and underwater mastery — ores,
 * stone-like blocks, and the hidden places most players avoid.
 */
public final class MiningXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.DELVING.id();

    private MiningXpRules() {}

    public static long xpForBlockBreak(BlockState state) {
        if (state == null) {
            return 0L;
        }
        if (isOre(state)) {
            return 8L;
        }
        if (isStoneLike(state)) {
            return 1L;
        }
        return 0L;
    }

    public static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES);
    }

    public static boolean isStoneLike(BlockState state) {
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return "tuff".equals(path)
                || "calcite".equals(path)
                || "cobblestone".equals(path)
                || "cobbled_deepslate".equals(path)
                || "blackstone".equals(path)
                || "end_stone".equals(path);
    }
}
