package net.zoogle.levelrpg.client.ui;

import net.minecraft.client.Minecraft;

/**
 * Utility to temporarily disable the vanilla menu background blur while our custom GUIs are open.
 * It reference-counts open LevelRPG screens so blur is restored only when the last one closes.
 */
public final class BlurUtil {
    private static Integer PREV_MENU_BLUR = null;
    private static int OPEN_SCREENS = 0;

    private BlurUtil() {}

    public static void pushNoBlur() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                if (OPEN_SCREENS == 0) {
                    PREV_MENU_BLUR = mc.options.menuBackgroundBlurriness().get();
                    if (PREV_MENU_BLUR == null) PREV_MENU_BLUR = 0;
                    mc.options.menuBackgroundBlurriness().set(0);
                }
                OPEN_SCREENS++;
            }
        } catch (Throwable ignored) {}
    }

    public static void popNoBlur() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                OPEN_SCREENS = Math.max(0, OPEN_SCREENS - 1);
                if (OPEN_SCREENS == 0 && PREV_MENU_BLUR != null) {
                    mc.options.menuBackgroundBlurriness().set(PREV_MENU_BLUR);
                    PREV_MENU_BLUR = null;
                }
            }
        } catch (Throwable ignored) {}
    }
}
