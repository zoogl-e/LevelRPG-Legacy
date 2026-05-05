package net.zoogle.levelrpg.client.input.valor;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.net.payload.RequestForwardFrenzyPayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeReleasePayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeStartPayload;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

public final class ValorInputController {
    private static final double RECKLESS_CLIENT_MIN_RESOLVE_TO_START = 48.0;
    private static final double FORWARD_FRENZY_CLIENT_RESOLVE_COST_GROUNDED = 30.0;
    private static final double FORWARD_FRENZY_CLIENT_RESOLVE_COST_AERIAL = 24.0;
    private static final int RECKLESS_HOLD_ARM_TICKS = 4;

    private static boolean recklessMouseDown;
    private static boolean recklessChargePending;
    private static boolean recklessChargeHeld;
    private static int recklessPressClientTick;

    private ValorInputController() {}

    public static void resetForPlayerTickRollback() {
        recklessPressClientTick = 0;
    }

    public static void tickRecklessChargeState(Minecraft mc) {
        if (mc.player == null) {
            recklessChargePending = false;
            recklessChargeHeld = false;
            return;
        }
        if (!recklessMouseDown) {
            recklessChargePending = false;
        }
        if (recklessChargePending) {
            if (!shouldAttemptRecklessCharge(mc)) {
                recklessChargePending = false;
            } else if (mc.player.tickCount - recklessPressClientTick >= RECKLESS_HOLD_ARM_TICKS) {
                PacketDistributor.sendToServer(new RequestRecklessChargeStartPayload());
                recklessChargeHeld = true;
                recklessChargePending = false;
            }
        }
        if (!recklessChargeHeld) {
            return;
        }
        if (!recklessMouseDown || !shouldAttemptRecklessCharge(mc)) {
            PacketDistributor.sendToServer(new RequestRecklessChargeReleasePayload());
            recklessChargeHeld = false;
        }
    }

    public static void onMouseButtonRight(InputEvent.MouseButton.Pre event, Minecraft mc) {
        if (event.getAction() == 0) {
            recklessMouseDown = false;
            recklessChargePending = false;
            if (recklessChargeHeld) {
                PacketDistributor.sendToServer(new RequestRecklessChargeReleasePayload());
                recklessChargeHeld = false;
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAction() != 1) {
            return;
        }
        recklessMouseDown = true;
        if (shouldAttemptRecklessCharge(mc)) {
            recklessChargePending = true;
            recklessPressClientTick = mc.player != null ? mc.player.tickCount : 0;
            return;
        }
        if (shouldAttemptForwardFrenzy(mc)) {
            PacketDistributor.sendToServer(new RequestForwardFrenzyPayload());
            event.setCanceled(true);
        }
    }

    private static boolean shouldAttemptForwardFrenzy(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        if (!ValorMeleeWeapons.isValorMelee(mc.player.getMainHandItem())) {
            return false;
        }
        if (!hasEnoughResolveForForwardFrenzy(mc)) {
            return false;
        }
        if (!mc.player.onGround()) {
            return true;
        }
        double horizontalSpeedSqr = mc.player.getDeltaMovement().horizontalDistanceSqr();
        return horizontalSpeedSqr > (0.05 * 0.05);
    }

    private static boolean shouldAttemptRecklessCharge(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        if (!ValorMeleeWeapons.isValorMelee(mc.player.getMainHandItem())) {
            return false;
        }
        if (!mc.player.isShiftKeyDown()) {
            return false;
        }
        if (!mc.player.onGround()) {
            return false;
        }
        double horizontalSpeedSqr = mc.player.getDeltaMovement().horizontalDistanceSqr();
        if (horizontalSpeedSqr > (0.05 * 0.05)) {
            return false;
        }
        return hasEnoughResolveForReckless();
    }

    private static boolean hasEnoughResolveForReckless() {
        Double resolve = getClientResolveValue();
        return resolve != null && resolve >= RECKLESS_CLIENT_MIN_RESOLVE_TO_START;
    }

    private static boolean hasEnoughResolveForForwardFrenzy(Minecraft mc) {
        Double resolve = getClientResolveValue();
        if (resolve == null) {
            return false;
        }
        double required = mc.player != null && mc.player.onGround()
                ? FORWARD_FRENZY_CLIENT_RESOLVE_COST_GROUNDED
                : FORWARD_FRENZY_CLIENT_RESOLVE_COST_AERIAL;
        return resolve >= required;
    }

    private static Double getClientResolveValue() {
        for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
            if (!GaugeRegistry.RESOLVE.equals(gauge.id())) {
                continue;
            }
            return gauge.value();
        }
        return null;
    }
}
