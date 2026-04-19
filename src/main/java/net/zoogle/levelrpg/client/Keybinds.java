package net.zoogle.levelrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.zoogle.levelrpg.LevelRPG;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class Keybinds {
    private Keybinds() {}

    public static KeyMapping OPEN_BOOK;
    public static KeyMapping TOGGLE_ORBIT;
    public static KeyMapping TOGGLE_ORBIT_BOOK;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        OPEN_BOOK = new KeyMapping(
                "key.levelrpg.open",
                InputConstants.KEY_K,
                "key.categories.levelrpg"
        );
        event.register(OPEN_BOOK);
        TOGGLE_ORBIT = new KeyMapping(
                "key.levelrpg.debug.orbit",
                InputConstants.KEY_O,
                "key.categories.levelrpg"
        );
        event.register(TOGGLE_ORBIT);
        TOGGLE_ORBIT_BOOK = new KeyMapping(
                "key.levelrpg.debug.orbit_book",
                InputConstants.KEY_P,
                "key.categories.levelrpg"
        );
        event.register(TOGGLE_ORBIT_BOOK);
    }

    @EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (OPEN_BOOK != null) {
                while (OPEN_BOOK.consumeClick()) {
                    if (!net.zoogle.levelrpg.Config.enableLevelBookKeybind) {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal("Level Book keybind is disabled in config"), true);
                        }
                        continue;
                    }
                    if (!(mc.screen instanceof net.zoogle.levelrpg.client.ui.LevelBookScreen)) {
                        // Ensure world debug rendering is off before opening the GUI
                        try {
                            //net.zoogle.levelrpg.client.debug.BookOrbitDebug.enabled = false;
                            //net.zoogle.levelrpg.client.debug.BookOrbitDebug.renderBook = false;
                            //net.zoogle.levelrpg.client.debug.BookCrosshairRender.enabled = false;
                        } catch (Throwable ignored) {}
                        mc.setScreen(new net.zoogle.levelrpg.client.ui.LevelBookScreen());
                    }
                }
            }
        }
    }
}
