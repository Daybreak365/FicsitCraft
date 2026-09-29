package com.ficsitcraft;

import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.progress.ProgressState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

/** /ficsit unlockall | reset | unlock <index> | status  (operators only) */
public final class FicsitCommands {
	private FicsitCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("ficsit")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.literal("unlockall").executes(ctx -> {
							ProgressState ps = ProgressState.get(ctx.getSource().getServer());
							for (int i = 0; i < Milestones.ALL.size(); i++) ps.complete(i);
							ctx.getSource().sendFeedback(() -> Text.literal("All milestones completed."), true);
							return 1;
						}))
						.then(CommandManager.literal("reset").executes(ctx -> {
							ProgressState.get(ctx.getSource().getServer()).reset();
							ctx.getSource().sendFeedback(() -> Text.literal("HUB progress reset."), true);
							return 1;
						}))
						.then(CommandManager.literal("unlock")
								.then(CommandManager.argument("milestone", IntegerArgumentType.integer(0, 63)).executes(ctx -> {
									int m = IntegerArgumentType.getInteger(ctx, "milestone");
									if (Milestones.get(m) == null) return 0;
									ProgressState.get(ctx.getSource().getServer()).complete(m);
									ctx.getSource().sendFeedback(() -> Text.literal("Completed milestone ").append(Milestones.get(m).name()), true);
									return 1;
								})))
						.then(CommandManager.literal("status").executes(ctx -> {
							ProgressState ps = ProgressState.get(ctx.getSource().getServer());
							for (var m : Milestones.ALL) {
								String mark = ps.isDone(m.index()) ? "[x] " : "[ ] ";
								ctx.getSource().sendFeedback(() -> Text.literal(mark + m.index() + " T" + m.tier() + " ").append(m.name()), false);
							}
							return 1;
						}))));
	}
}
