package net.zoogle.levelrpg.finesse;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks recent melee hits for {@code flurry_of_blows} attack-speed stacks.
 */
public final class FinesseFlurryTracker {
    private static final int MAX_STACKS = 5;
    private static final int DECAY_TICKS_NO_HIT = 38;
    private static final Map<UUID, Integer> STACKS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HIT_TICK = new HashMap<>();

    private FinesseFlurryTracker() {
    }

    public static void onMeleeHit(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        LAST_HIT_TICK.put(id, player.tickCount);
        int next = Math.min(MAX_STACKS, STACKS.getOrDefault(id, 0) + 1);
        STACKS.put(id, next);
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        Integer last = LAST_HIT_TICK.get(id);
        if (last == null) {
            return;
        }
        if (player.tickCount - last > DECAY_TICKS_NO_HIT) {
            STACKS.remove(id);
            LAST_HIT_TICK.remove(id);
        }
    }

    public static int stacks(ServerPlayer player) {
        return player == null ? 0 : STACKS.getOrDefault(player.getUUID(), 0);
    }

    public static void remove(UUID playerId) {
        STACKS.remove(playerId);
        LAST_HIT_TICK.remove(playerId);
    }
}
