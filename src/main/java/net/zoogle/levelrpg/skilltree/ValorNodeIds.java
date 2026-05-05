package net.zoogle.levelrpg.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public final class ValorNodeIds {
    public static final ResourceLocation SKILL = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "valor");

    /** Core node: Resolve gauge + crit scaling (see {@code valor_resolve} in skill tree JSON). */
    public static final String RESOLVE = "resolve";

    /** Duelist branch — vertical slice. */
    public static final String MELEE_COMBATANT = "melee_combatant";
    public static final String COMBO_BREAK = "combo_break";
    public static final String SEASONED_FIGHTER = "seasoned_fighter";
    public static final String MONSTER_HUNTER = "monster_hunter";
    public static final String MORTAL_WOUND = "mortal_wound";

    /** Vanguard branch. */
    public static final String VANGUARD = "vanguard";
    public static final String DEFLECTION = "deflection";
    public static final String SHIELD_BASH = "shield_bash";
    public static final String INPENETRABLE = "inpenetrable";
    public static final String GOAD = "goad";
    public static final String IMMOVABLE_OBJECT = "immovable_object";
    public static final String JUDGEMENT_DAY = "judgement_day";
    public static final String BATTERING_RAM = "battering_ram";
    public static final String PROTECTION_AURA = "protection_aura";
    public static final String BULWARK_OF_HOPE = "bulwark_of_hope";

    /** Duelist side branch (requires melee + combo). */
    public static final String FAR_REACH = "far_reach";
    public static final String FORWARD_FRENZY = "forward_frenzy";
    public static final String RECKLESS_STRIKE = "reckless_strike";
    public static final String CRESCENT_SLASH = "crescent_slash";
    public static final String STEELED = "steeled";

    private ValorNodeIds() {
    }
}
