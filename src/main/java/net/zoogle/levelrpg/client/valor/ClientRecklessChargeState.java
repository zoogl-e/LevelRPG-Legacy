package net.zoogle.levelrpg.client.valor;

import net.minecraft.client.Minecraft;
import net.zoogle.levelrpg.net.payload.RecklessChargeStatePayload;

public final class ClientRecklessChargeState {
    private static boolean active;
    private static int startClientTick;
    private static int maxChargeTicks = 1;

    private ClientRecklessChargeState() {
    }

    public static void apply(RecklessChargeStatePayload payload) {
        if (payload == null || !payload.active()) {
            active = false;
            startClientTick = 0;
            maxChargeTicks = 1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int tickNow = mc.player != null ? mc.player.tickCount : 0;
        active = true;
        startClientTick = tickNow;
        maxChargeTicks = Math.max(1, payload.maxChargeTicks());
    }

    public static boolean isActive() {
        return active;
    }

    public static double progress(int clientTickNow) {
        if (!active) {
            return 0.0;
        }
        int elapsed = Math.max(0, clientTickNow - startClientTick);
        return Math.max(0.0, Math.min(1.0, elapsed / (double) maxChargeTicks));
    }
}
