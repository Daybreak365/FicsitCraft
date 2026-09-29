package com.ficsitcraft.screen;

import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class MinerScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final PropertyDelegate props;
	private final BlockPos pos;

	public MinerScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
		this(syncId, playerInv, new SimpleInventory(1), new ArrayPropertyDelegate(5), pos);
	}

	public MinerScreenHandler(int syncId, PlayerInventory playerInv, com.ficsitcraft.blockentity.MinerBlockEntity be, PropertyDelegate props) {
		this(syncId, playerInv, be, props, be.getPos());
	}

	private MinerScreenHandler(int syncId, PlayerInventory playerInv, Inventory inventory, PropertyDelegate props, BlockPos pos) {
		super(ModScreenHandlers.MINER, syncId);
		this.inventory = inventory;
		this.props = props;
		this.pos = pos;
		checkSize(inventory, 1);
		addSlot(new MachineSlots.OutputSlot(inventory, 0, 134, 44));
		MachineSlots.addPlayerInventory(this::addSlot, playerInv, 8, 104);
		addProperties(props);
	}

	public BlockPos getPos() {
		return pos;
	}

	public double getProgress() {
		return props.get(0) / 1000.0;
	}

	public int getStatus() {
		return props.get(1);
	}

	/** -1 when there is no node below. */
	public int getNodeType() {
		return (props.get(2) & 0xFF) - 1;
	}

	public int getPurity() {
		return (props.get(2) >> 8) & 0xFF;
	}

	public double getGridCapacity() {
		return props.get(3) / 10.0;
	}

	public double getGridDemand() {
		return props.get(4) / 10.0;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return MachineSlots.quickMove(this, player, 1, index, this::insertItem);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return inventory.canPlayerUse(player);
	}
}
