package com.ficsitcraft.mixin;

import com.ficsitcraft.railway.RailRiders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player driving a locomotive is positioned by the train, not by collisions: PlayerEntity.tick resets noClip every tick,
 * so the "inside a wall" check (suffocation damage, dark overlay) would still fire while the cab passes through blocks.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "isInsideWall()Z", at = @At("HEAD"), cancellable = true)
	private void ficsitcraft$riderNotInWall(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof PlayerEntity p && RailRiders.isRiding(p.getUuid())) cir.setReturnValue(false);
	}
}
