package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.zipline.ZiplineClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import com.ficsitcraft.item.ZiplineItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
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
		// the arm that holds the Zipline goes straight up to the line, the other one hangs loosely
		PlayerEntity p = (PlayerEntity) entity;
		Arm holdArm = ZiplineItem.isHoldingInMain(p) ? p.getMainArm() : p.getMainArm().getOpposite();
		boolean right = holdArm == Arm.RIGHT;
		net.minecraft.client.model.ModelPart up = right ? model.rightArm : model.leftArm;
		net.minecraft.client.model.ModelPart down = right ? model.leftArm : model.rightArm;
		float side = right ? 1f : -1f;
		up.pitch = (float) Math.PI + 0.12f + swing;
		up.yaw = 0.0f;
		up.roll = 0.18f * side;
		down.pitch = 0.15f - swing;
		down.yaw = 0.0f;
		down.roll = -0.25f * side;
		// legs dangle
		model.rightLeg.pitch = 0.12f + swing;
		model.leftLeg.pitch = -0.08f - swing;
		model.rightLeg.roll = 0.03f;
		model.leftLeg.roll = -0.03f;
	}
}
