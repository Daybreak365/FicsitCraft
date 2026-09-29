package com.ficsitcraft.railway;

import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Binary encodings shared by the server and the client. */
public final class RailCodec {
	public static final int CHUNK = 500_000;

	private RailCodec() {
	}

	public static byte[] compress(byte[] raw) throws IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try (DeflaterOutputStream d = new DeflaterOutputStream(bo, new Deflater(6))) {
			d.write(raw);
		}
		return bo.toByteArray();
	}

	public static byte[] decompress(byte[] data) throws IOException {
		try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
			return in.readAllBytes();
		}
	}

	public static byte[] encodeGraph(RailGraph g) throws IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		g.write(new DataOutputStream(bo));
		return compress(bo.toByteArray());
	}

	public static RailGraph decodeGraph(byte[] data) throws IOException {
		return RailGraph.read(new DataInputStream(new ByteArrayInputStream(decompress(data))));
	}

	/** Splits into packet-sized parts: each is [part, parts, bytes...]. */
	public static List<byte[]> chunks(byte[] data) {
		int parts = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
		List<byte[]> out = new ArrayList<>();
		for (int i = 0; i < parts; i++) {
			int from = i * CHUNK, to = Math.min(data.length, from + CHUNK);
			byte[] p = new byte[2 + to - from];
			p[0] = (byte) i;
			p[1] = (byte) parts;
			System.arraycopy(data, from, p, 2, to - from);
			out.add(p);
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------ train state

	public static final int F_DRIVEN = 1, F_AUTOPILOT = 2, F_DOCKED = 4, F_POWERED = 8, F_DERAILED = 16;

	public static void writeTrainState(DataOutput o, Train t) throws IOException {
		o.writeLong(t.id.getMostSignificantBits());
		o.writeLong(t.id.getLeastSignificantBits());
		o.writeUTF(t.name);
		int flags = (t.driven ? F_DRIVEN : 0) | (t.autopilot ? F_AUTOPILOT : 0) | (t.docked ? F_DOCKED : 0) | (t.powered ? F_POWERED : 0) | (t.derailed ? F_DERAILED : 0);
		o.writeByte(flags);
		o.writeFloat((float) t.speed);
		o.writeDouble(t.tailOffset);
		o.writeByte(t.path.size());
		for (Dir d : t.path) {
			o.writeLong(d.track());
			o.writeBoolean(d.forward());
		}
		o.writeByte(t.vehicles.size());
		for (Vehicle v : t.vehicles) {
			o.writeLong(v.id.getMostSignificantBits());
			o.writeLong(v.id.getLeastSignificantBits());
			o.writeByte(v.type.ordinal());
			o.writeByte(v.facing);
			o.writeByte((int) Math.round(v.load * 100));
		}
	}

	public static Train readTrainState(DataInput in) throws IOException {
		Train t = new Train(new UUID(in.readLong(), in.readLong()));
		t.name = in.readUTF();
		int flags = in.readByte();
		t.driven = (flags & F_DRIVEN) != 0;
		t.autopilot = (flags & F_AUTOPILOT) != 0;
		t.docked = (flags & F_DOCKED) != 0;
		t.powered = (flags & F_POWERED) != 0;
		t.derailed = (flags & F_DERAILED) != 0;
		t.speed = in.readFloat();
		t.tailOffset = in.readDouble();
		int np = in.readByte();
		for (int i = 0; i < np; i++) t.path.add(new Dir(in.readLong(), in.readBoolean()));
		int nv = in.readByte();
		VehicleType[] types = VehicleType.values();
		for (int i = 0; i < nv; i++) {
			Vehicle v = new Vehicle(new UUID(in.readLong(), in.readLong()), types[in.readByte()]);
			v.facing = in.readByte();
			v.load = in.readByte() / 100.0;
			t.vehicles.add(v);
		}
		return t;
	}
}
