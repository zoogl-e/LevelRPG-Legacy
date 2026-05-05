package net.zoogle.levelrpg.events;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.zoogle.levelrpg.finesse.FinesseFlurryTracker;
import net.zoogle.levelrpg.finesse.FinesseGuardState;
import net.zoogle.levelrpg.finesse.FinessePassiveModifiers;
import net.zoogle.levelrpg.finesse.FinesseTechniqueActivations;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.net.payload.TriggerFistCombatStancePayload;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.progression.PassiveSkillScalingService;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Finesse Rhythm: builds from movement and outgoing hits, falls sharply when the player is hurt.
 */
public final class FinesseGameplayEvents {
    private static final double RHYTHM_GAIN_PER_DAMAGE = 1.65;
    private static final double RHYTHM_GAIN_FLOOR = 0.5;
    private static final double RHYTHM_GAIN_CAP_FRACTION_OF_MAX = 0.12;
    /** After taking damage, retain this fraction of current Rhythm. */
    private static final double RHYTHM_RETAIN_AFTER_HIT = 0.52;
    private static final int MOVEMENT_SAMPLE_INTERVAL_TICKS = 8;
    private static final double MOVEMENT_MIN_STEP = 0.04;
    private static final double MOVEMENT_GAIN_PER_BLOCK = 2.4;
    private static final double MOVEMENT_GAIN_CAP_PER_SAMPLE = 3.8;

    private static final Map<UUID, Vec3> LAST_RHYTHM_SAMPLE_POS = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            UUID id = event.getEntity().getUUID();
            LAST_RHYTHM_SAMPLE_POS.remove(id);
            FinesseFlurryTracker.remove(id);
            FinesseTechniqueActivations.clearPlayer(id);
            FinesseUnarmedCombat.remove(id);
            FinesseGuardState.remove(id);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        // Clear stale juggle/cooldown state from the previous life
        FinesseTechniqueActivations.clearPlayer(id);
        LAST_RHYTHM_SAMPLE_POS.remove(id);
        // Zero Rhythm on death — flow must be rebuilt through combat and movement.
        // set() internally fetches, mutates, saves, and syncs — so it's sufficient on its own.
        // A subsequent syncAll with a stale profile reference would overwrite the correct sync.
        PlayerGauges.set(player, GaugeRegistry.RHYTHM, 0.0);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || player.isCreative()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        FinessePassiveModifiers.apply(player, profile);
        FinesseGuardState.tick(player, profile);
        if (FinesseTechniqueActivations.isGhostStepping(player)) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                        2, 0.2D, 0.4D, 0.2D, 0.03D);
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + player.getBbHeight() * 0.2D, player.getZ(),
                        1, 0.1D, 0.1D, 0.1D, 0.01D);
            }
        }
        // Maintain juggle state (NoGravity freeze) every tick
        if (player.level() instanceof ServerLevel serverLevel) {
            FinesseTechniqueActivations.tickJuggleState(serverLevel);
        }
        if (player.tickCount % MOVEMENT_SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.RHYTHM)) {
            LAST_RHYTHM_SAMPLE_POS.remove(player.getUUID());
            return;
        }
        double max = profile.gauges.getMax(player, profile, GaugeRegistry.RHYTHM);
        if (max <= 0.0) {
            return;
        }
        Vec3 current = player.position();
        Vec3 last = LAST_RHYTHM_SAMPLE_POS.put(player.getUUID(), current);
        if (last == null) {
            return;
        }
        double dist = horizontalDistance(current, last);
        if (dist < MOVEMENT_MIN_STEP) {
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, profile);
            return;
        }
        double gain = Math.min(MOVEMENT_GAIN_CAP_PER_SAMPLE, dist * MOVEMENT_GAIN_PER_BLOCK);
        if (gain > 0.0001) {
            PlayerGauges.add(player, GaugeRegistry.RHYTHM, gain);
            profile = LevelProfile.get(player);
        }
        PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(player, profile);
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            triggerFistCombatOnDamage(victim, event.getSource());
            applyRhythmBreak(victim, damage);
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == attacker) {
            return;
        }

        LevelProfile profile = LevelProfile.get(attacker);
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.RHYTHM)) {
            return;
        }
        double max = profile.gauges.getMax(attacker, profile, GaugeRegistry.RHYTHM);
        if (max <= 0.0) {
            return;
        }
        double gain = Math.max(RHYTHM_GAIN_FLOOR, damage * RHYTHM_GAIN_PER_DAMAGE);
        gain = Math.min(gain, max * RHYTHM_GAIN_CAP_FRACTION_OF_MAX);
        if (gain > 0.0001) {
            PlayerGauges.add(attacker, GaugeRegistry.RHYTHM, gain);
            profile = LevelProfile.get(attacker);
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(attacker, profile);
        }
        // Juggle bounce: if the victim is an active juggle target, bounce both up
        FinesseTechniqueActivations.tryJuggleBounce(attacker, event.getEntity());
        // Reset juggle target's invulnerability so rapid aerial punches all connect.
        // Scoped strictly to the juggle owner hitting their own juggle target.
        if (FinesseTechniqueActivations.isJuggleTarget(target.getId())) {
            java.util.UUID owner = FinesseTechniqueActivations.getJuggleOwner(target.getId());
            if (owner != null && owner.equals(attacker.getUUID())) {
                target.invulnerableTime = 0;
                target.hurtTime = 0;
            }
        }
    }

    @SubscribeEvent
    public void onLivingKnockBack(LivingKnockBackEvent event) {
        // Suppress horizontal knockback during aerial juggle — the upward kick from
        // tryJuggleBounce uses setDeltaMovement directly and is not affected by this.
        if (event.getEntity() instanceof ServerPlayer victim) {
            if (FinesseTechniqueActivations.isPlayerJuggleSuspended(victim)) {
                event.setStrength(0.0f);
            }
        } else if (FinesseTechniqueActivations.isJuggleTarget(event.getEntity().getId())) {
            event.setStrength(0.0f);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        // If a juggle target is killed while the owning player is airborne,
        // grant them one free fall damage cancel as a reward.
        UUID owner = FinesseTechniqueActivations.getJuggleOwner(event.getEntity().getId());
        if (owner == null) return;
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null || ownerPlayer.onGround()) return;
        FinesseTechniqueActivations.grantAerialKillGrace(owner);
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // Consume aerial kill grace to negate the next instance of fall damage.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;
        if (FinesseTechniqueActivations.consumeAerialKillGrace(player.getUUID())) {
            event.setNewDamage(0.0f);
        }
    }

    @SubscribeEvent
    public void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!FinesseGuardState.shouldReserveSelectedEmptyHandSlot(player)) {
            return;
        }
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.hasPickUpDelay()) {
            return;
        }
        ItemStack groundStack = itemEntity.getItem();
        if (groundStack.isEmpty()) {
            return;
        }
        ItemStack remaining = groundStack.copy();
        int before = remaining.getCount();
        if (!addToInventorySkippingSelectedSlot(player, remaining)) {
            return;
        }
        int pickedUp = before - remaining.getCount();
        if (pickedUp <= 0) {
            return;
        }
        groundStack.setCount(remaining.getCount());
        player.getInventory().setChanged();
        player.take(itemEntity, pickedUp);
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2F,
                ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
        );
        if (groundStack.isEmpty()) {
            itemEntity.discard();
        }
        event.setCanPickup(TriState.FALSE);
    }

    private static boolean addToInventorySkippingSelectedSlot(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        boolean movedAny = false;
        for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack existing = inventory.items.get(slot);
            if (!canMergePickupStack(existing, stack)) {
                continue;
            }
            int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.grow(moved);
            stack.shrink(moved);
            movedAny = true;
        }
        for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
            if (slot == selected || !inventory.items.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack placed = stack.copyWithCount(moved);
            inventory.items.set(slot, placed);
            stack.shrink(moved);
            movedAny = true;
        }
        return movedAny;
    }

    private static boolean canMergePickupStack(ItemStack existing, ItemStack incoming) {
        return !existing.isEmpty()
                && existing.getCount() < existing.getMaxStackSize()
                && ItemStack.isSameItemSameComponents(existing, incoming);
    }

    private static void triggerFistCombatOnDamage(ServerPlayer victim, DamageSource source) {
        if (source.is(DamageTypes.FALL)) {
            return;
        }
        if (FinesseGuardState.markFistCombatUsed(victim)) {
            victim.connection.send(new ClientboundCustomPayloadPacket(new TriggerFistCombatStancePayload()));
        }
    }

    private static void applyRhythmBreak(ServerPlayer victim, float damage) {
        if (victim.isSpectator() || victim.isCreative()) {
            return;
        }
        if (damage <= 0.0F) {
            return;
        }
        LevelProfile profile = LevelProfile.get(victim);
        if (!SkillUnlockQuery.hasNode(profile, FinesseNodeIds.SKILL, FinesseNodeIds.RHYTHM)) {
            return;
        }
        double max = profile.gauges.getMax(victim, profile, GaugeRegistry.RHYTHM);
        if (max <= 0.0) {
            return;
        }
        double current = profile.gauges.getValue(GaugeRegistry.RHYTHM);
        if (current <= 0.0001) {
            return;
        }
        double next = current * RHYTHM_RETAIN_AFTER_HIT;
        if (PlayerGauges.set(victim, GaugeRegistry.RHYTHM, next)) {
            profile = LevelProfile.get(victim);
            PassiveSkillScalingService.applyFinesseRhythmMovementSpeed(victim, profile);
        }
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
