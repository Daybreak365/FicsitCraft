package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.railway.TrainRide;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cab of a locomotive positions the player: no pushing out of the blocks the train passes through. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "pushOutOfBlocks(DD)V", at = @At("HEAD"), cancellable = true)
	private void ficsitcraft$noPushWhileRiding(double x, double z, CallbackInfo ci) {
		if (TrainRide.isRiding()) ci.cancel();
	}
}
