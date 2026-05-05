package net.zoogle.levelrpg.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.gauge.GaugeDefinition;
import net.zoogle.levelrpg.gauge.GaugeModifiers;
import net.zoogle.levelrpg.gauge.GaugeRegistry;
import net.zoogle.levelrpg.gauge.PlayerGauges;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.DelvingNodeIds;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DelvingGameplayEvents {
    private static final ResourceLocation AQUA_MOBILITY_MODIFIER = id("delving_aqua_mobility");
    private static final ResourceLocation ABYSSAL_FORM_WATER_MODIFIER = id("delving_abyssal_form_water");
    private static final ResourceLocation ABYSSAL_FORM_LAND_MODIFIER = id("delving_abyssal_form_land");
    private static final ResourceLocation WATER_EFFICIENCY_MODIFIER = id("delving_water_efficiency");
    private static final ResourceLocation AQUA_MINING_MODIFIER = id("delving_aqua_mining");
    private static final ResourceLocation WATER_ADAPTATION_MODIFIER = id("delving_water_adaptation");

    private static final int VEIN_MINER_BASE_EXTRA_BLOCKS = 8;
    private static final int VEIN_MINER_EARTHBREAKER_EXTRA_BLOCKS = 16;
    private static final double VEIN_MINER_COST_PER_BLOCK = 5.0;
    private static final int OVERDRIVE_STREAK_TIMEOUT_TICKS = 100;
    private static final double OVERDRIVE_WATER_DISTANCE_MOMENTUM_PER_BLOCK = 1.0;
    private static final double OVERDRIVE_WATER_MOMENTUM_MAX_PER_SECOND = 10.0;
    private static final double OVERDRIVE_HASTE_DRAIN_PER_STACK = 10.0;
    private static final double TIDE_DASH_MIN_ACTIVATION_COST = 2.0;
    private static final double TIDE_DASH_DRAIN_PER_TICK = 1.0;
    private static final double TIDE_DASH_ACCEL_PER_TICK = 0.045;
    /** Max total velocity magnitude while Tide Dash hold is active (full look-vector steering). */
    private static final double TIDE_DASH_MAX_SPEED = 0.42;
    private static final int TIDE_DASH_INPUT_GRACE_TICKS = 2;
    private static final double MINING_MOMENTUM_MAX_PER_SECOND = 10.0;
    public static final int HAMMER_STRIKE_DURATION_TICKS = 120;
    public static final double HAMMER_STRIKE_COST_PER_BLOCK = 1.0;
    public static final int HAMMER_STRIKE_MAX_BLOCKS_PER_ORIGIN = 9;
    private static final int CHARGING_JUMP_MAX_CHARGE_TICKS = 40;
    private static final double CHARGING_JUMP_FULL_LAUNCH_VELOCITY = 3;
    private static final double CHARGING_JUMP_COST = 100.0;
    private static final int NEUTRAL_BUOYANCY_REACTIVATION_DELAY_TICKS = 60;
    /** Gentle horizontal nudge when eyes are above water (Neutral Buoyancy surface skim). */
    private static final double NEUTRAL_BUOYANCY_SURFACE_SPEED_MULT = 1.045;
    private static final double AQUA_MINING_SUBMERGED_SPEED_BONUS = 0.85;
    private static final double ABYSS_DWELLER_SUBMERGED_SPEED_BONUS = 0.15;
    private static final Map<UUID, DelvingPlayerState> PLAYER_STATES = new HashMap<>();

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        BlockState state = event.getState();
        float speed = event.getNewSpeed();
        if (has(profile, DelvingNodeIds.DEEPCUTTER) && isDeepslateLike(state)) {
            speed *= 1.35F;
        }
        event.setNewSpeed(speed);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        BlockState state = event.getState();
        if (has(profile, DelvingNodeIds.PROSPECTOR) && isOre(state) && player.getRandom().nextFloat() < 0.10F) {
            dropProspectorBonus(level, event.getPos(), state, player);
        }
        if (has(profile, DelvingNodeIds.ENDURING_LABOR) && player.getMainHandItem().is(ItemTags.PICKAXES)) {
            player.getFoodData().setExhaustion(Math.max(0.0F, player.getFoodData().getExhaustionLevel() - 0.01F));
        }
        handleOverdriveBlockBreak(player, profile);
        handleHammerStrikeBlockBreak(level, player, profile, event.getPos(), state);
        if (has(profile, DelvingNodeIds.VEIN_MINER) && player.isShiftKeyDown() && isOre(state)) {
            veinMine(level, player, profile, event.getPos(), state);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        boolean inWater = isInWater(player);

        applyAttribute(player, Attributes.OXYGEN_BONUS, WATER_ADAPTATION_MODIFIER, has(profile, DelvingNodeIds.WATER_ADAPTATION) ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        applyAttribute(player, Attributes.SUBMERGED_MINING_SPEED, AQUA_MINING_MODIFIER, submergedMiningBonus(profile), AttributeModifier.Operation.ADD_VALUE);
        applyAttribute(player, Attributes.WATER_MOVEMENT_EFFICIENCY, WATER_EFFICIENCY_MODIFIER, waterEfficiency(profile), AttributeModifier.Operation.ADD_VALUE);
        applyAttribute(player, Attributes.MOVEMENT_SPEED, AQUA_MOBILITY_MODIFIER, inWater ? underwaterSpeed(profile) : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyAttribute(player, Attributes.MOVEMENT_SPEED, ABYSSAL_FORM_LAND_MODIFIER, !inWater && has(profile, DelvingNodeIds.ABYSSAL_FORM) ? -0.15 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyAttribute(player, Attributes.MOVEMENT_SPEED, ABYSSAL_FORM_WATER_MODIFIER, inWater && has(profile, DelvingNodeIds.ABYSSAL_FORM) ? 0.30 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (inWater) {
            handleUnderwaterTick(player, profile);
        } else {
            resetWaterTravelState(player);
        }
        tickHammerStrike(player);
        handleOverdriveHasteTick(player, profile);
        handleDeepSight(player, profile);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DelvingPlayerState state = PLAYER_STATES.get(player.getUUID());
            if (state != null) {
                clearNeutralBuoyancyLock(player, state);
            }
        }
        PLAYER_STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        if (!has(profile, DelvingNodeIds.REINFORCED_LUNGS)) {
            return;
        }
        ResourceLocation damageType = event.getSource().typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
        if (damageType != null && "in_wall".equals(damageType.getPath())) {
            event.setNewDamage(event.getNewDamage() * 0.5F);
        }
    }

    private static void handleUnderwaterTick(ServerPlayer player, LevelProfile profile) {
        DelvingPlayerState state = state(player);
        handleTideDashTick(player, profile, state);
        if (has(profile, DelvingNodeIds.ABYSS_DWELLER)) {
            player.setAirSupply(player.getMaxAirSupply());
        } else if (has(profile, DelvingNodeIds.WATER_ADAPTATION) && player.tickCount % 20 == 0) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 20));
        }
        boolean chargingJumpActive = has(profile, DelvingNodeIds.WATER_WALKING) && handleChargingJump(player, profile, state);
        if (!chargingJumpActive && has(profile, DelvingNodeIds.NEUTRAL_BUOYANCY)) {
            if (player.onGround()) {
                state.neutralBuoyancyGrounded = true;
                state.neutralBuoyancyReactivationTick = player.tickCount + NEUTRAL_BUOYANCY_REACTIVATION_DELAY_TICKS;
                clearNeutralBuoyancyLock(player, state);
            } else {
                if (state.neutralBuoyancyGrounded && state.neutralBuoyancyReactivationTick == 0) {
                    state.neutralBuoyancyReactivationTick = player.tickCount + NEUTRAL_BUOYANCY_REACTIVATION_DELAY_TICKS;
                }
                state.neutralBuoyancyGrounded = false;
                if (player.tickCount >= state.neutralBuoyancyReactivationTick) {
                    applyNeutralBuoyancy(player, profile, state);
                } else {
                    clearNeutralBuoyancyLock(player, state);
                }
            }
        } else {
            clearNeutralBuoyancyLock(player, state);
            state.neutralBuoyancyGrounded = false;
            state.neutralBuoyancyReactivationTick = 0;
        }
        if ((has(profile, DelvingNodeIds.ABYSS_DWELLER) || has(profile, DelvingNodeIds.ABYSSAL_FORM))
                && player.tickCount % 40 == 0) {
            PlayerGauges.add(player, GaugeRegistry.MOMENTUM, has(profile, DelvingNodeIds.ABYSSAL_FORM) ? 2.0 : 1.0);
        }
        if (has(profile, DelvingNodeIds.OVERDRIVE)) {
            handleOverdriveWaterTravel(player, profile);
        }
    }

    private static void tickHammerStrike(ServerPlayer player) {
        DelvingPlayerState state = PLAYER_STATES.get(player.getUUID());
        if (state == null || state.hammerStrikeTicks <= 0) {
            return;
        }
        if (!isHammerStrikeTool(player.getMainHandItem())) {
            state.hammerStrikeTicks = 0;
            return;
        }
        state.hammerStrikeTicks--;
    }

    private static boolean handleChargingJump(ServerPlayer player, LevelProfile profile, DelvingPlayerState state) {
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            state.chargingJumpSneakHeld = false;
            return false;
        }
        boolean sneaking = player.isShiftKeyDown();
        boolean underwater = isInWater(player);
        if (!sneaking && state.chargingJumpSneakHeld) {
            state.chargingJumpSneakHeld = false;
            int chargedTicks = Math.max(0, player.tickCount - state.chargingJumpChargeStartTick);
            if (chargedTicks < CHARGING_JUMP_MAX_CHARGE_TICKS) {
                return false;
            }
            if (!PlayerGauges.spend(player, GaugeRegistry.MOMENTUM, CHARGING_JUMP_COST)) {
                return false;
            }
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, Math.max(velocity.y, CHARGING_JUMP_FULL_LAUNCH_VELOCITY), velocity.z);
            player.fallDistance = 0.0F;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            return true;
        }
        if (!underwater) {
            state.chargingJumpSneakHeld = false;
            return false;
        }
        if (sneaking) {
            if (state.chargingJumpSneakHeld && !player.onGround()) {
                // Require grounded contact for the full charge duration.
                state.chargingJumpSneakHeld = false;
                return false;
            }
            if (!state.chargingJumpSneakHeld) {
                if (!player.onGround()) {
                    return false;
                }
                state.chargingJumpChargeStartTick = player.tickCount;
                state.chargingJumpSneakHeld = true;
            }
            return true;
        }
        return false;
    }

    private static void applyNeutralBuoyancy(ServerPlayer player, LevelProfile profile, DelvingPlayerState state) {
        if (!has(profile, DelvingNodeIds.NEUTRAL_BUOYANCY)) {
            clearNeutralBuoyancyLock(player, state);
            return;
        }
        if (player.isShiftKeyDown() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            clearNeutralBuoyancyLock(player, state);
            return;
        }
        if (!state.neutralBuoyancyNoGravityApplied) {
            player.setNoGravity(true);
            state.neutralBuoyancyNoGravityApplied = true;
        }
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0.0) {
            // Keep neutral buoyancy locked at zero downward velocity while preserving
            // normal upward swim impulses from jump/space.
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        }
        if (!player.isUnderWater()) {
            velocity = new Vec3(
                    velocity.x * NEUTRAL_BUOYANCY_SURFACE_SPEED_MULT,
                    velocity.y,
                    velocity.z * NEUTRAL_BUOYANCY_SURFACE_SPEED_MULT
            );
        }
        player.setDeltaMovement(velocity);
    }

    private static void clearNeutralBuoyancyLock(ServerPlayer player, DelvingPlayerState state) {
        if (!state.neutralBuoyancyNoGravityApplied) {
            return;
        }
        player.setNoGravity(false);
        state.neutralBuoyancyNoGravityApplied = false;
    }

    private static void handleOverdriveWaterTravel(ServerPlayer player, LevelProfile profile) {
        DelvingPlayerState state = state(player);
        Vec3 current = player.position();
        if (state.lastWaterPosition == null) {
            state.lastWaterPosition = current;
            state.waterMomentumWindowTick = player.tickCount;
            return;
        }
        if (player.tickCount - state.waterMomentumWindowTick >= 20) {
            state.waterMomentumWindowTick = player.tickCount;
            state.waterMomentumThisWindow = 0.0;
        }
        double distance = current.distanceTo(state.lastWaterPosition);
        state.lastWaterPosition = current;
        if (distance < 0.05 || distance > 1.5) {
            return;
        }
        state.waterTravelRemainder += distance * OVERDRIVE_WATER_DISTANCE_MOMENTUM_PER_BLOCK;
        double grantable = Math.floor(state.waterTravelRemainder);
        if (grantable <= 0.0) {
            return;
        }
        double remainingThisSecond = Math.max(0.0, OVERDRIVE_WATER_MOMENTUM_MAX_PER_SECOND - state.waterMomentumThisWindow);
        double toGrant = Math.min(grantable, remainingThisSecond);
        if (toGrant <= 0.0) {
            return;
        }
        state.waterTravelRemainder -= toGrant;
        state.waterMomentumThisWindow += toGrant;
        PlayerGauges.add(player, GaugeRegistry.MOMENTUM, toGrant);
    }

    private static void resetWaterTravelState(ServerPlayer player) {
        DelvingPlayerState state = PLAYER_STATES.get(player.getUUID());
        if (state != null) {
            state.lastWaterPosition = null;
            state.waterTravelRemainder = 0.0;
            state.waterMomentumThisWindow = 0.0;
            state.tideDashActive = false;
            state.tideDashActivatedTick = -1;
            state.tideDashLastInputTick = -1;
            state.chargingJumpSneakHeld = false;
            state.chargingJumpChargeStartTick = 0;
            state.neutralBuoyancyGrounded = false;
            state.neutralBuoyancyReactivationTick = 0;
            clearNeutralBuoyancyLock(player, state);
        }
    }

    private static void handleOverdriveBlockBreak(ServerPlayer player, LevelProfile profile) {
        DelvingPlayerState state = state(player);
        if (state.hammerStrikeTicks > 0 || state.abilityBreakDepth > 0 || !has(profile, DelvingNodeIds.OVERDRIVE) || !isMomentumFull(player, profile)) {
            return;
        }
        if (player.tickCount - state.lastBreakTick > OVERDRIVE_STREAK_TIMEOUT_TICKS) {
            state.breaksAtCurrentStack = 0;
        }
        state.lastBreakTick = player.tickCount;
        state.breaksAtCurrentStack++;
        int threshold = overdriveThreshold(state.hasteStacks);
        if (state.hasteStacks < 3 && state.breaksAtCurrentStack >= threshold) {
            state.hasteStacks++;
            state.breaksAtCurrentStack = 0;
            applyOverdriveHaste(player, state.hasteStacks);
        }
    }

    private static void handleOverdriveHasteTick(ServerPlayer player, LevelProfile profile) {
        DelvingPlayerState state = PLAYER_STATES.get(player.getUUID());
        if (state == null || state.hasteStacks <= 0) {
            return;
        }
        if (!has(profile, DelvingNodeIds.OVERDRIVE) || profile.gauges.getValue(GaugeRegistry.MOMENTUM) <= 0.0) {
            clearOverdriveHaste(player, state);
            return;
        }
        applyOverdriveHaste(player, state.hasteStacks);
        if (player.tickCount % 20 != 0) {
            return;
        }
        PlayerGauges.add(player, GaugeRegistry.MOMENTUM, -(state.hasteStacks * OVERDRIVE_HASTE_DRAIN_PER_STACK));
        if (profile.gauges.getValue(GaugeRegistry.MOMENTUM) <= 0.0) {
            clearOverdriveHaste(player, state);
        }
    }

    private static boolean isMomentumFull(ServerPlayer player, LevelProfile profile) {
        double value = profile.gauges.getValue(GaugeRegistry.MOMENTUM);
        double max = profile.gauges.getMax(player, profile, GaugeRegistry.MOMENTUM);
        return max > 0.0 && value + 0.0001 >= max;
    }

    private static int overdriveThreshold(int currentHasteStacks) {
        return switch (currentHasteStacks) {
            case 0 -> 16;
            case 1 -> 32;
            default -> 64;
        };
    }

    private static void applyOverdriveHaste(ServerPlayer player, int stacks) {
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, Math.max(0, stacks - 1), true, false, true));
    }

    private static void clearOverdriveHaste(ServerPlayer player, DelvingPlayerState state) {
        int previousStacks = state.hasteStacks;
        state.hasteStacks = 0;
        state.breaksAtCurrentStack = 0;
        MobEffectInstance current = player.getEffect(MobEffects.DIG_SPEED);
        if (current != null && current.getAmplifier() == previousStacks - 1 && current.getDuration() <= 45) {
            player.removeEffect(MobEffects.DIG_SPEED);
        }
    }

    private static void handleDeepSight(ServerPlayer player, LevelProfile profile) {
        if (!has(profile, DelvingNodeIds.DEEP_SIGHT)) {
            return;
        }
        if (player.blockPosition().getY() < 40) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
        } else if (player.tickCount % 80 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, true));
        }
    }

    private static double waterEfficiency(LevelProfile profile) {
        double value = 0.0;
        if (has(profile, DelvingNodeIds.CURRENT_CONTROL)) {
            value += 0.8;
        }
        if (has(profile, DelvingNodeIds.ABYSSAL_FORM)) {
            value += 1.0;
        }
        return value;
    }

    private static double underwaterSpeed(LevelProfile profile) {
        double value = 0.0;
        if (has(profile, DelvingNodeIds.AQUA_MOBILITY)) {
            value += 0.10;
        }
        if (has(profile, DelvingNodeIds.ABYSS_DWELLER)) {
            value += 0.10;
        }
        return value;
    }

    private static double submergedMiningBonus(LevelProfile profile) {
        if (!has(profile, DelvingNodeIds.AQUA_MINING)) {
            return 0.0;
        }
        double value = AQUA_MINING_SUBMERGED_SPEED_BONUS;
        if (has(profile, DelvingNodeIds.ABYSS_DWELLER)) {
            value += ABYSS_DWELLER_SUBMERGED_SPEED_BONUS;
        }
        return value;
    }

    private static void dropProspectorBonus(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                ItemStack bonus = drop.copy();
                bonus.setCount(1);
                Block.popResource(level, pos, bonus);
                return;
            }
        }
    }

    private static void veinMine(ServerLevel level, ServerPlayer player, LevelProfile profile, BlockPos origin, BlockState originState) {
        DelvingPlayerState playerState = state(player);
        if (playerState.veinMining) {
            return;
        }
        playerState.veinMining = true;
        try {
            int cap = has(profile, DelvingNodeIds.EARTHBREAKER) ? VEIN_MINER_EARTHBREAKER_EXTRA_BLOCKS : VEIN_MINER_BASE_EXTRA_BLOCKS;
            int broken = 0;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            HashSet<BlockPos> seen = new HashSet<>();
            queue.add(origin);
            seen.add(origin);
            while (!queue.isEmpty() && broken < cap) {
                BlockPos current = queue.removeFirst();
                for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                    BlockPos immutable = next.immutable();
                    if (immutable.equals(origin) || !seen.add(immutable) || !level.isLoaded(immutable)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(immutable);
                    if (!sameOreType(originState, state) || !canHarvest(player, state, immutable)) {
                        continue;
                    }
                    queue.add(immutable);
                    if (!profile.gauges.canSpend(player, profile, GaugeRegistry.MOMENTUM, VEIN_MINER_COST_PER_BLOCK)) {
                        return;
                    }
                    if (profile.gauges.spend(player, profile, GaugeRegistry.MOMENTUM, VEIN_MINER_COST_PER_BLOCK)) {
                        playerState.abilityBreakDepth++;
                        boolean destroyed;
                        try {
                            destroyed = player.gameMode.destroyBlock(immutable);
                        } finally {
                            playerState.abilityBreakDepth--;
                        }
                        if (!destroyed) {
                            profile.gauges.add(player, profile, GaugeRegistry.MOMENTUM, VEIN_MINER_COST_PER_BLOCK);
                            continue;
                        }
                        broken++;
                    }
                    if (broken >= cap) {
                        return;
                    }
                }
            }
        } finally {
            PlayerGauges.sync(player, profile, GaugeRegistry.MOMENTUM);
            LevelProfile.save(player, profile);
            playerState.veinMining = false;
        }
    }

    public static boolean activateHammerStrike(ServerPlayer player) {
        if (player == null || !isHammerStrikeTool(player.getMainHandItem())) {
            return false;
        }
        DelvingPlayerState state = state(player);
        state.hammerStrikeTicks = HAMMER_STRIKE_DURATION_TICKS;
        return true;
    }

    public static boolean requestTideDashHold(ServerPlayer player) {
        if (player == null || !isInWater(player)) {
            return false;
        }
        DelvingPlayerState state = state(player);
        if (!state.tideDashActive) {
            LevelProfile profile = LevelProfile.get(player);
            if (!profile.gauges.spend(player, profile, GaugeRegistry.MOMENTUM, TIDE_DASH_MIN_ACTIVATION_COST)) {
                return false;
            }
            state.tideDashActivatedTick = player.tickCount;
        }
        state.tideDashActive = true;
        state.tideDashLastInputTick = player.tickCount;
        return true;
    }

    private static void handleTideDashTick(ServerPlayer player, LevelProfile profile, DelvingPlayerState state) {
        if (!state.tideDashActive) {
            return;
        }
        if (player.tickCount - state.tideDashLastInputTick > TIDE_DASH_INPUT_GRACE_TICKS) {
            state.tideDashActive = false;
            return;
        }
        if (!isInWater(player) || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            state.tideDashActive = false;
            return;
        }
        if (player.tickCount > state.tideDashActivatedTick
                && !profile.gauges.spend(player, profile, GaugeRegistry.MOMENTUM, TIDE_DASH_DRAIN_PER_TICK)) {
            state.tideDashActive = false;
            return;
        }
        Vec3 look = player.getLookAngle();
        double lookLenSq = look.lengthSqr();
        if (lookLenSq < 1.0e-8) {
            return;
        }
        Vec3 dir = look.scale(1.0 / Math.sqrt(lookLenSq));
        Vec3 velocity = player.getDeltaMovement();
        Vec3 next = velocity.add(dir.scale(TIDE_DASH_ACCEL_PER_TICK));
        double speedSq = next.lengthSqr();
        if (speedSq > TIDE_DASH_MAX_SPEED * TIDE_DASH_MAX_SPEED) {
            next = next.normalize().scale(TIDE_DASH_MAX_SPEED);
        }
        player.setDeltaMovement(next);
    }

    private static void handleHammerStrikeBlockBreak(ServerLevel level, ServerPlayer player, LevelProfile profile, BlockPos origin, BlockState originState) {
        DelvingPlayerState playerState = state(player);
        if (playerState.hammerStrikeTicks <= 0 || playerState.hammerStrikeProcessing) {
            return;
        }
        if (!isHammerStrikeTool(player.getMainHandItem())) {
            playerState.hammerStrikeTicks = 0;
            return;
        }
        if (!canHarvest(player, originState, origin) || !spendHammerStrikeBlockCost(player, profile, playerState)) {
            playerState.hammerStrikeTicks = 0;
            return;
        }
        playerState.suppressMiningMomentumTick = player.tickCount;
        playerState.hammerStrikeProcessing = true;
        try {
            int affected = 1;
            for (BlockPos pos : hammerStrikeArea(origin, player)) {
                if (affected >= HAMMER_STRIKE_MAX_BLOCKS_PER_ORIGIN) {
                    break;
                }
                if (pos.equals(origin) || !level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !canHarvest(player, state, pos)) {
                    continue;
                }
                if (!spendHammerStrikeBlockCost(player, profile, playerState)) {
                    playerState.hammerStrikeTicks = 0;
                    break;
                }
                playerState.abilityBreakDepth++;
                boolean destroyed;
                try {
                    destroyed = player.gameMode.destroyBlock(pos);
                } finally {
                    playerState.abilityBreakDepth--;
                }
                if (!destroyed) {
                    profile.gauges.add(player, profile, GaugeRegistry.MOMENTUM, HAMMER_STRIKE_COST_PER_BLOCK);
                    continue;
                }
                affected++;
            }
        } finally {
            playerState.hammerStrikeProcessing = false;
            PlayerGauges.sync(player, profile, GaugeRegistry.MOMENTUM);
            LevelProfile.save(player, profile);
        }
    }

    private static boolean spendHammerStrikeBlockCost(ServerPlayer player, LevelProfile profile, DelvingPlayerState state) {
        if (!profile.gauges.spend(player, profile, GaugeRegistry.MOMENTUM, HAMMER_STRIKE_COST_PER_BLOCK)) {
            state.hammerStrikeTicks = 0;
            return false;
        }
        return true;
    }

    private static List<BlockPos> hammerStrikeArea(BlockPos origin, ServerPlayer player) {
        Direction.Axis axis = Direction.getNearest(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z).getAxis();
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>(9);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos offset = switch (axis) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                };
                positions.add(offset);
            }
        }
        return positions;
    }

    private static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES);
    }

    private static boolean sameOreType(BlockState a, BlockState b) {
        return isOre(b) && a.getBlock() == b.getBlock();
    }

    private static boolean isDeepslateLike(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.is(Blocks.DEEPSLATE)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || (id != null && id.getPath().contains("deepslate"));
    }

    private static boolean isHammerStrikeTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS);
    }

    private static boolean canHarvest(ServerPlayer player, BlockState state, BlockPos pos) {
        return state.getDestroySpeed(player.level(), pos) >= 0.0F
                && (!state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state));
    }

    private static boolean has(LevelProfile profile, String nodeId) {
        return SkillUnlockQuery.hasNode(profile, DelvingNodeIds.SKILL, nodeId);
    }

    public static boolean addMiningMomentum(ServerPlayer player, LevelProfile profile, double amount) {
        if (player == null || profile == null || amount <= 0.0 || player.hasEffect(MobEffects.DIG_SPEED)) {
            return false;
        }
        DelvingPlayerState state = state(player);
        if (state.abilityBreakDepth > 0 || state.suppressMiningMomentumTick == player.tickCount) {
            return false;
        }
        if (player.tickCount - state.miningMomentumWindowTick >= 20) {
            state.miningMomentumWindowTick = player.tickCount;
            state.miningMomentumThisWindow = 0.0;
        }
        double remaining = Math.max(0.0, MINING_MOMENTUM_MAX_PER_SECOND - state.miningMomentumThisWindow);
        if (remaining <= 0.0) {
            return false;
        }
        GaugeDefinition momentum = GaugeRegistry.get(GaugeRegistry.MOMENTUM);
        double modifiedRequested = momentum == null ? amount : GaugeModifiers.modifyGain(player, profile, momentum, amount);
        double multiplier = amount <= 0.0 ? 1.0 : Math.max(0.0, modifiedRequested / amount);
        double rawToAdd = multiplier <= 0.0 ? Math.min(amount, remaining) : Math.min(amount, remaining / multiplier);
        if (rawToAdd <= 0.0) {
            return false;
        }
        boolean changed = PlayerGauges.add(player, GaugeRegistry.MOMENTUM, rawToAdd);
        if (changed) {
            double actual = momentum == null ? rawToAdd : GaugeModifiers.modifyGain(player, profile, momentum, rawToAdd);
            state.miningMomentumThisWindow += actual;
        }
        return changed;
    }

    private static boolean isInWater(ServerPlayer player) {
        return player.isInWaterOrBubble();
    }

    private static DelvingPlayerState state(ServerPlayer player) {
        return PLAYER_STATES.computeIfAbsent(player.getUUID(), unused -> new DelvingPlayerState());
    }

    private static void applyAttribute(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }

    private static final class DelvingPlayerState {
        private int breaksAtCurrentStack;
        private int hasteStacks;
        private int lastBreakTick;
        private Vec3 lastWaterPosition;
        private double waterTravelRemainder;
        private int waterMomentumWindowTick;
        private double waterMomentumThisWindow;
        private int miningMomentumWindowTick;
        private double miningMomentumThisWindow;
        private int hammerStrikeTicks;
        private boolean hammerStrikeProcessing;
        private boolean tideDashActive;
        private int tideDashActivatedTick = -1;
        private int tideDashLastInputTick = -1;
        private boolean veinMining;
        private int abilityBreakDepth;
        private int suppressMiningMomentumTick = -1;
        private boolean chargingJumpSneakHeld;
        private int chargingJumpChargeStartTick;
        private boolean neutralBuoyancyNoGravityApplied;
        private boolean neutralBuoyancyGrounded;
        private int neutralBuoyancyReactivationTick;
    }
}
