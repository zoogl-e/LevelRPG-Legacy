package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public final class FinesseNodeIds {
    public static final ResourceLocation SKILL = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "finesse");

    /** Core node: Rhythm gauge + movement scaling ({@code finesse_rhythm} in skill tree JSON). */
    public static final String RHYTHM = "rhythm";

    public static final String PRECISION_SHOT = "precision_shot";
    public static final String HAND_TO_HAND_COMBAT = "hand_to_hand_combat";
    public static final String FLURRY_OF_BLOWS = "flurry_of_blows";
    public static final String HANDS_UP = "hands_up";
    public static final String VERSATILE_BRAWLER = "versatile_brawler";
    public static final String LUCKY_SHOT = "lucky_shot";
    public static final String QUICK_DRAW = "quick_draw";
    public static final String VERSATILE_SHOT = "versatile_shot";
    public static final String DESPERATE_MEASURE = "desperate_measure";
    public static final String SMOOTH_MOVES = "smooth_moves";
    public static final String ASSASSIN = "assassin";
    public static final String GHOST_STEP = "ghost_step";
    public static final String RAPID_VOLLEY = "rapid_volley";
    public static final String LIKE_THE_WIND = "like_the_wind";
    public static final String CALCULATED_SHOT = "calculated_shot";
    public static final String BLURRED_IMAGE = "blurred_image";
    public static final String DAVID_AND_GOLIATH = "david_and_goliath";
    public static final String UPPERCUT = "uppercut";
    public static final String PIERCING_SHOT = "piercing_shot";
    public static final String BLOT_OUT_THE_SUN = "blot_out_the_sun";

    private FinesseNodeIds() {
    }
}
