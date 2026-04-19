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
import net.zoogle.levelrpg.command.LevelCommands;
import net.zoogle.levelrpg.net.Network;

@Mod(LevelRPG.MODID)
public class LevelRPG {
    public static final String MODID = "levelrpg";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LevelRPG(IEventBus modEventBus, ModContainer modContainer) {
        // GeckoLib 4.x no longer requires explicit initialize() call for NeoForge.
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(net.zoogle.levelrpg.net.Network::registerPayloadHandlers);

        NeoForge.EVENT_BUS.register(new ProfileEvents());
        NeoForge.EVENT_BUS.register(new ActivityEvents());
        NeoForge.EVENT_BUS.register(new LevelCommands());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.data.DataEvents());
        NeoForge.EVENT_BUS.register(new net.zoogle.levelrpg.events.GateEvents());

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("LevelRPG common setup");
        Network.init();
    }
}
