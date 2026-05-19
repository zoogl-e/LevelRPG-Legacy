package net.zoogle.levelrpg.events;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.zoogle.levelrpg.Config;
import net.zoogle.levelrpg.world.IndexChamberManager;
import net.zoogle.levelrpg.world.IndexPlacementData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class IndexPlacementEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHAMBER_PLACEMENT_LOGIN_DELAY_TICKS = 100;
    private final Map<UUID, PendingPlacement> pendingPlacements = new HashMap<>();

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            logPlacementHook(serverLevel, "level_load");
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleIndexChamberPlacement(player, "player_login");
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pendingPlacements.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null || pendingPlacements.isEmpty()) {
            return;
        }
        if (!Config.generateIndexNearSpawn) {
            pendingPlacements.clear();
            return;
        }
        if (IndexPlacementData.get(overworld).chamberPlaced()) {
            for (UUID playerId : pendingPlacements.keySet()) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    IndexChamberManager.syncGuideTarget(player);
                }
            }
            pendingPlacements.clear();
            return;
        }

        long gameTime = overworld.getGameTime();
        Iterator<Map.Entry<UUID, PendingPlacement>> iterator = pendingPlacements.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingPlacement> entry = iterator.next();
            PendingPlacement pending = entry.getValue();
            if (gameTime < pending.dueGameTime()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                tryEnsureIndexChamber(player, pending.trigger(), pending.anchor());
            }
            return;
        }
    }

    private static void logPlacementHook(ServerLevel serverLevel, String trigger) {
        LOGGER.info(
                "Index Chamber placement hook fired [{}]: dimension={} overworld={} enabled={} spawn={}",
                trigger,
                serverLevel.dimension().location(),
                serverLevel.dimension() == Level.OVERWORLD,
                Config.generateIndexNearSpawn,
                serverLevel.getSharedSpawnPos()
        );
    }

    private void scheduleIndexChamberPlacement(ServerPlayer player, String trigger) {
        ServerLevel serverLevel = player.serverLevel();
        logPlacementHook(serverLevel, trigger);
        if (serverLevel.dimension() != Level.OVERWORLD || !Config.generateIndexNearSpawn) {
            return;
        }
        if (IndexPlacementData.get(serverLevel).chamberPlaced()) {
            IndexChamberManager.syncGuideTarget(player);
            return;
        }
        long dueGameTime = serverLevel.getGameTime() + CHAMBER_PLACEMENT_LOGIN_DELAY_TICKS;
        pendingPlacements.put(player.getUUID(), new PendingPlacement(dueGameTime, trigger, player.blockPosition()));
        LOGGER.info(
                "Index Chamber placement scheduled [{}]: player={} anchor={} delayTicks={}",
                trigger,
                player.getGameProfile().getName(),
                player.blockPosition(),
                CHAMBER_PLACEMENT_LOGIN_DELAY_TICKS
        );
    }

    private static void tryEnsureIndexChamber(ServerPlayer player, String trigger, net.minecraft.core.BlockPos anchor) {
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel.dimension() != Level.OVERWORLD || !Config.generateIndexNearSpawn) {
            return;
        }
        LOGGER.info("Index Chamber placement using delayed player anchor [{}]: player={} pos={}", trigger, player.getGameProfile().getName(), anchor);
        IndexChamberManager.tryEnsureChamber(serverLevel, trigger, anchor);
        IndexChamberManager.syncGuideTarget(player);
    }

    private record PendingPlacement(long dueGameTime, String trigger, net.minecraft.core.BlockPos anchor) {
    }
}
