package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.zipline.ZiplineClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zipline pose: the right arm is raised straight up holding the zipline against the cable, the left arm and legs
 * hang loosely and sway a little. Player sleeves copy the arm transforms afterwards, so they follow automatically.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void ficsitcraft$ziplinePose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
										 float headYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity) || !ZiplineClient.isRiding(entity)) return;
		BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
		float swing = MathHelper.sin(animationProgress * 0.12f) * 0.06f;
		// right arm straight up to the line
		model.rightArm.pitch = (float) Math.PI + 0.12f + swing;
		model.rightArm.yaw = 0.0f;
		model.rightArm.roll = 0.18f;
		// left arm relaxed, slightly out
		model.leftArm.pitch = 0.15f - swing;
		model.leftArm.yaw = 0.0f;
		model.leftArm.roll = -0.25f;
		// legs dangle
		model.rightLeg.pitch = 0.12f + swing;
		model.leftLeg.pitch = -0.08f - swing;
		model.rightLeg.roll = 0.03f;
		model.leftLeg.roll = -0.03f;
	}
}
