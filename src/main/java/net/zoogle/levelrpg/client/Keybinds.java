package net.zoogle.levelrpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.zoogle.levelrpg.client.input.finesse.HandsUpGuardController;
import net.zoogle.levelrpg.client.input.finesse.UppercutChargeController;
import net.zoogle.levelrpg.client.input.movement.GhostStepInputController;
import net.zoogle.levelrpg.client.input.technique.TechniqueInputController;
import net.zoogle.levelrpg.client.input.valor.ValorInputController;
import net.zoogle.levelrpg.net.payload.MarkFistCombatIntentPayload;
import net.zoogle.levelrpg.net.payload.MarkFistWeakSpotIntentPayload;
import net.zoogle.levelrpg.net.payload.RequestFistPunchPayload;
import net.zoogle.levelrpg.net.payload.RequestShieldBashPayload;
import net.zoogle.levelrpg.finesse.FinesseProjectileParry;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.technique.PlayerTechniqueData;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class Keybinds {
    private static final float UNARMED_CLIENT_ATTACK_READY_THRESHOLD = 0.75F;
    private static final int UNARMED_COMBAT_SESSION_TIMEOUT_TICKS = 30;
    private static final double EXTENDED_FIST_REACH = 3.35D;

    public static float getUppercutChargeProgress(net.minecraft.client.player.AbstractClientPlayer player, float partialTicks) {
        return UppercutChargeController.getChargeProgress(player, partialTicks);
    }
    private Keybinds() {}

    public static KeyMapping OPEN_BOOK;
    public static KeyMapping TECHNIQUE_SELECT_MODIFIER;
    public static KeyMapping TECHNIQUE_TRIGGER;
    public static final KeyMapping[] TECHNIQUE_SLOTS = new KeyMapping[PlayerTechniqueData.SLOT_COUNT];
    private static boolean nextUnarmedPunchOffhand;
    private static int lastUnarmedPunchClientTick = Integer.MIN_VALUE;
    private static int lastParryReturnProbeClientTick = Integer.MIN_VALUE;
    private static final int PARRY_RETURN_PROBE_AFTER_GUARD_TICKS = 35;

    public static boolean isTechniqueSelectModeActive() {
        return TechniqueInputController.isTechniqueSelectModeActive();
    }

    public static int pendingSelectedSlotZeroBased() {
        return TechniqueInputController.pendingSelectedSlotZeroBased();
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        OPEN_BOOK = new KeyMapping(
                "key.levelrpg.open",
                InputConstants.KEY_K,
                "key.categories.levelrpg"
        );
        event.register(OPEN_BOOK);
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
            if (GhostStepInputController.isClientCooldownTickInvalid(tickNow)) {
                GhostStepInputController.resetForPlayerTickRollback();
                ValorInputController.resetForPlayerTickRollback();
                HandsUpGuardController.resetForPlayerTickRollback();
                UppercutChargeController.resetForPlayerTickRollback();
                lastUnarmedPunchClientTick = Integer.MIN_VALUE;
                lastParryReturnProbeClientTick = Integer.MIN_VALUE;
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
                GhostStepInputController.tickGhostStepInput(mc, TechniqueInputController.isTechniqueSelectModeActive());
                ValorInputController.tickRecklessChargeState(mc);
                HandsUpGuardController.tick(mc);
                UppercutChargeController.tick(mc);
                TechniqueInputController.tick(mc);
            }
            else {
                HandsUpGuardController.tick(mc);
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            TechniqueInputController.onMouseScroll(event);
        }

        @SubscribeEvent
        public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            if (UppercutChargeController.isCharging() && event.isAttack()) {
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
            if (event.isUseItem() && HandsUpGuardController.shouldUseHandsUpGuard(mc)) {
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

        private static void markFistCombatIntent(int tick) {
            net.zoogle.levelrpg.client.finesse.FinesseFirstPersonUnarmedRenderer.markUnarmedCombatUsed(tick);
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
                }
                UppercutChargeController.onLeftMouseButton(mc, event.getAction());
                return;
            }
            if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
                return;
            }
            ValorInputController.onMouseButtonRight(event, mc);
        }

        private static boolean trySendParryReturnProbe(Minecraft mc) {
            if (mc.player == null || mc.options == null) {
                return false;
            }
            if (!shouldUseFinesseUnarmedCombat(mc)) {
                return false;
            }
            int tick = mc.player.tickCount;
            if (tick - HandsUpGuardController.lastGuardingClientTick() > PARRY_RETURN_PROBE_AFTER_GUARD_TICKS) {
                return false;
            }
            if (tick == lastParryReturnProbeClientTick) {
                return false;
            }
            lastParryReturnProbeClientTick = tick;
            PacketDistributor.sendToServer(new RequestFistPunchPayload(-1));
            return true;
        }

    }
}
