package net.zoogle.levelrpg;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class CreeperTest {
    @SubscribeEvent
    public void test(LivingIncomingDamageEvent event) {
        event.setCanceled(true);
    }
}
