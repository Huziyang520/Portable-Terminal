package com.portableterminal.forge;

import com.portableterminal.config.ConfigSchema;
import com.portableterminal.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Predicate;

/**
 * Forge 1.20.1 的 spec。界面由 Neo Configuration Screen Port 提供，它直接读 Forge 的 ConfigTracker，
 * 所以这里只负责登记 spec 与把值刷进运行期快照。
 */
public final class ForgeConfig {

	private static final Predicate<Object> IS_STRING = value -> value instanceof String;

	private static final ForgeConfigSpec SPEC;
	private static final ForgeConfigSpec.BooleanValue MASTER_SWITCH;
	private static final ForgeConfigSpec.IntValue SCAN_RADIUS;
	private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("Portable Terminal settings.").push(ConfigSchema.SECTION_GENERAL);

		MASTER_SWITCH = builder
			.comment("Turns the whole mod off when false.")
			.translation(ConfigSchema.translationKey(ConfigSchema.KEY_MASTER_SWITCH))
			.define(ConfigSchema.KEY_MASTER_SWITCH, ConfigSchema.DEFAULT_MASTER_SWITCH);

		SCAN_RADIUS = builder
			.comment("Container scan radius in blocks.", "Range 0-128.")
			.translation(ConfigSchema.translationKey(ConfigSchema.KEY_SCAN_RADIUS))
			.defineInRange(ConfigSchema.KEY_SCAN_RADIUS, ConfigSchema.DEFAULT_SCAN_RADIUS,
				ConfigSchema.MIN_SCAN_RADIUS, ConfigSchema.MAX_SCAN_RADIUS);

		BLACKLIST = builder
			.comment("Block ids the terminal ignores, for example minecraft:furnace.")
			.translation(ConfigSchema.translationKey(ConfigSchema.KEY_BLACKLIST))
			.defineList(ConfigSchema.KEY_BLACKLIST, List.<String>of(), IS_STRING);

		builder.pop();
		SPEC = builder.build();
	}

	private ForgeConfig() {
	}

	public static ForgeConfigSpec spec() {
		return SPEC;
	}

	public static void applyAll() {
		if (!SPEC.isLoaded()) {
			return;
		}
		ModConfig.applyAll(MASTER_SWITCH.get(), SCAN_RADIUS.get(), List.copyOf(BLACKLIST.get()));
	}
}
