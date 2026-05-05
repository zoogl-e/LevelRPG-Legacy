package net.zoogle.levelrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.gauge.ClientGaugeCache;
import net.zoogle.levelrpg.client.gauge.MomentumHudController;
import net.zoogle.levelrpg.client.finesse.FinesseFirstPersonUnarmedRenderer;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCache;
import net.zoogle.levelrpg.client.technique.ClientTechniqueCostResolver;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.net.payload.ActivateTechniqueSlotPayload;
import net.zoogle.levelrpg.net.payload.MarkFistCombatIntentPayload;
import net.zoogle.levelrpg.net.payload.MarkFistWeakSpotIntentPayload;
import net.zoogle.levelrpg.net.payload.RequestForwardFrenzyPayload;
import net.zoogle.levelrpg.net.payload.RequestFistPunchPayload;
import net.zoogle.levelrpg.net.payload.RequestGhostStepPayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeReleasePayload;
import net.zoogle.levelrpg.net.payload.RequestRecklessChargeStartPayload;
import net.zoogle.levelrpg.net.payload.RequestShieldBashPayload;
import net.zoogle.levelrpg.net.payload.RequestUppercutPayload;
import net.zoogle.levelrpg.net.payload.SelectTechniqueSlotPayload;
import net.zoogle.levelrpg.net.payload.SetHandsUpGuardingPayload;
import net.zoogle.levelrpg.finesse.FinesseProjectileParry;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;
import net.zoogle.levelrpg.technique.TechniqueDefinition;
import net.zoogle.levelrpg.technique.TechniqueRegistry;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

import java.util.Objects;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class Keybinds {
    private static final double RECKLESS_CLIENT_MIN_RESOLVE_TO_START = 48.0;
    private static final double FORWARD_FRENZY_CLIENT_RESOLVE_COST_GROUNDED = 30.0;
    private static final double FORWARD_FRENZY_CLIENT_RESOLVE_COST_AERIAL = 24.0;
    private static final int RECKLESS_HOLD_ARM_TICKS = 4;
    private static final int GHOST_STEP_DOUBLE_TAP_TICKS = 7;
    private static final int GHOST_STEP_CLIENT_COOLDOWN_TICKS = 10;
    private static final float UNARMED_CLIENT_ATTACK_READY_THRESHOLD = 0.75F;
    private static final int UNARMED_COMBAT_SESSION_TIMEOUT_TICKS = 30;
    private static final double EXTENDED_FIST_REACH = 3.35D;
    private static final double HANDS_UP_OPENER_THREAT_REACH = 6.0D;
    private static final double HANDS_UP_OPENER_NEARBY_RADIUS = 4.0D;
    private static final double HANDS_UP_OPENER_PROJECTILE_RADIUS = 5.5D;
    private static final boolean[] GHOST_STEP_KEY_WAS_DOWN = new boolean[4];
    private static final int[] GHOST_STEP_LAST_TAP_TICK = new int[4];
    private static int ghostStepClientCooldownUntil;
    public static boolean uppercutCharging = false;
    public static int uppercutChargeStartTick = 0;
    private static boolean uppercutChargePending = false;
    private static int uppercutPressClientTick = 0;

    public static float getUppercutChargeProgress(net.minecraft.client.player.AbstractClientPlayer player, float partialTicks) {
        if (!uppercutCharging || player == null) return 0.0F;
        float ticks = (player.tickCount - uppercutChargeStartTick) + partialTicks;
        return Math.min(1.0F, ticks / 20.0F);
    }
    private Keybinds() {}

    public static KeyMapping OPEN_BOOK;
    public static KeyMapping TOGGLE_ORBIT;
    public static KeyMapping TOGGLE_ORBIT_BOOK;
    public static KeyMapping TECHNIQUE_SELECT_MODIFIER;
    public static KeyMapping TECHNIQUE_TRIGGER;
    public static final KeyMapping[] TECHNIQUE_SLOTS = new KeyMapping[PlayerTechniqueData.SLOT_COUNT];
    private static final boolean[] TECHNIQUE_SLOT_WAS_DOWN = new boolean[PlayerTechniqueData.SLOT_COUNT];
    private static boolean techniqueSelectWasDown;
    private static int pendingSelectedSlot = 1;
    private static boolean recklessMouseDown;
    private static boolean recklessChargePending;
    private static boolean recklessChargeHeld;
    private static int recklessPressClientTick;
    private static boolean nextUnarmedPunchOffhand;
    private static int lastUnarmedPunchClientTick = Integer.MIN_VALUE;
    private static int lastParryReturnProbeClientTick = Integer.MIN_VALUE;
    private static boolean handsUpGuardSent;
    private static int lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
    private static int lastHandsUpGuardingClientTick = Integer.MIN_VALUE;
    private static final int HANDS_UP_GUARD_KEEPALIVE_TICKS = 5;
    private static final int PARRY_RETURN_PROBE_AFTER_GUARD_TICKS = 35;

    public static boolean isTechniqueSelectModeActive() {
        return techniqueSelectWasDown;
    }

    public static int pendingSelectedSlotZeroBased() {
        return Math.max(0, Math.min(PlayerTechniqueData.SLOT_COUNT - 1, pendingSelectedSlot - 1));
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        OPEN_BOOK = new KeyMapping(
                "key.levelrpg.open",
                InputConstants.KEY_K,
                "key.categories.levelrpg"
        );
        event.register(OPEN_BOOK);
        TOGGLE_ORBIT = new KeyMapping(
                "key.levelrpg.debug.orbit",
                InputConstants.KEY_O,
                "key.categories.levelrpg"
        );
        event.register(TOGGLE_ORBIT);
        TOGGLE_ORBIT_BOOK = new KeyMapping(
                "key.levelrpg.debug.orbit_book",
                InputConstants.KEY_P,
                "key.categories.levelrpg"
        );
        event.register(TOGGLE_ORBIT_BOOK);
        TECHNIQUE_SELECT_MODIFIER = new KeyMapping(
                "key.levelrpg.technique.select_mode",
                InputConstants.KEY_R,
                "key.categories.levelrpg"
        );
        event.register(TECHNIQUE_SELECT_MODIFIER);
        TECHNIQUE_TRIGGER = new KeyMapping(
                "key.levelrpg.technique.trigger",
                InputConstants.Type.MOUSE,
                InputConstants.MOUSE_BUTTON_MIDDLE,
                "key.categories.levelrpg"
        );
        event.register(TECHNIQUE_TRIGGER);
        int[] defaults = {
                InputConstants.KEY_1,
                InputConstants.KEY_2,
                InputConstants.KEY_3,
                InputConstants.KEY_4,
                InputConstants.KEY_5,
                InputConstants.KEY_6,
                InputConstants.KEY_7,
                InputConstants.KEY_8,
                InputConstants.KEY_9
        };
        for (int i = 0; i < TECHNIQUE_SLOTS.length; i++) {
            TECHNIQUE_SLOTS[i] = new KeyMapping(
                    "key.levelrpg.technique." + (i + 1),
                    defaults[i],
                    "key.categories.levelrpg"
            );
            event.register(TECHNIQUE_SLOTS[i]);
        }
    }

    @EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // If the player entity is recreated (e.g. on death/respawn), its tickCount resets to 0.
            // We must clear any stale client-side tick trackers so inputs don't become locked out.
            int tickNow = mc.player.tickCount;
            if (ghostStepClientCooldownUntil > tickNow + 100) {
                ghostStepClientCooldownUntil = 0;
                recklessPressClientTick = 0;
                lastUnarmedPunchClientTick = Integer.MIN_VALUE;
                lastParryReturnProbeClientTick = Integer.MIN_VALUE;
                lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
                lastHandsUpGuardingClientTick = Integer.MIN_VALUE;
                uppercutChargeStartTick = 0;
                uppercutPressClientTick = 0;
                for (int i = 0; i < GHOST_STEP_LAST_TAP_TICK.length; i++) {
                    GHOST_STEP_LAST_TAP_TICK[i] = 0;
                }
            }
            
            if (OPEN_BOOK != null) {
                while (OPEN_BOOK.consumeClick()) {
                    if (!net.zoogle.levelrpg.Config.enableLevelBookKeybind) {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal("Level Book keybind is disabled in config"), true);
                        }
                        continue;
                    }
                    if (!EnchiridionJournalOpener.isCurrentJournalScreen(mc)) {
                        // Ensure world debug rendering is off before opening the GUI
                        try {
                            //net.zoogle.levelrpg.client.debug.BookOrbitDebug.enabled = false;
                            //net.zoogle.levelrpg.client.debug.BookOrbitDebug.renderBook = false;
                            //net.zoogle.levelrpg.client.debug.BookCrosshairRender.enabled = false;
                        } catch (Throwable ignored) {}
                        EnchiridionJournalOpener.openLevelRpgJournal(mc);
                    }
                }
            }
            if (mc.screen == null) {
                tickGhostStepInput(mc);
                tickRecklessChargeState(mc);
                tickHandsUpGuardState(mc);
                tickUppercutChargeState(mc);
                boolean selectDown = (TECHNIQUE_SELECT_MODIFIER != null && TECHNIQUE_SELECT_MODIFIER.isDown())
                        || InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_R);
                if (selectDown && !techniqueSelectWasDown) {
                    pendingSelectedSlot = ClientTechniqueCache.selectedSlot() + 1;
                    showPendingTechniquePopup(mc);
                }
                for (int i = 0; i < TECHNIQUE_SLOTS.length; i++) {
                    KeyMapping mapping = TECHNIQUE_SLOTS[i];
                    if (mapping == null) {
                        continue;
                    }
                    boolean down = mapping.isDown();
                    if (down && !TECHNIQUE_SLOT_WAS_DOWN[i]) {
                        if (selectDown) {
                            pendingSelectedSlot = i + 1;
                            showPendingTechniquePopup(mc);
                            revealMomentumForTechniqueAttempt(i);
                            PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(i + 1));
                        } else {
                            revealMomentumForTechniqueAttempt(i);
                            PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(i + 1));
                        }
                    }
                    TECHNIQUE_SLOT_WAS_DOWN[i] = down;
                    while (mapping.consumeClick()) {
                        // drain queued clicks so hold/repeat does not re-trigger activations
                    }
                }
                if (!selectDown && techniqueSelectWasDown) {
                    PacketDistributor.sendToServer(new SelectTechniqueSlotPayload(pendingSelectedSlot));
                }
                if (TECHNIQUE_TRIGGER != null) {
                    while (TECHNIQUE_TRIGGER.consumeClick()) {
                        int selected = ClientTechniqueCache.selectedSlot() + 1;
                        revealMomentumForTechniqueAttempt(selected - 1);
                        PacketDistributor.sendToServer(new ActivateTechniqueSlotPayload(selected));
                    }
                }
                if (selectDown) {
                    showPendingTechniquePopup(mc);
                }
                techniqueSelectWasDown = selectDown;
            }
            else {
                tickHandsUpGuardState(mc);
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (!techniqueSelectWasDown) {
                return;
            }
            double delta = event.getScrollDeltaY();
            if (Math.abs(delta) < 0.0001) {
                return;
            }
            int step = delta > 0.0 ? -1 : 1;
            int zeroBased = pendingSelectedSlot - 1;
            int next = (zeroBased + step) % PlayerTechniqueData.SLOT_COUNT;
            if (next < 0) {
                next += PlayerTechniqueData.SLOT_COUNT;
            }
            pendingSelectedSlot = next + 1;
            showPendingTechniquePopup(Minecraft.getInstance());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            if (uppercutCharging && event.isAttack()) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            if (event.isAttack() && shouldUseFinesseUnarmedCombat(mc)) {
                int tick = mc.player.tickCount;
                if (tick - lastUnarmedPunchClientTick > UNARMED_COMBAT_SESSION_TIMEOUT_TICKS) {
                    nextUnarmedPunchOffhand = false;
                }
                if (isBlockAttackTarget(mc)) {
                    nextUnarmedPunchOffhand = false;
                    if (trySendParryReturnProbe(mc)) {
                        event.setSwingHand(false);
                        event.setCanceled(true);
                    }
                    return;
                }
                markFistCombatIntent(tick);
                Entity fistTarget = findFistTarget(mc);
                boolean tapParriedProjectile = fistTarget instanceof Projectile p
                        && FinesseProjectileParry.isParryableProjectile(p);
                if (!tapParriedProjectile && mc.player.getAttackStrengthScale(0.0F) < UNARMED_CLIENT_ATTACK_READY_THRESHOLD) {
                    event.setSwingHand(false);
                    return;
                }
                if (fistTarget != null) {
                    event.setSwingHand(false);
                    markWeakSpotIntentIfAimed(mc, fistTarget);
                    PacketDistributor.sendToServer(new RequestFistPunchPayload(fistTarget.getId()));
                    mc.player.resetAttackStrengthTicker();
                    swingUnarmedPunch(mc, tick);
                    event.setCanceled(true);
                    return;
                }
                if (trySendParryReturnProbe(mc)) {
                    event.setSwingHand(false);
                    event.setCanceled(true);
                    return;
                }
                playUnarmedWhiffSound(mc);
                if (nextUnarmedPunchOffhand) {
                    event.setSwingHand(false);
                    mc.player.resetAttackStrengthTicker();
                    swingUnarmedPunch(mc, tick);
                    event.setCanceled(true);
                    return;
                }
                nextUnarmedPunchOffhand = !nextUnarmedPunchOffhand;
                lastUnarmedPunchClientTick = tick;
                return;
            } else if (event.isAttack()) {
                nextUnarmedPunchOffhand = false;
            }
            if (event.isUseItem() && shouldUseHandsUpGuard(mc)) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            if (event.isAttack() && mc.player.isBlocking()) {
                PacketDistributor.sendToServer(new RequestShieldBashPayload());
                event.setCanceled(true);
                return;
            }
            // RMB ability dispatch is handled in onMouseButton to avoid event-order cancellation conflicts.
        }

        private static boolean shouldUseFinesseUnarmedCombat(Minecraft mc) {
            if (mc.player == null || mc.player.isSpectator()) {
                return false;
            }
            if (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()) {
                return false;
            }
            var unlocked = net.zoogle.levelrpg.client.data.ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
            return unlocked.contains(FinesseNodeIds.HAND_TO_HAND_COMBAT)
                    || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.HAND_TO_HAND_COMBAT);
        }

        private static boolean shouldUseHandsUpGuard(Minecraft mc) {
            if (mc.player == null || mc.player.isSpectator()) {
                return false;
            }
            if (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()) {
                return false;
            }
            var unlocked = net.zoogle.levelrpg.client.data.ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
            boolean hasHandsUp = unlocked.contains(FinesseNodeIds.HANDS_UP)
                    || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.HANDS_UP);
            return hasHandsUp
                    && (FinesseFirstPersonUnarmedRenderer.isFistCombatActive(mc.player)
                    || hasHandsUpOpenerThreat(mc));
        }

        private static boolean canUseUppercut(Minecraft mc) {
            if (!shouldUseHandsUpGuard(mc)) return false;
            var unlocked = net.zoogle.levelrpg.client.data.ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
            return unlocked.contains(FinesseNodeIds.UPPERCUT)
                    || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.UPPERCUT);
        }

        private static void tickHandsUpGuardState(Minecraft mc) {
            if (mc.player == null) {
                handsUpGuardSent = false;
                lastHandsUpGuardPacketTick = Integer.MIN_VALUE;
                return;
            }
            boolean guarding = mc.screen == null
                    && mc.options != null
                    && mc.options.keyUse.isDown()
                    && shouldUseHandsUpGuard(mc);
            int tick = mc.player.tickCount;
            if (guarding) {
                lastHandsUpGuardingClientTick = tick;
                FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(tick);
            }
            boolean needsKeepalive = guarding
                    && tick - lastHandsUpGuardPacketTick >= HANDS_UP_GUARD_KEEPALIVE_TICKS;
            if (guarding != handsUpGuardSent || needsKeepalive) {
                PacketDistributor.sendToServer(new SetHandsUpGuardingPayload(guarding));
                handsUpGuardSent = guarding;
                lastHandsUpGuardPacketTick = tick;
            }
        }

        private static void tickUppercutChargeState(Minecraft mc) {
            if (mc.player == null) return;
            if (uppercutChargePending) {
                if (!mc.options.keyAttack.isDown() || !canUseUppercut(mc)) {
                    uppercutChargePending = false;
                    uppercutCharging = false;
                } else if (mc.player.tickCount - uppercutPressClientTick >= 4) {
                    if (!uppercutCharging) {
                        uppercutCharging = true;
                        uppercutChargeStartTick = mc.player.tickCount;
                    }
                }
            }
            if (uppercutCharging && mc.level != null) {
                if (!mc.options.keyAttack.isDown() || !canUseUppercut(mc)) {
                    uppercutChargePending = false;
                    uppercutCharging = false;
                    PacketDistributor.sendToServer(new RequestUppercutPayload(mc.player.tickCount - uppercutChargeStartTick));
                    return;
                }
                double progress = Math.max(0.0, Math.min(1.0, (mc.player.tickCount - uppercutChargeStartTick) / 20.0));
                net.zoogle.levelrpg.client.ui.ChargeUiController.offer("uppercut", "Uppercut", progress, 0xFFBB00);
                
                if (mc.player.tickCount % 2 == 0) {
                    float offset = mc.player.getBbHeight() * 0.45F;
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            mc.player.getX() + (mc.player.getRandom().nextDouble() - 0.5D) * 0.6D,
                            mc.player.getY() + offset + (mc.player.getRandom().nextDouble() - 0.5D) * 0.5D,
                            mc.player.getZ() + (mc.player.getRandom().nextDouble() - 0.5D) * 0.6D,
                            0.0D, 0.05D, 0.0D);
                }
            }
        }

        private static boolean isBlockAttackTarget(Minecraft mc) {
            return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        }

        private static Entity findFistTarget(Minecraft mc) {
            if (mc.hitResult instanceof EntityHitResult entityHit) {
                Entity hitEntity = entityHit.getEntity();
                if (hitEntity instanceof LivingEntity) {
                    return hitEntity;
                }
                if (hitEntity instanceof Projectile projectile && FinesseProjectileParry.isParryableProjectile(projectile)) {
                    return hitEntity;
                }
            }
            return findExtendedFistTarget(mc);
        }

        private static Entity findExtendedFistTarget(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                return null;
            }
            Vec3 eye = mc.player.getEyePosition(1.0F);
            Vec3 look = mc.player.getViewVector(1.0F);
            AABB searchBox = mc.player.getBoundingBox().expandTowards(look.scale(EXTENDED_FIST_REACH)).inflate(2.0D);
            
            Entity bestEntity = null;
            double bestAngle = -1.0;

            for (Entity entity : mc.level.getEntities(mc.player, searchBox, e -> EntitySelector.CAN_BE_COLLIDED_WITH.test(e) && (e instanceof LivingEntity || (e instanceof Projectile p && FinesseProjectileParry.isParryableProjectile(p))))) {
                Vec3 entityCenter = entity.getBoundingBox().getCenter();
                Vec3 toEntity = entityCenter.subtract(eye);
                double distSqr = toEntity.lengthSqr();
                
                // Max range check (slightly padded for center-mass distance)
                if (distSqr > (EXTENDED_FIST_REACH + 1.5) * (EXTENDED_FIST_REACH + 1.5)) {
                    continue;
                }
                
                toEntity = toEntity.normalize();
                double dot = look.dot(toEntity);
                
                // If player is in the air (juggling), the cone is massive (0.3 dot = ~72 degrees)
                // If grounded, the cone is tighter but still generous (0.75 dot = ~41 degrees)
                double minDot = mc.player.onGround() ? 0.75 : 0.3;
                
                if (dot > minDot) {
                    // Prioritize whoever is closest to the exact center of the screen
                    if (dot > bestAngle) {
                        bestAngle = dot;
                        bestEntity = entity;
                    }
                }
            }
            return bestEntity;
        }

        private static boolean hasHandsUpOpenerThreat(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                return false;
            }
            Vec3 eye = mc.player.getEyePosition(1.0F);
            Vec3 look = mc.player.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(HANDS_UP_OPENER_THREAT_REACH));
            EntityHitResult aimed = ProjectileUtil.getEntityHitResult(
                    mc.level,
                    mc.player,
                    eye,
                    end,
                    mc.player.getBoundingBox().expandTowards(look.scale(HANDS_UP_OPENER_THREAT_REACH)).inflate(1.5D),
                    entity -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && entity instanceof Enemy
            );
            if (aimed != null) {
                return true;
            }
            AABB nearby = mc.player.getBoundingBox().inflate(HANDS_UP_OPENER_NEARBY_RADIUS);
            if (!mc.level.getEntities(mc.player, nearby, entity -> entity instanceof Enemy).isEmpty()) {
                return true;
            }
            AABB projectileBox = mc.player.getBoundingBox().inflate(HANDS_UP_OPENER_PROJECTILE_RADIUS);
            return !mc.level.getEntitiesOfClass(Projectile.class, projectileBox, projectile -> {
                if (!FinesseProjectileParry.isParryableProjectile(projectile) || projectile.getOwner() == mc.player) {
                    return false;
                }
                Vec3 movement = projectile.getDeltaMovement();
                Vec3 toPlayer = mc.player.getEyePosition().subtract(projectile.position());
                return movement.lengthSqr() >= 0.0025D
                        && toPlayer.lengthSqr() >= 0.0001D
                        && movement.normalize().dot(toPlayer.normalize()) > 0.55D;
            }).isEmpty();
        }

        private static void markFistCombatIntent(int tick) {
            FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(tick);
            PacketDistributor.sendToServer(new MarkFistCombatIntentPayload());
        }

        private static void markWeakSpotIntentIfAimed(Minecraft mc, Entity target) {
            if (mc.player == null || !(target instanceof LivingEntity livingTarget)) {
                return;
            }
            Vec3 eye = mc.player.getEyePosition(1.0F);
            Vec3 look = mc.player.getViewVector(1.0F);
            if (FinesseUnarmedCombat.isRayInWeakSpot(eye, look, livingTarget)) {
                PacketDistributor.sendToServer(new MarkFistWeakSpotIntentPayload(target.getId()));
            }
        }

        private static void swingUnarmedPunch(Minecraft mc, int tick) {
            if (nextUnarmedPunchOffhand) {
                mc.player.swing(InteractionHand.OFF_HAND);
            } else {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            nextUnarmedPunchOffhand = !nextUnarmedPunchOffhand;
            lastUnarmedPunchClientTick = tick;
        }

        private static void playUnarmedWhiffSound(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                return;
            }
            mc.level.playLocalSound(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    SoundSource.PLAYERS,
                    0.38F,
                    0.82F + mc.player.getRandom().nextFloat() * 0.16F,
                    false
            );
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
                if (event.getAction() == 1) {
                    trySendParryReturnProbe(mc);
                    if (canUseUppercut(mc) && mc.options.keyUse.isDown()) {
                        uppercutChargePending = true;
                        uppercutPressClientTick = mc.player.tickCount;
                    }
                } else if (event.getAction() == 0) {
                    uppercutChargePending = false;
                }
                return;
            }
            if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
                return;
            }
            if (event.getAction() == 0) {
                recklessMouseDown = false;
                recklessChargePending = false;
                if (recklessChargeHeld) {
                    PacketDistributor.sendToServer(new RequestRecklessChargeReleasePayload());
                    recklessChargeHeld = false;
                    event.setCanceled(true);
                }
                return;
            }
            if (event.getAction() != 1) {
                return;
            }
            recklessMouseDown = true;
            if (shouldAttemptRecklessCharge(mc)) {
                recklessChargePending = true;
                recklessPressClientTick = mc.player != null ? mc.player.tickCount : 0;
                return;
            }
            if (shouldAttemptForwardFrenzy(mc)) {
                PacketDistributor.sendToServer(new RequestForwardFrenzyPayload());
                event.setCanceled(true);
            }
        }

        private static boolean trySendParryReturnProbe(Minecraft mc) {
            if (mc.player == null || mc.options == null) {
                return false;
            }
            if (!shouldUseFinesseUnarmedCombat(mc)) {
                return false;
            }
            int tick = mc.player.tickCount;
            if (tick - lastHandsUpGuardingClientTick > PARRY_RETURN_PROBE_AFTER_GUARD_TICKS) {
                return false;
            }
            if (tick == lastParryReturnProbeClientTick) {
                return false;
            }
            lastParryReturnProbeClientTick = tick;
            PacketDistributor.sendToServer(new RequestFistPunchPayload(-1));
            return true;
        }

        private static boolean shouldAttemptForwardFrenzy(Minecraft mc) {
            if (mc.player == null) {
                return false;
            }
            if (!ValorMeleeWeapons.isValorMelee(mc.player.getMainHandItem())) {
                return false;
            }
            if (!hasEnoughResolveForForwardFrenzy(mc)) {
                return false;
            }
            if (!mc.player.onGround()) {
                return true;
            }
            double horizontalSpeedSqr = mc.player.getDeltaMovement().horizontalDistanceSqr();
            return horizontalSpeedSqr > (0.05 * 0.05);
        }

        private static void tickGhostStepInput(Minecraft mc) {
            if (mc.player == null || mc.screen != null || techniqueSelectWasDown) {
                return;
            }
            boolean[] down = {
                    mc.options.keyUp.isDown(),
                    mc.options.keyDown.isDown(),
                    mc.options.keyLeft.isDown(),
                    mc.options.keyRight.isDown()
            };
            int[][] directions = {
                    {1, 0},
                    {-1, 0},
                    {0, -1},
                    {0, 1}
            };
            int tick = mc.player.tickCount;
            for (int i = 0; i < down.length; i++) {
                if (down[i] && !GHOST_STEP_KEY_WAS_DOWN[i]) {
                    if (tick >= ghostStepClientCooldownUntil
                            && tick - GHOST_STEP_LAST_TAP_TICK[i] <= GHOST_STEP_DOUBLE_TAP_TICKS) {
                        ghostStepClientCooldownUntil = tick + GHOST_STEP_CLIENT_COOLDOWN_TICKS;
                        PacketDistributor.sendToServer(new RequestGhostStepPayload(directions[i][0], directions[i][1]));
                        GHOST_STEP_LAST_TAP_TICK[i] = 0;
                    } else {
                        GHOST_STEP_LAST_TAP_TICK[i] = tick;
                    }
                }
                GHOST_STEP_KEY_WAS_DOWN[i] = down[i];
            }
        }

        private static boolean shouldAttemptRecklessCharge(Minecraft mc) {
            if (mc.player == null) {
                return false;
            }
            if (!ValorMeleeWeapons.isValorMelee(mc.player.getMainHandItem())) {
                return false;
            }
            if (!mc.player.isShiftKeyDown()) {
                return false;
            }
            if (!mc.player.onGround()) {
                return false;
            }
            double horizontalSpeedSqr = mc.player.getDeltaMovement().horizontalDistanceSqr();
            if (horizontalSpeedSqr > (0.05 * 0.05)) {
                return false;
            }
            return hasEnoughResolveForReckless();
        }

        private static boolean hasEnoughResolveForReckless() {
            Double resolve = getClientResolveValue();
            return resolve != null && resolve >= RECKLESS_CLIENT_MIN_RESOLVE_TO_START;
        }

        private static boolean hasEnoughResolveForForwardFrenzy(Minecraft mc) {
            Double resolve = getClientResolveValue();
            if (resolve == null) {
                return false;
            }
            double required = mc.player != null && mc.player.onGround()
                    ? FORWARD_FRENZY_CLIENT_RESOLVE_COST_GROUNDED
                    : FORWARD_FRENZY_CLIENT_RESOLVE_COST_AERIAL;
            return resolve >= required;
        }

        private static Double getClientResolveValue() {
            for (ClientGaugeCache.GaugeView gauge : ClientGaugeCache.gauges()) {
                if (!GaugeRegistry.RESOLVE.equals(gauge.id())) {
                    continue;
                }
                return gauge.value();
            }
            return null;
        }

        private static void tickRecklessChargeState(Minecraft mc) {
            if (mc.player == null) {
                recklessChargePending = false;
                recklessChargeHeld = false;
                return;
            }
            if (!recklessMouseDown) {
                recklessChargePending = false;
            }
            if (recklessChargePending) {
                if (!shouldAttemptRecklessCharge(mc)) {
                    recklessChargePending = false;
                } else if (mc.player.tickCount - recklessPressClientTick >= RECKLESS_HOLD_ARM_TICKS) {
                    PacketDistributor.sendToServer(new RequestRecklessChargeStartPayload());
                    recklessChargeHeld = true;
                    recklessChargePending = false;
                }
            }
            if (!recklessChargeHeld) {
                return;
            }
            if (!recklessMouseDown || !shouldAttemptRecklessCharge(mc)) {
                PacketDistributor.sendToServer(new RequestRecklessChargeReleasePayload());
                recklessChargeHeld = false;
            }
        }

        private static void showPendingTechniquePopup(Minecraft minecraft) {
            if (minecraft.gui == null) {
                return;
            }
            ResourceLocation techniqueId = ClientTechniqueCache.slot(Math.max(0, pendingSelectedSlot - 1));
            TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
            if (technique != null) {
                String label = technique.displayName().isBlank() ? technique.id().getPath() : technique.displayName();
                double effectiveCost = ClientTechniqueCostResolver.effectiveCost(technique);
                String subtitle = technique.cost().hasGaugeCost()
                        ? " Cost: " + (int) Math.round(effectiveCost)
                        : " Cost: None";
                MutableComponent message = Component.literal(Objects.requireNonNull(label))
                        .append(Component.literal(subtitle).withStyle(ChatFormatting.AQUA));
                minecraft.gui.setOverlayMessage(message, false);
            } else {
                minecraft.gui.setOverlayMessage(Component.literal("Empty Technique Slot"), false);
            }
        }

        private static void revealMomentumForTechniqueAttempt(int zeroBasedSlot) {
            if (!PlayerTechniqueData.isValidSlot(zeroBasedSlot)) {
                return;
            }
            ResourceLocation techniqueId = ClientTechniqueCache.slot(zeroBasedSlot);
            TechniqueDefinition technique = TechniqueRegistry.get(techniqueId);
            if (technique == null || !technique.cost().hasGaugeCost()) {
                return;
            }
            if (!GaugeRegistry.MOMENTUM.equals(technique.cost().gaugeId())) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            int tickNow = mc.player != null ? mc.player.tickCount : 0;
            MomentumHudController.onTechniqueAttempt(ClientTechniqueCostResolver.effectiveCost(technique), tickNow);
        }
    }
}
