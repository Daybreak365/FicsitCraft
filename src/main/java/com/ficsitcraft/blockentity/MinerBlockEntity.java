package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.block.MinerBlock;
import com.ficsitcraft.block.Purity;
import com.ficsitcraft.block.ResourceNodeBlock;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.MinerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MinerBlockEntity extends InventoryPowerBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	private static final int[] SLOTS = {0};
	public static final int STATUS_POWERED = 1;
	public static final int STATUS_WORKING = 2;
	public static final int STATUS_NO_NODE = 4;
	public static final int STATUS_OUTPUT_FULL = 8;
	public static final int STATUS_FUSE = 16;

	private double progress; // in items (0..1)
	private boolean wantsPower;
	private int status;
	private int nodeInfo; // (type ordinal + 1) | purity << 8

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> (int) (progress * 1000);
				case 1 -> status;
				case 2 -> nodeInfo;
				case 3 -> (int) Math.min(32000, Math.round(gridCapacity * 10));
				case 4 -> (int) Math.min(32000, Math.round(gridDemand * 10));
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return 5;
		}
	};

	public MinerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MINER, pos, state, 1);
	}

	private MinerBlock minerBlock() {
		return (MinerBlock) getCachedState().getBlock();
	}

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public double getMaxPowerDemand() {
		return minerBlock().powerMW();
	}

	@Override
	public double getPowerDemand() {
		return wantsPower ? minerBlock().powerMW() : 0;
	}

	public static void tick(World world, BlockPos pos, BlockState state, MinerBlockEntity be) {
		be.tickPower();
		BlockState below = world.getBlockState(pos.down());
		int st = 0;
		boolean working = false;
		if (below.getBlock() instanceof ResourceNodeBlock node) {
			Purity purity = below.get(ResourceNodeBlock.PURITY);
			be.nodeInfo = (node.getType().ordinal() + 1) | (purity.ordinal() << 8);
			ItemStack out = be.items.get(0);
			ItemStack product = new ItemStack(node.getType().resource());
			boolean fits = out.isEmpty() || (ItemStack.areItemsAndComponentsEqual(out, product) && out.getCount() < out.getMaxCount());
			be.wantsPower = fits;
			if (!fits) st |= STATUS_OUTPUT_FULL;
			if (fits && be.powered) {
				working = true;
				double perTick = be.minerBlock().baseRate() * purity.multiplier / 1200.0;
				be.progress += perTick;
				while (be.progress >= 1.0) {
					be.progress -= 1.0;
					if (out.isEmpty()) {
						be.items.set(0, product.copy());
						out = be.items.get(0);
					} else if (out.getCount() < out.getMaxCount()) {
						out.increment(1);
					}
				}
				be.markDirty();
			}
		} else {
			be.nodeInfo = 0;
			be.wantsPower = false;
			st |= STATUS_NO_NODE;
		}
		if (be.powered) st |= STATUS_POWERED;
		if (working) st |= STATUS_WORKING;
		if (be.gridFuseBlown) st |= STATUS_FUSE;
		be.status = st;

		be.pushOutput(0, state.get(MachineBlock.FACING));

		if (state.get(MachineBlock.ACTIVE) != working) {
			world.setBlockState(pos, state.with(MachineBlock.ACTIVE, working), 3);
		}
	}

	@Override
	public int[] getAvailableSlots(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return false;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return true;
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putDouble("Progress", progress);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		progress = nbt.getDouble("Progress");
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
		return new MinerScreenHandler(syncId, playerInventory, this, properties);
	}
}
