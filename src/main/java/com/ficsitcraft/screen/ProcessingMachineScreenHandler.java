package com.ficsitcraft.screen;

import com.ficsitcraft.blockentity.ProcessingMachineBlockEntity;
import com.ficsitcraft.data.MachineType;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
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
import org.jetbrains.annotations.Nullable;

public class ProcessingMachineScreenHandler extends ScreenHandler {
	public static final int BUTTON_PREV = 0;
	public static final int BUTTON_NEXT = 1;
	public static final int BUTTON_CLEAR = 2;

	private final Inventory inventory;
	private final PropertyDelegate props;
	@Nullable
	private final ProcessingMachineBlockEntity machine;
	private final BlockPos pos;

	/** Client constructor. */
	public ProcessingMachineScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
		this(syncId, playerInv, null, new SimpleInventory(ProcessingMachineBlockEntity.SIZE), new ArrayPropertyDelegate(5), pos);
	}

	/** Server constructor. */
	public ProcessingMachineScreenHandler(int syncId, PlayerInventory playerInv, ProcessingMachineBlockEntity be, PropertyDelegate props) {
		this(syncId, playerInv, be, be, props, be.getPos());
	}

	private ProcessingMachineScreenHandler(int syncId, PlayerInventory playerInv, @Nullable ProcessingMachineBlockEntity be,
										   Inventory inventory, PropertyDelegate props, BlockPos pos) {
		super(ModScreenHandlers.PROCESSING_MACHINE, syncId);
		this.inventory = inventory;
		this.props = props;
		this.machine = be;
		this.pos = pos;
		checkSize(inventory, ProcessingMachineBlockEntity.SIZE);
		inventory.onOpen(playerInv.player);

		for (int i = 0; i < ProcessingMachineBlockEntity.INPUTS; i++) {
			final int slot = i;
			addSlot(new MachineSlots.FilteredSlot(inventory, i, 20 + i * 18, 44,
					stack -> {
						SfRecipe r = getRecipe();
						return r != null && slot < r.inputs().size() && r.inputs().get(slot).ing().test(stack);
					},
					() -> {
						SfRecipe r = getRecipe();
						return r != null && slot < r.inputs().size();
					}));
		}
		addSlot(new MachineSlots.OutputSlot(inventory, ProcessingMachineBlockEntity.OUTPUT, 134, 44));
		MachineSlots.addPlayerInventory(this::addSlot, playerInv, 8, 104);
		addProperties(props);
	}

	public BlockPos getPos() {
		return pos;
	}

	@Nullable
	public SfRecipe getRecipe() {
		return Recipes.get(props.get(1) - 1);
	}

	public int getProgress() {
		return props.get(0);
	}

	public int getStatus() {
		return props.get(2);
	}

	public double getGridCapacity() {
		return props.get(3) / 10.0;
	}

	public double getGridDemand() {
		return props.get(4) / 10.0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (machine == null) return false;
		switch (id) {
			case BUTTON_PREV -> machine.cycleRecipe(player, -1);
			case BUTTON_NEXT -> machine.cycleRecipe(player, 1);
			case BUTTON_CLEAR -> machine.cycleRecipe(player, 0);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return MachineSlots.quickMove(this, player, ProcessingMachineBlockEntity.SIZE, index, this::insertItem);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return inventory.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		inventory.onClose(player);
	}
}
