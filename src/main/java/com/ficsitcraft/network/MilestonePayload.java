package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> all clients: a HUB milestone was completed. */
public record MilestonePayload(int milestone) implements CustomPayload {
	public static final CustomPayload.Id<MilestonePayload> ID = new CustomPayload.Id<>(FicsitCraft.id("milestone"));
	public static final PacketCodec<RegistryByteBuf, MilestonePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, MilestonePayload::milestone, MilestonePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
