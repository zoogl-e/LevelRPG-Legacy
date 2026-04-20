package net.zoogle.levelrpg.data;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public class DataEvents {
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SkillLoader());
        event.addListener(new XpCurves());
        event.addListener(new ActivityRules());
        event.addListener(new RecipeUnlockLoader());
        event.addListener(new SkillTreeLoader());
    }
}
