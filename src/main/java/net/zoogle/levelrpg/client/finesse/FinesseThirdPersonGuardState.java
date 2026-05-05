package net.zoogle.levelrpg.client.finesse;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.net.payload.SyncFinesseGuardVisualPayload;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class FinesseThirdPersonGuardState {
    private static final float RAISE_PER_TICK = 0.22F;
    private static final float LOWER_PER_TICK = 0.18F;
    private static final Map<Integer, GuardVisualState> STATES = new ConcurrentHashMap<>();

    private FinesseThirdPersonGuardState() {
    }

    public static void apply(SyncFinesseGuardVisualPayload payload) {
        STATES.compute(payload.entityId(), (id, state) -> {
            GuardVisualState next = state == null ? new GuardVisualState() : state;
            next.guarding = payload.guarding();
            return next;
        });
    }

    public static float progress(int entityId) {
        GuardVisualState state = STATES.get(entityId);
        if (state == null) {
            return 0.0F;
        }
        float progress = state.progress;
        return progress * progress * (3.0F - 2.0F * progress);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            STATES.clear();
            return;
        }
        Iterator<Map.Entry<Integer, GuardVisualState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, GuardVisualState> entry = iterator.next();
            GuardVisualState state = entry.getValue();
            float delta = state.guarding ? RAISE_PER_TICK : -LOWER_PER_TICK;
            state.progress = Math.max(0.0F, Math.min(1.0F, state.progress + delta));
            if (!state.guarding && state.progress <= 0.0F) {
                iterator.remove();
            }
        }
    }

    private static final class GuardVisualState {
        private boolean guarding;
        private float progress;
    }
}
