package net.zoogle.levelrpg.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowPierceInvoker {
    @Invoker("setPierceLevel")
    void levelrpg$setPierceLevel(byte pierceLevel);
}
