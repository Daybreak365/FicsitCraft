package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.PowerPoleBlock;
import com.ficsitcraft.power.GridStats;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.PowerPoleScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Power pole. Keeps a one-minute history (one sample per second) of its grid's consumption and
 * capacity for the power graph GUI.
 */
public class PowerPoleBlockEntity extends PowerNodeBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final int HISTORY = 60;

	private final int[] demandHistory = new int[HISTORY];   // MW * 10
	private final int[] capacityHistory = new int[HISTORY]; // MW * 10
	private int head; // index of the next sample to write
	private int samples;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			GridStats s = gridStats;
			return switch (index) {
				case PowerPoleScreenHandler.P_CAPACITY -> mw10(s.capacity());
				case PowerPoleScreenHandler.P_DEMAND -> mw10(s.demand());
				case PowerPoleScreenHandler.P_MAX_DEMAND -> mw10(s.maxDemand());
				case PowerPoleScreenHandler.P_FLAGS -> (s.fuseBlown() ? 1 : 0) | (s.ok() ? 2 : 0);
				case PowerPoleScreenHandler.P_GENERATORS -> s.generators();
				case PowerPoleScreenHandler.P_CONSUMERS -> s.consumers();
				case PowerPoleScreenHandler.P_NODES -> s.nodes();
				case PowerPoleScreenHandler.P_LINES -> connections.size();
				case PowerPoleScreenHandler.P_MAX_LINES -> getMaxConnections();
				case PowerPoleScreenHandler.P_HEAD -> head;
				case PowerPoleScreenHandler.P_SAMPLES -> samples;
				default -> {
					int i = index - PowerPoleScreenHandler.P_HISTORY;
					if (i >= 0 && i < HISTORY) yield demandHistory[i];
					i -= HISTORY;
					if (i >= 0 && i < HISTORY) yield capacityHistory[i];
					yield 0;
				}
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return PowerPoleScreenHandler.PROPERTY_COUNT;
		}
	};

	public PowerPoleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.POWER_POLE, pos, state);
	}

	private static int mw10(double mw) {
		return (int) Math.max(0, Math.min(32000, Math.round(mw * 10)));
	}

	@Override
	public int getMaxConnections() {
		return getCachedState().getBlock() instanceof PowerPoleBlock p ? p.getMaxConnections() : 4;
	}

	@Override
	public Vec3d getConnectorOffset() {
		int h = getCachedState().getBlock() instanceof PowerPoleBlock p ? p.getHeight() : 1;
		// the insulators sit on top of the crossbar of the top section
		return new Vec3d(0.5, h - 1 + 0.95, 0.5);
	}

	@Override
	public void onGridUpdate(GridStats stats) {
		super.onGridUpdate(stats);
		if (world != null && world.getTime() % 20 == 0) {
			demandHistory[head] = stats.fuseBlown() ? 0 : mw10(stats.demand());
			capacityHistory[head] = mw10(stats.capacity());
			head = (head + 1) % HISTORY;
			if (samples < HISTORY) samples++;
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, PowerPoleBlockEntity be) {
		be.tickPower();
	}

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new PowerPoleScreenHandler(syncId, playerInventory, this, properties);
	}
}
