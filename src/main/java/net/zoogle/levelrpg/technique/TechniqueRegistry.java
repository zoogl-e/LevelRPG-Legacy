package net.zoogle.levelrpg.technique;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.events.DelvingGameplayEvents;
import net.zoogle.levelrpg.events.ValorTechniqueEvents;
import net.zoogle.levelrpg.finesse.FinesseTechniqueActivations;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.skilltree.DelvingNodeIds;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;
import net.zoogle.levelrpg.net.payload.CrescentSlashCameraSpinPayload;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TechniqueRegistry {
    private static final double TIDE_DASH_MAX_SPEED = 1.6;
    public static final ResourceLocation TIDE_DASH = id("tide_dash");
    public static final ResourceLocation HAMMER_STRIKE = id("hammer_strike");
    public static final ResourceLocation ANCHOR_DROP = id("anchor_drop");
    public static final ResourceLocation WATER_WALKING = ANCHOR_DROP;
    public static final ResourceLocation DELVING = id("delving");
    public static final ResourceLocation VALOR = id("valor");
    public static final ResourceLocation GOAD = id("goad");
    public static final ResourceLocation FORWARD_FRENZY = id("forward_frenzy");
    public static final ResourceLocation RECKLESS_STRIKE = id("reckless_strike");
    public static final ResourceLocation CRESCENT_SLASH = id("crescent_slash");
    public static final ResourceLocation JUDGEMENT_DAY = id("judgement_day");
    public static final ResourceLocation GHOST_STEP = id("ghost_step");
    public static final ResourceLocation RAPID_VOLLEY = id("rapid_volley");
    public static final ResourceLocation LIKE_THE_WIND = id("like_the_wind");
    public static final ResourceLocation CALCULATED_SHOT = id("calculated_shot");
    public static final ResourceLocation UPPERCUT = id("uppercut");
    public static final ResourceLocation BLOT_OUT_THE_SUN = id("blot_out_the_sun");

    private static final LinkedHashMap<ResourceLocation, TechniqueDefinition> TECHNIQUES = new LinkedHashMap<>();

    static {
        register(new TechniqueDefinition(
                TIDE_DASH,
                "Tide Dash",
                "Burst forward underwater.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "prismarine_shard"),
                DELVING,
                "tide_dash",
                new TechniqueCost(GaugeRegistry.MOMENTUM, 10.0),
                0,
                context -> context.player().isInWaterOrBubble()
                        ? TechniqueResult.success("Ready")
                        : TechniqueResult.failure("Tide Dash requires you to be underwater"),
                context -> {
                    Vec3 look = context.player().getLookAngle();
                    double lookLenSq = look.lengthSqr();
                    if (lookLenSq < 1.0e-8) {
                        return TechniqueResult.failure("Tide Dash failed");
                    }
                    Vec3 dir = look.scale(1.0 / Math.sqrt(lookLenSq));
                    Vec3 impulse = dir.scale(1.25);
                    Vec3 current = context.player().getDeltaMovement();
                    Vec3 next = current.add(impulse);
                    if (next.lengthSqr() > TIDE_DASH_MAX_SPEED * TIDE_DASH_MAX_SPEED) {
                        next = next.normalize().scale(TIDE_DASH_MAX_SPEED);
                    }
                    context.player().setDeltaMovement(next);
                    context.player().connection.send(new ClientboundSetEntityMotionPacket(context.player()));
                    return TechniqueResult.success("Tide Dash");
                }
        ));
        register(new TechniqueDefinition(
                HAMMER_STRIKE,
                "Hammer Strike",
                "Mine a 3x3 area.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron_pickaxe"),
                DELVING,
                DelvingNodeIds.HAMMER_STRIKE,
                new TechniqueCost(GaugeRegistry.MOMENTUM, 1.0),
                80,
                context -> TechniqueResult.success("Ready"),
                context -> DelvingGameplayEvents.activateHammerStrike(context.player())
                        ? TechniqueResult.success("Hammer Strike")
                        : TechniqueResult.failure("Hammer Strike failed")
        ));
        register(new TechniqueDefinition(
                ANCHOR_DROP,
                "Charging Jump",
                "Passive: hold sneak underwater to charge, release to launch upward.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "prismarine_crystals"),
                DELVING,
                DelvingNodeIds.ANCHOR_DROP,
                TechniqueCost.NONE,
                60,
                context -> TechniqueResult.success("Passive"),
                context -> TechniqueResult.success("Charging Jump is passive")
        ));
        register(new TechniqueDefinition(
                GOAD,
                "Goad",
                "Taunt nearby enemies and fortify yourself.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "goat_horn"),
                VALOR,
                ValorNodeIds.GOAD,
                new TechniqueCost(GaugeRegistry.RESOLVE, 26.0),
                200,
                context -> TechniqueResult.success("Ready"),
                context -> ValorTechniqueEvents.activateGoad(context.player(), 20 * 8, 10.0)
                        ? TechniqueResult.success("Goad")
                        : TechniqueResult.success("Goad")
        ));
        register(new TechniqueDefinition(
                FORWARD_FRENZY,
                "Forward Frenzy",
                "Passive sword input: sprint + jump + right-click to dash through enemies.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron_sword"),
                VALOR,
                ValorNodeIds.FORWARD_FRENZY,
                TechniqueCost.NONE,
                0,
                context -> TechniqueResult.success("Passive"),
                context -> TechniqueResult.success("Use sprint + jump + right-click with a melee weapon")
        ));
        register(new TechniqueDefinition(
                RECKLESS_STRIKE,
                "Reckless Strike",
                "Passive sword input: hold left-click while still, then release for a massive cleave.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "netherite_sword"),
                VALOR,
                ValorNodeIds.RECKLESS_STRIKE,
                TechniqueCost.NONE,
                0,
                context -> TechniqueResult.success("Passive"),
                context -> TechniqueResult.success("Hold left-click while still, then release")
        ));
        register(new TechniqueDefinition(
                CRESCENT_SLASH,
                "Crescent Slash",
                "Spin in place to damage enemies around you.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_sword"),
                VALOR,
                ValorNodeIds.CRESCENT_SLASH,
                new TechniqueCost(GaugeRegistry.RESOLVE, 35.0),
                220,
                context -> ValorMeleeWeapons.isValorMelee(context.player().getMainHandItem())
                        ? TechniqueResult.success("Ready")
                        : TechniqueResult.failure("Crescent Slash requires a melee weapon"),
                context -> {
                    // Always allow activation; nearby hits are optional.
                    ValorTechniqueEvents.activateCrescentSlash(context.player());
                    context.player().connection.send(new ClientboundCustomPayloadPacket(
                            new CrescentSlashCameraSpinPayload(12)));
                    return TechniqueResult.success("Crescent Slash");
                }
        ));
        register(new TechniqueDefinition(
                JUDGEMENT_DAY,
                "Judgement Day",
                "Briefly nullify incoming damage and reflect hits.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "totem_of_undying"),
                VALOR,
                ValorNodeIds.JUDGEMENT_DAY,
                new TechniqueCost(GaugeRegistry.RESOLVE, 80.0),
                20 * 45,
                context -> TechniqueResult.success("Ready"),
                context -> {
                    ValorTechniqueEvents.startJudgementDay(context.player(), 20 * 6);
                    return TechniqueResult.success("Judgement Day");
                }
        ));
        register(new TechniqueDefinition(
                GHOST_STEP,
                "Ghost Step",
                "Passive: double-tap a movement direction to dash.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "feather"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.GHOST_STEP,
                TechniqueCost.NONE,
                0,
                context -> TechniqueResult.success("Ready"),
                FinesseTechniqueActivations::activateGhostStep
        ));
        register(new TechniqueDefinition(
                RAPID_VOLLEY,
                "Rapid Volley",
                "For a short time your shots fly faster.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "arrow"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.RAPID_VOLLEY,
                new TechniqueCost(GaugeRegistry.RHYTHM, 26.0),
                20 * 22,
                context -> {
                    var main = context.player().getMainHandItem();
                    if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
                        return TechniqueResult.success("Ready");
                    }
                    return TechniqueResult.failure("Rapid Volley requires a bow or crossbow in your main hand");
                },
                FinesseTechniqueActivations::activateRapidVolley
        ));
        register(new TechniqueDefinition(
                LIKE_THE_WIND,
                "Like The Wind",
                "Brief invisibility, speed, and safer landings.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "phantom_membrane"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.LIKE_THE_WIND,
                new TechniqueCost(GaugeRegistry.RHYTHM, 36.0),
                20 * 19,
                context -> TechniqueResult.success("Ready"),
                FinesseTechniqueActivations::activateLikeTheWind
        ));
        register(new TechniqueDefinition(
                CALCULATED_SHOT,
                "Calculated Shot",
                "Slow your fall to line up a shot.",
                ResourceLocation.fromNamespaceAndPath("minecraft", "wind_charge"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.CALCULATED_SHOT,
                new TechniqueCost(GaugeRegistry.RHYTHM, 12.0),
                20 * 10,
                context -> {
                    var main = context.player().getMainHandItem();
                    if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
                        return TechniqueResult.success("Ready");
                    }
                    return TechniqueResult.failure("Calculated Shot requires a bow or crossbow in your main hand");
                },
                FinesseTechniqueActivations::activateCalculatedShot
        ));
        register(new TechniqueDefinition(
                UPPERCUT,
                "Uppercut",
                "A rising strike in front of you (full charge system later).",
                ResourceLocation.fromNamespaceAndPath("minecraft", "leather_boots"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.UPPERCUT,
                new TechniqueCost(GaugeRegistry.RHYTHM, 18.0),
                20 * 3,
                context -> context.player().getMainHandItem().isEmpty()
                        ? TechniqueResult.success("Ready")
                        : TechniqueResult.failure("Uppercut requires an empty main hand"),
                FinesseTechniqueActivations::activateUppercut
        ));
        register(new TechniqueDefinition(
                BLOT_OUT_THE_SUN,
                "Blot Out the Sun",
                "A burst of damage around you (volley visuals later).",
                ResourceLocation.fromNamespaceAndPath("minecraft", "tipped_arrow"),
                FinesseNodeIds.SKILL,
                FinesseNodeIds.BLOT_OUT_THE_SUN,
                new TechniqueCost(GaugeRegistry.RHYTHM, 42.0),
                20 * 50,
                context -> TechniqueResult.success("Ready"),
                FinesseTechniqueActivations::activateBlotOutTheSun
        ));
    }

    private TechniqueRegistry() {
    }

    public static void init() {
        // Forces static initialization.
    }

    public static TechniqueDefinition get(ResourceLocation id) {
        return TECHNIQUES.get(id);
    }

    public static Collection<TechniqueDefinition> entries() {
        return TECHNIQUES.values();
    }

    public static ResourceLocation resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = raw.indexOf(':') >= 0
                ? ResourceLocation.parse(raw)
                : ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, raw);
        return TECHNIQUES.containsKey(id) ? id : null;
    }

    private static void register(TechniqueDefinition definition) {
        TECHNIQUES.put(definition.id(), definition);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }

    public static Map<ResourceLocation, TechniqueDefinition> view() {
        return Map.copyOf(TECHNIQUES);
    }
}
