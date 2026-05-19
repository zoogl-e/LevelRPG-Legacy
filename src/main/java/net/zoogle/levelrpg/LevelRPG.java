package net.zoogle.levelrpg;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import net.zoogle.levelrpg.events.ProfileEvents;
import net.zoogle.levelrpg.events.ActivityEvents;
import net.zoogle.levelrpg.events.PassiveSkillEvents;
import net.zoogle.levelrpg.command.LevelCommands;
import net.zoogle.levelrpg.gauge.DelvingMomentumGaugeModifiers;
import net.zoogle.levelrpg.gauge.FinesseRhythmGaugeModifiers;
import net.zoogle.levelrpg.gauge.GaugeModifiers;
import net.zoogle.levelrpg.gauge.ValorResolveGaugeModifiers;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.registry.LevelRpgBlockEntities;
import net.zoogle.levelrpg.registry.LevelRpgBlocks;
import net.zoogle.levelrpg.technique.TechniqueRegistry;

@Mod(LevelRPG.MODID)
public class LevelRPG {
    public static final String MODID = "levelrpg";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LevelRPG(IEventBus modEventBus, ModContainer modContainer) {
        // GeckoLib 4.x no longer requires explicit initialize() call for NeoForge.
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(net.zoogle.levelrpg.net.Network::registerPayloadHandlers);
        LevelRpgBlocks.register(modEventBus);
        LevelRpgBlockEntities.register(modEventBus);
        TechniqueRegistry.init();
        GaugeModifiers.register(new DelvingMomentumGaugeModifiers());
        GaugeModifiers.register(new ValorResolveGaugeModifiers());
        GaugeModifiers.register(new FinesseRhythmGaugeModifiers());

        NeoForge.EVENT_BUS.register(new ProfileEvents());
        NeoForge.EVENT_BUS.register(new ActivityEvents());
        NeoForge.EVENT_BUS.register(new PassiveSkillEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.DelvingGameplayEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.FinesseGameplayEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.FinesseCombatEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.finesse.FinesseProjectileParry.EventHandler());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.finesse.FinesseWeakSpotEffects());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.ValorGameplayEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.ValorDuelistSliceEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.ValorExtendedBranchesEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.ValorTechniqueEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.GaugeEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.TechniqueEvents());
        NeoForge.EVENT_BUS.register(new LevelCommands());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.data.DataEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.GateEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.IndexPlacementEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.IndexTrialEvents());

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("LevelRPG common setup");
        Network.init();
    }
}
