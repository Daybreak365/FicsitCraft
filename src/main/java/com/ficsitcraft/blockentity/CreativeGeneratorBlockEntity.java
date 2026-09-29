package com.ficsitcraft.blockentity;

import com.ficsitcraft.power.GridStats;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.CreativeGeneratorScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Fuel-free power source for testing (see {@link com.ficsitcraft.block.CreativeGeneratorBlock}). */
public class CreativeGeneratorBlockEntity extends PowerNodeBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final double MW = 3000;

	private boolean enabled = true;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			GridStats s = getGridStats();
			return switch (index) {
				case CreativeGeneratorScreenHandler.P_ENABLED -> enabled ? 1 : 0;
				case CreativeGeneratorScreenHandler.P_CAPACITY -> (int) Math.round(Math.min(3200, s.capacity()) * 10);
				case CreativeGeneratorScreenHandler.P_DEMAND -> (int) Math.round(Math.min(3200, s.demand()) * 10);
				case CreativeGeneratorScreenHandler.P_FUSE -> s.fuseBlown() ? 1 : 0;
				case CreativeGeneratorScreenHandler.P_GENERATORS -> s.generators();
				case CreativeGeneratorScreenHandler.P_CONSUMERS -> s.consumers();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return CreativeGeneratorScreenHandler.COUNT;
		}
	};

	public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CREATIVE_GENERATOR, pos, state);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		markDirty();
	}

	@Override
	public int getMaxConnections() {
		return 4;
	}

	@Override
	public boolean isGenerator() {
		return true;
	}

	@Override
	public double getPowerCapacity() {
		return enabled ? MW : 0;
	}

	@Override
	public Vec3d getConnectorOffset() {
		return com.ficsitcraft.multiblock.Multiblocks.connectorOffset(getCachedState());
	}

	public static void tick(World world, BlockPos pos, BlockState state, CreativeGeneratorBlockEntity be) {
		be.tickPower();
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putBoolean("Enabled", enabled);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
		return new CreativeGeneratorScreenHandler(syncId, inv, this, properties);
	}
}
