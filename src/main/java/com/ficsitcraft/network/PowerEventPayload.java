package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: a power grid's fuse blew (kind 0) or power was restored after a reset (kind 1). */
public record PowerEventPayload(int kind, float demand, float capacity) implements CustomPayload {
	public static final int FUSE_BLOWN = 0;
	public static final int RESTORED = 1;

	public static final CustomPayload.Id<PowerEventPayload> ID = new CustomPayload.Id<>(FicsitCraft.id("power_event"));
	public static final PacketCodec<RegistryByteBuf, PowerEventPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, PowerEventPayload::kind,
			PacketCodecs.FLOAT, PowerEventPayload::demand,
			PacketCodecs.FLOAT, PowerEventPayload::capacity,
			PowerEventPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
