package net.zoogle.levelrpg.events;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.net.payload.RecklessChargeStatePayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ValorTechniqueEvents {
    private static final Map<UUID, Long> JUDGEMENT_DAY_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, ShieldBashState> ACTIVE_SHIELD_BASH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SHIELD_BASH_LAST_USE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SHIELD_BASH_LAST_BLOCKING_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FORWARD_FRENZY_IFRAME_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, ForwardFrenzyState> ACTIVE_FORWARD_FRENZY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECKLESS_CHARGE_START_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> RECKLESS_FROZEN_RESOLVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECKLESS_LAST_USE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_GROUNDED_TICK = new ConcurrentHashMap<>();
    private static final int JUDGEMENT_REFLECT_CAP = 14;
    private static final int SHIELD_BASH_COOLDOWN_TICKS = 20;
    private static final int SHIELD_BASH_ACTIVE_TICKS = 10;
    private static final int SHIELD_BASH_BLOCKING_GRACE_TICKS = 6;
    private static final double SHIELD_BASH_RESOLVE_COST = 20.0;
    private static final float SHIELD_BASH_DAMAGE = 7.0F;
    private static final float SHIELD_BASH_KNOCKBACK = 1.25F;
    private static final double SHIELD_BASH_CHARGE_SPEED = 1.35;
    private static final double SHIELD_BASH_BOUNCE_SPEED = 0.78;
    private static final int FORWARD_FRENZY_IFRAMES = 8;
    private static final int FORWARD_FRENZY_ACTIVE_TICKS = 12;
    private static final double FORWARD_FRENZY_AERIAL_RESOLVE_COST = 24.0;
    private static final double FORWARD_FRENZY_GROUNDED_RESOLVE_COST = 30.0;
    private static final int FORWARD_FRENZY_JUMP_BUFFER_TICKS = 8;
    private static final int RECKLESS_COOLDOWN_TICKS = 95;
    private static final int RECKLESS_MAX_CHARGE_TICKS = 30;
    private static final int RECKLESS_MIN_CHARGE_TICKS = 6;
    private static final double RECKLESS_RESOLVE_MIN = 20.0;
    private static final double RECKLESS_RESOLVE_MAX = 48.0;
    private static final ResourceLocation RECKLESS_ARMOR_SHRED_ID = id("reckless_charge_armor_shred");
    private static final ResourceLocation RECKLESS_MOVE_SLOW_ID = id("reckless_charge_move_slow");

    private record ShieldBashState(long expireTick, boolean hit) {
    }

    private static final class ForwardFrenzyState {
        private final long expireTick;
        private final Vec3 forwardDir;
        private final float baseDamage;
        private final double falloff;
        private int hits;
        private final Set<UUID> hitTargets = new HashSet<>();

        private ForwardFrenzyState(long expireTick, Vec3 forwardDir, float baseDamage, double falloff) {
            this.expireTick = expireTick;
            this.forwardDir = forwardDir;
            this.baseDamage = baseDamage;
            this.falloff = falloff;
        }
    }

    public static void startJudgementDay(ServerPlayer player, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        long until = player.level().getGameTime() + durationTicks;
        JUDGEMENT_DAY_UNTIL_TICK.put(player.getUUID(), until);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks + 5, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, durationTicks + 5, 0, false, true, true));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIncomingDamageJudgementDay(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        long frenzyIFrames = FORWARD_FRENZY_IFRAME_UNTIL_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (frenzyIFrames != Long.MIN_VALUE && now <= frenzyIFrames) {
            event.setNewDamage(0.0F);
            return;
        }
        long until = JUDGEMENT_DAY_UNTIL_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (until == Long.MIN_VALUE || now > until) {
            if (until != Long.MIN_VALUE) {
                JUDGEMENT_DAY_UNTIL_TICK.remove(player.getUUID());
            }
            return;
        }
        float incoming = event.getNewDamage();
        if (incoming > 0.0F && event.getSource().getEntity() instanceof LivingEntity attacker) {
            float reflected = Math.min(JUDGEMENT_REFLECT_CAP, incoming);
            attacker.hurt(player.damageSources().playerAttack(player), reflected);
        }
        event.setNewDamage(0.0F);
    }

    public static String tryActivateShieldBash(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return "Shield Bash unavailable";
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.SHIELD_BASH)) {
            return "Requires valor node shield_bash";
        }
        long now = player.level().getGameTime();
        boolean isBlockingNow = player.isBlocking();
        long lastBlocking = SHIELD_BASH_LAST_BLOCKING_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (!isBlockingNow && (lastBlocking == Long.MIN_VALUE || now - lastBlocking > SHIELD_BASH_BLOCKING_GRACE_TICKS)) {
            return "Shield Bash requires blocking";
        }
        long last = SHIELD_BASH_LAST_USE_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < SHIELD_BASH_COOLDOWN_TICKS) {
            return "Shield Bash recharging";
        }
        if (!PlayerGauges.spend(player, GaugeRegistry.RESOLVE, SHIELD_BASH_RESOLVE_COST)) {
            return "Not enough Resolve";
        }
        Vec3 look = player.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0e-8) {
            return "Shield Bash failed";
        }
        Vec3 dir = look.normalize();
        Vec3 impulse = new Vec3(dir.x * SHIELD_BASH_CHARGE_SPEED, Math.max(0.08, dir.y * 0.18), dir.z * SHIELD_BASH_CHARGE_SPEED);
        player.setDeltaMovement(impulse);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        SHIELD_BASH_LAST_USE_TICK.put(player.getUUID(), now);
        ACTIVE_SHIELD_BASH.put(player.getUUID(), new ShieldBashState(now + SHIELD_BASH_ACTIVE_TICKS, false));
        return null;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isBlocking()) {
            SHIELD_BASH_LAST_BLOCKING_TICK.put(player.getUUID(), level.getGameTime());
        }
        if (player.onGround()) {
            LAST_GROUNDED_TICK.put(player.getUUID(), level.getGameTime());
        }
        tickForwardFrenzy(player, level.getGameTime());
        tickRecklessCharge(player, level.getGameTime());
        ShieldBashState state = ACTIVE_SHIELD_BASH.get(player.getUUID());
        if (state == null) {
            return;
        }
        long now = level.getGameTime();
        if (now > state.expireTick) {
            ACTIVE_SHIELD_BASH.remove(player.getUUID());
            return;
        }
        if (state.hit) {
            ACTIVE_SHIELD_BASH.remove(player.getUUID());
            return;
        }
        AABB hitbox = player.getBoundingBox().inflate(0.45);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitbox, e -> e != player && e.isAlive())) {
            if (target.hurt(player.damageSources().playerAttack(player), SHIELD_BASH_DAMAGE)) {
                target.knockback(SHIELD_BASH_KNOCKBACK, player.getX() - target.getX(), player.getZ() - target.getZ());
            }
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.SHIELD_BLOCK,
                    SoundSource.PLAYERS,
                    1.0F,
                    0.9F + player.getRandom().nextFloat() * 0.2F
            );
            Vec3 look = player.getLookAngle();
            Vec3 bounceDir = look.lengthSqr() >= 1.0e-8
                    ? look.normalize()
                    : new Vec3(0.0, 0.0, 1.0);
            player.setDeltaMovement(-bounceDir.x * SHIELD_BASH_BOUNCE_SPEED, Math.max(0.12, player.getDeltaMovement().y * 0.5), -bounceDir.z * SHIELD_BASH_BOUNCE_SPEED);
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            ACTIVE_SHIELD_BASH.put(player.getUUID(), new ShieldBashState(now, true));
            return;
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        JUDGEMENT_DAY_UNTIL_TICK.remove(id);
        ACTIVE_SHIELD_BASH.remove(id);
        SHIELD_BASH_LAST_USE_TICK.remove(id);
        SHIELD_BASH_LAST_BLOCKING_TICK.remove(id);
        FORWARD_FRENZY_IFRAME_UNTIL_TICK.remove(id);
        ACTIVE_FORWARD_FRENZY.remove(id);
        RECKLESS_CHARGE_START_TICK.remove(id);
        RECKLESS_FROZEN_RESOLVE.remove(id);
        RECKLESS_LAST_USE_TICK.remove(id);
        LAST_GROUNDED_TICK.remove(id);
        if (event.getEntity() instanceof ServerPlayer player) {
            clearRecklessChargeModifiers(player);
            syncRecklessChargeState(player, false);
        }
    }

    public static boolean activateGoad(ServerPlayer player, int durationTicks, double radius) {
        if (player == null) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        boolean affected = false;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, Entity::isAlive)) {
            mob.setTarget(player);
            affected = true;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, durationTicks, 0, false, true, true));
        return affected;
    }

    public static String tryActivateForwardFrenzy(ServerPlayer player) {
        if (player == null) {
            return "Forward Frenzy unavailable";
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.FORWARD_FRENZY)) {
            return "Requires valor node forward_frenzy";
        }
        if (!ValorMeleeWeapons.isValorMelee(player.getMainHandItem())) {
            return "Forward Frenzy requires a melee weapon";
        }
        long now = player.level().getGameTime();
        long lastGrounded = LAST_GROUNDED_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        boolean jumpWindow = !player.onGround()
                && lastGrounded != Long.MIN_VALUE
                && now - lastGrounded <= FORWARD_FRENZY_JUMP_BUFFER_TICKS;
        boolean groundedTrigger = player.onGround();
        if (!jumpWindow && !groundedTrigger) {
            return "Forward Frenzy requires a jump or grounded trigger";
        }
        double resolveCost = jumpWindow ? FORWARD_FRENZY_AERIAL_RESOLVE_COST : FORWARD_FRENZY_GROUNDED_RESOLVE_COST;
        if (!PlayerGauges.spend(player, GaugeRegistry.RESOLVE, resolveCost)) {
            return "Not enough Resolve";
        }
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0e-8) {
            return "Forward Frenzy failed";
        }
        Vec3 dir = look.normalize();
        double y = Mth.clamp(dir.y, -0.12, 0.22);
        Vec3 dashDir = new Vec3(dir.x, y, dir.z);
        if (dashDir.lengthSqr() < 1.0e-8) {
            dashDir = new Vec3(dir.x, 0.0, dir.z);
        }
        if (dashDir.lengthSqr() < 1.0e-8) {
            return "Forward Frenzy failed";
        }
        dashDir = dashDir.normalize();
        double dashMagnitude = jumpWindow ? 1.75 : 1.55;
        float baseDamage = jumpWindow ? 9.0F : 7.0F;
        double damageFalloff = jumpWindow ? 0.78 : 0.74;
        Vec3 dash = dashDir.scale(dashMagnitude);
        player.setDeltaMovement(dash);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.swing(InteractionHand.MAIN_HAND);
        FORWARD_FRENZY_IFRAME_UNTIL_TICK.put(player.getUUID(), now + FORWARD_FRENZY_IFRAMES);
        ACTIVE_FORWARD_FRENZY.put(player.getUUID(),
                new ForwardFrenzyState(now + FORWARD_FRENZY_ACTIVE_TICKS, dashDir, baseDamage, damageFalloff));
        return null;
    }

    public static boolean activateCrescentSlash(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        AABB aoe = player.getBoundingBox().inflate(4.0, 1.5, 4.0);
        boolean hit = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, aoe, e -> e != player && e.isAlive())) {
            Vec3 to = target.position().subtract(player.position());
            if (to.lengthSqr() < 0.0001) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), 10.0F);
            target.knockback(1.0, player.getX() - target.getX(), player.getZ() - target.getZ());
            hit = true;
        }
        return hit;
    }

    public static String tryStartRecklessCharge(ServerPlayer player) {
        if (player == null) {
            return "Reckless Strike unavailable";
        }
        if (RECKLESS_CHARGE_START_TICK.containsKey(player.getUUID())) {
            return null;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.RECKLESS_STRIKE)) {
            return "Requires valor node reckless_strike";
        }
        if (!ValorMeleeWeapons.isValorMelee(player.getMainHandItem())) {
            return "Reckless Strike requires a melee weapon";
        }
        if (!player.onGround()) {
            return "Stand your ground to charge Reckless Strike";
        }
        if (!player.isShiftKeyDown()) {
            return "Sneak to charge Reckless Strike";
        }
        long now = player.level().getGameTime();
        long last = RECKLESS_LAST_USE_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < RECKLESS_COOLDOWN_TICKS) {
            return "Reckless Strike recharging";
        }
        if (!profile.gauges.canSpend(player, profile, GaugeRegistry.RESOLVE, RECKLESS_RESOLVE_MAX)) {
            return "Not enough Resolve";
        }
        RECKLESS_FROZEN_RESOLVE.put(player.getUUID(), profile.gauges.getValue(GaugeRegistry.RESOLVE));
        RECKLESS_CHARGE_START_TICK.put(player.getUUID(), now);
        syncRecklessChargeState(player, true);
        return null;
    }

    public static String tryReleaseRecklessCharge(ServerPlayer player) {
        if (player == null) {
            return "Reckless Strike unavailable";
        }
        Long startTick = RECKLESS_CHARGE_START_TICK.remove(player.getUUID());
        RECKLESS_FROZEN_RESOLVE.remove(player.getUUID());
        clearRecklessChargeModifiers(player);
        syncRecklessChargeState(player, false);
        if (startTick == null) {
            return null;
        }
        long now = player.level().getGameTime();
        int chargeTicks = Mth.clamp((int) (now - startTick), 0, RECKLESS_MAX_CHARGE_TICKS);
        if (chargeTicks < RECKLESS_MIN_CHARGE_TICKS) {
            return "Reckless Strike charge released too early";
        }
        float charge = chargeTicks / (float) RECKLESS_MAX_CHARGE_TICKS;
        double resolveCost = Mth.lerp(charge, RECKLESS_RESOLVE_MIN, RECKLESS_RESOLVE_MAX);
        if (!PlayerGauges.spend(player, GaugeRegistry.RESOLVE, resolveCost)) {
            return "Not enough Resolve";
        }
        player.swing(InteractionHand.MAIN_HAND);
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0e-8) {
            return "Reckless Strike failed";
        }
        Vec3 dir = look.normalize();
        double reach = Mth.lerp(charge, 3.4, 5.2);
        float damage = (float) Mth.lerp(charge, 10.0, 24.0);
        float knockback = (float) Mth.lerp(charge, 1.0, 1.8);
        Vec3 eye = player.getEyePosition();
        AABB search = player.getBoundingBox().expandTowards(dir.scale(reach)).inflate(1.0, 1.0, 1.0);
        LivingEntity bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, search, e -> e != player && e.isAlive())) {
            Vec3 targetCenter = candidate.getBoundingBox().getCenter();
            Vec3 to = targetCenter.subtract(eye);
            double distance = to.length();
            if (distance <= 0.01 || distance > reach + 0.7) {
                continue;
            }
            Vec3 toNorm = to.scale(1.0 / distance);
            double dot = toNorm.dot(dir);
            if (dot < 0.70) {
                continue;
            }
            // Prefer entities centered in front of the crosshair, then nearer ones.
            double score = dot * 10.0 - distance;
            if (score > bestScore) {
                bestScore = score;
                bestTarget = candidate;
            }
        }
        boolean hit = false;
        if (bestTarget != null) {
            if (bestTarget.hurt(player.damageSources().playerAttack(player), damage)) {
                bestTarget.knockback(knockback, player.getX() - bestTarget.getX(), player.getZ() - bestTarget.getZ());
            }
            hit = true;
        }
        RECKLESS_LAST_USE_TICK.put(player.getUUID(), now);
        return hit ? null : "Reckless Strike whiffed";
    }

    private static void tickRecklessCharge(ServerPlayer player, long now) {
        Long start = RECKLESS_CHARGE_START_TICK.get(player.getUUID());
        if (start == null) {
            RECKLESS_FROZEN_RESOLVE.remove(player.getUUID());
            clearRecklessChargeModifiers(player);
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        Vec3 currentVelocity = player.getDeltaMovement();
        if (!player.onGround()
                || !player.isShiftKeyDown()
                || !ValorMeleeWeapons.isValorMelee(player.getMainHandItem())
                || !SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.RECKLESS_STRIKE)
                || !profile.gauges.canSpend(player, profile, GaugeRegistry.RESOLVE, RECKLESS_RESOLVE_MAX)) {
            RECKLESS_CHARGE_START_TICK.remove(player.getUUID());
            RECKLESS_FROZEN_RESOLVE.remove(player.getUUID());
            clearRecklessChargeModifiers(player);
            syncRecklessChargeState(player, false);
            return;
        }
        Double frozenResolve = RECKLESS_FROZEN_RESOLVE.get(player.getUUID());
        if (frozenResolve != null) {
            double currentResolve = profile.gauges.getValue(GaugeRegistry.RESOLVE);
            if (Math.abs(currentResolve - frozenResolve) > 0.0001) {
                profile.gauges.setValue(player, profile, GaugeRegistry.RESOLVE, frozenResolve);
                LevelProfile.save(player, profile);
                PlayerGauges.sync(player, profile, GaugeRegistry.RESOLVE);
            }
        }
        int chargeTicks = Mth.clamp((int) (now - start), 0, RECKLESS_MAX_CHARGE_TICKS);
        float charge = chargeTicks / (float) RECKLESS_MAX_CHARGE_TICKS;
        double armorPenalty = -Mth.lerp(charge, 0.10, 0.65);
        double movePenalty = -1.0;
        applyChargeModifier(player.getAttribute(Attributes.ARMOR), RECKLESS_ARMOR_SHRED_ID, armorPenalty);
        applyChargeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), RECKLESS_MOVE_SLOW_ID, movePenalty);
        player.setSprinting(false);
        if (Math.abs(currentVelocity.x) > 0.001 || Math.abs(currentVelocity.z) > 0.001) {
            player.setDeltaMovement(0.0, Math.min(0.0, currentVelocity.y), 0.0);
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private static void tickForwardFrenzy(ServerPlayer player, long now) {
        ForwardFrenzyState state = ACTIVE_FORWARD_FRENZY.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (now > state.expireTick) {
            ACTIVE_FORWARD_FRENZY.remove(player.getUUID());
            return;
        }
        AABB hitbox = player.getBoundingBox().expandTowards(state.forwardDir.scale(2.1)).inflate(0.75, 0.70, 0.75);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, hitbox, e -> e != player && e.isAlive())) {
            UUID id = target.getUUID();
            if (state.hitTargets.contains(id)) {
                continue;
            }
            Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            if (to.lengthSqr() < 1.0e-8) {
                continue;
            }
            Vec3 toNorm = to.normalize();
            if (toNorm.dot(state.forwardDir) < 0.30) {
                continue;
            }
            float damage = Math.max(3.0F, (float) (state.baseDamage * Math.pow(state.falloff, state.hits)));
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.knockback(0.7, player.getX() - target.getX(), player.getZ() - target.getZ());
            state.hitTargets.add(id);
            state.hits++;
        }
        if (player.horizontalCollision) {
            ACTIVE_FORWARD_FRENZY.remove(player.getUUID());
        }
    }

    private static void clearRecklessChargeModifiers(ServerPlayer player) {
        clearChargeModifier(player.getAttribute(Attributes.ARMOR), RECKLESS_ARMOR_SHRED_ID);
        clearChargeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), RECKLESS_MOVE_SLOW_ID);
    }

    private static void applyChargeModifier(AttributeInstance instance, ResourceLocation id, double amount) {
        if (instance == null) {
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                id,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void clearChargeModifier(AttributeInstance instance, ResourceLocation id) {
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static void syncRecklessChargeState(ServerPlayer player, boolean active) {
        if (player == null) {
            return;
        }
        int ticks = active ? RECKLESS_MAX_CHARGE_TICKS : 0;
        player.connection.send(new ClientboundCustomPayloadPacket(new RecklessChargeStatePayload(active, ticks)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }
}
