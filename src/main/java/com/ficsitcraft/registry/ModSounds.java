package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Train sounds (synthesised by tools/gen_sounds.py, declared in assets/ficsitcraft/sounds.json). */
public final class ModSounds {
	/** Rail rumble and joint clacks (loop). */
	public static final SoundEvent TRAIN_ROLL = register("train_roll");
	/** Traction motor hum of a locomotive (loop). */
	public static final SoundEvent TRAIN_MOTOR = register("train_motor");
	/** Wheels squealing on the rails while braking (loop). */
	public static final SoundEvent TRAIN_BRAKE = register("train_brake");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = FicsitCraft.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}
