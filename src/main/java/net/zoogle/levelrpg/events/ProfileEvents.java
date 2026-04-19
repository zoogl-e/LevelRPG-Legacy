package net.zoogle.levelrpg.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.zoogle.levelrpg.Config;
import net.zoogle.levelrpg.net.Network;
import net.zoogle.levelrpg.profile.LevelProfile;

/**
 * Instance-based event listeners registered on NeoForge.EVENT_BUS.
 */
public class ProfileEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Ensure profile exists and is saved; optionally ping the player once
        LevelProfile p = LevelProfile.get(sp);
        LevelProfile.save(sp, p);
        Network.sendSync(sp, p);
        if (Config.feedbackLoginMessage) {
            sp.displayClientMessage(Component.literal("LevelRPG profile loaded."), false);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (event.getOriginal() instanceof ServerPlayer oldPlayer) {
            LevelProfile.copy(oldPlayer, newPlayer);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // After respawn, ensure client has fresh profile snapshot
        Network.sendSync(sp, LevelProfile.get(sp));
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Network.sendSync(sp, LevelProfile.get(sp));
    }
}
