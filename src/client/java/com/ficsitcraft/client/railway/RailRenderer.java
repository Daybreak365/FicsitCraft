package com.ficsitcraft.client.railway;

import com.ficsitcraft.client.building.BuildingMesh;
import com.ficsitcraft.client.building.BuildingModel;
import com.ficsitcraft.client.building.BuildingModels;
import com.ficsitcraft.item.RailwayItem;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.rail.Bezier;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailPlacement;
import com.ficsitcraft.rail.RailTrack;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Draws the railway in the world: track meshes, trains, signals with their lamps, switch levers and the placement preview. */
public final class RailRenderer {
	private static final double RANGE = 176;
	private static final Identifier ATLAS = PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	private static final Identifier BALLAST = Identifier.of("ficsitcraft", "block/trn/track_ballast");
	private static final Identifier SLEEPER = Identifier.of("ficsitcraft", "block/trn/track_sleeper");
	private static final Identifier RAIL = Identifier.of("ficsitcraft", "block/trn/track_rail");
	private static final Identifier HOLO = Identifier.of("ficsitcraft", "block/trn/track_hologram");

	/** Shown under the crosshair while holding the Railway. */
	public static Text previewText;

	private static final Map<Long, Mesh> MESHES = new HashMap<>();
	private static int meshVersion = -1;

	private RailRenderer() {
	}

	// ------------------------------------------------------------------------------------------ mesh

	/** Pre-built quads of one track piece in world coordinates (x y z u v per vertex, normal per quad). */
	private static final class Mesh {
		float[] v = new float[4096];
		float[] nrm = new float[1024];
		byte[] piece = new byte[512];
		int quads;
		BlockPos[] lightPos;
		V3 center;
		double radius;

		void quad(V3 a, V3 b, V3 c, V3 d, float u0, float v0, float u1, float v1, V3 n, int pc) {
			if ((quads + 1) * 20 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
			if ((quads + 1) * 3 > nrm.length) nrm = java.util.Arrays.copyOf(nrm, nrm.length * 2);
			if (quads + 1 > piece.length) piece = java.util.Arrays.copyOf(piece, piece.length * 2);
			int o = quads * 20;
			put(o, a, u0, v0);
			put(o + 5, b, u1, v0);
			put(o + 10, c, u1, v1);
			put(o + 15, d, u0, v1);
			nrm[quads * 3] = (float) n.x();
			nrm[quads * 3 + 1] = (float) n.y();
			nrm[quads * 3 + 2] = (float) n.z();
			piece[quads] = (byte) pc;
			quads++;
		}

		private void put(int o, V3 p, float u, float vv) {
			v[o] = (float) p.x();
			v[o + 1] = (float) p.y();
			v[o + 2] = (float) p.z();
			v[o + 3] = u;
			v[o + 4] = vv;
		}
	}

	private static Sprite sprite(Identifier id) {
		Function<Identifier, Sprite> atlas = MinecraftClient.getInstance().getSpriteAtlas(ATLAS);
		return atlas.apply(id);
	}

	private static Mesh build(Bezier curve, boolean hologram) {
		Mesh m = new Mesh();
		Sprite bal = sprite(hologram ? HOLO : BALLAST), sl = sprite(hologram ? HOLO : SLEEPER), rl = sprite(hologram ? HOLO : RAIL);
		double len = curve.length;
		int n = Math.max(2, (int) Math.ceil(len / 0.5));
		V3 up0 = new V3(0, 1, 0);
		V3[] P = new V3[n + 1], R = new V3[n + 1], U = new V3[n + 1], T = new V3[n + 1];
		for (int i = 0; i <= n; i++) {
			double d = len * i / n;
			P[i] = curve.pointAtDist(d);
			T[i] = curve.tangentAtDist(d);
			V3 r = T[i].cross(up0);
			R[i] = r.lengthSq() < 1e-8 ? new V3(1, 0, 0) : r.normalize();
			U[i] = R[i].cross(T[i]).normalize();
		}
		int pieces = Math.max(1, (int) Math.ceil(len / 8.0));
		m.lightPos = new BlockPos[pieces];
		for (int k = 0; k < pieces; k++) {
			V3 p = curve.pointAtDist(len * (k + 0.5) / pieces);
			m.lightPos[k] = BlockPos.ofFloored(p.x(), p.y() + 0.4, p.z());
		}
		m.center = curve.point(0.5);
		m.radius = len / 2 + 4;
		float bu0 = bal.getMinU(), bu1 = bal.getMaxU(), bdv = bal.getMaxV() - bal.getMinV();
		float ru0 = rl.getMinU(), ru1 = rl.getMaxU(), rdv = rl.getMaxV() - rl.getMinV();
		for (int i = 0; i < n; i++) {
			int pc = Math.min(pieces - 1, (int) (i * (double) pieces / n));
			int q = i % 4;
			float bv0 = bal.getMinV() + bdv * q / 4f, bv1 = bal.getMinV() + bdv * (q + 1) / 4f;
			float rv0 = rl.getMinV() + rdv * q / 4f, rv1 = rl.getMinV() + rdv * (q + 1) / 4f;
			// ballast bed: top + sloped shoulders
			m.quad(at(P, R, U, i, -1.35, 0.10), at(P, R, U, i, 1.35, 0.10), at(P, R, U, i + 1, 1.35, 0.10), at(P, R, U, i + 1, -1.35, 0.10),
					bu0, bv0, bu1, bv1, U[i], pc);
			m.quad(at(P, R, U, i, -1.7, 0.0), at(P, R, U, i, -1.35, 0.10), at(P, R, U, i + 1, -1.35, 0.10), at(P, R, U, i + 1, -1.7, 0.0),
					bu0, bv0, bu0 + (bu1 - bu0) * 0.15f, bv1, R[i].neg().add(U[i]).normalize(), pc);
			m.quad(at(P, R, U, i, 1.35, 0.10), at(P, R, U, i, 1.7, 0.0), at(P, R, U, i + 1, 1.7, 0.0), at(P, R, U, i + 1, 1.35, 0.10),
					bu1 - (bu1 - bu0) * 0.15f, bv0, bu1, bv1, R[i].add(U[i]).normalize(), pc);
			// rails
			for (int s = -1; s <= 1; s += 2) {
				double c = s * 0.8;
				m.quad(at(P, R, U, i, c - 0.09, 0.35), at(P, R, U, i, c + 0.09, 0.35), at(P, R, U, i + 1, c + 0.09, 0.35), at(P, R, U, i + 1, c - 0.09, 0.35),
						ru0, rv0, ru0 + (ru1 - ru0) * 0.4f, rv1, U[i], pc);
				m.quad(at(P, R, U, i, c - 0.09, 0.20), at(P, R, U, i, c - 0.09, 0.35), at(P, R, U, i + 1, c - 0.09, 0.35), at(P, R, U, i + 1, c - 0.09, 0.20),
						ru0, rv0, ru1, rv1, R[i].neg(), pc);
				m.quad(at(P, R, U, i, c + 0.09, 0.35), at(P, R, U, i, c + 0.09, 0.20), at(P, R, U, i + 1, c + 0.09, 0.20), at(P, R, U, i + 1, c + 0.09, 0.35),
						ru0, rv0, ru1, rv1, R[i], pc);
			}
		}
		// sleepers
		float su0 = sl.getMinU(), su1 = sl.getMaxU(), sv0 = sl.getMinV(), sv1 = sl.getMaxV();
		for (double d = 0.45; d < len - 0.2; d += 0.9) {
			V3 p = curve.pointAtDist(d);
			V3 t = curve.tangentAtDist(d);
			V3 r = t.cross(up0);
			r = r.lengthSq() < 1e-8 ? new V3(1, 0, 0) : r.normalize();
			V3 u = r.cross(t).normalize();
			int pc = Math.min(pieces - 1, (int) (d / len * pieces));
			V3 a = p.add(r.mul(-1.2)).add(t.mul(-0.17)), b = p.add(r.mul(1.2)).add(t.mul(-0.17));
			V3 c = p.add(r.mul(1.2)).add(t.mul(0.17)), dd = p.add(r.mul(-1.2)).add(t.mul(0.17));
			V3 lo = u.mul(0.10), hi = u.mul(0.20);
			m.quad(a.add(hi), b.add(hi), c.add(hi), dd.add(hi), su0, sv0, su1, sv1, u, pc);
			m.quad(a.add(lo), a.add(hi), dd.add(hi), dd.add(lo), su0, sv0, su0 + (su1 - su0) * 0.3f, sv1, r.neg(), pc);
			m.quad(b.add(hi), b.add(lo), c.add(lo), c.add(hi), su0, sv0, su0 + (su1 - su0) * 0.3f, sv1, r, pc);
			m.quad(a.add(lo), b.add(lo), b.add(hi), a.add(hi), su0, sv0, su1, sv0 + (sv1 - sv0) * 0.3f, t.neg(), pc);
			m.quad(dd.add(hi), c.add(hi), c.add(lo), dd.add(lo), su0, sv0, su1, sv0 + (sv1 - sv0) * 0.3f, t, pc);
		}
		return m;
	}

	private static V3 at(V3[] P, V3[] R, V3[] U, int i, double lat, double h) {
		return P[i].add(R[i].mul(lat)).add(U[i].mul(h));
	}

	private static void draw(Mesh m, VertexConsumer vc, MatrixStack.Entry e, Vec3d cam, ClientWorld world, int argb, boolean fullBright) {
		Matrix4f mat = e.getPositionMatrix();
		int[] lights = new int[m.lightPos.length];
		for (int k = 0; k < lights.length; k++) lights[k] = fullBright ? LightmapTextureManager.MAX_LIGHT_COORDINATE : WorldRenderer.getLightmapCoordinates(world, m.lightPos[k]);
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		for (int q = 0; q < m.quads; q++) {
			int light = lights[m.piece[q]];
			float nx = m.nrm[q * 3], ny = m.nrm[q * 3 + 1], nz = m.nrm[q * 3 + 2];
			for (int k = 0; k < 4; k++) {
				int o = q * 20 + k * 5;
				vc.vertex(mat, (float) (m.v[o] - cam.x), (float) (m.v[o + 1] - cam.y), (float) (m.v[o + 2] - cam.z))
						.color(r, g, b, a).texture(m.v[o + 3], m.v[o + 4]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
			}
		}
	}

	// ------------------------------------------------------------------------------------------ orientation

	/** Rotation that turns the local mesh frame (forward = -Z, up = +Y) so that forward points along f. */
	public static Quaternionf orient(double fx, double fy, double fz) {
		Vector3f f = new Vector3f((float) fx, (float) fy, (float) fz);
		if (f.lengthSquared() < 1e-9f) f.set(0, 0, -1);
		f.normalize();
		Vector3f up0 = new Vector3f(0, 1, 0);
		Vector3f r = f.cross(up0, new Vector3f());
		if (r.lengthSquared() < 1e-8f) r.set(1, 0, 0);
		r.normalize();
		Vector3f u = r.cross(f, new Vector3f()).normalize();
		Matrix3f m = new Matrix3f(r.x, r.y, r.z, u.x, u.y, u.z, -f.x, -f.y, -f.z);
		return new Quaternionf().setFromNormalized(m);
	}

	// ------------------------------------------------------------------------------------------ main entry

	public static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;
		if (meshVersion != ClientRail.graphVersion) {
			MESHES.clear();
			meshVersion = ClientRail.graphVersion;
		}
		MatrixStack matrices = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
		Vec3d cam = ctx.camera().getPos();
		float delta = ctx.tickCounter().getTickDelta(false);
		VertexConsumerProvider.Immediate imm = client.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer vc = imm.getBuffer(RenderLayer.getEntityCutoutNoCull(ATLAS));
		MatrixStack.Entry entry = matrices.peek();

		RailGraph g = ClientRail.graph;
		for (RailTrack t : g.tracks.values()) {
			V3 mid = t.curve.point(0.5);
			double dx = mid.x() - cam.x, dz = mid.z() - cam.z;
			if (dx * dx + dz * dz > (RANGE + t.length() / 2) * (RANGE + t.length() / 2)) continue;
			// buildings draw their own bed; embedded pieces are covered by the station models
			if (t.owner != RailNode.NO_OWNER) continue;
			Mesh m = MESHES.computeIfAbsent(t.id, k -> build(t.curve, false));
			draw(m, vc, entry, cam, world, 0xFFFFFFFF, false);
		}
		drawNodeFurniture(client, world, matrices, imm, vc, cam, delta);
		drawTrains(client, world, matrices, imm, vc, cam, delta);
		imm.draw();
		drawVehicleOutline(client, world, matrices, imm, cam, delta);
		drawPreview(client, world, matrices, imm, cam, delta);
	}

	// ------------------------------------------------------------------------------------------ signals + switches

	private static BuildingModel redOn, redOff, greenOn, greenOff;

	private static BuildingModel lamp(String tex, double y0, double y1, boolean glow) {
		BuildingModel.BoxBuilder b = BuildingModel.builder(new Footprint(1, 1, 1, 0, 0)).box(0.33, y0, 0.75, 0.67, y1, 0.79)
				.all("ficsitcraft:block/trn/" + tex).stretch();
		if (glow) b = b.alwaysGlow();
		return b.done().build();
	}

	private static void ensureLamps() {
		if (redOn != null) return;
		redOn = lamp("signal_lamp_red", 3.55, 3.89, true);
		redOff = lamp("signal_lamp_off", 3.55, 3.89, false);
		greenOn = lamp("signal_lamp_green", 3.11, 3.45, true);
		greenOff = lamp("signal_lamp_off", 3.11, 3.45, false);
	}

	/** World position of the housing of a signal (for hit tests). */
	public static V3 signalPos(RailNode n, int side) {
		V3 travel = side == RailNode.BACK ? n.dir : n.dir.neg();
		V3 right = travel.cross(new V3(0, 1, 0));
		right = right.lengthSq() < 1e-8 ? new V3(1, 0, 0) : right.normalize();
		return n.pos.add(right.mul(2.3));
	}

	public static V3 leverPos(RailNode n, int side) {
		V3 right = n.dir.cross(new V3(0, 1, 0));
		right = right.lengthSq() < 1e-8 ? new V3(1, 0, 0) : right.normalize();
		return n.pos.add(right.mul(side == RailNode.FRONT ? 2.2 : -2.2)).add(new V3(0, 1.5, 0));
	}

	private static void drawNodeFurniture(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider.Immediate imm,
										 VertexConsumer vc, Vec3d cam, float delta) {
		ensureLamps();
		BuildingModel sigBlock = BuildingModels.local("signal_block"), sigPath = BuildingModels.local("signal_path");
		BuildingModel stand = BuildingModels.local("switch_stand"), lever = BuildingModels.local("switch_lever");
		float time = world.getTime() + delta;
		for (RailNode n : ClientRail.graph.nodes.values()) {
			double dx = n.pos.x() - cam.x, dz = n.pos.z() - cam.z;
			if (dx * dx + dz * dz > 120 * 120) continue;
			for (int s = 0; s < 2; s++) {
				int type = n.signal(s);
				if (type != 0) {
					BuildingModel model = type == 1 ? sigBlock : sigPath;
					if (model != null) {
						V3 travel = s == RailNode.BACK ? n.dir : n.dir.neg();
						V3 p = signalPos(n, s);
						boolean open = ClientRail.signals.getOrDefault(n.id * 2 + s, true);
						int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(p.x(), p.y() + 1, p.z()));
						matrices.push();
						matrices.translate(p.x() - cam.x, p.y() - cam.y, p.z() - cam.z);
						matrices.multiply(orient(-travel.x(), 0, -travel.z()));
						BuildingMesh.renderLocal(model, false, time, matrices, vc, light, -1, 0.5f, 0.5f);
						// lamps: red on top, green below; the lit one glows
						BuildingModel red = open ? redOff : redOn;
						BuildingModel green = open ? greenOn : greenOff;
						BuildingMesh.renderLocal(red, false, time, matrices, vc, light, -1, 0.5f, 0.5f);
						BuildingMesh.renderLocal(green, false, time, matrices, vc, light, -1, 0.5f, 0.5f);
						matrices.pop();
					}
				}
				if (n.isSwitch(s)) drawSwitchTrack(world, matrices, vc, cam, n, s, time, delta);
				if (n.isSwitch(s) && stand != null && lever != null) {
					V3 lp = leverPos(n, s).sub(new V3(0, 1.5, 0));
					int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(lp.x(), lp.y() + 1, lp.z()));
					matrices.push();
					matrices.translate(lp.x() - cam.x, lp.y() - cam.y, lp.z() - cam.z);
					matrices.multiply(orient(n.dir.x(), 0, n.dir.z()));
					BuildingMesh.renderLocal(stand, false, time, matrices, vc, light, -1, 0.5f, 0.5f);
					matrices.translate(0, 1.1, 0);
					int sel = n.sel(s);
					float tilt = n.side(s).size() == 2 ? (sel == 0 ? -35f : 35f) : (sel - 1) * 35f;
					// the lever swings over with the blades
					ClientRail.SwitchAnim an = ClientRail.switchAnims.get(n.id * 2 + s);
					if (an != null) {
						float k = switchProgress(an, time);
						float from = n.side(s).size() == 2 ? (an.from() == 0 ? -35f : 35f) : (an.from() - 1) * 35f;
						tilt = from + (tilt - from) * k;
					}
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
					BuildingMesh.renderLocal(lever, false, time, matrices, vc, light, -1, 0.5f, 0.5f);
					matrices.pop();
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------ switch points

	private static BuildingModel swBlade, swOn, swOff;
	private static final float SWITCH_TICKS = 12f;

	private static BuildingModel strip(String tex, double x0, double x1, double y0, double y1, double z0, double z1, boolean glow) {
		BuildingModel.BoxBuilder b = BuildingModel.builder(new Footprint(1, 1, 1, 0, 0)).box(x0, y0, z0, x1, y1, z1)
				.all("ficsitcraft:block/trn/" + tex).stretch();
		if (glow) b = b.alwaysGlow();
		return b.done().build();
	}

	private static void ensureSwitchModels() {
		if (swBlade != null) return;
		// two blades on the rails (gauge +-0.8 around the centre line at x = 0.5), a glowing route strip between the rails
		swBlade = BuildingModel.builder(new Footprint(1, 1, 1, 0, 0))
				.box(-0.4, 0.30, 0.0, -0.2, 0.41, 3.2).all("ficsitcraft:block/trn/switch_blade").stretch().done()
				.box(1.2, 0.30, 0.0, 1.4, 0.41, 3.2).all("ficsitcraft:block/trn/switch_blade").stretch().done()
				.build();
		swOn = strip("switch_route_on", 0.34, 0.66, 0.20, 0.25, 0.0, 0.92, true);
		swOff = strip("switch_route_off", 0.38, 0.62, 0.20, 0.23, 0.0, 0.92, false);
	}

	private static float switchProgress(ClientRail.SwitchAnim a, float time) {
		float k = Math.min(1f, Math.max(0f, (time - a.start()) / SWITCH_TICKS));
		return k * k * (3 - 2 * k);
	}

	/** The branch a switch is set to shows on the rails (glowing green route, dark strips on the others) and the blades slide over when it is thrown. */
	private static void drawSwitchTrack(ClientWorld world, MatrixStack matrices, VertexConsumer vc, Vec3d cam, RailNode n, int s, float time, float delta) {
		ensureSwitchModels();
		com.ficsitcraft.rail.RailGraph g = ClientRail.graph;
		java.util.List<Long> branches = n.side(s);
		int sel = n.sel(s);
		ClientRail.SwitchAnim an = ClientRail.switchAnims.get(n.id * 2 + s);
		float k = an == null ? 1f : switchProgress(an, world.getTime() + delta);
		if (an != null && world.getTime() + delta - an.start() > SWITCH_TICKS + 2) ClientRail.switchAnims.remove(n.id * 2 + s);
		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(n.pos.x(), n.pos.y() + 0.6, n.pos.z()));
		// route strips along every branch
		for (int i = 0; i < branches.size(); i++) {
			RailTrack t = g.tracks.get(branches.get(i));
			if (t == null) continue;
			boolean fromStart = t.nodeA == n.id && t.sideA == s;
			boolean lit = i == sel;
			// the strip of the new route grows outwards while the blades move over, the old one shrinks away
			double len = lit ? (an == null ? 6.0 : 1.0 + 5.0 * k) : (an != null && i == an.from() ? 6.0 - 5.0 * k : 2.5);
			for (double d = 3.4; d < 3.4 + len && d < t.length() - 0.4; d += 1.0) {
				double dd = fromStart ? d : t.length() - d;
				V3 p = t.curve.pointAtDist(dd);
				V3 tan = t.curve.tangentAtDist(dd);
				if (!fromStart) tan = tan.neg();
				matrices.push();
				matrices.translate(p.x() - cam.x, p.y() - cam.y, p.z() - cam.z);
				matrices.multiply(orient(tan.x(), tan.y(), tan.z()));
				BuildingMesh.renderLocal(lit ? swOn : swOff, false, time, matrices, vc, light, -1, 0.5f, 0f);
				matrices.pop();
			}
		}
		// the blades: from the node along the direction the switch is set to (sliding between the old and the new branch)
		V3 to = g.branchLeaveDir(n, s, sel);
		V3 dir = to;
		if (an != null) dir = g.branchLeaveDir(n, s, an.from()).lerp(to, k);
		if (dir.lengthSq() < 1e-8) dir = n.leaveDir(s);
		dir = dir.normalize();
		matrices.push();
		matrices.translate(n.pos.x() - cam.x, n.pos.y() - cam.y, n.pos.z() - cam.z);
		matrices.multiply(orient(dir.x(), dir.y(), dir.z()));
		BuildingMesh.renderLocal(swBlade, false, time, matrices, vc, light, -1, 0.5f, 0f);
		matrices.pop();
	}

	// ------------------------------------------------------------------------------------------ trains

	/** Bogie-based pose of a vehicle. */
	public record VehiclePose(V3 center, V3 forward, double yawFacing) {
	}

	public static VehiclePose poseOf(Train t, int i) {
		Vehicle v = t.vehicles.get(i);
		double u = t.vehicleCenterU(i);
		double span = v.type.length * 0.32;
		V3 pf = t.pointAtU(ClientRail.graph, u + span), pr = t.pointAtU(ClientRail.graph, u - span);
		V3 dir = pf.sub(pr);
		if (dir.lengthSq() < 1e-8) dir = t.tangentAtU(ClientRail.graph, u);
		dir = dir.normalize();
		if (v.facing < 0) dir = dir.neg();
		return new VehiclePose(pf.lerp(pr, 0.5).add(new V3(0, 0.35, 0)), dir, 0);
	}

	private static void drawTrains(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider.Immediate imm,
								  VertexConsumer vc, Vec3d cam, float delta) {
		float time = world.getTime() + delta;
		for (Map.Entry<UUID, ClientRail.CTrain> en : ClientRail.trains.entrySet()) {
			ClientRail.CTrain c = en.getValue();
			if (c.base == null || !ClientRail.pathKnown(c.base)) continue;
			Train t = ClientRail.posed(c, world, delta);
			V3 mid = t.pointAtU(ClientRail.graph, t.tailOffset + t.length() / 2);
			double dx = mid.x() - cam.x, dz = mid.z() - cam.z;
			double reach = RANGE + t.length() / 2;
			if (dx * dx + dz * dz > reach * reach) continue;
			boolean moving = Math.abs(t.speed) > 0.03;
			for (int i = 0; i < t.vehicles.size(); i++) {
				Vehicle v = t.vehicles.get(i);
				BuildingModel model = BuildingModels.local(v.type.id);
				if (model == null) continue;
				VehiclePose p = poseOf(t, i);
				int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(p.center().x(), p.center().y() + 1.5, p.center().z()));
				matrices.push();
				matrices.translate(p.center().x() - cam.x, p.center().y() - cam.y, p.center().z() - cam.z);
				matrices.multiply(orient(p.forward().x(), p.forward().y(), p.forward().z()));
				if (c.base.derailed && c.derailedAt >= 0) tipOver(matrices, c, t.id, v, world.getTime() + delta);
				float ox = 1.5f, oz = (float) (v.type.length / 2);
				BuildingMesh.renderLocal(model, moving || v.type == VehicleType.LOCOMOTIVE && t.driven, time, matrices, vc, light, -1, ox, oz,
						ClientRail.rollOf(c, v.facing, delta));
				matrices.pop();
			}
		}
	}

	/**
	 * A crashed car falls over sideways: it rolls about the rail line (each car by a slightly different angle, a bit
	 * off the track and slewed) with a short ease-in so it topples instead of snapping.
	 */
	private static void tipOver(MatrixStack matrices, ClientRail.CTrain c, UUID trainId, Vehicle v, float now) {
		float k = Math.min(1f, Math.max(0f, (now - c.derailedAt) / 12f));
		k = k * k * (3 - 2 * k);
		int h = v.id.hashCode() * 31 + 7;
		float side = (trainId.hashCode() & 1) == 0 ? 1f : -1f;
		float roll = side * k * (62f + (h & 31) * 0.6f);
		float yaw = k * (((h >> 5) & 7) - 3.5f) * 1.6f;
		float shift = k * (((h >> 8) & 7) - 3.5f) * 0.12f + side * k * 0.25f;
		double lift = 1.45 * Math.sin(Math.toRadians(Math.abs(roll))) * 0.95;
		matrices.translate(shift, lift, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
	}

	// ------------------------------------------------------------------------------------------ hover outline

	/** Black outline around the car under the crosshair (like the block selection box), so the hit box is visible. */
	private static void drawVehicleOutline(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider.Immediate imm,
										   Vec3d cam, float delta) {
		RailInteract.Target tg = RailInteract.hoveredVehicle(client, delta);
		if (tg == null) return;
		ClientRail.CTrain ct = ClientRail.trains.get(tg.train());
		if (ct == null || ct.base == null || !ClientRail.pathKnown(ct.base)) return;
		Train t = ClientRail.posed(ct, world, delta);
		for (int i = 0; i < t.vehicles.size(); i++) {
			Vehicle v = t.vehicles.get(i);
			if (!v.id.equals(tg.vehicle())) continue;
			VehiclePose p = poseOf(t, i);
			V3 f = p.forward();
			V3 rt = f.cross(new V3(0, 1, 0));
			rt = rt.lengthSq() < 1e-8 ? new V3(1, 0, 0) : rt.normalize();
			V3 up = rt.cross(f).normalize();
			V3 c = p.center().add(new V3(0, 1.8, 0));
			double hw = RailInteract.HIT_HALF_WIDTH, hh = RailInteract.HIT_HALF_HEIGHT, hl = v.type.length / 2;
			V3[] k = new V3[8];
			for (int m = 0; m < 8; m++) {
				double sx = (m & 1) == 0 ? -1 : 1, sy = (m & 2) == 0 ? -1 : 1, sz = (m & 4) == 0 ? -1 : 1;
				k[m] = c.add(rt.mul(sx * hw)).add(up.mul(sy * hh)).add(f.mul(sz * hl));
			}
			VertexConsumer lines = imm.getBuffer(RenderLayer.getLines());
			MatrixStack.Entry e = matrices.peek();
			int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
			for (int[] ed : edges) {
				V3 a = k[ed[0]], b = k[ed[1]], n = b.sub(a).normalize();
				lines.vertex(e, (float) (a.x() - cam.x), (float) (a.y() - cam.y), (float) (a.z() - cam.z)).color(0f, 0f, 0f, 0.6f).normal(e, (float) n.x(), (float) n.y(), (float) n.z());
				lines.vertex(e, (float) (b.x() - cam.x), (float) (b.y() - cam.y), (float) (b.z() - cam.z)).color(0f, 0f, 0f, 0.6f).normal(e, (float) n.x(), (float) n.y(), (float) n.z());
			}
			imm.draw();
			return;
		}
	}

	// ------------------------------------------------------------------------------------------ preview

	private static void drawPreview(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider.Immediate imm,
									Vec3d cam, float delta) {
		previewText = null;
		if (!(client.player.getMainHandStack().getItem() instanceof RailwayItem)) return;
		if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		Vec3d h = hit.getPos();
		V3 hp = new V3(h.x, h.y, h.z);
		Vec3d look = client.player.getRotationVec(delta);
		RailGraph g = ClientRail.graph;
		VertexConsumer lines = imm.getBuffer(RenderLayer.getLines());
		if (ClientRail.pendingPos == null) {
			RailPlacement.Anchor a = RailPlacement.resolve(g, hp, look.x, look.z, false, null);
			if (a == null) {
				previewText = Text.translatable("message.ficsitcraft.rail_no_free_side");
				return;
			}
			marker(matrices, lines, cam, a.pos(), a.leave(), a.isNode() ? 0.3f : a.isSplit() ? 1f : 0.35f, a.isNode() ? 1f : 0.8f, 1f);
			imm.draw();
			previewText = Text.translatable(a.isNode() ? "hud.ficsitcraft.rail_snap_node" : a.isSplit() ? "hud.ficsitcraft.rail_snap_track" : "hud.ficsitcraft.rail_start");
			return;
		}
		RailPlacement.Anchor a = new RailPlacement.Anchor(ClientRail.pendingPos, ClientRail.pendingLeave, -1, 0, -1, 0);
		RailPlacement.Anchor b = RailPlacement.resolve(g, hp, look.x, look.z, true, a.pos());
		RailPlacement.Result r = RailPlacement.plan(g, a, b);
		marker(matrices, lines, cam, a.pos(), a.leave(), 0.35f, 0.8f, 1f);
		if (r.plan() != null && r.plan().curve() != null) {
			Mesh m = build(r.plan().curve(), true);
			VertexConsumer vc = imm.getBuffer(RenderLayer.getEntityTranslucent(ATLAS));
			draw(m, vc, matrices.peek(), cam, world, r.valid() ? 0x8055C8FF : 0x90FF4A3A, true);
			imm.draw();
		}
		// `lines` may have been drawn (and its builder closed) when the translucent layer was requested above
		if (b != null) marker(matrices, imm.getBuffer(RenderLayer.getLines()), cam, b.pos(), b.leave(), r.valid() ? 0.35f : 1f, r.valid() ? 0.8f : 0.3f, r.valid() ? 1f : 0.25f);
		imm.draw();
		if (r.valid()) {
			previewText = Text.translatable("hud.ficsitcraft.rail_preview", String.format("%.1f", r.plan().length()), r.plan().cost());
		} else {
			previewText = Text.translatable("message.ficsitcraft.rail_problem_" + r.problem());
		}
	}

	private static void marker(MatrixStack matrices, VertexConsumer lines, Vec3d cam, V3 p, V3 dir, float r, float g, float b) {
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p.x() - 0.25 - cam.x, p.y() - cam.y, p.z() - 0.25 - cam.z,
				p.x() + 0.25 - cam.x, p.y() + 0.9 - cam.y, p.z() + 0.25 - cam.z);
		WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1f);
		if (dir != null) {
			V3 e = p.add(dir.mul(1.6));
			net.minecraft.util.math.Box tip = new net.minecraft.util.math.Box(e.x() - 0.12 - cam.x, e.y() - cam.y + 0.05, e.z() - 0.12 - cam.z,
					e.x() + 0.12 - cam.x, e.y() - cam.y + 0.3, e.z() + 0.12 - cam.z);
			WorldRenderer.drawBox(matrices, lines, tip, r, g, b, 1f);
		}
	}
}
