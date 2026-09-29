package com.ficsitcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * An infinite resource node. Pioneers can hand-mine it (right click) and place a Miner on top of it.
 */
public class ResourceNodeBlock extends Block {
	public static final EnumProperty<Purity> PURITY = EnumProperty.of("purity", Purity.class);
	private static final Map<UUID, Long> LAST_MINE = new WeakHashMap<>();

	private final NodeType type;

	public ResourceNodeBlock(NodeType type, Settings settings) {
		super(settings);
		this.type = type;
		setDefaultState(getStateManager().getDefaultState().with(PURITY, Purity.NORMAL));
	}

	public NodeType getType() {
		return type;
	}

	@Override
	public MapCodec<? extends Block> getCodec() {
		return createCodec(s -> new ResourceNodeBlock(type, s));
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(PURITY);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		long now = world.getTime();
		Long last = LAST_MINE.get(player.getUuid());
		if (last != null && now - last < 10) return ActionResult.CONSUME;
		LAST_MINE.put(player.getUuid(), now);

		Purity purity = state.get(PURITY);
		Item item = type.resource();
		player.getInventory().offerOrDrop(new ItemStack(item, purity.handYield));
		world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, 0.8f + world.random.nextFloat() * 0.4f);
		player.sendMessage(Text.translatable("message.ficsitcraft.node_info",
				Text.translatable("node.ficsitcraft." + type.id),
				Text.translatable("purity.ficsitcraft." + purity.asString())).formatted(Formatting.GRAY), true);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.node").formatted(Formatting.GRAY));
	}
}
