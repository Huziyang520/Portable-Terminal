package com.portableterminal.config;

import com.portableterminal.menu.PortableTerminalLayout;

/**
 * 配置项登记表：选项的段/键/默认值/范围只在这里写一次，三端的 spec 与语言键都从这里取。
 */
public final class ConfigSchema {

	public static final String SECTION_GENERAL = "general";

	public static final String KEY_MASTER_SWITCH = "master_switch";
	public static final String KEY_SCAN_RADIUS = "scan_radius";
	public static final String KEY_TERMINAL_ROWS = "terminal_rows";
	public static final String KEY_BLACKLIST = "blacklist";
	public static final String KEY_INSERT_ORDER = "insert_order";
	public static final String KEY_CRAFTING = "crafting_enabled";
	public static final String KEY_SHOW_RECIPE_BOOK = "show_recipe_book";

	public static final boolean DEFAULT_MASTER_SWITCH = true;
	public static final boolean DEFAULT_CRAFTING = true;
	public static final boolean DEFAULT_SHOW_RECIPE_BOOK = true;
	public static final String ORDER_NEAREST = "NEAREST";
	public static final String ORDER_FARTHEST = "FARTHEST";
	public static final String ORDER_COORDINATES = "COORDINATES";
	public static final String DEFAULT_INSERT_ORDER = ORDER_NEAREST;
	public static final int DEFAULT_SCAN_RADIUS = 16;
	public static final int MAX_SCAN_RADIUS = 128;
	public static final int MIN_SCAN_RADIUS = 0;
	public static final int DEFAULT_TERMINAL_ROWS = PortableTerminalLayout.DEFAULT_ROWS;
	public static final int MIN_TERMINAL_ROWS = PortableTerminalLayout.MIN_ROWS;
	public static final int MAX_TERMINAL_ROWS = PortableTerminalLayout.MAX_ROWS;

	// ==================== 合成取料来源 ====================
	/** 优先从终端容器取料，不够再用背包。 */
	public static final int CRAFT_SOURCE_TERMINAL_FIRST = 0;
	/** 优先用背包，不够再从终端取。 */
	public static final int CRAFT_SOURCE_INVENTORY_FIRST = 1;
	/** 只用终端容器，完全不碰背包。 */
	public static final int CRAFT_SOURCE_TERMINAL_ONLY = 2;
	public static final int CRAFT_SOURCE_COUNT = 3;
	public static final int DEFAULT_CRAFT_SOURCE = CRAFT_SOURCE_TERMINAL_FIRST;
	public static final String[] CRAFT_SOURCE_KEYS = {"terminal_first", "inventory_first", "terminal_only"};

	// ==================== 清空合成栏去向 ====================
	public static final int CLEAR_DEST_INVENTORY = 0;
	public static final int CLEAR_DEST_TERMINAL = 1;
	public static final int CLEAR_DEST_COUNT = 2;
	public static final int DEFAULT_CLEAR_DEST = CLEAR_DEST_INVENTORY;
	public static final String[] CLEAR_DEST_KEYS = {"inventory", "terminal"};

	/** 文案沿用原有的 config.portableterminal.* 键，界面与旧版本保持一致。 */
	public static final String TRANSLATION_PREFIX = "config.portableterminal.";

	private ConfigSchema() {
	}

	public static String translationKey(String key) {
		return TRANSLATION_PREFIX + key;
	}

	public static String sectionTranslationKey() {
		return PORTABLE_TERMINAL_ID + ".configuration." + SECTION_GENERAL;
	}

	private static final String PORTABLE_TERMINAL_ID = "portableterminal";
}
