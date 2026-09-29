package com.ficsitcraft.railway;

import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;

/** What a freight car (36 item slots) or a fluid freight car (tank) carries. */
public final class VehicleCargo {
	public static final int SLOTS = 36;
	public static final double FLUID_CAPACITY = 800;

	public final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
	public SfFluid fluid = SfFluid.NONE;
	public double amount;

	public static VehicleCargo of(Vehicle v) {
		return v.cargo instanceof VehicleCargo c ? c : null;
	}

	public static VehicleCargo ensure(Vehicle v) {
		if (v.type == VehicleType.LOCOMOTIVE) return null;
		if (!(v.cargo instanceof VehicleCargo)) v.cargo = new VehicleCargo();
		return (VehicleCargo) v.cargo;
	}

	/** 0..1 fill for the mass model / platform completion checks. */
	public double fill(VehicleType type) {
		if (type == VehicleType.FLUID_CAR) return Math.min(1, amount / FLUID_CAPACITY);
		double used = 0;
		for (ItemStack s : items) if (!s.isEmpty()) used += s.getCount() / (double) Math.max(1, s.getMaxCount());
		return Math.min(1, used / SLOTS);
	}

	public boolean itemsEmpty() {
		for (ItemStack s : items) if (!s.isEmpty()) return false;
		return true;
	}

	public boolean itemsFull() {
		for (ItemStack s : items) if (s.isEmpty() || s.getCount() < s.getMaxCount()) return false;
		return true;
	}

	public void write(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		Inventories.writeNbt(nbt, items, lookup);
		nbt.putString("Fluid", fluid.id);
		nbt.putDouble("Amount", amount);
	}

	public static VehicleCargo read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		VehicleCargo c = new VehicleCargo();
		Inventories.readNbt(nbt, c.items, lookup);
		c.fluid = SfFluid.byId(nbt.getString("Fluid"));
		c.amount = nbt.getDouble("Amount");
		return c;
	}

	/** Inventory view for the container screen. */
	public static final class View implements Inventory {
		private final VehicleCargo cargo;
		private final java.util.function.BooleanSupplier valid;
		private final Runnable dirty;

		public View(VehicleCargo cargo, java.util.function.BooleanSupplier valid, Runnable dirty) {
			this.cargo = cargo;
			this.valid = valid;
			this.dirty = dirty;
		}

		@Override
		public int size() {
			return SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return cargo.itemsEmpty();
		}

		@Override
		public ItemStack getStack(int slot) {
			return cargo.items.get(slot);
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			ItemStack r = Inventories.splitStack(cargo.items, slot, amount);
			if (!r.isEmpty()) markDirty();
			return r;
		}

		@Override
		public ItemStack removeStack(int slot) {
			return Inventories.removeStack(cargo.items, slot);
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			cargo.items.set(slot, stack);
			stack.capCount(getMaxCount(stack));
			markDirty();
		}

		@Override
		public void markDirty() {
			dirty.run();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return valid.getAsBoolean();
		}

		@Override
		public void clear() {
			cargo.items.clear();
		}
	}
}
