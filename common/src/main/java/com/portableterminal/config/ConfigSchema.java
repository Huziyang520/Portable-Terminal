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

	public static final boolean DEFAULT_MASTER_SWITCH = true;
	public static final int DEFAULT_SCAN_RADIUS = 8;
	public static final int MAX_SCAN_RADIUS = 128;
	public static final int MIN_SCAN_RADIUS = 0;
	public static final int DEFAULT_TERMINAL_ROWS = PortableTerminalLayout.DEFAULT_ROWS;
	public static final int MIN_TERMINAL_ROWS = PortableTerminalLayout.MIN_ROWS;
	public static final int MAX_TERMINAL_ROWS = PortableTerminalLayout.MAX_ROWS;

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
