package net.zoogle.levelrpg.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.gauge.GaugeDefinition;
import net.zoogle.levelrpg.gauge.GaugeModifiers;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.skilltree.DelvingNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GaugeEvents {
    private static final long CAP_DECAY_GRACE_TICKS = 60L;
    private static final double CAP_EPSILON = 0.0001;
    private static final double MOMENTUM_DECAY_BASE_PER_SECOND = 1.0;
    private static final double MOMENTUM_DECAY_MAX_PER_SECOND = 128.0;
    private static final double RESOLVE_DECAY_BASE_PER_SECOND = 5.0;
    private static final double RESOLVE_DECAY_RAMP_START_SECONDS = 3.0;
    private static final double RESOLVE_DECAY_EXP_BASE = 1.35;
    private static final double RESOLVE_DECAY_MAX_PER_SECOND = 180.0;
    private static final Map<UUID, Double> MOMENTUM_DECAY_RATE = new HashMap<>();
    private static final Map<UUID, Long> MOMENTUM_LAST_DECAY_TICK = new HashMap<>();
    private static final Map<UUID, Double> RESOLVE_DECAY_RATE = new HashMap<>();
    private static final Map<UUID, Long> RESOLVE_LAST_DECAY_TICK = new HashMap<>();
    private static final Map<UUID, Long> RESOLVE_LAST_ACTIVITY_TICK = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MOMENTUM_DECAY_RATE.remove(player.getUUID());
            MOMENTUM_LAST_DECAY_TICK.remove(player.getUUID());
            RESOLVE_DECAY_RATE.remove(player.getUUID());
            RESOLVE_LAST_DECAY_TICK.remove(player.getUUID());
            RESOLVE_LAST_ACTIVITY_TICK.put(player.getUUID(), player.level().getGameTime());
            PlayerGauges.syncAll(player, LevelProfile.get(player));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (gameTime % 20L != 0L) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        boolean anyChanged = false;
        for (GaugeDefinition gauge : GaugeRegistry.entries()) {
            double decayPerSecond = GaugeModifiers.computeDecayPerSecond(player, profile, gauge);
            if (decayPerSecond <= 0.0) {
                if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                    MOMENTUM_DECAY_RATE.remove(player.getUUID());
                    MOMENTUM_LAST_DECAY_TICK.remove(player.getUUID());
                } else if (GaugeRegistry.RESOLVE.equals(gauge.id())) {
                    RESOLVE_DECAY_RATE.remove(player.getUUID());
                    RESOLVE_LAST_DECAY_TICK.remove(player.getUUID());
                    RESOLVE_LAST_ACTIVITY_TICK.remove(player.getUUID());
                }
                continue;
            }
            var instance = profile.gauges.get(gauge.id());
            if (instance.value() <= gauge.min()) {
                if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                    MOMENTUM_DECAY_RATE.remove(player.getUUID());
                    MOMENTUM_LAST_DECAY_TICK.remove(player.getUUID());
                } else if (GaugeRegistry.RESOLVE.equals(gauge.id())) {
                    RESOLVE_DECAY_RATE.remove(player.getUUID());
                    RESOLVE_LAST_DECAY_TICK.remove(player.getUUID());
                    RESOLVE_LAST_ACTIVITY_TICK.remove(player.getUUID());
                }
                continue;
            }
            double max = profile.gauges.getMax(player, profile, gauge.id());
            long decayDelayTicks = gauge.decayDelayTicks();
            if (max > gauge.min() && instance.value() + CAP_EPSILON >= max) {
                // Hitting cap should feel rewarding: hold full gauges a bit longer before passive decay starts.
                decayDelayTicks += CAP_DECAY_GRACE_TICKS;
            }
            if (gameTime - instance.lastChangedTick() < decayDelayTicks) {
                continue;
            }
            double effectiveDecay = decayPerSecond;
            double resolveIdleSeconds = 0.0;
            if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                long lastDecayTick = MOMENTUM_LAST_DECAY_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
                // If momentum changed since our last passive decay, restart the ramp.
                if (instance.lastChangedTick() > lastDecayTick) {
                    MOMENTUM_DECAY_RATE.remove(player.getUUID());
                }
                effectiveDecay = Math.max(
                        MOMENTUM_DECAY_BASE_PER_SECOND,
                        MOMENTUM_DECAY_RATE.getOrDefault(player.getUUID(), MOMENTUM_DECAY_BASE_PER_SECOND)
                );
                effectiveDecay = Math.min(MOMENTUM_DECAY_MAX_PER_SECOND, effectiveDecay);
            } else if (GaugeRegistry.RESOLVE.equals(gauge.id())) {
                // Resolve decays gently at first, then ramps exponentially after ~3s idle.
                resolveIdleSeconds = Math.max(0.0, (gameTime - instance.lastChangedTick()) / 20.0);
                long activityTick = RESOLVE_LAST_ACTIVITY_TICK.getOrDefault(player.getUUID(), gameTime);
                long lastResolveDecayTick = RESOLVE_LAST_DECAY_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
                // If Resolve changed from a non-decay source since our last decay pulse, restart the ramp.
                if (instance.lastChangedTick() > lastResolveDecayTick) {
                    RESOLVE_DECAY_RATE.remove(player.getUUID());
                    activityTick = instance.lastChangedTick();
                    RESOLVE_LAST_ACTIVITY_TICK.put(player.getUUID(), activityTick);
                }
                resolveIdleSeconds = Math.max(0.0, (gameTime - activityTick) / 20.0);
                effectiveDecay = Math.max(RESOLVE_DECAY_BASE_PER_SECOND,
                        RESOLVE_DECAY_RATE.getOrDefault(player.getUUID(), RESOLVE_DECAY_BASE_PER_SECOND));
                effectiveDecay = Math.min(RESOLVE_DECAY_MAX_PER_SECOND, effectiveDecay);
                if (resolveIdleSeconds <= RESOLVE_DECAY_RAMP_START_SECONDS) {
                    // Hold flat at base until the ramp window starts.
                    effectiveDecay = RESOLVE_DECAY_BASE_PER_SECOND;
                }
            }
            boolean changed = profile.gauges.add(player, profile, gauge.id(), -effectiveDecay);
            if (changed) {
                anyChanged = true;
                PlayerGauges.sync(player, profile, gauge.id());
                if (GaugeRegistry.RHYTHM.equals(gauge.id())) {
                    PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, profile);
                }
                if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                    MOMENTUM_LAST_DECAY_TICK.put(player.getUUID(), gameTime);
                    MOMENTUM_DECAY_RATE.put(player.getUUID(), Math.min(MOMENTUM_DECAY_MAX_PER_SECOND, effectiveDecay * 2.0));
                } else if (GaugeRegistry.RESOLVE.equals(gauge.id())) {
                    RESOLVE_LAST_DECAY_TICK.put(player.getUUID(), gameTime);
                    if (resolveIdleSeconds > RESOLVE_DECAY_RAMP_START_SECONDS) {
                        RESOLVE_DECAY_RATE.put(player.getUUID(),
                                Math.min(RESOLVE_DECAY_MAX_PER_SECOND, effectiveDecay * RESOLVE_DECAY_EXP_BASE));
                    } else {
                        RESOLVE_DECAY_RATE.remove(player.getUUID());
                    }
                }
            } else if (GaugeRegistry.MOMENTUM.equals(gauge.id())) {
                MOMENTUM_DECAY_RATE.remove(player.getUUID());
                MOMENTUM_LAST_DECAY_TICK.remove(player.getUUID());
            } else if (GaugeRegistry.RESOLVE.equals(gauge.id())) {
                RESOLVE_DECAY_RATE.remove(player.getUUID());
                RESOLVE_LAST_DECAY_TICK.remove(player.getUUID());
                RESOLVE_LAST_ACTIVITY_TICK.remove(player.getUUID());
            }
        }
        if (anyChanged) {
            LevelProfile.save(player, profile);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var state = event.getState();
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Tags.Blocks.ORES)) {
            LevelProfile profile = LevelProfile.get(player);
            if (!SkillUnlockQuery.hasNode(profile, DelvingNodeIds.SKILL, DelvingNodeIds.OVERDRIVE)) {
                return;
            }
            double amount = 5.0;
            if ((state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER))
                    && SkillUnlockQuery.hasNode(profile, DelvingNodeIds.SKILL, DelvingNodeIds.STONE_RESERVOIR)) {
                amount += 1.0;
            }
            DelvingGameplayEvents.addMiningMomentum(player, profile, amount);
        }
    }
}
