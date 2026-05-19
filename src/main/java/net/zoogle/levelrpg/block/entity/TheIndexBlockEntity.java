package net.zoogle.levelrpg.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.zoogle.levelrpg.registry.LevelRpgBlockEntities;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TheIndexBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE_0 = RawAnimation.begin().thenLoop("idle_0");
    private static final double PLAYER_SETTLE_RADIUS = 4.0D;
    private static final float SETTLE_IN_LERP = 0.11F;
    private static final float SETTLE_OUT_LERP = 0.06F;
    private static final float CRYSTAL_RENDER_LERP = 0.14F;
    private static final float CRYSTAL_WAKE_TICKS = 36.0F;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private float settleStrength;
    private float crystalOffsetX;
    private float crystalOffsetZ;
    private float crystalEngagement;
    private float crystalSpinTicks;
    private float crystalSpinAngle;
    private float crystalSpinVelocity;
    private float crystalWakeTicks;
    private long lastCrystalParticleGameTime = Long.MIN_VALUE;
    private long lastCrystalOrbitParticleGameTime = Long.MIN_VALUE;
    private long nextCrystalIdleChimeGameTime = Long.MIN_VALUE;
    private boolean playerNearbyLastFrame;
    private boolean crystalPlayerNearby;
    private boolean crystalWakeBurstPending;
    private boolean crystalDisengageSoundPending;
    private boolean restartIdleOnRelease;

    public TheIndexBlockEntity(BlockPos pos, BlockState blockState) {
        super(LevelRpgBlockEntities.THE_INDEX.get(), pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            if (consumeRestartIdleOnRelease()) {
                state.getController().forceAnimationReset();
            }
            state.getController().setAnimation(IDLE_0);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public float settleStrength() {
        return settleStrength;
    }

    public void updateClientSettleState() {
        Level level = getLevel();
        boolean playerNearby = false;
        if (level != null) {
            double centerX = getBlockPos().getX() + 0.5D;
            double centerZ = getBlockPos().getZ() + 0.5D;
            for (Player player : level.players()) {
                double dx = player.getX() - centerX;
                double dz = player.getZ() - centerZ;
                if (dx * dx + dz * dz <= PLAYER_SETTLE_RADIUS * PLAYER_SETTLE_RADIUS) {
                    playerNearby = true;
                    break;
                }
            }
        }

        float target = playerNearby ? 1.0F : 0.0F;
        float lerp = playerNearby ? SETTLE_IN_LERP : SETTLE_OUT_LERP;
        this.settleStrength += (target - this.settleStrength) * lerp;
        if (!playerNearby && playerNearbyLastFrame) {
            this.restartIdleOnRelease = true;
        }
        this.playerNearbyLastFrame = playerNearby;
    }

    public boolean isSettled() {
        return settleStrength > 0.995F;
    }

    public float crystalOffsetX() {
        return crystalOffsetX;
    }

    public float crystalOffsetZ() {
        return crystalOffsetZ;
    }

    public float crystalEngagement() {
        return crystalEngagement;
    }

    public float crystalSpinTicks() {
        return crystalSpinTicks;
    }

    public float crystalSpinAngle() {
        return crystalSpinAngle;
    }

    public float crystalWakeTicks() {
        return crystalWakeTicks;
    }

    public boolean consumeCrystalWakeBurst() {
        if (!crystalWakeBurstPending) {
            return false;
        }
        crystalWakeBurstPending = false;
        return true;
    }

    public boolean consumeCrystalDisengageSound() {
        if (!crystalDisengageSoundPending) {
            return false;
        }
        crystalDisengageSoundPending = false;
        return true;
    }

    public void tickCrystalEngagedSpin() {
        if (crystalSpinTicks > 0.0F) {
            crystalSpinTicks = Math.max(0.0F, crystalSpinTicks - 1.0F);
        }
        if (crystalWakeTicks > 0.0F) {
            crystalWakeTicks = Math.max(0.0F, crystalWakeTicks - 1.0F);
        }
        if (crystalSpinVelocity != 0.0F) {
            crystalSpinAngle += crystalSpinVelocity;
            crystalSpinVelocity *= 0.86F;
            if (Math.abs(crystalSpinVelocity) < 0.05F) {
                crystalSpinVelocity = 0.0F;
            }
        }
    }

    public void updateCrystalRenderState(float targetOffsetX, float targetOffsetZ, float targetEngagement, boolean playerNearby) {
        if (playerNearby && !this.crystalPlayerNearby) {
            this.crystalSpinTicks = 24.0F;
            this.crystalSpinVelocity = 34.0F;
            this.crystalWakeTicks = CRYSTAL_WAKE_TICKS;
            this.crystalWakeBurstPending = true;
        } else if (!playerNearby && this.crystalPlayerNearby) {
            this.crystalDisengageSoundPending = true;
        }
        this.crystalPlayerNearby = playerNearby;
        this.crystalOffsetX += (targetOffsetX - this.crystalOffsetX) * CRYSTAL_RENDER_LERP;
        this.crystalOffsetZ += (targetOffsetZ - this.crystalOffsetZ) * CRYSTAL_RENDER_LERP;
        this.crystalEngagement += (targetEngagement - this.crystalEngagement) * CRYSTAL_RENDER_LERP;
    }

    public boolean shouldEmitCrystalParticles(long gameTime) {
        if (gameTime == lastCrystalParticleGameTime) {
            return false;
        }
        lastCrystalParticleGameTime = gameTime;
        return true;
    }

    public boolean shouldEmitCrystalOrbitParticles(long gameTime) {
        if (gameTime == lastCrystalOrbitParticleGameTime) {
            return false;
        }
        lastCrystalOrbitParticleGameTime = gameTime;
        return true;
    }

    public boolean shouldPlayCrystalIdleChime(long gameTime, int delayTicks) {
        if (nextCrystalIdleChimeGameTime == Long.MIN_VALUE) {
            nextCrystalIdleChimeGameTime = gameTime + delayTicks;
            return false;
        }
        if (gameTime < nextCrystalIdleChimeGameTime) {
            return false;
        }
        nextCrystalIdleChimeGameTime = gameTime + delayTicks;
        return true;
    }

    private boolean consumeRestartIdleOnRelease() {
        if (!restartIdleOnRelease) {
            return false;
        }
        restartIdleOnRelease = false;
        return true;
    }
}
