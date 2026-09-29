package com.ficsitcraft.screen;

import com.ficsitcraft.blockentity.GeneratorBlockEntity;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class GeneratorScreenHandler extends ScreenHandler {
	public static final int BUTTON_RESET_FUSE = 0;

	private final Inventory inventory;
	private final PropertyDelegate props;
	@Nullable
	private final GeneratorBlockEntity generator;
	private final BlockPos pos;

	public GeneratorScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
		this(syncId, playerInv, null, new SimpleInventory(1), new ArrayPropertyDelegate(6), pos);
	}

	public GeneratorScreenHandler(int syncId, PlayerInventory playerInv, GeneratorBlockEntity be, PropertyDelegate props) {
		this(syncId, playerInv, be, be, props, be.getPos());
	}

	private GeneratorScreenHandler(int syncId, PlayerInventory playerInv, @Nullable GeneratorBlockEntity be, Inventory inventory,
								   PropertyDelegate props, BlockPos pos) {
		super(ModScreenHandlers.GENERATOR, syncId);
		this.inventory = inventory;
		this.props = props;
		this.generator = be;
		this.pos = pos;
		checkSize(inventory, 1);
		addSlot(new Slot(inventory, 0, 26, 44) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return isCoal() ? GeneratorBlockEntity.fuelValue(com.ficsitcraft.block.GeneratorBlock.Kind.COAL, stack) > 0
						: GeneratorBlockEntity.fuelValue(com.ficsitcraft.block.GeneratorBlock.Kind.BIOMASS, stack) > 0;
			}
		});
		MachineSlots.addPlayerInventory(this::addSlot, playerInv, 8, 104);
		addProperties(props);
	}

	public BlockPos getPos() {
		return pos;
	}

	public double getBurnFraction() {
		return props.get(0) / 1000.0;
	}

	public double getGridCapacity() {
		return props.get(1) / 10.0;
	}

	public double getGridDemand() {
		return props.get(2) / 10.0;
	}

	public int getFlags() {
		return props.get(3);
	}

	public boolean isCoal() {
		return (getFlags() & GeneratorBlockEntity.FLAG_COAL) != 0;
	}

	public double getWaterFraction() {
		return props.get(5) / 1000.0;
	}

	public double getLoad() {
		return props.get(4) / 1000.0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == BUTTON_RESET_FUSE && generator != null) {
			generator.requestFuseReset();
			return true;
		}
		return false;
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
