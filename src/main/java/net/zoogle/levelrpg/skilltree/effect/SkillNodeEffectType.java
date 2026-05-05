package net.zoogle.levelrpg.skilltree.effect;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

public final class SkillNodeEffectType {
    public static final ResourceLocation PASSIVE_MODIFIER = id("passive_modifier");
    public static final ResourceLocation GRANT_TECHNIQUE = id("grant_technique");
    public static final ResourceLocation UNLOCK_GAUGE = id("unlock_gauge");
    public static final ResourceLocation GAUGE_MODIFIER = id("gauge_modifier");
    public static final ResourceLocation AXIOM_MODIFIER = id("axiom_modifier");
    public static final ResourceLocation EVENT_LISTENER = id("event_listener");

    private SkillNodeEffectType() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }
}
