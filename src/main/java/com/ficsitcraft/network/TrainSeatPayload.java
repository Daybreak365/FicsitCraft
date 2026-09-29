package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> nearby clients: player (entity id) sits in / left a locomotive cab (for the seated pose). */
public record TrainSeatPayload(int entityId, boolean seated) implements CustomPayload {
	public static final CustomPayload.Id<TrainSeatPayload> ID = new CustomPayload.Id<>(FicsitCraft.id("train_seat"));
	public static final PacketCodec<RegistryByteBuf, TrainSeatPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, TrainSeatPayload::entityId, PacketCodecs.BOOL, TrainSeatPayload::seated, TrainSeatPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
