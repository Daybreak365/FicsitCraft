package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.GeneratorBlock;
import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.registry.ModItems;
import com.ficsitcraft.screen.GeneratorScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Biomass Burner (30 MW) and Coal Generator (75 MW, needs an adjacent water source).
 * Fuel is burned proportionally to the grid load, like in Satisfactory.
 */
public class GeneratorBlockEntity extends InventoryPowerBlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, com.ficsitcraft.fluid.FluidEndpoint {
	/** Coal generator water: 45 m³/min at full load, 20 m³ internal buffer (Satisfactory values). */
	public static final double WATER_PER_MIN = 45;
	public static final double WATER_BUFFER = 20;
	private double water;
	private static final int[] SLOTS = {0};
	public static final int FLAG_FUSE = 1;
	public static final int FLAG_WATER = 2;
	public static final int FLAG_RUNNING = 4;
	public static final int FLAG_COAL = 8;
	public static final int FLAG_HAS_FUEL = 16;

	private double energyLeft; // MJ remaining from the current fuel item
	private double energyMax;
	private boolean fuseBlown;
	private boolean waterOk;
	private boolean running;
	private double lastLoad;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> energyMax <= 0 ? 0 : (int) (energyLeft / energyMax * 1000);
				case 1 -> (int) Math.min(32000, Math.round(gridCapacity * 10));
				case 2 -> (int) Math.min(32000, Math.round(gridDemand * 10));
				case 3 -> flags();
				case 4 -> (int) Math.round(lastLoad * 1000);
				case 5 -> (int) Math.round(water / WATER_BUFFER * 1000);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return 6;
		}
	};

	public GeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GENERATOR, pos, state, 1);
	}

	public GeneratorBlock.Kind getKind() {
		return getCachedState().getBlock() instanceof GeneratorBlock g ? g.getKind() : GeneratorBlock.Kind.BIOMASS;
	}

	private int flags() {
		int f = 0;
		if (gridFuseBlown) f |= FLAG_FUSE;
		if (waterOk) f |= FLAG_WATER;
		if (running) f |= FLAG_RUNNING;
		if (getKind() == GeneratorBlock.Kind.COAL) f |= FLAG_COAL;
		if (energyLeft > 0 || !items.get(0).isEmpty()) f |= FLAG_HAS_FUEL;
		return f;
	}

	/** Energy in MJ provided by one item (Satisfactory values). */
	public static double fuelValue(GeneratorBlock.Kind kind, ItemStack stack) {
		if (stack.isEmpty()) return 0;
		if (kind == GeneratorBlock.Kind.COAL) {
			if (stack.isOf(Items.COAL) || stack.isOf(Items.CHARCOAL)) return 300;
			if (stack.isOf(Items.COAL_BLOCK)) return 2700;
			return 0;
		}
		if (stack.isOf(ModItems.SOLID_BIOFUEL)) return 450;
		if (stack.isOf(ModItems.BIOMASS)) return 180;
		if (stack.isIn(ItemTags.LOGS)) return 100;
		if (stack.isIn(ItemTags.LEAVES)) return 15;
		if (stack.isIn(ItemTags.PLANKS)) return 25;
		if (stack.isIn(ItemTags.SAPLINGS)) return 10;
		return 0;
	}

	// ------------------------------------------------------------------ power

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public boolean isGenerator() {
		return true;
	}

	@Override
	public boolean isFuseBlown() {
		return fuseBlown;
	}

	@Override
	public void setFuseBlown(boolean blown) {
		if (fuseBlown != blown) {
			fuseBlown = blown;
			markDirty();
		}
	}

	private boolean canRun() {
		if (getKind() == GeneratorBlock.Kind.COAL && !waterOk) return false;
		return energyLeft > 0 || fuelValue(getKind(), items.get(0)) > 0;
	}

	@Override
	public double getPowerCapacity() {
		return canRun() ? getKind().mw : 0;
	}

	@Override
	public void onGridUpdate(com.ficsitcraft.power.GridStats stats) {
		super.onGridUpdate(stats);
		running = false;
		lastLoad = 0;
		double load = stats.load();
		if (!stats.ok() || load <= 0 || !canRun()) return;
		double need = load * getKind().mw / 20.0; // MJ this tick
		while (need > 1e-9) {
			if (energyLeft <= 0) {
				ItemStack fuel = items.get(0);
				double v = fuelValue(getKind(), fuel);
				if (v <= 0) break;
				fuel.decrement(1);
				energyLeft += v;
				energyMax = v;
			}
			double take = Math.min(need, energyLeft);
			energyLeft -= take;
			need -= take;
		}
		if (getKind() == GeneratorBlock.Kind.COAL) water = Math.max(0, water - load * WATER_PER_MIN / 1200.0);
		running = true;
		lastLoad = load;
		markDirty();
	}

	public static void tick(World world, BlockPos pos, BlockState state, GeneratorBlockEntity be) {
		be.tickPower();
		be.waterOk = be.getKind() != GeneratorBlock.Kind.COAL || be.water > 1e-4;
		boolean active = be.running;
		if (state.get(MachineBlock.ACTIVE) != active) world.setBlockState(pos, state.with(MachineBlock.ACTIVE, active), 3);
	}

	@Override
	public boolean acceptsPipes() {
		return getKind() == GeneratorBlock.Kind.COAL;
	}

	/** Pipes feed water into the coal generator's buffer. */
	@Override
	public double offerFluid(com.ficsitcraft.fluid.SfFluid fluid, double amount) {
		if (getKind() != GeneratorBlock.Kind.COAL || fluid != com.ficsitcraft.fluid.SfFluid.WATER) return 0;
		double accepted = Math.min(amount, WATER_BUFFER - water);
		if (accepted <= 0) return 0;
		water += accepted;
		markDirty();
		return accepted;
	}

	// ------------------------------------------------------------------ inventory

	@Override
	public int[] getAvailableSlots(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return fuelValue(getKind(), stack) > 0;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return isValid(slot, stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putDouble("Energy", energyLeft);
		nbt.putDouble("EnergyMax", energyMax);
		nbt.putBoolean("Fuse", fuseBlown);
		nbt.putDouble("Water", water);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		energyLeft = nbt.getDouble("Energy");
		energyMax = nbt.getDouble("EnergyMax");
		fuseBlown = nbt.getBoolean("Fuse");
		water = nbt.getDouble("Water");
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
		return new GeneratorScreenHandler(syncId, playerInventory, this, properties);
	}
}
