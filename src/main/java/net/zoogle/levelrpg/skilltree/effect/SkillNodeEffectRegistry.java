package net.zoogle.levelrpg.skilltree.effect;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SkillNodeEffectRegistry {
    private static final LinkedHashSet<ResourceLocation> KNOWN_TYPES = new LinkedHashSet<>();

    static {
        registerType(SkillNodeEffectType.PASSIVE_MODIFIER);
        registerType(SkillNodeEffectType.GRANT_TECHNIQUE);
        registerType(SkillNodeEffectType.UNLOCK_GAUGE);
        registerType(SkillNodeEffectType.GAUGE_MODIFIER);
        registerType(SkillNodeEffectType.AXIOM_MODIFIER);
        registerType(SkillNodeEffectType.EVENT_LISTENER);
    }

    private SkillNodeEffectRegistry() {
    }

    public static void registerType(ResourceLocation type) {
        if (type != null) {
            KNOWN_TYPES.add(type);
        }
    }

    public static boolean isKnownType(ResourceLocation type) {
        return KNOWN_TYPES.contains(type);
    }

    public static Set<ResourceLocation> knownTypes() {
        return Set.copyOf(KNOWN_TYPES);
    }
}
