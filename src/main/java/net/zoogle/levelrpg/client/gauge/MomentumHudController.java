package net.zoogle.levelrpg.client.gauge;

public final class MomentumHudController {
    private static final int SHOW_TICKS = 80;
    private static final double CAP_SHOW_THRESHOLD = 0.995;
    private static final double CAP_HIDE_THRESHOLD = 0.985;
    private static final int SHAKE_HOLD_TICKS = 8;
    private static final double SHAKE_DECAY_FACTOR = 0.86;

    private static int showBarUntilTick = Integer.MIN_VALUE / 2;
    private static int showLabelUntilTick = Integer.MIN_VALUE / 2;
    private static boolean cappedLatched;
    private static double barAlpha;
    private static double labelAlpha;
    private static double shakeIntensity;
    private static int shakeHoldTicks;
    private static int lastUpdatedTick = Integer.MIN_VALUE / 2;
    private static Snapshot lastSnapshot = new Snapshot(0.0, 0.0, 0.0, false);

    private MomentumHudController() {
    }

    public static void onTechniqueAttempt(double momentumCost, int tickNow) {
        showBarUntilTick = Math.max(showBarUntilTick, tickNow + SHOW_TICKS);
        showLabelUntilTick = Math.max(showLabelUntilTick, tickNow + SHOW_TICKS);
        double normalized = Math.max(0.0, momentumCost) / 40.0;
        shakeIntensity = Math.min(1.0, shakeIntensity + 0.16 + (normalized * 0.35));
        shakeHoldTicks = SHAKE_HOLD_TICKS;
    }

    public static Snapshot update(int tickNow, boolean selectModeActive, double fraction) {
        if (tickNow == lastUpdatedTick) {
            return lastSnapshot;
        }
        if (cappedLatched) {
            if (fraction <= CAP_HIDE_THRESHOLD) {
                cappedLatched = false;
            }
        } else if (fraction >= CAP_SHOW_THRESHOLD) {
            cappedLatched = true;
        }

        boolean forceVisible = selectModeActive || tickNow < showBarUntilTick;
        boolean forceLabel = selectModeActive || tickNow < showLabelUntilTick;

        barAlpha = forceVisible ? 1.0 : 0.0;
        labelAlpha = forceLabel ? 1.0 : 0.0;

        if (shakeHoldTicks > 0) {
            shakeHoldTicks--;
        } else if (shakeIntensity > 0.0001) {
            shakeIntensity *= SHAKE_DECAY_FACTOR;
            if (shakeIntensity < 0.015) {
                shakeIntensity = 0.0;
            }
        }

        lastUpdatedTick = tickNow;
        lastSnapshot = new Snapshot(barAlpha, labelAlpha, shakeIntensity, cappedLatched);
        return lastSnapshot;
    }

    public record Snapshot(double barAlpha, double labelAlpha, double shakeIntensity, boolean capped) {
    }
}
