package net.zoogle.levelrpg.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.zoogle.levelrpg.compat.AeronauticsRopeCompat;
import net.zoogle.levelrpg.world.IndexChamberManager;

public class IndexTrialEvents {
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null) {
            AeronauticsRopeCompat.tick(overworld);
            IndexChamberManager.tickPendingSableAssemblies(overworld);
            IndexChamberManager.tickPendingKineticRefreshes(overworld);
            IndexChamberManager.tickTrialKeyRewardProgress(overworld);
            IndexChamberManager.tickVaultActivationObserver(overworld);
            IndexChamberManager.tickVaultCopperReplacement(overworld);
            if (overworld.getGameTime() % 100L == 0L) {
                IndexChamberManager.syncGuideTargets(overworld);
            }
        }
    }
}
