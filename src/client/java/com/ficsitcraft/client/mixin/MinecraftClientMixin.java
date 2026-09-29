package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.railway.RailInteract;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets a click on a train or a track switch through even though neither is a block or an entity. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true)
	private void ficsitcraft$railUse(CallbackInfo ci) {
		if (RailInteract.tryUse((MinecraftClient) (Object) this)) ci.cancel();
	}

	@Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
	private void ficsitcraft$railAttack(CallbackInfoReturnable<Boolean> cir) {
		if (RailInteract.tryAttack((MinecraftClient) (Object) this)) cir.setReturnValue(true);
	}

	@Inject(method = "handleBlockBreaking(Z)V", at = @At("HEAD"), cancellable = true)
	private void ficsitcraft$railHold(boolean breaking, CallbackInfo ci) {
		if (breaking && RailInteract.blocksBreaking((MinecraftClient) (Object) this)) ci.cancel();
	}
}
