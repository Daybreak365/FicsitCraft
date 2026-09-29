package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.DisplayBlockEntity;
import com.ficsitcraft.screen.CraftBenchScreenHandler;
import com.ficsitcraft.screen.HubScreenHandler;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** The HUB and the Craft Bench: multi-block buildings that open a GUI. The terminal faces the player (back side). */
public class FacingGuiBlock extends MachineBlock {
	public enum Kind {HUB, CRAFT_BENCH}

	private final Kind kind;

	public FacingGuiBlock(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new FacingGuiBlock(kind, s));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DisplayBlockEntity(pos, state);
	}

	@Override
	public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		ScreenHandlerContext ctx = ScreenHandlerContext.create(world, pos);
		return switch (kind) {
			case HUB -> new SimpleNamedScreenHandlerFactory((syncId, inv, player) -> new HubScreenHandler(syncId, inv, ctx),
					Text.translatable("gui.ficsitcraft.hub"));
			case CRAFT_BENCH -> new SimpleNamedScreenHandlerFactory((syncId, inv, player) -> new CraftBenchScreenHandler(syncId, inv, ctx),
					Text.translatable("gui.ficsitcraft.craft_bench"));
		};
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
		return ActionResult.CONSUME;
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return false;
	}
}
