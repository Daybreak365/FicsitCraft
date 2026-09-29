package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: the player attached to (true) or detached from (false) a power line with the zipline. */
public record ZiplinePayload(boolean attached) implements CustomPayload {
	public static final CustomPayload.Id<ZiplinePayload> ID = new CustomPayload.Id<>(FicsitCraft.id("zipline"));
	public static final PacketCodec<RegistryByteBuf, ZiplinePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, ZiplinePayload::attached, ZiplinePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
