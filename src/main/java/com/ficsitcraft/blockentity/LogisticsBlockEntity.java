package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.LogisticsBlock;
import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LogisticsBlockEntity extends BlockEntity implements MachineBlock.DropsContents {
	private int roundRobin;

	private final SingleVariantStorage<ItemVariant> buffer = new SingleVariantStorage<>() {
		@Override
		protected ItemVariant getBlankVariant() {
			return ItemVariant.blank();
		}

		@Override
		protected long getCapacity(ItemVariant variant) {
			return 1;
		}

		@Override
		protected boolean canExtract(ItemVariant variant) {
			return false;
		}

		@Override
		protected void onFinalCommit() {
			markDirty();
		}
	};

	public LogisticsBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LOGISTICS, pos, state);
	}

	private boolean isSplitter() {
		return getCachedState().getBlock() instanceof LogisticsBlock l && l.isSplitter();
	}

	private Direction facing() {
		return getCachedState().get(MachineBlock.FACING);
	}

	@Nullable
	public Storage<ItemVariant> getStorage(@Nullable Direction side) {
		if (side == null) return buffer;
		Direction f = facing();
		if (isSplitter()) return side == f.getOpposite() ? buffer : null;
		return side != f && side.getAxis() != Direction.Axis.Y ? buffer : null;
	}

	public static void tick(World world, BlockPos pos, BlockState state, LogisticsBlockEntity be) {
		if (be.buffer.isResourceBlank() || be.buffer.amount <= 0) return;
		Direction f = state.get(MachineBlock.FACING);
		Direction[] outs = be.isSplitter()
				? new Direction[]{f.rotateYCounterclockwise(), f, f.rotateYClockwise()}
				: new Direction[]{f};
		for (int k = 0; k < outs.length; k++) {
			int idx = (be.roundRobin + k) % outs.length;
			Direction d = outs[idx];
			Storage<ItemVariant> target = ItemStorage.SIDED.find(world, pos.offset(d), d.getOpposite());
			if (target == null) continue;
			try (Transaction tx = Transaction.openOuter()) {
				long moved = target.insert(be.buffer.variant, be.buffer.amount, tx);
				if (moved > 0) {
					tx.commit();
					be.buffer.amount -= moved;
					if (be.buffer.amount <= 0) be.buffer.variant = ItemVariant.blank();
					be.roundRobin = (idx + 1) % outs.length;
					be.markDirty();
					return;
				}
			}
		}
	}

	@Override
	public void dropContents(World world, BlockPos pos) {
		if (!buffer.isResourceBlank() && buffer.amount > 0) {
			ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, buffer.variant.toStack((int) buffer.amount));
			buffer.variant = ItemVariant.blank();
			buffer.amount = 0;
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		SingleVariantStorage.writeNbt(buffer, ItemVariant.CODEC, nbt, lookup);
		nbt.putInt("RR", roundRobin);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		SingleVariantStorage.readNbt(buffer, ItemVariant.CODEC, ItemVariant::blank, nbt, lookup);
		roundRobin = nbt.getInt("RR");
	}
}
