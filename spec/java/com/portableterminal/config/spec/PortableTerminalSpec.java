package com.portableterminal.config.spec;

import com.portableterminal.config.ConfigSchema;
import com.portableterminal.config.InsertOrder;
import com.portableterminal.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Predicate;

/**
 * 三端共用的 spec 定义：NeoForge 提供这套类；Fabric 由 Forge Config API Port 提供同名同形的类，
 * 所以同一个源文件在两边各编译一次，界面与键名自然一致。
 */
public final class PortableTerminalSpec {

	private static final Predicate<Object> IS_STRING = value -> value instanceof String;

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.BooleanValue MASTER_SWITCH;
	public static final ModConfigSpec.IntValue SCAN_RADIUS;
	public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
	public static final ModConfigSpec.EnumValue<InsertOrder> INSERT_ORDER;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
			// allowEmpty + 新元素工厂：原生配置屏幕的列表页据此给出「新建元素」按钮。
			.defineListAllowEmpty(ConfigSchema.KEY_BLACKLIST, List.of(), () -> "", IS_STRING);

		INSERT_ORDER = builder
			.comment("Which container gets items first when inserting.",
				"NEAREST = closest first, FARTHEST = farthest first, COORDINATES = by block position.")
			.translation(ConfigSchema.translationKey(ConfigSchema.KEY_INSERT_ORDER))
			.defineEnum(ConfigSchema.KEY_INSERT_ORDER, InsertOrder.NEAREST);

		builder.pop();
		SPEC = builder.build();
	}

	private PortableTerminalSpec() {
	}

	/** spec 载入后把值刷进运行期快照；未载入时保持登记表默认值。 */
	public static void applyAll() {
		if (!SPEC.isLoaded()) {
			return;
		}
		ModConfig.applyAll(MASTER_SWITCH.get(), SCAN_RADIUS.get(), List.copyOf(BLACKLIST.get()), INSERT_ORDER.get().name());
	}
}
