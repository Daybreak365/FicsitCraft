package com.ficsitcraft.mixin;

import com.ficsitcraft.railway.RailRiders;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PlayerEntity.tick starts with {@code noClip = isSpectator()}: that resets every noClip flag we could set from outside. A
 * player sitting in a locomotive cab is moved by the train, so it must count as noClip for the whole tick (no pushing out
 * of blocks, no suffocation, no dark in-wall overlay, no "moved wrongly" rubber-banding), on the client and on the server.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z", ordinal = 0))
	private boolean ficsitcraft$riderIsNoClip(PlayerEntity self) {
		return self.isSpectator() || RailRiders.isRiding(self.getUuid());
	}
}
