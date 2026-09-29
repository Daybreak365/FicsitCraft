package com.ficsitcraft.data;

import net.minecraft.text.Text;

import java.util.List;

public record Milestone(int index, String id, int tier, List<Cost> cost) {
	public Text name() {
		return Text.translatable("milestone.ficsitcraft." + id);
	}

	public Text description() {
		return Text.translatable("milestone.ficsitcraft." + id + ".desc");
	}
}
