package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.railway.TrainRide;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No dark "inside a block" screen while the cab passes through terrain. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
	@Inject(method = "getInWallBlockState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true)
	private static void ficsitcraft$noWallOverlayWhileRiding(PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
		if (TrainRide.isRiding()) cir.setReturnValue(null);
	}
}
