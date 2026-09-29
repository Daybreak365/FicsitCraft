package com.ficsitcraft.blockentity;

import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** A power node with a simple slot inventory (exposed to hoppers/belts through the Fabric Transfer API fallback). */
public abstract class InventoryPowerBlockEntity extends PowerNodeBlockEntity implements SidedInventory {
	protected final DefaultedList<ItemStack> items;

	protected InventoryPowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
		super(type, pos, state);
		this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
	}

	@Override
	public int size() {
		return items.size();
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
	public ItemStack removeStack(int slot, int amount) {
		ItemStack r = Inventories.splitStack(items, slot, amount);
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
		if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player, 10.0f); // large buildings
	}

	@Override
	public net.minecraft.util.math.Vec3d getConnectorOffset() {
		return com.ficsitcraft.multiblock.Multiblocks.connectorOffset(getCachedState());
	}

	@Override
	public void clear() {
		items.clear();
		markDirty();
	}

	/** Pushes items from the given slot into the inventory/belt in front of the machine. */
	protected void pushOutput(int slot, Direction dir) {
		if (world == null) return;
		ItemStack out = items.get(slot);
		if (out.isEmpty()) return;
		BlockPos targetPos = com.ficsitcraft.multiblock.Multiblocks.outputTarget(getCachedState(), pos, dir);
		Storage<ItemVariant> target = ItemStorage.SIDED.find(world, targetPos, dir.getOpposite());
		if (target == null) return;
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = target.insert(ItemVariant.of(out), out.getCount(), tx);
			if (inserted > 0) {
				tx.commit();
				out.decrement((int) inserted);
				markDirty();
			}
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		Inventories.writeNbt(nbt, items, lookup);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		items.clear();
		Inventories.readNbt(nbt, items, lookup);
	}
}
