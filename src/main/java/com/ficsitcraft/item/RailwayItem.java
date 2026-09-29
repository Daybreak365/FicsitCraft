package com.ficsitcraft.item;

import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailPlacement;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailNet;
import com.ficsitcraft.railway.RailWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Railway: place tracks with two clicks (start, end). Tracks snap to existing track ends, can branch off the middle of
 * an existing track and bend smoothly. After each piece the end becomes the next start, so you can lay a line by
 * clicking along it. Sneak + right click cancels. One item per {@value com.ficsitcraft.rail.RailPlanner#UNIT} blocks.
 */
public class RailwayItem extends Item {
	private record Pending(RegistryKey<World> dim, RailPlacement.Anchor start) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	public RailwayItem(Settings settings) {
		super(settings);
	}

	public static void forget(UUID player) {
		PENDING.remove(player);
	}

	private static void msg(ServerPlayerEntity p, String key, Formatting color, Object... args) {
		p.sendMessage(Text.translatable("message.ficsitcraft." + key, args).formatted(color), true);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(ctx.getPlayer() instanceof ServerPlayerEntity p) || !(ctx.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;
		RailWorld rw = RailWorld.get(world);
		RailGraph g = rw.graph;

		if (p.isSneaking()) {
			if (PENDING.remove(p.getUuid()) != null) {
				RailNet.sendPending(p, null, null);
				msg(p, "rail_cancel", Formatting.GRAY);
			}
			return ActionResult.CONSUME;
		}

		Vec3d h = ctx.getHitPos();
		V3 hit = new V3(h.x, h.y, h.z);
		Vec3d look = p.getRotationVector();
		Pending pd = PENDING.get(p.getUuid());
		if (pd != null && !pd.dim().equals(world.getRegistryKey())) pd = null;
		if (pd != null && !startStillValid(g, pd.start())) {
			PENDING.remove(p.getUuid());
			RailNet.sendPending(p, null, null);
			pd = null;
		}

		if (pd == null) {
			RailPlacement.Anchor a = RailPlacement.resolve(g, hit, look.x, look.z, false, null);
			if (a == null) {
				msg(p, "rail_no_free_side", Formatting.RED);
				return ActionResult.CONSUME;
			}
			PENDING.put(p.getUuid(), new Pending(world.getRegistryKey(), a));
			RailNet.sendPending(p, a.pos(), a.leave());
			msg(p, "rail_start", Formatting.AQUA);
			world.playSound(null, BlockPos.ofFloored(h), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7f, 1.4f);
			return ActionResult.CONSUME;
		}

		RailPlacement.Anchor a = pd.start();
		RailPlacement.Anchor b = RailPlacement.resolve(g, hit, look.x, look.z, true, a.pos());
		RailPlacement.Result r = RailPlacement.plan(g, a, b);
		if (!r.valid()) {
			msg(p, "rail_problem_" + r.problem(), Formatting.RED);
			return ActionResult.CONSUME;
		}
		int cost = r.plan().cost();
		ItemStack stack = ctx.getStack();
		if (!p.isCreative() && stack.getCount() < cost) {
			msg(p, "rail_need_items", Formatting.RED, cost);
			return ActionResult.CONSUME;
		}
		long endNode = rw.commitTrack(r);
		if (endNode == -2) {
			msg(p, "rail_occupied", Formatting.RED);
			return ActionResult.CONSUME;
		}
		if (endNode < 0) {
			msg(p, "rail_problem_no_free_side", Formatting.RED);
			return ActionResult.CONSUME;
		}
		if (!p.isCreative()) stack.decrement(cost);
		world.playSound(null, BlockPos.ofFloored(h), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.5f, 1.5f);
		msg(p, "rail_placed", Formatting.GREEN, String.format("%.1f", r.plan().length()), cost);

		// continue the line from the new end
		RailNode end = g.nodes.get(endNode);
		RailPlacement.Anchor next = null;
		if (end != null) {
			int used = b.isNode() ? b.side() : (b.isSplit() ? -1 : RailNode.BACK);
			if (used >= 0) {
				int free = 1 - used;
				if (end.side(free).size() < RailNode.MAX_PER_SIDE) next = new RailPlacement.Anchor(end.pos, end.leaveDir(free), end.id, free, -1, 0);
			}
		}
		if (next != null) {
			PENDING.put(p.getUuid(), new Pending(world.getRegistryKey(), next));
			RailNet.sendPending(p, next.pos(), next.leave());
		} else {
			PENDING.remove(p.getUuid());
			RailNet.sendPending(p, null, null);
		}
		return ActionResult.CONSUME;
	}

	private static boolean startStillValid(RailGraph g, RailPlacement.Anchor a) {
		if (a.isNode()) {
			RailNode n = g.nodes.get(a.node());
			return n != null && n.side(a.side()).size() < RailNode.MAX_PER_SIDE;
		}
		if (a.isSplit()) return g.tracks.containsKey(a.splitTrack());
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.railway").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.railway2").formatted(Formatting.DARK_GRAY));
	}
}
