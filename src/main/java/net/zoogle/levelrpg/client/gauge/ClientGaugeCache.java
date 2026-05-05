package net.zoogle.levelrpg.client.gauge;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.net.payload.GaugeSyncPayload;

import java.util.Collection;
import java.util.LinkedHashMap;

public final class ClientGaugeCache {
    private static final double SMOOTHING_INCREASE_ALPHA = 0.18;
    private static final double SMOOTHING_DECREASE_ALPHA = 0.28;
    private static final double SMOOTHING_SNAP_EPSILON = 0.02;
    private static final double SPEND_SIGNAL_EPSILON = 0.10;
    private static final int SPEND_STREAK_DECAY_DELAY_TICKS = 12;
    private static final double SPEND_STREAK_DECAY_FACTOR = 0.90;
    private static final double VISIBILITY_FADE_IN_ALPHA = 0.25;
    private static final double VISIBILITY_FADE_OUT_ALPHA = 0.45;
    private static final double VISIBILITY_SNAP_EPSILON = 0.02;
    private static final LinkedHashMap<ResourceLocation, GaugeView> GAUGES = new LinkedHashMap<>();

    private ClientGaugeCache() {
    }

    public static void apply(GaugeSyncPayload payload) {
        ResourceLocation id = payload.gaugeId();
        GaugeView existing = GAUGES.get(id);
        if (existing == null) {
            double value = Math.max(0.0, payload.value());
            GAUGES.put(id, new GaugeView(
                    id,
                    payload.displayName(),
                    value,
                    value,
                    Math.max(0.0, payload.max()),
                    payload.hiddenWhenEmpty(),
                    payload.primaryColor(),
                    payload.backgroundColor()
            ));
            return;
        }
        existing.updateFromPayload(payload);
    }

    public static Collection<GaugeView> gauges() {
        return GAUGES.values();
    }

    public static void tickSmoothing() {
        for (GaugeView gauge : GAUGES.values()) {
            gauge.stepTowardTarget();
        }
    }

    public static final class GaugeView {
        private final ResourceLocation id;
        private String displayName;
        private double displayValue;
        private double targetValue;
        private double max;
        private boolean hiddenWhenEmpty;
        private int primaryColor;
        private int backgroundColor;
        private double visibilityAlpha = 1.0;
        private double pendingSpentAmount;
        private double spendShakeIntensity;
        private int spendStreakDecayTicks;

        public GaugeView(
                ResourceLocation id,
                String displayName,
                double displayValue,
                double targetValue,
                double max,
                boolean hiddenWhenEmpty,
                int primaryColor,
                int backgroundColor
        ) {
            this.id = id;
            this.displayName = displayName;
            this.displayValue = displayValue;
            this.targetValue = targetValue;
            this.max = max;
            this.hiddenWhenEmpty = hiddenWhenEmpty;
            this.primaryColor = primaryColor;
            this.backgroundColor = backgroundColor;
        }

        public ResourceLocation id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public double value() {
            return displayValue;
        }

        public double max() {
            return max;
        }

        public boolean hiddenWhenEmpty() {
            return hiddenWhenEmpty;
        }

        public int primaryColor() {
            return primaryColor;
        }

        public int backgroundColor() {
            return backgroundColor;
        }

        public double alpha() {
            return visibilityAlpha;
        }

        public void updateFromPayload(GaugeSyncPayload payload) {
            this.displayName = payload.displayName();
            double previousTarget = this.targetValue;
            double nextTarget = Math.max(0.0, payload.value());
            this.targetValue = nextTarget;
            this.max = Math.max(0.0, payload.max());
            this.hiddenWhenEmpty = payload.hiddenWhenEmpty();
            this.primaryColor = payload.primaryColor();
            this.backgroundColor = payload.backgroundColor();
            double spent = Math.max(0.0, previousTarget - nextTarget);
            if (spent >= SPEND_SIGNAL_EPSILON) {
                pendingSpentAmount += spent;
                double normalizedSpent = max > 0.0 ? spent / max : spent / 100.0;
                spendShakeIntensity = Math.min(1.0, spendShakeIntensity + 0.14 + (normalizedSpent * 2.4));
                spendStreakDecayTicks = SPEND_STREAK_DECAY_DELAY_TICKS;
            }
            if (Math.abs(displayValue - targetValue) <= SMOOTHING_SNAP_EPSILON) {
                this.displayValue = targetValue;
            }
        }

        public void stepTowardTarget() {
            double delta = targetValue - displayValue;
            if (Math.abs(delta) <= SMOOTHING_SNAP_EPSILON) {
                displayValue = targetValue;
            } else {
                double alpha = delta > 0.0 ? SMOOTHING_INCREASE_ALPHA : SMOOTHING_DECREASE_ALPHA;
                displayValue += delta * alpha;
                displayValue = Math.max(0.0, Math.min(max, displayValue));
            }
            double targetAlpha = (!hiddenWhenEmpty || displayValue > 0.0001 || targetValue > 0.0001) ? 1.0 : 0.0;
            double alphaDelta = targetAlpha - visibilityAlpha;
            if (Math.abs(alphaDelta) <= VISIBILITY_SNAP_EPSILON) {
                visibilityAlpha = targetAlpha;
            } else {
                double fade = alphaDelta > 0.0 ? VISIBILITY_FADE_IN_ALPHA : VISIBILITY_FADE_OUT_ALPHA;
                visibilityAlpha += alphaDelta * fade;
                visibilityAlpha = Math.max(0.0, Math.min(1.0, visibilityAlpha));
            }
            if (spendStreakDecayTicks > 0) {
                spendStreakDecayTicks--;
            } else if (spendShakeIntensity > 0.0001) {
                spendShakeIntensity *= SPEND_STREAK_DECAY_FACTOR;
                if (spendShakeIntensity < 0.02) {
                    spendShakeIntensity = 0.0;
                }
            }
        }

        public boolean shouldRender() {
            return visibilityAlpha > 0.02;
        }

        public double consumePendingSpentAmount() {
            double spent = pendingSpentAmount;
            pendingSpentAmount = 0.0;
            return spent;
        }

        public double spendShakeIntensity() {
            return spendShakeIntensity;
        }
    }
}
