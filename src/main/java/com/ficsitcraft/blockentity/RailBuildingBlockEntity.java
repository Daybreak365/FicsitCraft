package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.block.RailBuildingBlock;
import com.ficsitcraft.fluid.FluidEndpoint;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.Multiblocks;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailTrack;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailWorld;
import com.ficsitcraft.railway.VehicleCargo;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.PlatformScreenHandler;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Block entity of the four railway buildings. All of them are power connections (the locomotives draw their power through
 * a powered building of the network). Depending on the kind it also is a named station, a freight platform (36 item slots,
 * loads / unloads the docked freight car) or a fluid platform (400 m³ tank feeding / fed by pipelines).
 */
public class RailBuildingBlockEntity extends PowerNodeBlockEntity implements Inventory, ExtendedScreenHandlerFactory<BlockPos>, FluidEndpoint {
	public static final double TANK = 400;
	private static final int SLOTS = 36;
	private static final double FLUID_RATE = 6.0;      // m³ per tick between tank and car
	private static final int ITEM_RATE = 4;            // items per tick between inventory and car

	/** What the platform is doing right now (shown in its GUI). */
	public static final int ST_NO_POWER = 0, ST_NO_CAR = 1, ST_WORKING = 2, ST_DONE = 3, ST_WAIT_INPUT = 4, ST_PLATFORM_FULL = 5, ST_MISMATCH = 6;

	private String stationName = "";
	private long trackId = -1;
	private boolean unload;                            // false = load the car from the platform, true = unload the car into the platform
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
	private SfFluid fluid = SfFluid.NONE;
	private double amount;

	// docking state (not saved)
	private UUID dockedTrain;
	private boolean done = true;
	private final List<PipeBlockEntity> outlets = new ArrayList<>();
	private long outletsScanned = -100;
	private int status = ST_NO_CAR;
	private double carAmount;
	private SfFluid carFluid = SfFluid.NONE;
	private int carFillPct = -1;                       // -1: no car docked

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case PlatformScreenHandler.P_UNLOAD -> unload ? 1 : 0;
				case PlatformScreenHandler.P_STATUS -> status;
				case PlatformScreenHandler.P_TANK -> (int) Math.round(amount * 10);
				case PlatformScreenHandler.P_FLUID -> fluid.ordinal();
				case PlatformScreenHandler.P_CAR_PCT -> carFillPct;
				case PlatformScreenHandler.P_CAR_AMOUNT -> (int) Math.round(carAmount * 10);
				case PlatformScreenHandler.P_CAR_FLUID -> carFluid.ordinal();
				case PlatformScreenHandler.P_POWERED -> powered ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return PlatformScreenHandler.COUNT;
		}
	};

	public RailBuildingBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RAIL_BUILDING, pos, state);
	}

	public RailBuildingBlock.Kind getKind() {
		return getCachedState().getBlock() instanceof RailBuildingBlock b ? b.getKind() : RailBuildingBlock.Kind.EMPTY;
	}

	// ------------------------------------------------------------------ power node

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public double getMaxPowerDemand() {
		return getKind().powerMW;
	}

	@Override
	public double getPowerDemand() {
		double base = getKind().powerMW;
		if (world instanceof ServerWorld sw) base += RailWorld.get(sw).tapExtra(pos.asLong());
		return base;
	}

	@Override
	public Vec3d getConnectorOffset() {
		return Multiblocks.connectorOffset(getCachedState());
	}

	// ------------------------------------------------------------------ station

	public String getStationName() {
		return stationName;
	}

	public void setStationName(String name) {
		this.stationName = name;
		if (world instanceof ServerWorld sw && getKind() == RailBuildingBlock.Kind.STATION) {
			RailWorld.get(sw).registerStation(pos.asLong(), name, trackId);
		}
		sync();
	}

	public void setTrack(long id) {
		this.trackId = id;
		if (world instanceof ServerWorld sw && getKind() == RailBuildingBlock.Kind.STATION && !stationName.isEmpty()) {
			RailWorld.get(sw).registerStation(pos.asLong(), stationName, trackId);
		}
		markDirty();
	}

	// ------------------------------------------------------------------ geometry helpers

	private V3 platformCenter() {
		BlockState state = getCachedState();
		Footprint fp = Multiblocks.get(state.getBlock());
		if (fp == null) return new V3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		Vec3d c = fp.localPoint(state.get(MachineBlock.FACING), fp.ax() + 0.5, 0.35, fp.depth() / 2.0);
		return new V3(pos.getX() + c.x, pos.getY() + c.y, pos.getZ() + c.z);
	}

	/** Positions just outside the side walls where belts / chests / hoppers can take items (every row, ground level and one up). */
	private List<BlockPos> sidePorts() {
		BlockState state = getCachedState();
		Footprint fp = Multiblocks.get(state.getBlock());
		Direction f = state.get(MachineBlock.FACING);
		List<BlockPos> out = new ArrayList<>();
		if (fp == null) return out;
		for (int row = 0; row < fp.depth(); row++) {
			for (int y = 0; y <= 1; y++) {
				out.add(fp.toWorld(pos, f, -1, y, row));
				out.add(fp.toWorld(pos, f, fp.width(), y, row));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ interaction

	/** Load (false) or unload (true) the docked car; set from the platform GUI. */
	public void setUnload(boolean value) {
		if (unload == value) return;
		unload = value;
		markDirty();
	}

	public boolean isUnloading() {
		return unload;
	}

	public double getFluidAmount() {
		return amount;
	}

	public SfFluid getFluid() {
		return fluid;
	}

	/** True while a train is docked here and the transfer is not finished. */
	public boolean isBusyWith(UUID train) {
		return dockedTrain != null && dockedTrain.equals(train) && !done;
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(World world, BlockPos pos, BlockState state, RailBuildingBlockEntity be) {
		if (!(world instanceof ServerWorld sw)) return;
		be.tickPower();
		RailWorld rw = RailWorld.get(sw);
		long now = world.getTime();
		if (be.trackId < 0 || !rw.graph.tracks.containsKey(be.trackId)) be.trackId = be.findOwnTrack(rw);
		rw.reportTap(pos.asLong(), be.trackId, be.powered, now);
		RailBuildingBlock.Kind kind = be.getKind();
		if (kind == RailBuildingBlock.Kind.FREIGHT || kind == RailBuildingBlock.Kind.FLUID) {
			if (now % 20 == 0) rw.registerPlatform(pos.asLong(), be);
			be.tickPlatform(sw, rw, kind, now);
		} else if (kind == RailBuildingBlock.Kind.STATION && now % 100 == 0 && !be.stationName.isEmpty()) {
			rw.registerStation(pos.asLong(), be.stationName, be.trackId);
		}
		boolean active = be.dockedTrain != null && !be.done;
		if (state.contains(MachineBlock.ACTIVE) && state.get(MachineBlock.ACTIVE) != active) {
			world.setBlockState(pos, state.with(MachineBlock.ACTIVE, active), 3);
		}
	}

	private long findOwnTrack(RailWorld rw) {
		long owner = pos.asLong();
		for (RailTrack t : rw.graph.tracks.values()) if (t.owner == owner) return t.id;
		return -1;
	}

	private void tickPlatform(ServerWorld sw, RailWorld rw, RailBuildingBlock.Kind kind, long now) {
		VehicleType wanted = kind == RailBuildingBlock.Kind.FREIGHT ? VehicleType.FREIGHT_CAR : VehicleType.FLUID_CAR;
		RailWorld.Docked d = powered ? rw.dockedNear(platformCenter(), 4.7, wanted) : null;
		if (d == null) {
			dockedTrain = null;
			done = true;
			status = powered ? ST_NO_CAR : ST_NO_POWER;
			carFillPct = -1;
			carAmount = 0;
			carFluid = SfFluid.NONE;
		} else {
			dockedTrain = d.train().id;
			VehicleCargo cargo = VehicleCargo.ensure(d.vehicle());
			status = kind == RailBuildingBlock.Kind.FREIGHT ? transferItems(cargo) : transferFluid(cargo);
			done = status == ST_DONE;
			carFillPct = (int) Math.round(cargo.fill(wanted) * 100);
			carAmount = cargo.amount;
			carFluid = cargo.fluid;
			if (status == ST_WORKING) d.train().dirty = true;
		}
		if (kind == RailBuildingBlock.Kind.FREIGHT) {
			if (unload && now % 2 == 0) pushItemsOut(sw);
		} else {
			pushFluidOut(sw, now);
		}
	}

	// ---- items

	/** Moves items between the platform and the car and returns the resulting ST_* state. */
	private int transferItems(VehicleCargo car) {
		int budget = ITEM_RATE;
		int movedTotal = 0;
		DefaultedList<ItemStack> from = unload ? car.items : items;
		DefaultedList<ItemStack> to = unload ? items : car.items;
		for (int i = 0; i < from.size() && budget > 0; i++) {
			ItemStack st = from.get(i);
			if (st.isEmpty()) continue;
			int moved = insert(to, st, budget);
			if (moved > 0) {
				st.decrement(moved);
				budget -= moved;
				movedTotal += moved;
				markDirty();
			}
		}
		if (unload ? car.itemsEmpty() : car.itemsFull()) return ST_DONE;
		if (movedTotal > 0) return ST_WORKING;
		// nothing moved: load mode has run out of cargo on the platform, unload mode has no room left on it
		return unload ? ST_PLATFORM_FULL : ST_WAIT_INPUT;
	}

	private static int insert(DefaultedList<ItemStack> list, ItemStack stack, int max) {
		int left = Math.min(max, stack.getCount());
		int moved = 0;
		for (int i = 0; i < list.size() && left > 0; i++) {
			ItemStack t = list.get(i);
			if (!t.isEmpty() && ItemStack.areItemsAndComponentsEqual(t, stack) && t.getCount() < t.getMaxCount()) {
				int n = Math.min(left, t.getMaxCount() - t.getCount());
				t.increment(n);
				left -= n;
				moved += n;
			}
		}
		for (int i = 0; i < list.size() && left > 0; i++) {
			if (list.get(i).isEmpty()) {
				int n = Math.min(left, stack.getMaxCount());
				list.set(i, stack.copyWithCount(n));
				left -= n;
				moved += n;
			}
		}
		return moved;
	}

	private void pushItemsOut(ServerWorld sw) {
		for (BlockPos target : sidePorts()) {
			ItemStack first = ItemStack.EMPTY;
			int slot = -1;
			for (int i = 0; i < items.size(); i++) {
				if (!items.get(i).isEmpty()) {
					first = items.get(i);
					slot = i;
					break;
				}
			}
			if (slot < 0) return;
			Direction dir = directionTo(target);
			if (dir == null) continue;
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(sw, target, dir.getOpposite());
			if (storage == null) continue;
			try (Transaction tx = Transaction.openOuter()) {
				long inserted = storage.insert(ItemVariant.of(first), 1, tx);
				if (inserted > 0) {
					tx.commit();
					first.decrement(1);
					markDirty();
				}
			}
		}
	}

	/** Horizontal direction from the nearest platform cell to a port position outside the wall. */
	@Nullable
	private Direction directionTo(BlockPos target) {
		BlockState state = getCachedState();
		Footprint fp = Multiblocks.get(state.getBlock());
		Direction f = state.get(MachineBlock.FACING);
		if (fp == null) return null;
		BlockPos leftCell = fp.toWorld(pos, f, -1, 0, 0);
		BlockPos rightCell = fp.toWorld(pos, f, fp.width(), 0, 0);
		// decide by which side the target lies on
		Direction left = f.rotateYCounterclockwise();
		Direction right = f.rotateYClockwise();
		double dl = Math.abs(target.getX() - leftCell.getX()) * Math.abs(left.getOffsetX()) + Math.abs(target.getZ() - leftCell.getZ()) * Math.abs(left.getOffsetZ());
		double dr = Math.abs(target.getX() - rightCell.getX()) * Math.abs(right.getOffsetX()) + Math.abs(target.getZ() - rightCell.getZ()) * Math.abs(right.getOffsetZ());
		return dl <= dr ? left : right;
	}

	// ---- fluids

	private int transferFluid(VehicleCargo car) {
		if (unload) {
			// car -> tank
			if (car.amount <= 1e-6) return ST_DONE;
			if (amount > 1e-6 && fluid != car.fluid) return ST_MISMATCH;
			double q = Math.min(FLUID_RATE, Math.min(car.amount, TANK - amount));
			if (q <= 1e-9) return ST_PLATFORM_FULL;
			if (amount <= 1e-6) fluid = car.fluid;
			amount += q;
			car.amount -= q;
			if (car.amount < 1e-6) {
				car.amount = 0;
				car.fluid = SfFluid.NONE;
			}
			markDirty();
			return car.amount <= 1e-6 ? ST_DONE : ST_WORKING;
		}
		// tank -> car
		if (car.amount >= VehicleCargo.FLUID_CAPACITY - 1e-6) return ST_DONE;
		if (amount <= 1e-6) return ST_WAIT_INPUT;
		if (car.amount > 1e-6 && car.fluid != fluid) return ST_MISMATCH;
		double q = Math.min(FLUID_RATE, Math.min(amount, VehicleCargo.FLUID_CAPACITY - car.amount));
		if (car.amount <= 1e-6) car.fluid = fluid;
		car.amount += q;
		amount -= q;
		if (amount < 1e-6) {
			amount = 0;
			fluid = SfFluid.NONE;
		}
		markDirty();
		return car.amount >= VehicleCargo.FLUID_CAPACITY - 1e-6 ? ST_DONE : ST_WORKING;
	}

	private void scanOutlets(World world) {
		outlets.clear();
		BlockState state = getCachedState();
		Footprint fp = Multiblocks.get(state.getBlock());
		List<BlockPos> cells = fp == null ? List.of(pos) : fp.positions(pos, state.get(MachineBlock.FACING));
		Set<BlockPos> own = new HashSet<>(cells);
		for (BlockPos c : cells) {
			for (Direction d : Direction.values()) {
				BlockPos n = c.offset(d);
				if (own.contains(n)) continue;
				if (world.getBlockEntity(n) instanceof PipeBlockEntity pipe && pipe.connectsTo(d.getOpposite())) outlets.add(pipe);
			}
		}
	}

	private void pushFluidOut(ServerWorld sw, long now) {
		if (now - outletsScanned >= 20) {
			scanOutlets(sw);
			outletsScanned = now;
		}
		if (!unload || amount <= 1e-6 || !powered) return;
		int remaining = outlets.size();
		for (PipeBlockEntity pipe : outlets) {
			if (pipe.isRemoved()) {
				remaining--;
				continue;
			}
			pipe.applyBoost(pos.getY() + 8.0);
			if (pipe.getAmount() > 1e-6 && pipe.getFluid() != fluid) {
				remaining--;
				continue;
			}
			double q = Math.min(amount / Math.max(1, remaining), Math.min(pipe.getMaxFlow(), pipe.getCapacity() - pipe.getAmount()));
			if (q > 1e-9) {
				if (pipe.getAmount() <= 1e-6) pipe.setFluid(fluid);
				pipe.setAmount(pipe.getAmount() + q);
				amount -= q;
			}
			remaining--;
		}
		if (amount < 1e-6) {
			amount = 0;
			fluid = SfFluid.NONE;
		}
	}

	@Override
	public boolean acceptsPipes() {
		return getKind() == RailBuildingBlock.Kind.FLUID;
	}

	@Override
	public double offerFluid(SfFluid f, double offered) {
		if (getKind() != RailBuildingBlock.Kind.FLUID || unload || f == SfFluid.NONE) return 0;
		if (amount > 1e-6 && fluid != f) return 0;
		double accepted = Math.min(offered, TANK - amount);
		if (accepted <= 0) return 0;
		if (amount <= 1e-6) fluid = f;
		amount += accepted;
		markDirty();
		return accepted;
	}

	// ------------------------------------------------------------------ inventory (freight platform)

	@Override
	public int size() {
		return getKind() == RailBuildingBlock.Kind.FREIGHT ? SLOTS : 0;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack s : items) if (!s.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int count) {
		ItemStack r = Inventories.splitStack(items, slot, count);
		if (!r.isEmpty()) markDirty();
		return r;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.capCount(getMaxCount(stack));
		markDirty();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return !unload;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player, 10.0f);
	}

	@Override
	public void clear() {
		items.clear();
	}

	// ------------------------------------------------------------------ nbt / menu

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putString("StationName", stationName);
		nbt.putLong("Track", trackId);
		nbt.putBoolean("Unload", unload);
		nbt.putString("Fluid", fluid.id);
		nbt.putDouble("Amount", amount);
		Inventories.writeNbt(nbt, items, lookup);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		stationName = nbt.getString("StationName");
		trackId = nbt.contains("Track") ? nbt.getLong("Track") : -1;
		unload = nbt.getBoolean("Unload");
		fluid = SfFluid.byId(nbt.getString("Fluid"));
		amount = nbt.getDouble("Amount");
		items.clear();
		Inventories.readNbt(nbt, items, lookup);
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
	public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
		return new PlatformScreenHandler(syncId, inv, this, properties);
	}
}
