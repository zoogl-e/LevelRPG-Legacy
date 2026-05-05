package net.zoogle.levelrpg.gauge;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

import java.util.Collection;
import java.util.LinkedHashMap;

public final class GaugeRegistry {
    public static final ResourceLocation MOMENTUM = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "momentum");
    public static final ResourceLocation RESOLVE = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "resolve");
    public static final ResourceLocation RHYTHM = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "rhythm");
    private static final LinkedHashMap<ResourceLocation, GaugeDefinition> REGISTRY = new LinkedHashMap<>();

    static {
        register(new GaugeDefinition(
                MOMENTUM,
                "Momentum",
                "A dynamic resource for movement and mining pressure.",
                0.0,
                100.0,
                0.0,
                1.0,
                60,
                true,
                0xFF58D68D,
                0xAA1F2A22,
                null
        ));
        register(new GaugeDefinition(
                RESOLVE,
                "Resolve",
                "Dealing damage builds Resolve; taking damage reduces it. Higher Resolve empowers critical hits.",
                0.0,
                100.0,
                0.0,
                5.0,
                60,
                true,
                0xFFFFC857,
                0xAA2A221F,
                null
        ));
        register(new GaugeDefinition(
                RHYTHM,
                "Rhythm",
                "Movement and successful hits raise Rhythm; taking damage knocks it down. Higher Rhythm quickens your step.",
                0.0,
                100.0,
                0.0,
                2.25,
                50,
                true,
                0xFFE056D0,
                0xAA221F2A,
                null
        ));
    }

    private GaugeRegistry() {
    }

    public static void register(GaugeDefinition definition) {
        if (definition != null) {
            REGISTRY.put(definition.id(), definition);
        }
    }

    public static GaugeDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<GaugeDefinition> entries() {
        return REGISTRY.values();
    }
}
