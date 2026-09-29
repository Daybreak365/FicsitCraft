package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.blockentity.ConveyorBeltBlockEntity;
import com.ficsitcraft.blockentity.DisplayBlockEntity;
import com.ficsitcraft.blockentity.GeneratorBlockEntity;
import com.ficsitcraft.multiblock.MachinePartBlock;
import net.minecraft.util.math.BlockPos;
import com.ficsitcraft.blockentity.LogisticsBlockEntity;
import com.ficsitcraft.blockentity.MinerBlockEntity;
import com.ficsitcraft.blockentity.PowerPoleBlockEntity;
import com.ficsitcraft.blockentity.ProcessingMachineBlockEntity;
import com.ficsitcraft.blockentity.StorageContainerBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static final BlockEntityType<ProcessingMachineBlockEntity> PROCESSING_MACHINE = register("processing_machine",
			ProcessingMachineBlockEntity::new, ModBlocks.SMELTER, ModBlocks.FOUNDRY, ModBlocks.CONSTRUCTOR, ModBlocks.ASSEMBLER, ModBlocks.MANUFACTURER);
	public static final BlockEntityType<MinerBlockEntity> MINER = register("miner",
			MinerBlockEntity::new, ModBlocks.MINER_MK1, ModBlocks.MINER_MK2);
	public static final BlockEntityType<GeneratorBlockEntity> GENERATOR = register("generator",
			GeneratorBlockEntity::new, ModBlocks.BIOMASS_BURNER, ModBlocks.COAL_GENERATOR);
	public static final BlockEntityType<com.ficsitcraft.blockentity.CreativeGeneratorBlockEntity> CREATIVE_GENERATOR = register("creative_generator",
			com.ficsitcraft.blockentity.CreativeGeneratorBlockEntity::new, ModBlocks.CREATIVE_GENERATOR);
	public static final BlockEntityType<PowerPoleBlockEntity> POWER_POLE = register("power_pole",
			PowerPoleBlockEntity::new, ModBlocks.POWER_POLE_MK1, ModBlocks.POWER_POLE_MK2, ModBlocks.POWER_POLE_MK3);
	public static final BlockEntityType<ConveyorBeltBlockEntity> CONVEYOR_BELT = register("conveyor_belt",
			ConveyorBeltBlockEntity::new, ModBlocks.CONVEYOR_BELT_MK1, ModBlocks.CONVEYOR_BELT_MK2, ModBlocks.CONVEYOR_BELT_MK3);
	public static final BlockEntityType<LogisticsBlockEntity> LOGISTICS = register("logistics",
			LogisticsBlockEntity::new, ModBlocks.SPLITTER, ModBlocks.MERGER);
	public static final BlockEntityType<com.ficsitcraft.blockentity.PipeBlockEntity> PIPE = register("pipe",
			com.ficsitcraft.blockentity.PipeBlockEntity::new, ModBlocks.PIPELINE_MK1, ModBlocks.PIPELINE_MK2);
	public static final BlockEntityType<com.ficsitcraft.blockentity.PipelinePumpBlockEntity> PIPELINE_PUMP = register("pipeline_pump",
			com.ficsitcraft.blockentity.PipelinePumpBlockEntity::new, ModBlocks.PIPELINE_PUMP);
	public static final BlockEntityType<com.ficsitcraft.blockentity.WaterExtractorBlockEntity> WATER_EXTRACTOR = register("water_extractor",
			com.ficsitcraft.blockentity.WaterExtractorBlockEntity::new, ModBlocks.WATER_EXTRACTOR);
	public static final BlockEntityType<com.ficsitcraft.blockentity.FluidBufferBlockEntity> FLUID_BUFFER = register("fluid_buffer",
			com.ficsitcraft.blockentity.FluidBufferBlockEntity::new, ModBlocks.FLUID_BUFFER);
	public static final BlockEntityType<DisplayBlockEntity> DISPLAY = register("display",
			DisplayBlockEntity::new, ModBlocks.HUB, ModBlocks.CRAFT_BENCH);
	public static final BlockEntityType<StorageContainerBlockEntity> STORAGE_CONTAINER = register("storage_container",
			StorageContainerBlockEntity::new, ModBlocks.STORAGE_CONTAINER);

	public static final BlockEntityType<com.ficsitcraft.blockentity.RailBuildingBlockEntity> RAIL_BUILDING = register("rail_building",
			com.ficsitcraft.blockentity.RailBuildingBlockEntity::new, ModBlocks.TRAIN_STATION, ModBlocks.FREIGHT_PLATFORM,
			ModBlocks.FLUID_FREIGHT_PLATFORM, ModBlocks.EMPTY_PLATFORM);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntityFactory<T> factory, Block... blocks) {
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, FicsitCraft.id(name), BlockEntityType.Builder.create(factory, blocks).build(null));
	}

	public static void init() {
		// Belts, splitters and mergers expose custom storages; everything else uses the Inventory fallback.
		ItemStorage.SIDED.registerForBlockEntity((be, dir) -> be.getStorage(dir), CONVEYOR_BELT);
		ItemStorage.SIDED.registerForBlockEntity((be, dir) -> be.getStorage(dir), LOGISTICS);
		// Every part of a multi-block building forwards item transfer to its controller.
		ItemStorage.SIDED.registerForBlocks((world, pos, state, be, dir) -> {
			BlockPos controller = MachinePartBlock.controllerPos(state, pos);
			if (controller.equals(pos) || world.getBlockState(controller).getBlock() instanceof MachinePartBlock) return null;
			return ItemStorage.SIDED.find(world, controller, dir);
		}, ModBlocks.MACHINE_PART);
	}
}
