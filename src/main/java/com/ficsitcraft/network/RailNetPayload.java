package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: everything about the railway (graph snapshots, trains, signals, UI requests). Body layouts live in RailNet. */
public record RailNetPayload(int type, byte[] data) implements CustomPayload {
	public static final CustomPayload.Id<RailNetPayload> ID = new CustomPayload.Id<>(FicsitCraft.id("rail_net"));
	public static final PacketCodec<RegistryByteBuf, RailNetPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, RailNetPayload::type, PacketCodecs.BYTE_ARRAY, RailNetPayload::data, RailNetPayload::new);

	public static final int GRAPH = 1, SWITCH = 2, SIGNALS = 3, TRAINS = 4, TRAIN_REMOVE = 5, PENDING = 6,
			OPEN_TRAIN = 7, OPEN_STATION = 8, RIDE = 9, CLEAR = 10;

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
