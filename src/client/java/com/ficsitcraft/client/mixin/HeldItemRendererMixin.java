package com.ficsitcraft.client.mixin;

import com.ficsitcraft.client.zipline.ZiplineClient;
import com.ficsitcraft.item.ZiplineItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: while riding, the hand holding the Zipline is lifted to the top of the screen, up to the cable. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void ficsitcraft$ziplineHandUp(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
										   ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
										   int light, CallbackInfo ci) {
		if (!(item.getItem() instanceof ZiplineItem) || !ZiplineClient.isRiding(player)) return;
		// camera space: +Y up, -Z forward. The default hand pose is at the lower right; move it up along the screen.
		boolean left = (hand == Hand.MAIN_HAND) == (player.getMainArm() == net.minecraft.util.Arm.LEFT);
		matrices.translate(left ? 0.1 : -0.1, 0.95, 0.05);
	}
}
