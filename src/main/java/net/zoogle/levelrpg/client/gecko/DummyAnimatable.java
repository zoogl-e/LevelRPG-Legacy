package net.zoogle.levelrpg.client.gecko;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * GeckoLib animatable used for GUI rendering of the Enchiridion.
 * Plays a selected clip from animations/model.animation.json provided by BookGeoModel.
 */
public class DummyAnimatable implements GeoAnimatable {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // Desired animation clip and looping flag, controlled by the GUI screen
    private volatile String desiredClip = "animation.model.idle_open";
    private volatile boolean desiredLoop = true;

    // Optional override texture for dynamic pages
    private volatile ResourceLocation overrideTexture = null;

    // Applied/active clip built into a RawAnimation for the controller
    private volatile String appliedClip = null;
    private RawAnimation currentAnimation = null;

    // Completion state reported from the controller each tick
    private volatile boolean currentFinished = false;

    // Wall-clock start time for ticking
    private final long startTimeMs = System.currentTimeMillis();

    /**
     * Select the animation clip to play. Clip names must match those inside model.animation.json
     */
    public void setDesiredAnimation(String clip, boolean loop) {
        if (clip == null || clip.isEmpty()) return;
        this.desiredClip = clip;
        this.desiredLoop = loop;
    }

    /** Optional override texture to replace the model's base texture (used for dynamic pages). */
    public void setOverrideTexture(ResourceLocation rl) {
        this.overrideTexture = rl;
    }

    public ResourceLocation getOverrideTexture() {
        return this.overrideTexture;
    }

    /** Returns the animation clip that is currently applied to the controller. */
    public String getAppliedClip() {
        return appliedClip;
    }

    /** True when the controller reports the current (non-looping) animation has finished. */
    public boolean isCurrentAnimationFinished() {
        return currentFinished;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "book_controller", 0, this::predicate));
    }

    private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> state) {
        // If the desired clip changed, rebuild the RawAnimation which restarts playback
        if (!desiredClip.equals(appliedClip) || currentAnimation == null) {
            appliedClip = desiredClip;
            currentAnimation = RawAnimation.begin().then(
                    appliedClip,
                    desiredLoop ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE
            );
        }
        // Continue (or start) playing the current animation
        state.setAndContinue(currentAnimation);

        // Update completion status for polling (loops will generally not report finished)
        try {
            currentFinished = state.getController() != null && state.getController().hasAnimationFinished();
        } catch (Throwable t) {
            currentFinished = false;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object animatable) {
        // Drive animations using wall-clock time at 20 ticks per second
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        return elapsedMs / 50.0; // 1000ms / 50ms = 20 ticks per second
    }
}
