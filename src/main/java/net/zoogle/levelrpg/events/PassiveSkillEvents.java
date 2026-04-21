package net.zoogle.levelrpg.events;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;

/**
 * Thin event bridge for baseline passive scaling. The actual formulas and
 * modifier rules live in {@link PassiveSkillScalingService}.
 */
public class PassiveSkillEvents {
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        PassiveSkillScalingService.applyMiningBreakSpeed(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PassiveSkillScalingService.forget(player);
    }
}
