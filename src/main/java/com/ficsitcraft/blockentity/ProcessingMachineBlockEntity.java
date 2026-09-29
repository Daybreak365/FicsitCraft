package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.block.ProcessingMachineBlock;
import com.ficsitcraft.data.MachineType;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.data.Stack;
import com.ficsitcraft.progress.ProgressState;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.ProcessingMachineScreenHandler;
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

import java.util.ArrayList;
import java.util.List;

public class ProcessingMachineBlockEntity extends InventoryPowerBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final int INPUTS = 4;
	public static final int OUTPUT = 4;
	public static final int SIZE = 5;
	private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4};

	public static final int STATUS_POWERED = 1;
	public static final int STATUS_WORKING = 2;
	public static final int STATUS_NO_INPUT = 4;
	public static final int STATUS_OUTPUT_FULL = 8;
	public static final int STATUS_FUSE = 16;

	private int recipeIndex = -1;
	private int progress;
	private boolean wantsPower;
	private int status;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> recipeIndex + 1;
				case 2 -> status;
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

	public ProcessingMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PROCESSING_MACHINE, pos, state, SIZE);
	}

	public MachineType getMachineType() {
		return getCachedState().getBlock() instanceof ProcessingMachineBlock b ? b.getMachineType() : MachineType.CONSTRUCTOR;
	}

	@Nullable
	public SfRecipe getRecipe() {
		SfRecipe r = Recipes.get(recipeIndex);
		return r != null && r.machine() == getMachineType() ? r : null;
	}

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public double getMaxPowerDemand() {
		return getMachineType().powerMW;
	}

	@Override
	public double getPowerDemand() {
		return wantsPower ? getMachineType().powerMW : 0;
	}

	// ------------------------------------------------------------------ logic

	public static void tick(World world, BlockPos pos, BlockState state, ProcessingMachineBlockEntity be) {
		be.tickPower();
		SfRecipe r = be.getRecipe();
		boolean hasInputs = r != null && be.hasInputs(r);
		boolean outputFits = r != null && be.outputFits(r);
		boolean can = hasInputs && outputFits;
		be.wantsPower = can;
		boolean working = can && be.powered;

		if (working) {
			be.progress++;
			if (be.progress >= r.timeTicks()) {
				be.craft(r);
				be.progress = 0;
			}
			be.markDirty();
		} else if (!hasInputs && be.progress != 0) {
			be.progress = 0;
			be.markDirty();
		}

		int st = 0;
		if (be.powered) st |= STATUS_POWERED;
		if (working) st |= STATUS_WORKING;
		if (r != null && !hasInputs) st |= STATUS_NO_INPUT;
		if (r != null && !outputFits) st |= STATUS_OUTPUT_FULL;
		if (be.gridFuseBlown) st |= STATUS_FUSE;
		be.status = st;

		be.pushOutput(OUTPUT, state.get(MachineBlock.FACING));

		if (state.get(MachineBlock.ACTIVE) != working) {
			world.setBlockState(pos, state.with(MachineBlock.ACTIVE, working), 3);
		}
	}

	private boolean hasInputs(SfRecipe r) {
		List<Stack> in = r.inputs();
		for (int i = 0; i < in.size(); i++) {
			ItemStack s = items.get(i);
			if (!in.get(i).ing().test(s) || s.getCount() < in.get(i).count()) return false;
		}
		return true;
	}

	private boolean outputFits(SfRecipe r) {
		ItemStack out = items.get(OUTPUT);
		if (out.isEmpty()) return true;
		return out.isOf(r.output()) && out.getCount() + r.outCount() <= out.getMaxCount();
	}

	private void craft(SfRecipe r) {
		List<Stack> in = r.inputs();
		for (int i = 0; i < in.size(); i++) items.get(i).decrement(in.get(i).count());
		ItemStack out = items.get(OUTPUT);
		if (out.isEmpty()) items.set(OUTPUT, r.outputStack());
		else out.increment(r.outCount());
	}

	/** Cycles through the unlocked recipes of this machine. dir: -1 previous, +1 next, 0 clear. */
	public void cycleRecipe(PlayerEntity player, int dir) {
		ProgressState ps = world == null ? null : ProgressState.get(world);
		List<SfRecipe> list = new ArrayList<>();
		for (SfRecipe r : Recipes.forMachine(getMachineType())) {
			if (ps == null || ps.isUnlocked(r) || player.isCreative()) list.add(r);
		}
		int newIndex;
		if (dir == 0 || list.isEmpty()) {
			newIndex = -1;
		} else {
			int cur = -1;
			for (int i = 0; i < list.size(); i++) if (list.get(i).index() == recipeIndex) cur = i;
			int next = cur < 0 ? (dir > 0 ? 0 : list.size() - 1) : Math.floorMod(cur + dir, list.size());
			newIndex = list.get(next).index();
		}
		setRecipe(player, newIndex);
	}

	public void setRecipe(PlayerEntity player, int newIndex) {
		if (newIndex == recipeIndex) return;
		// Return all input items to the player, like Satisfactory does when switching recipes.
		for (int i = 0; i < INPUTS; i++) {
			ItemStack s = items.get(i);
			if (!s.isEmpty()) {
				player.getInventory().offerOrDrop(s);
				items.set(i, ItemStack.EMPTY);
			}
		}
		recipeIndex = newIndex;
		progress = 0;
		sync();
	}

	// ------------------------------------------------------------------ inventory rules

	@Override
	public int[] getAvailableSlots(Direction side) {
		return ALL_SLOTS;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		if (slot >= INPUTS) return false;
		SfRecipe r = getRecipe();
		return r != null && slot < r.inputs().size() && r.inputs().get(slot).ing().test(stack);
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		Direction front = getCachedState().get(MachineBlock.FACING);
		return slot < INPUTS && dir != front && isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == OUTPUT;
	}

	// ------------------------------------------------------------------ nbt & gui

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		SfRecipe r = getRecipe();
		nbt.putString("Recipe", r == null ? "" : r.id());
		nbt.putInt("Progress", progress);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		String id = nbt.getString("Recipe");
		recipeIndex = -1;
		for (SfRecipe r : Recipes.ALL) if (r.id().equals(id)) recipeIndex = r.index();
		progress = nbt.getInt("Progress");
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
		return new ProcessingMachineScreenHandler(syncId, playerInventory, this, properties);
	}
}
