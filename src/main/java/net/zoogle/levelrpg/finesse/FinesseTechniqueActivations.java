package net.zoogle.levelrpg.finesse;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.technique.TechniqueActivationContext;
import net.zoogle.levelrpg.technique.TechniqueResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handlers for Finesse techniques (dash, volley window, wind form, calculated fall).
 */
public final class FinesseTechniqueActivations {
    private static final double GHOST_STEP_GROUND_HORIZONTAL = 0.96;
    private static final double GHOST_STEP_AIR_HORIZONTAL = 0.83;
    private static final double GHOST_STEP_GROUND_VERTICAL = 0.10;
    private static final double GHOST_STEP_AIR_VERTICAL = 0.10;
    private static final double GHOST_STEP_RHYTHM_COST = 18.0;
    private static final int GHOST_STEP_COOLDOWN_TICKS = 14;
    private static final double GHOST_STEP_MAX_HORIZONTAL_SPEED = 1.35;
    
    private record JuggleLink(int targetId, int expireTick) {}
    private static final Map<UUID, JuggleLink> UPPERCUT_JUGGLE_TARGETS = new ConcurrentHashMap<>();
    /** Tracks which players are currently suspended in a juggle (NoGravity), expiry by server tick. */
    private static final Map<UUID, Integer> PLAYER_JUGGLE_SUSPEND_UNTIL = new ConcurrentHashMap<>();
    private static final double SQRT_EPSILON = 1.0e-8;
    private static final Map<UUID, Integer> GHOST_STEP_COOLDOWN_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RAPID_VOLLEY_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BLURRED_IMAGE_COOLDOWN_UNTIL_TICK = new ConcurrentHashMap<>();
    /** Ticks the juggle lasts after an Uppercut connects. */
    private static final int JUGGLE_DURATION_TICKS = 100; // 5 seconds
    /** Extra ticks added to the juggle window per punch. */
    private static final int JUGGLE_BOUNCE_EXTEND_TICKS = 30;
    /** Upward velocity applied to both player and mob on each juggle punch. */
    private static final double JUGGLE_BOUNCE_Y = 0.35;

    /** Players who killed their juggle target in the air and have one free fall damage cancel. */
    private static final java.util.Set<UUID> AERIAL_KILL_GRACE_PLAYERS =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private FinesseTechniqueActivations() {
    }

    public static void clearPlayer(UUID playerId) {
        GHOST_STEP_COOLDOWN_UNTIL_TICK.remove(playerId);
        RAPID_VOLLEY_UNTIL_TICK.remove(playerId);
        BLURRED_IMAGE_COOLDOWN_UNTIL_TICK.remove(playerId);
        PLAYER_JUGGLE_SUSPEND_UNTIL.remove(playerId);
        UPPERCUT_JUGGLE_TARGETS.remove(playerId);
        AERIAL_KILL_GRACE_PLAYERS.remove(playerId);
    }

    /** Called when the juggle target dies mid-air. Grants one free fall damage cancel. */
    public static void grantAerialKillGrace(UUID playerId) {
        AERIAL_KILL_GRACE_PLAYERS.add(playerId);
    }

    /**
     * Called when the player is about to take fall damage.
     * Returns true (and consumes the grace) if fall damage should be negated.
     */
    public static boolean consumeAerialKillGrace(UUID playerId) {
        return AERIAL_KILL_GRACE_PLAYERS.remove(playerId);
    }

    /** Returns true if the given mob entity is currently being juggled by any player. */
    public static boolean isJuggleTarget(int entityId) {
        for (JuggleLink link : UPPERCUT_JUGGLE_TARGETS.values()) {
            if (link.targetId() == entityId) return true;
        }
        return false;
    }

    /** Returns the player UUID whose juggle target matches this entity ID, or null. */
    public static UUID getJuggleOwner(int entityId) {
        for (Map.Entry<UUID, JuggleLink> e : UPPERCUT_JUGGLE_TARGETS.entrySet()) {
            if (e.getValue().targetId() == entityId) return e.getKey();
        }
        return null;
    }

    /**
     * Called every server tick from FinesseGameplayEvents.
     * Maintains NoGravity on suspended mobs and cleans up expired juggle states.
     */
    public static void tickJuggleState(net.minecraft.server.level.ServerLevel level) {
        int now = level.getServer().getTickCount();
        UPPERCUT_JUGGLE_TARGETS.entrySet().removeIf(entry -> {
            JuggleLink link = entry.getValue();
            net.minecraft.world.entity.Entity entity = level.getEntity(link.targetId());
            if (entity == null || !entity.isAlive() || now > link.expireTick()) {
                if (entity != null && entity.isAlive()) {
                    entity.setNoGravity(false);
                }
                return true;
            }
            // Only maintain the freeze if Ghost Step has already been used to lock on
            if (entity.isNoGravity()) {
                Vec3 vel = entity.getDeltaMovement();
                entity.setDeltaMovement(vel.x * 0.85, 0.0, vel.z * 0.85);
            }
            return false;
        });
        // Player suspension is handled via NoGravity set in Ghost Step; clean up on expiry
        PLAYER_JUGGLE_SUSPEND_UNTIL.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                net.minecraft.server.level.ServerPlayer sp =
                        level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (sp != null) {
                    sp.setNoGravity(false);
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Called from FinesseGameplayEvents when a Finesse punch lands on a juggle target.
     * Bounces both entities upward and refreshes the juggle window.
     */
    public static void tryJuggleBounce(ServerPlayer attacker, net.minecraft.world.entity.Entity victim) {
        UUID juggleOwner = getJuggleOwner(victim.getId());
        if (juggleOwner == null || !juggleOwner.equals(attacker.getUUID())) return;
        JuggleLink current = UPPERCUT_JUGGLE_TARGETS.get(attacker.getUUID());
        if (current == null) return;
        int newExpiry = attacker.server.getTickCount() + JUGGLE_BOUNCE_EXTEND_TICKS;
        UPPERCUT_JUGGLE_TARGETS.put(attacker.getUUID(), new JuggleLink(current.targetId(), newExpiry));
        PLAYER_JUGGLE_SUSPEND_UNTIL.put(attacker.getUUID(), newExpiry);
        // Bounce mob up
        Vec3 mobVel = victim.getDeltaMovement();
        victim.setDeltaMovement(mobVel.x, JUGGLE_BOUNCE_Y, mobVel.z);
        victim.hasImpulse = true;
        // Bounce player up
        Vec3 playerVel = attacker.getDeltaMovement();
        attacker.setDeltaMovement(playerVel.x, JUGGLE_BOUNCE_Y, playerVel.z);
        attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker));
        if (victim instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
        if (attacker.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    12, 0.3, 0.3, 0.3, 0.08);
        }
    }

    /** Returns true if this player is currently suspended in a juggle window (after Ghost Step). */
    public static boolean isPlayerJuggleSuspended(ServerPlayer player) {
        Integer until = PLAYER_JUGGLE_SUSPEND_UNTIL.get(player.getUUID());
        return until != null && player.server.getTickCount() <= until;
    }

    public static boolean isGhostStepping(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        int until = GHOST_STEP_COOLDOWN_UNTIL_TICK.getOrDefault(player.getUUID(), 0);
        return player.tickCount < until;
    }

    public static boolean isRapidVolleyActive(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        int until = RAPID_VOLLEY_UNTIL_TICK.getOrDefault(player.getUUID(), 0);
        return player.tickCount < until;
    }

    public static int blurredImageCooldownUntil(ServerPlayer victim) {
        return BLURRED_IMAGE_COOLDOWN_UNTIL_TICK.getOrDefault(victim.getUUID(), 0);
    }

    public static void setBlurredImageCooldown(ServerPlayer victim, int untilTick) {
        BLURRED_IMAGE_COOLDOWN_UNTIL_TICK.put(victim.getUUID(), untilTick);
    }

    public static TechniqueResult activateGhostStep(TechniqueActivationContext context) {
        ServerPlayer player = context.player();
        Vec3 look = player.getLookAngle();
        double lenH = look.x * look.x + look.z * look.z;
        if (lenH < SQRT_EPSILON) {
            return tryActivateGhostStep(player, 1, 0);
        }
        double inv = 1.0 / Math.sqrt(lenH);
        return tryActivateGhostStep(player, new Vec3(look.x * inv, 0.0, look.z * inv));
    }

    public static TechniqueResult tryActivateGhostStep(ServerPlayer player, int forward, int strafe) {
        int clampedForward = Integer.compare(forward, 0);
        int clampedStrafe = Integer.compare(strafe, 0);
        if (clampedForward == 0 && clampedStrafe == 0) {
            return TechniqueResult.failure("No Ghost Step direction");
        }
        float yaw = (float) Math.toRadians(player.getYRot());
        Vec3 forwardVec = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 rightVec = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 direction = forwardVec.scale(clampedForward).add(rightVec.scale(clampedStrafe));
        double len = direction.horizontalDistance();
        if (len < SQRT_EPSILON) {
            return TechniqueResult.failure("No Ghost Step direction");
        }
        return tryActivateGhostStep(player, direction.scale(1.0 / len));
    }

    private static TechniqueResult tryActivateGhostStep(ServerPlayer player, Vec3 normalizedHorizontalDirection) {
        if (player == null || player.isCreative() || player.isSpectator()) {
            return TechniqueResult.failure("Ghost Step unavailable");
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.GHOST_STEP)) {
            return TechniqueResult.failure("Requires Ghost Step");
        }
        int cooldownUntil = GHOST_STEP_COOLDOWN_UNTIL_TICK.getOrDefault(player.getUUID(), 0);
        if (player.tickCount < cooldownUntil) {
            return TechniqueResult.failure("Ghost Step is recovering");
        }
        if (!PlayerGauges.canSpend(player, GaugeRegistry.RHYTHM, GHOST_STEP_RHYTHM_COST)) {
            return TechniqueResult.failure("Not enough Rhythm");
        }
        if (!PlayerGauges.spend(player, GaugeRegistry.RHYTHM, GHOST_STEP_RHYTHM_COST)) {
            return TechniqueResult.failure("Not enough Rhythm");
        }
        GHOST_STEP_COOLDOWN_UNTIL_TICK.put(player.getUUID(), player.tickCount + GHOST_STEP_COOLDOWN_TICKS);

        boolean pursuitActive = false;
        JuggleLink link = UPPERCUT_JUGGLE_TARGETS.get(player.getUUID());
        if (link != null && player.server.getTickCount() <= link.expireTick()) {
            net.minecraft.world.entity.Entity targetEntity = player.level().getEntity(link.targetId());
            if (targetEntity != null && targetEntity.isAlive() && targetEntity.distanceToSqr(player) < 400.0) {

                // === Freeze the mob NOW at wherever it currently is ===
                targetEntity.setNoGravity(true);
                targetEntity.setDeltaMovement(0, 0, 0);
                targetEntity.hasImpulse = true;
                if (targetEntity instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }

                // Target is now stationary. Use projectile motion to arc the player up to it.
                // We want the player's eyes to arrive at the target's chest.
                double targetChestY = targetEntity.getY() + targetEntity.getBbHeight() * 0.5;
                double dx = targetEntity.getX() - player.getX();
                double dy = targetChestY - (player.getY() + player.getEyeHeight());
                double dz = targetEntity.getZ() - player.getZ();

                // Pick a travel time of 8 ticks; add gravity compensation on Y
                // (Minecraft gravity ≈ 0.08 blocks/tick accumulated over T ticks)
                final int T = 8;
                double vx = dx / T;
                double vy = dy / T + 0.08 * T / 2.0;
                double vz = dz / T;

                player.setDeltaMovement(vx, vy, vz);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                // Give the player a short suspension after they arrive so they can punch
                PLAYER_JUGGLE_SUSPEND_UNTIL.put(player.getUUID(),
                        player.server.getTickCount() + T + JUGGLE_BOUNCE_EXTEND_TICKS);

                pursuitActive = true;
            }
        }

        if (!pursuitActive) {
            double horizontal = player.onGround() ? GHOST_STEP_GROUND_HORIZONTAL : GHOST_STEP_AIR_HORIZONTAL;
            double vertical = player.onGround() ? GHOST_STEP_GROUND_VERTICAL : GHOST_STEP_AIR_VERTICAL;
            Vec3 current = player.getDeltaMovement();
            Vec3 horizontalNext = new Vec3(current.x, 0.0, current.z).add(normalizedHorizontalDirection.scale(horizontal));
            if (horizontalNext.horizontalDistanceSqr() > GHOST_STEP_MAX_HORIZONTAL_SPEED * GHOST_STEP_MAX_HORIZONTAL_SPEED) {
                horizontalNext = horizontalNext.normalize().scale(GHOST_STEP_MAX_HORIZONTAL_SPEED);
            }
            player.setDeltaMovement(horizontalNext.x, Math.max(current.y, vertical), horizontalNext.z);
        }

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, GHOST_STEP_COOLDOWN_TICKS, 0, false, false, true));
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    pursuitActive ? 40 : 25, 0.4D, pursuitActive ? 0.8D : 0.4D, 0.4D, 0.1D);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.5D, player.getZ(),
                    15, 0.3D, 0.3D, 0.3D, 0.02D);
            if (pursuitActive) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 1.6F);
            }
        }
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
        return TechniqueResult.success(pursuitActive ? "Ghost Step (Pursuit)" : "Ghost Step");
    }

    public static TechniqueResult activateRapidVolley(TechniqueActivationContext context) {
        ServerPlayer player = context.player();
        RAPID_VOLLEY_UNTIL_TICK.put(player.getUUID(), player.tickCount + 200);
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
        return TechniqueResult.success("Rapid Volley");
    }

    public static TechniqueResult activateLikeTheWind(TechniqueActivationContext context) {
        ServerPlayer player = context.player();
        int dur = 20 * 8;
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false, true));
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
        return TechniqueResult.success("Like The Wind");
    }

    public static TechniqueResult activateCalculatedShot(TechniqueActivationContext context) {
        ServerPlayer player = context.player();
        int dur = 20 * 5;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false, true));
        Vec3 m = player.getDeltaMovement();
        player.setDeltaMovement(m.x, Math.max(m.y, 0.22), m.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
        return TechniqueResult.success("Calculated Shot");
    }

    /**
     * Partial Uppercut: technique strike in a short cone — not a full hold-to-charge melee rework.
     */
    public static TechniqueResult activateUppercut(TechniqueActivationContext context) {
        activateUppercutFromPayload(context.player(), 0);
        return TechniqueResult.success("Uppercut");
    }

    public static void activateUppercutFromPayload(ServerPlayer player, int chargeTicks) {
        if (!SkillUnlockQuery.hasNode(LevelProfile.get(player), FinesseNodeIds.SKILL, FinesseNodeIds.UPPERCUT)) {
            return;
        }
        
        float charge = net.minecraft.util.Mth.clamp(chargeTicks / 20.0F, 0.0F, 1.0F);
        
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB sweep = new AABB(eye, eye.add(look.scale(3.6))).inflate(0.85, 0.65, 0.85);
        int hits = 0;
        
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Vec3 particlePos = eye.add(look.scale(1.2));
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    particlePos.x, particlePos.y, particlePos.z,
                    15 + (int)(charge * 15), 0.3, 0.4, 0.3, 0.1);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    particlePos.x, particlePos.y, particlePos.z,
                    5, 0.2, 0.2, 0.2, 0.05);
            
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS,
                    1.2F, 0.6F + (charge * 0.4F));
        }

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && e.isPickable())) {
            float damage = 6.0F + (charge * 8.0F);
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                hits++;
                // Normal upward launch — NoGravity is NOT applied here.
                // It only gets applied when the player uses Ghost Step to pursue.
                double verticalPush = 0.65D + (charge * 0.75D);
                target.setDeltaMovement(target.getDeltaMovement().x, verticalPush, target.getDeltaMovement().z);
                target.hasImpulse = true;
                if (target instanceof ServerPlayer spTarget) {
                    spTarget.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(spTarget));
                }
                int expiry = player.server.getTickCount() + JUGGLE_DURATION_TICKS;
                UPPERCUT_JUGGLE_TARGETS.put(player.getUUID(), new JuggleLink(target.getId(), expiry));
            }
        }
        
        if (hits > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS,
                    0.8F, 1.4F + (charge * 0.4F));
        }
        
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
    }

    /**
     * Partial mastery: one radial pulse of damage (full volley simulation deferred).
     */
    public static TechniqueResult activateBlotOutTheSun(TechniqueActivationContext context) {
        ServerPlayer player = context.player();
        AABB zone = player.getBoundingBox().inflate(5.25);
        int struck = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, zone, e -> e != player && e.isAlive() && e.isPickable())) {
            if (struck >= 16) {
                break;
            }
            if (target.hurt(player.damageSources().playerAttack(player), 4.0F)) {
                struck++;
            }
        }
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, LevelProfile.get(player));
        return TechniqueResult.success("Blot Out the Sun");
    }
}
