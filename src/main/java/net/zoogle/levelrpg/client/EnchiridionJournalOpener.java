package net.zoogle.levelrpg.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Bridges LevelRPG's journal keybind to Enchiridion's v2 client API without
 * introducing a hard compile-time dependency from this project.
 */
public final class EnchiridionJournalOpener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation LEVEL_RPG_JOURNAL_BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("enchiridion", "level_rpg_journal");

    private static final String ENCHIRIDION_MOD_ID = "enchiridion";
    private static final String ENCHIRIDION_CLIENT_CLASS = "net.zoogle.enchiridion.EnchiridionClient";
    private static final String ENCHIRIDION_BOOK_SCREEN_CLASS = "net.zoogle.enchiridion.client.ui.BookScreen";

    private EnchiridionJournalOpener() {}

    public static boolean isCurrentJournalScreen(Minecraft minecraft) {
        return minecraft.screen != null
                && ENCHIRIDION_BOOK_SCREEN_CLASS.equals(minecraft.screen.getClass().getName());
    }

    public static boolean openLevelRpgJournal(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (!ModList.get().isLoaded(ENCHIRIDION_MOD_ID)) {
            minecraft.player.displayClientMessage(Component.literal("Enchiridion is not available; Level RPG Journal cannot open."), true);
            return false;
        }
        try {
            Class<?> clientClass = Class.forName(ENCHIRIDION_CLIENT_CLASS);
            Method getMethod = clientClass.getMethod("get");
            Object client = getMethod.invoke(null);
            Method openBookMethod = clientClass.getMethod("openBook", ResourceLocation.class);
            LOGGER.info(
                    "Opening Enchiridion journal book {} with LevelRPG client cache state: canonicalReady={}, skills={}, lastSkillId={}",
                    LEVEL_RPG_JOURNAL_BOOK_ID,
                    ClientProfileCache.hasCanonicalProfileData(),
                    ClientProfileCache.getSkillsView().size(),
                    ClientProfileCache.getLastSkillId()
            );
            openBookMethod.invoke(client, LEVEL_RPG_JOURNAL_BOOK_ID);
            return true;
        } catch (ReflectiveOperationException ex) {
            LOGGER.warn("Failed to open Enchiridion journal book {}", LEVEL_RPG_JOURNAL_BOOK_ID, ex);
            minecraft.player.displayClientMessage(Component.literal("Enchiridion journal integration is unavailable."), true);
            return false;
        }
    }
}
