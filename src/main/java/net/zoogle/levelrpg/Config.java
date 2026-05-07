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

    // Progression investment source gating (Index scaffolding; no block check yet)
    private static final ModConfigSpec.BooleanValue REQUIRE_INDEX_FOR_DISCIPLINE_INVESTMENT = BUILDER
            .comment("If true, normal player-facing Discipline investment is intended to happen at The Index. Commands/system remain allowed.")
            .define("progression.requireIndexForDisciplineInvestment", true);

    private static final ModConfigSpec.BooleanValue ALLOW_BOOK_DISCIPLINE_INVESTMENT = BUILDER
            .comment("If true, the Enchiridion/book may perform Discipline investment without The Index.")
            .define("progression.allowBookDisciplineInvestment", false);

    private static final ModConfigSpec.BooleanValue GENERATE_INDEX_NEAR_SPAWN = BUILDER
            .comment("If true, place one original The Index block near Overworld spawn.")
            .define("progression.generateIndexNearSpawn", true);

    private static final ModConfigSpec.IntValue INDEX_SPAWN_SEARCH_RADIUS = BUILDER
            .comment("Horizontal search radius (in blocks) around spawn for Index placement.")
            .defineInRange("progression.indexSpawnSearchRadius", 16, 0, 128);

    private static final ModConfigSpec.BooleanValue AWARD_ESSENCE_FROM_ADVANCEMENTS = BUILDER
            .comment("If true, completed vanilla advancements can award one-time Essence.")
            .define("progression.awardEssenceFromAdvancements", true);

    private static final ModConfigSpec.IntValue ADVANCEMENT_TASK_ESSENCE = BUILDER
            .comment("Essence reward for TASK frame advancements.")
            .defineInRange("progression.advancementTaskEssence", 1, 0, 1000);

    private static final ModConfigSpec.IntValue ADVANCEMENT_GOAL_ESSENCE = BUILDER
            .comment("Essence reward for GOAL frame advancements.")
            .defineInRange("progression.advancementGoalEssence", 2, 0, 1000);

    private static final ModConfigSpec.IntValue ADVANCEMENT_CHALLENGE_ESSENCE = BUILDER
            .comment("Essence reward for CHALLENGE frame advancements.")
            .defineInRange("progression.advancementChallengeEssence", 3, 0, 1000);

    // Autocorrect settings
    public static final ModConfigSpec.IntValue AUTOCORRECT_MAX_DISTANCE = BUILDER
            .comment("Levenshtein distance threshold for skill name autocorrect in commands. 0 disables fuzzy matching.")
            .defineInRange("autocorrect.maxDistance", 2, 0, 5);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Cached values
    public static boolean feedbackLoginMessage;
    public static boolean devEnableEditor;
    public static boolean enableLevelBookKeybind = true; // default on to match spec default
    public static boolean requireIndexForDisciplineInvestment = true;
    public static boolean allowBookDisciplineInvestment = false;
    public static boolean generateIndexNearSpawn = true;
    public static int indexSpawnSearchRadius = 16;
    public static boolean awardEssenceFromAdvancements = true;
    public static int advancementTaskEssence = 1;
    public static int advancementGoalEssence = 2;
    public static int advancementChallengeEssence = 3;
    public static int autocorrectMaxDistance;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        feedbackLoginMessage = FEEDBACK_LOGIN_MESSAGE.get();
        devEnableEditor = DEV_ENABLE_EDITOR.get();
        enableLevelBookKeybind = ENABLE_LEVEL_BOOK_KEYBIND.get();
        requireIndexForDisciplineInvestment = REQUIRE_INDEX_FOR_DISCIPLINE_INVESTMENT.get();
        allowBookDisciplineInvestment = ALLOW_BOOK_DISCIPLINE_INVESTMENT.get();
        generateIndexNearSpawn = GENERATE_INDEX_NEAR_SPAWN.get();
        indexSpawnSearchRadius = Math.max(0, INDEX_SPAWN_SEARCH_RADIUS.get());
        awardEssenceFromAdvancements = AWARD_ESSENCE_FROM_ADVANCEMENTS.get();
        advancementTaskEssence = Math.max(0, ADVANCEMENT_TASK_ESSENCE.get());
        advancementGoalEssence = Math.max(0, ADVANCEMENT_GOAL_ESSENCE.get());
        advancementChallengeEssence = Math.max(0, ADVANCEMENT_CHALLENGE_ESSENCE.get());
        autocorrectMaxDistance = Math.max(0, AUTOCORRECT_MAX_DISTANCE.get());
    }
}
