package com.ficsitcraft.client.railway;

import com.ficsitcraft.client.building.BuildingModel;
import com.ficsitcraft.client.building.BuildingModels;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sparks flying off the rails where the wheels grind while a train brakes hard. */
public final class TrainFx {
	private static final double RANGE = 48;
	private static final double HALF_GAUGE = 0.8;
	private static final int MAX_PER_TICK = 90;

	private TrainFx() {
	}

	public static void tick(MinecraftClient c) {
		ClientWorld world = c.world;
		if (world == null || c.player == null || c.isPaused()) return;
		Random rnd = world.random;
		V3 me = new V3(c.player.getX(), c.player.getY(), c.player.getZ());
		int budget = MAX_PER_TICK;
		for (Map.Entry<UUID, ClientRail.CTrain> en : ClientRail.trains.entrySet()) {
			ClientRail.CTrain ct = en.getValue();
			if (ct.base == null || !ClientRail.pathKnown(ct.base) || !TrainAudio.squealing(ct)) continue;
			double k = TrainAudio.squealIntensity(ct);
			Train t = ClientRail.posed(ct, world, 0f);
			double dir = Math.signum(t.speed);
			for (int i = 0; i < t.vehicles.size() && budget > 0; i++) {
				Vehicle v = t.vehicles.get(i);
				double uc = t.vehicleCenterU(i);
				if (t.pointAtU(ClientRail.graph, uc).distanceTo(me) > RANGE) continue;
				BuildingModel model = BuildingModels.local(v.type.id);
				List<Float> axles = model == null ? List.of() : model.wheelAxles();
				for (float z : axles) {
					double u = uc + v.facing * (z - v.type.length / 2);
					V3 p = t.pointAtU(ClientRail.graph, u);
					V3 tan = t.tangentAtU(ClientRail.graph, u);
					V3 right = tan.cross(new V3(0, 1, 0));
					right = right.lengthSq() < 1e-8 ? new V3(1, 0, 0) : right.normalize();
					for (int side = -1; side <= 1; side += 2) {
						if (rnd.nextDouble() > 0.45 * k) continue;
						V3 at = p.add(right.mul(side * HALF_GAUGE)).add(new V3(0, 0.14, 0));
						// the sparks are thrown off backwards / sideways and up
						double vx = -tan.x() * dir * (0.05 + rnd.nextDouble() * 0.12) + right.x() * side * (0.02 + rnd.nextDouble() * 0.1);
						double vz = -tan.z() * dir * (0.05 + rnd.nextDouble() * 0.12) + right.z() * side * (0.02 + rnd.nextDouble() * 0.1);
						double vy = 0.05 + rnd.nextDouble() * 0.16;
						double roll = rnd.nextDouble();
						if (roll < 0.55) world.addParticle(ParticleTypes.CRIT, at.x(), at.y(), at.z(), vx * 2, vy * 1.5, vz * 2);
						else if (roll < 0.85) world.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x(), at.y(), at.z(), vx * 3, vy, vz * 3);
						else world.addParticle(ParticleTypes.LAVA, at.x(), at.y(), at.z(), 0, 0, 0);
						budget--;
					}
				}
			}
		}
	}
}
