package net.zoogle.levelrpg.client.input.movement;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.net.payload.RequestGhostStepPayload;

public final class GhostStepInputController {
    private static final int GHOST_STEP_DOUBLE_TAP_TICKS = 7;
    private static final int GHOST_STEP_CLIENT_COOLDOWN_TICKS = 10;
    private static final boolean[] GHOST_STEP_KEY_WAS_DOWN = new boolean[4];
    private static final int[] GHOST_STEP_LAST_TAP_TICK = new int[4];
    private static int ghostStepClientCooldownUntil;

    private GhostStepInputController() {}

    public static boolean isClientCooldownTickInvalid(int tickNow) {
        return ghostStepClientCooldownUntil > tickNow + 100;
    }

    public static void resetForPlayerTickRollback() {
        ghostStepClientCooldownUntil = 0;
        for (int i = 0; i < GHOST_STEP_LAST_TAP_TICK.length; i++) {
            GHOST_STEP_LAST_TAP_TICK[i] = 0;
        }
    }

    public static void tickGhostStepInput(Minecraft mc, boolean techniqueSelectModeActive) {
        if (mc.player == null || mc.screen != null || techniqueSelectModeActive) {
            return;
        }
        boolean[] down = {
                mc.options.keyUp.isDown(),
                mc.options.keyDown.isDown(),
                mc.options.keyLeft.isDown(),
                mc.options.keyRight.isDown()
        };
        int[][] directions = {
                {1, 0},
                {-1, 0},
                {0, -1},
                {0, 1}
        };
        int tick = mc.player.tickCount;
        for (int i = 0; i < down.length; i++) {
            if (down[i] && !GHOST_STEP_KEY_WAS_DOWN[i]) {
                if (tick >= ghostStepClientCooldownUntil
                        && tick - GHOST_STEP_LAST_TAP_TICK[i] <= GHOST_STEP_DOUBLE_TAP_TICKS) {
                    ghostStepClientCooldownUntil = tick + GHOST_STEP_CLIENT_COOLDOWN_TICKS;
                    PacketDistributor.sendToServer(new RequestGhostStepPayload(directions[i][0], directions[i][1]));
                    GHOST_STEP_LAST_TAP_TICK[i] = 0;
                } else {
                    GHOST_STEP_LAST_TAP_TICK[i] = tick;
                }
            }
            GHOST_STEP_KEY_WAS_DOWN[i] = down[i];
        }
    }
}
