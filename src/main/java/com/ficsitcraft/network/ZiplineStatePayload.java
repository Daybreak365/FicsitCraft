package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> nearby clients: player (entity id) started / stopped riding a zipline (for the arm pose and sparks). */
public record ZiplineStatePayload(int entityId, boolean riding) implements CustomPayload {
	public static final CustomPayload.Id<ZiplineStatePayload> ID = new CustomPayload.Id<>(FicsitCraft.id("zipline_state"));
	public static final PacketCodec<RegistryByteBuf, ZiplineStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, ZiplineStatePayload::entityId, PacketCodecs.BOOL, ZiplineStatePayload::riding, ZiplineStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
