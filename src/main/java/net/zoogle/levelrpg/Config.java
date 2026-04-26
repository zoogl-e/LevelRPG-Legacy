package net.zoogle.levelrpg;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = LevelRPG.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Toggles
    private static final ModConfigSpec.BooleanValue FEEDBACK_LOGIN_MESSAGE = BUILDER
            .comment("Show a small 'LevelRPG profile loaded' message when a player joins.")
            .define("feedback.loginMessage", false);

    private static final ModConfigSpec.BooleanValue DEV_ENABLE_EDITOR = BUILDER
            .comment("Enable developer editor UI and features (client-side).")
            .define("dev.enableEditor", false);

    private static final ModConfigSpec.BooleanValue ENABLE_LEVEL_BOOK_KEYBIND = BUILDER
            .comment("Enable the Level Book keybind (client-side). If false, the K key will not open the Level Book.")
            .define("client.enableLevelBookKeybind", true);

    // Autocorrect settings
    public static final ModConfigSpec.IntValue AUTOCORRECT_MAX_DISTANCE = BUILDER
            .comment("Levenshtein distance threshold for skill name autocorrect in commands. 0 disables fuzzy matching.")
            .defineInRange("autocorrect.maxDistance", 2, 0, 5);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Cached values
    public static boolean feedbackLoginMessage;
    public static boolean devEnableEditor;
    public static boolean enableLevelBookKeybind = true; // default on to match spec default
    public static int autocorrectMaxDistance;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        feedbackLoginMessage = FEEDBACK_LOGIN_MESSAGE.get();
        devEnableEditor = DEV_ENABLE_EDITOR.get();
        enableLevelBookKeybind = ENABLE_LEVEL_BOOK_KEYBIND.get();
        autocorrectMaxDistance = Math.max(0, AUTOCORRECT_MAX_DISTANCE.get());
    }
}
