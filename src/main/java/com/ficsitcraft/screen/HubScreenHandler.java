package com.ficsitcraft.screen;

import com.ficsitcraft.data.Cost;
import com.ficsitcraft.data.Milestone;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.progress.MaskDelegate;
import com.ficsitcraft.progress.ProgressState;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.registry.ModScreenHandlers;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * HUB terminal. Button ids: 0..99 = deposit items into milestone N, 100+N = select milestone N.
 * Properties: 0..3 milestone mask, 4 = selected milestone, 5..8 = already deposited amounts of the selection.
 */
public class HubScreenHandler extends ScreenHandler {
	public static final int SELECT_OFFSET = 100;
	public static final int INV_X = 48;
	public static final int INV_Y = 174;

	private final ScreenHandlerContext context;
	private final PropertyDelegate props;
	private int selected = 0;

	public HubScreenHandler(int syncId, PlayerInventory inv) {
		this(syncId, inv, ScreenHandlerContext.EMPTY);
	}

	public HubScreenHandler(int syncId, PlayerInventory inv, ScreenHandlerContext context) {
		super(ModScreenHandlers.HUB, syncId);
		this.context = context;
		MinecraftServer server = inv.player.getServer();
		if (inv.player.getWorld().isClient || server == null) {
			this.props = new ArrayPropertyDelegate(9);
		} else {
			ProgressState ps = ProgressState.get(server);
			PropertyDelegate mask = MaskDelegate.server(ps::mask);
			this.props = new PropertyDelegate() {
				@Override
				public int get(int index) {
					if (index < MaskDelegate.SIZE) return mask.get(index);
					if (index == 4) return selected;
					return Math.min(32000, ps.getDeposited(selected, index - 5));
				}

				@Override
				public void set(int index, int value) {
				}

				@Override
				public int size() {
					return 9;
				}
			};
		}
		MachineSlots.addPlayerInventory(this::addSlot, inv, INV_X, INV_Y);
		addProperties(props);
	}

	public long getMask() {
		return MaskDelegate.read(props);
	}

	public int getSyncedSelection() {
		return props.get(4);
	}

	public int getDeposited(int costIndex) {
		return props.get(5 + costIndex);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		MinecraftServer server = player.getServer();
		if (server == null) return false;
		if (id >= SELECT_OFFSET) {
			if (Milestones.get(id - SELECT_OFFSET) == null) return false;
			selected = id - SELECT_OFFSET;
			return true;
		}
		Milestone m = Milestones.get(id);
		if (m == null) return false;
		ProgressState ps = ProgressState.get(server);
		if (!Milestones.isAvailable(ps.mask(), m)) return false;

		// Deposit whatever the player has (Satisfactory lets you pay milestones in several trips).
		boolean depositedAny = false;
		boolean complete = true;
		for (int i = 0; i < m.cost().size(); i++) {
			Cost c = m.cost().get(i);
			int missing = c.count() - ps.getDeposited(m.index(), i);
			if (missing <= 0) continue;
			int give = player.isCreative() ? missing : Math.min(missing, InvUtil.count(player.getInventory(), c.item()));
			if (give > 0) {
				if (!player.isCreative()) InvUtil.remove(player.getInventory(), s -> !s.isEmpty() && s.isOf(c.item()), give);
				ps.addDeposit(m.index(), i, give, m.cost().size());
				depositedAny = true;
			}
			if (give < missing) complete = false;
		}

		if (complete) {
			ps.complete(m.index());
			// notification toast (not chat) for every pioneer on the server
			for (net.minecraft.server.network.ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.ficsitcraft.network.MilestonePayload(m.index()));
			}
			context.run((world, pos) -> world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1f, 1f));
		} else if (depositedAny) {
			player.sendMessage(Text.translatable("message.ficsitcraft.deposited").formatted(Formatting.YELLOW), true);
		} else {
			player.sendMessage(Text.translatable("message.ficsitcraft.missing_items").formatted(Formatting.RED), true);
		}
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, ModBlocks.HUB);
	}
}
