package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.block.ConveyorBeltBlock;
import com.ficsitcraft.block.CreativeGeneratorBlock;
import com.ficsitcraft.block.FacingGuiBlock;
import com.ficsitcraft.block.GeneratorBlock;
import com.ficsitcraft.block.LogisticsBlock;
import com.ficsitcraft.block.MinerBlock;
import com.ficsitcraft.block.NodeType;
import com.ficsitcraft.block.PipeBlock;
import com.ficsitcraft.block.PipelinePumpBlock;
import com.ficsitcraft.block.WaterExtractorBlock;
import com.ficsitcraft.block.PowerPoleBlock;
import com.ficsitcraft.block.ProcessingMachineBlock;
import com.ficsitcraft.block.RailBuildingBlock;
import com.ficsitcraft.block.ResourceNodeBlock;
import com.ficsitcraft.block.StorageContainerBlock;
import com.ficsitcraft.data.MachineType;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.MachinePartBlock;
import com.ficsitcraft.multiblock.Multiblocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
	/** Every block with an item, in creative tab order. */
	public static final List<Block> ALL = new ArrayList<>();
	public static final Map<NodeType, ResourceNodeBlock> NODES = new EnumMap<>(NodeType.class);

	private static AbstractBlock.Settings machine() {
		return AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).strength(2.5f, 6f)
				.sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK);
	}

	private static AbstractBlock.Settings node() {
		return AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(-1f, 3600000f)
				.sounds(BlockSoundGroup.STONE).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);
	}

	// Resource nodes
	public static final ResourceNodeBlock IRON_NODE = node(NodeType.IRON);
	public static final ResourceNodeBlock COPPER_NODE = node(NodeType.COPPER);
	public static final ResourceNodeBlock LIMESTONE_NODE = node(NodeType.LIMESTONE);
	public static final ResourceNodeBlock COAL_NODE = node(NodeType.COAL);
	public static final ResourceNodeBlock CATERIUM_NODE = node(NodeType.CATERIUM);
	public static final ResourceNodeBlock QUARTZ_NODE = node(NodeType.QUARTZ);

	// Special
	public static final Block HUB = register("hub", new FacingGuiBlock(FacingGuiBlock.Kind.HUB, machine()));
	public static final Block CRAFT_BENCH = register("craft_bench", new FacingGuiBlock(FacingGuiBlock.Kind.CRAFT_BENCH, machine()));
	public static final Block STORAGE_CONTAINER = register("storage_container", new StorageContainerBlock(machine()));

	// Production
	public static final Block MINER_MK1 = register("miner_mk1", new MinerBlock(1, machine()));
	public static final Block MINER_MK2 = register("miner_mk2", new MinerBlock(2, machine()));
	public static final Block SMELTER = register("smelter", new ProcessingMachineBlock(MachineType.SMELTER, machine().luminance(s -> s.get(ProcessingMachineBlock.ACTIVE) ? 10 : 0)));
	public static final Block FOUNDRY = register("foundry", new ProcessingMachineBlock(MachineType.FOUNDRY, machine().luminance(s -> s.get(ProcessingMachineBlock.ACTIVE) ? 12 : 0)));
	public static final Block CONSTRUCTOR = register("constructor", new ProcessingMachineBlock(MachineType.CONSTRUCTOR, machine()));
	public static final Block ASSEMBLER = register("assembler", new ProcessingMachineBlock(MachineType.ASSEMBLER, machine()));
	public static final Block MANUFACTURER = register("manufacturer", new ProcessingMachineBlock(MachineType.MANUFACTURER, machine()));

	// Power
	public static final Block BIOMASS_BURNER = register("biomass_burner", new GeneratorBlock(GeneratorBlock.Kind.BIOMASS, machine().luminance(s -> s.get(GeneratorBlock.ACTIVE) ? 9 : 0)));
	/** Test-only: creative players only, not craftable, not in the build gun. */
	public static final Block CREATIVE_GENERATOR = register("creative_generator", new CreativeGeneratorBlock(machine().luminance(s -> 7)));
	public static final Block COAL_GENERATOR = register("coal_generator", new GeneratorBlock(GeneratorBlock.Kind.COAL, machine().luminance(s -> s.get(GeneratorBlock.ACTIVE) ? 11 : 0)));
	public static final Block POWER_POLE_MK1 = register("power_pole_mk1", new PowerPoleBlock(1, machine()));
	public static final Block POWER_POLE_MK2 = register("power_pole_mk2", new PowerPoleBlock(2, machine()));
	public static final Block POWER_POLE_MK3 = register("power_pole_mk3", new PowerPoleBlock(3, machine()));

	// Logistics
	public static final Block CONVEYOR_BELT_MK1 = register("conveyor_belt_mk1", new ConveyorBeltBlock(1, machine().strength(1f, 6f)));
	public static final Block CONVEYOR_BELT_MK2 = register("conveyor_belt_mk2", new ConveyorBeltBlock(2, machine().strength(1f, 6f)));
	public static final Block CONVEYOR_BELT_MK3 = register("conveyor_belt_mk3", new ConveyorBeltBlock(3, machine().strength(1f, 6f)));
	public static final Block SPLITTER = register("splitter", new LogisticsBlock(true, machine()));
	public static final Block MERGER = register("merger", new LogisticsBlock(false, machine()));

	// Fluids
	public static final Block PIPELINE_MK1 = register("pipeline_mk1", new PipeBlock(1, machine().strength(1f, 6f)));
	public static final Block PIPELINE_MK2 = register("pipeline_mk2", new PipeBlock(2, machine().strength(1f, 6f)));
	public static final Block PIPELINE_PUMP = register("pipeline_pump", new PipelinePumpBlock(machine()));
	public static final Block WATER_EXTRACTOR = register("water_extractor", new WaterExtractorBlock(machine()));
	public static final Block FLUID_BUFFER = register("fluid_buffer", new com.ficsitcraft.block.FluidBufferBlock(machine()));

	// Architecture
	public static final Block FOUNDATION = register("foundation", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.LIGHT_GRAY).strength(2f, 8f).sounds(BlockSoundGroup.STONE)));
	public static final Block CONCRETE_WALL = register("concrete_wall", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.WHITE_GRAY).strength(2f, 8f).sounds(BlockSoundGroup.STONE)));

	// Railway buildings (5 x 9 blocks with a track through the middle)
	public static final Block TRAIN_STATION = register("train_station", new RailBuildingBlock(RailBuildingBlock.Kind.STATION, machine()));
	public static final Block FREIGHT_PLATFORM = register("freight_platform", new RailBuildingBlock(RailBuildingBlock.Kind.FREIGHT, machine()));
	public static final Block FLUID_FREIGHT_PLATFORM = register("fluid_freight_platform", new RailBuildingBlock(RailBuildingBlock.Kind.FLUID, machine()));
	public static final Block EMPTY_PLATFORM = register("empty_platform", new RailBuildingBlock(RailBuildingBlock.Kind.EMPTY, machine()));

	/** Invisible structure block of multi-block buildings (no item). */
	public static final Block MACHINE_PART = registerNoItem("machine_part", new MachinePartBlock(machine().dropsNothing()));

	private ModBlocks() {
	}

	private static <T extends Block> T registerNoItem(String name, T block) {
		return Registry.register(Registries.BLOCK, FicsitCraft.id(name), block);
	}

	private static ResourceNodeBlock node(NodeType type) {
		ResourceNodeBlock b = register(type.id + "_node", new ResourceNodeBlock(type, node()));
		NODES.put(type, b);
		return b;
	}

	private static <T extends Block> T register(String name, T block) {
		Registry.register(Registries.BLOCK, FicsitCraft.id(name), block);
		Registry.register(Registries.ITEM, FicsitCraft.id(name), new BlockItem(block, new Item.Settings()));
		ALL.add(block);
		return block;
	}

	public static void init() {
		// Building sizes (width, depth, height, controller column, controller row). Front = output side.
		Multiblocks.register(HUB, new Footprint(3, 4, 3, 1, 0));
		Multiblocks.register(CRAFT_BENCH, new Footprint(2, 1, 2, 0, 0));
		Multiblocks.register(STORAGE_CONTAINER, new Footprint(2, 3, 2, 0, 0));
		Multiblocks.register(MINER_MK1, new Footprint(3, 3, 5, 1, 1));
		Multiblocks.register(MINER_MK2, new Footprint(3, 3, 5, 1, 1));
		Multiblocks.register(SMELTER, new Footprint(2, 3, 3, 0, 0));
		Multiblocks.register(FOUNDRY, new Footprint(3, 3, 3, 1, 0));
		Multiblocks.register(CONSTRUCTOR, new Footprint(3, 3, 3, 1, 0));
		Multiblocks.register(ASSEMBLER, new Footprint(3, 5, 4, 1, 0));
		Multiblocks.register(MANUFACTURER, new Footprint(5, 6, 4, 2, 0));
		Multiblocks.register(BIOMASS_BURNER, new Footprint(3, 3, 3, 1, 0));
		Multiblocks.register(COAL_GENERATOR, new Footprint(3, 5, 6, 1, 0));
		Multiblocks.register(CREATIVE_GENERATOR, new Footprint(3, 3, 4, 1, 1));
		Multiblocks.register(WATER_EXTRACTOR, new Footprint(3, 3, 3, 1, 1));
		Multiblocks.register(FLUID_BUFFER, new Footprint(3, 3, 4, 1, 1));
		// Controller in the middle row: MachinePartBlock only stores offsets of up to +-MachinePartBlock.H_RANGE (7) blocks,
		// a controller at the back row of a 9 deep building would need 8.
		Multiblocks.register(TRAIN_STATION, new Footprint(5, 9, 4, 2, 4));
		Multiblocks.register(FREIGHT_PLATFORM, new Footprint(5, 9, 4, 2, 4));
		Multiblocks.register(FLUID_FREIGHT_PLATFORM, new Footprint(5, 9, 4, 2, 4));
		Multiblocks.register(EMPTY_PLATFORM, new Footprint(5, 9, 2, 2, 4));
	}
}
