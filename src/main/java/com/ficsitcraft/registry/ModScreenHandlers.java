package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.screen.BuildGunScreenHandler;
import com.ficsitcraft.screen.CraftBenchScreenHandler;
import com.ficsitcraft.screen.GeneratorScreenHandler;
import com.ficsitcraft.screen.HubScreenHandler;
import com.ficsitcraft.screen.MinerScreenHandler;
import com.ficsitcraft.screen.PowerPoleScreenHandler;
import com.ficsitcraft.screen.ProcessingMachineScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<ProcessingMachineScreenHandler> PROCESSING_MACHINE = register("processing_machine",
			new ExtendedScreenHandlerType<>(ProcessingMachineScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<MinerScreenHandler> MINER = register("miner",
			new ExtendedScreenHandlerType<>(MinerScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<GeneratorScreenHandler> GENERATOR = register("generator",
			new ExtendedScreenHandlerType<>(GeneratorScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<PowerPoleScreenHandler> POWER_POLE = register("power_pole",
			new ExtendedScreenHandlerType<>(PowerPoleScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<com.ficsitcraft.screen.FluidMachineScreenHandler> FLUID_MACHINE = register("fluid_machine",
			new ExtendedScreenHandlerType<>(com.ficsitcraft.screen.FluidMachineScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<com.ficsitcraft.screen.PipeScreenHandler> PIPE = register("pipe",
			new ExtendedScreenHandlerType<>(com.ficsitcraft.screen.PipeScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<com.ficsitcraft.screen.FluidBufferScreenHandler> FLUID_BUFFER = register("fluid_buffer",
			new ExtendedScreenHandlerType<>(com.ficsitcraft.screen.FluidBufferScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<com.ficsitcraft.screen.PlatformScreenHandler> PLATFORM = register("platform",
			new ExtendedScreenHandlerType<>(com.ficsitcraft.screen.PlatformScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<com.ficsitcraft.screen.CreativeGeneratorScreenHandler> CREATIVE_GENERATOR = register("creative_generator",
			new ExtendedScreenHandlerType<>(com.ficsitcraft.screen.CreativeGeneratorScreenHandler::new, BlockPos.PACKET_CODEC.cast()));
	public static final ScreenHandlerType<HubScreenHandler> HUB = register("hub",
			new ScreenHandlerType<>(HubScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
	public static final ScreenHandlerType<CraftBenchScreenHandler> CRAFT_BENCH = register("craft_bench",
			new ScreenHandlerType<>(CraftBenchScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
	public static final ScreenHandlerType<BuildGunScreenHandler> BUILD_GUN = register("build_gun",
			new ScreenHandlerType<>(BuildGunScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

	private ModScreenHandlers() {
	}

	private static <T extends ScreenHandler> ScreenHandlerType<T> register(String name, ScreenHandlerType<T> type) {
		return Registry.register(Registries.SCREEN_HANDLER, FicsitCraft.id(name), type);
	}

	public static void init() {
	}
}
