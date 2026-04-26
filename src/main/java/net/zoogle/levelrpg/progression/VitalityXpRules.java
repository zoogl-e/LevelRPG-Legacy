package net.zoogle.levelrpg.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.zoogle.levelrpg.profile.ProgressionSkill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Valor Grit XP rules. Valor absorbs the former Vitality skill — surviving
 * meaningful incoming danger develops the Grit branch alongside Offense.
 * Kept as a separate rule class so the XP logic remains easy to tune.
 */
public final class VitalityXpRules {
    public static final ResourceLocation SKILL_ID = ProgressionSkill.VALOR.id();

    private static final float MIN_MEANINGFUL_DAMAGE = 2.0F;
    private static final float LOW_HEALTH_THRESHOLD = 6.0F;
    private static final long SAME_THREAT_COOLDOWN_TICKS = 40L;

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private VitalityXpRules() {}

    public static long xpForDamageTaken(ServerPlayer player, DamageSource source, float damageTaken) {
        if (!isMeaningfulThreat(player, source) || damageTaken < MIN_MEANINGFUL_DAMAGE) {
            return 0L;
        }

        String threatKey = threatKey(source);
        if (threatKey == null || isOnCooldown(player, threatKey)) {
            return 0L;
        }

        long xp = Math.max(1L, Math.min(4L, (long) Math.floor(damageTaken / 2.0F)));
        if (player.getHealth() <= LOW_HEALTH_THRESHOLD && damageTaken >= 4.0F) {
            xp += 2L;
        }
        return xp;
    }

    private static boolean isMeaningfulThreat(ServerPlayer player, DamageSource source) {
        if (player == null || source == null || player.isCreative() || player.isSpectator()) {
            return false;
        }

        Entity attacker = source.getEntity();
        if (attacker == null || attacker == player) {
            return false;
        }
        return true;
    }

    private static String threatKey(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) {
            return null;
        }
        return attacker.getType().toString() + ":" + attacker.getUUID();
    }

    private static boolean isOnCooldown(ServerPlayer player, String threatKey) {
        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
        long now = player.level().getGameTime();
        Long nextAllowed = tracker.nextAllowedTickByThreat.get(threatKey);
        if (nextAllowed != null && now < nextAllowed) {
            return true;
        }
        tracker.nextAllowedTickByThreat.put(threatKey, now + SAME_THREAT_COOLDOWN_TICKS);
        return false;
    }

    private static final class Tracker {
        private final Map<String, Long> nextAllowedTickByThreat = new HashMap<>();
    }
}
