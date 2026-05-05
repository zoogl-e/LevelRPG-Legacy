package net.zoogle.levelrpg.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla {@link AbstractArrow} skips movement integration while {@code inGround}; parry suspend/reposition
 * can leave the arrow embedded in foliage or otherwise flagged stuck, so flight must clear this.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowGroundAccessor {
    @Accessor("inGround")
    void levelrpg$setInGround(boolean value);

    @Accessor("inGroundTime")
    void levelrpg$setInGroundTime(int value);
}
