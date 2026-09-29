package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.BeltShape;
import com.ficsitcraft.block.ConveyorBeltBlock;
import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A conveyor belt segment. Items travel along the belt with fixed spacing (4 items per block), so the
 * throughput equals the Satisfactory belt speed (60 / 120 / 270 items per minute).
 */
public class ConveyorBeltBlockEntity extends BlockEntity implements MachineBlock.DropsContents {
	public static final int MAX_ITEMS = 4;
	public static final float SPACING = 1.0f / MAX_ITEMS;

	public static final class BeltItem {
		public ItemStack stack;
		public float progress;
		public float prevProgress;

		BeltItem(ItemStack stack, float progress) {
			this.stack = stack;
			this.progress = progress;
			this.prevProgress = progress;
		}
	}

	/** Front-most item first. */
	private final List<BeltItem> items = new ArrayList<>();

	private final SingleVariantStorage<ItemVariant> intake = new SingleVariantStorage<>() {
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
		protected boolean canInsert(ItemVariant variant) {
			return items.size() < MAX_ITEMS;
		}

		@Override
		protected void onFinalCommit() {
			markDirty();
		}
	};

	public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONVEYOR_BELT, pos, state);
	}

	public Direction getFacing() {
		return getCachedState().get(ConveyorBeltBlock.FACING);
	}

	public float getSpeedPerTick() {
		int ipm = getCachedState().getBlock() instanceof ConveyorBeltBlock b ? b.itemsPerMinute() : 60;
		// items per second * spacing = blocks per second
		return ipm / 60f * SPACING / 20f;
	}

	/** Transfer API access: items can be inserted from any side except the front. */
	@Nullable
	public Storage<ItemVariant> getStorage(@Nullable Direction side) {
		if (side != null && side == getFacing()) return null;
		return intake;
	}

	public List<BeltItem> getItems() {
		return items;
	}

	// ------------------------------------------------------------------ ticking

	private void move() {
		float speed = getSpeedPerTick();
		float limit = 1.0f;
		for (BeltItem it : items) {
			it.prevProgress = it.progress;
			float target = Math.min(it.progress + speed, limit);
			if (target > it.progress) it.progress = target;
			limit = it.progress - SPACING;
		}
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, ConveyorBeltBlockEntity be) {
		be.move();
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, ConveyorBeltBlockEntity be) {
		be.move();
		boolean changed = false;

		// hand the front item over to whatever is in front
		if (!be.items.isEmpty() && be.items.get(0).progress >= 1.0f) {
			Direction dir = be.getFacing();
			Storage<ItemVariant> target = be.findTarget(world, pos, state, dir);
			if (target != null) {
				BeltItem front = be.items.get(0);
				try (Transaction tx = Transaction.openOuter()) {
					long inserted = target.insert(ItemVariant.of(front.stack), front.stack.getCount(), tx);
					if (inserted > 0) {
						tx.commit();
						front.stack.decrement((int) inserted);
						if (front.stack.isEmpty()) be.items.remove(0);
						changed = true;
					}
				}
			}
		}

		// move the intake buffer onto the belt
		if (!be.intake.isResourceBlank() && be.intake.amount > 0 && be.canAcceptNew()) {
			be.items.add(new BeltItem(be.intake.variant.toStack((int) be.intake.amount), 0f));
			be.intake.variant = ItemVariant.blank();
			be.intake.amount = 0;
			changed = true;
		}

		if (changed) be.sync();
	}

	/** Where the front item goes: straight ahead, one block up for ramps, or down onto a descending ramp. */
	@Nullable
	private Storage<ItemVariant> findTarget(World world, BlockPos pos, BlockState state, Direction dir) {
		BlockPos front = pos.offset(dir);
		if (dir.getAxis() != Direction.Axis.Y && state.get(ConveyorBeltBlock.SHAPE) == BeltShape.ASCENDING) {
			return ItemStorage.SIDED.find(world, front.up(), dir.getOpposite());
		}
		Storage<ItemVariant> target = ItemStorage.SIDED.find(world, front, dir.getOpposite());
		if (target == null && dir.getAxis() != Direction.Axis.Y) {
			BlockState below = world.getBlockState(front.down());
			if (below.getBlock() instanceof ConveyorBeltBlock && below.get(ConveyorBeltBlock.FACING) == dir
					&& below.get(ConveyorBeltBlock.SHAPE) == BeltShape.DESCENDING) {
				target = ItemStorage.SIDED.find(world, front.down(), dir.getOpposite());
			}
		}
		return target;
	}

	public BeltShape getShape() {
		return getCachedState().contains(ConveyorBeltBlock.SHAPE) ? getCachedState().get(ConveyorBeltBlock.SHAPE) : BeltShape.STRAIGHT;
	}

	private boolean canAcceptNew() {
		if (items.size() >= MAX_ITEMS) return false;
		return items.isEmpty() || items.get(items.size() - 1).progress >= SPACING;
	}

	/** Dropped item entities landing on the belt are picked up. */
	public void acceptItemEntity(ItemEntity entity) {
		if (entity.isRemoved() || !intake.isResourceBlank()) return;
		ItemStack stack = entity.getStack();
		if (stack.isEmpty() || !canAcceptNew()) return;
		intake.variant = ItemVariant.of(stack);
		intake.amount = 1;
		stack.decrement(1);
		if (stack.isEmpty()) entity.discard();
		else entity.setStack(stack);
		markDirty();
	}

	public void takeAll(PlayerEntity player) {
		for (BeltItem it : items) player.getInventory().offerOrDrop(it.stack);
		items.clear();
		if (!intake.isResourceBlank()) {
			player.getInventory().offerOrDrop(intake.variant.toStack((int) intake.amount));
			intake.variant = ItemVariant.blank();
			intake.amount = 0;
		}
		sync();
	}

	@Override
	public void dropContents(World world, BlockPos pos) {
		for (BeltItem it : items) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, it.stack);
		items.clear();
		if (!intake.isResourceBlank()) {
			ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, intake.variant.toStack((int) intake.amount));
			intake.variant = ItemVariant.blank();
			intake.amount = 0;
		}
	}

	// ------------------------------------------------------------------ sync & nbt

	private void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			BlockState s = getCachedState();
			world.updateListeners(pos, s, s, Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		NbtList list = new NbtList();
		for (BeltItem it : items) {
			if (it.stack.isEmpty()) continue;
			NbtCompound c = new NbtCompound();
			c.put("Item", it.stack.encode(lookup));
			c.putFloat("P", it.progress);
			list.add(c);
		}
		nbt.put("Belt", list);
		SingleVariantStorage.writeNbt(intake, ItemVariant.CODEC, nbt, lookup);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		// keep client-side interpolation smooth when the server sends an update
		List<BeltItem> old = new ArrayList<>(items);
		items.clear();
		NbtList list = nbt.getList("Belt", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			ItemStack stack = ItemStack.fromNbt(lookup, c.get("Item")).orElse(ItemStack.EMPTY);
			if (stack.isEmpty()) continue;
			float p = c.getFloat("P");
			BeltItem bi = new BeltItem(stack, p);
			for (BeltItem o : old) {
				if (ItemStack.areItemsAndComponentsEqual(o.stack, stack) && Math.abs(o.progress - p) < 0.3f) {
					bi.progress = Math.max(p, o.progress);
					bi.prevProgress = o.prevProgress;
					old.remove(o);
					break;
				}
			}
			items.add(bi);
		}
		SingleVariantStorage.readNbt(intake, ItemVariant.CODEC, ItemVariant::blank, nbt, lookup);
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
		return createNbt(lookup);
	}
}
