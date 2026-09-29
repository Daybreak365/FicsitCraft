package com.ficsitcraft.network;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: interactions with the railway (switches, dismantling, train control, driving input). */
public record RailActionPayload(int type, byte[] data) implements CustomPayload {
	public static final CustomPayload.Id<RailActionPayload> ID = new CustomPayload.Id<>(FicsitCraft.id("rail_action"));
	public static final PacketCodec<RegistryByteBuf, RailActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, RailActionPayload::type, PacketCodecs.BYTE_ARRAY, RailActionPayload::data, RailActionPayload::new);

	public static final int TOGGLE_SWITCH = 1, DISMANTLE_TRACK = 2, DISMANTLE_SIGNAL = 3, DISMANTLE_VEHICLE = 4,
			INTERACT_VEHICLE = 5, TRAIN_CMD = 6, DRIVE_INPUT = 7, STATION_RENAME = 8, OPEN_CAR = 9;

	// TRAIN_CMD sub commands
	public static final int CMD_DRIVE = 1, CMD_STOP_DRIVE = 2, CMD_AUTOPILOT = 3, CMD_TIMETABLE = 4, CMD_RENAME = 5,
			CMD_DECOUPLE = 6, CMD_COUPLE = 7, CMD_STOP_INDEX = 8, CMD_HORN = 9;

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
