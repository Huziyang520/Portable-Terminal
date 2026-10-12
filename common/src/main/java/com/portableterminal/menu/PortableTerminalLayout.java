package com.portableterminal.menu;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.resources.Identifier;

/**
 * Layout constants. Everything that depends on the terminal's row count is computed from it, so the
 * screen size (and the baked GUI sprite) always matches the slot grid.
 */
public final class PortableTerminalLayout {

	public static final int PADDING = 7;
	public static final int SLOT_SIZE = 18;
	public static final int COLS = 9;

	public static final int MIN_ROWS = 3;
	public static final int MAX_ROWS = 6;
	public static final int DEFAULT_ROWS = 4;

	public static final int TERM_LEFT = PADDING + 1;
	public static final int TERM_TOP = 32;

	public static final int SCROLLBAR_LEFT = TERM_LEFT + COLS * SLOT_SIZE + 3;
	public static final int SCROLLBAR_WIDTH = 12;
	public static final int SCROLLBAR_TOP = TERM_TOP - 1;
	public static final int SCROLL_THUMB_MIN = 8;

	public static final int IMAGE_WIDTH = SCROLLBAR_LEFT + SCROLLBAR_WIDTH + 8;

	public static final int INV_LEFT = PADDING + 1;

	public static final int OFFHAND_X = -22;

	public static final int SEARCH_X = TERM_LEFT;
	public static final int SEARCH_Y = 15;
	public static final int SEARCH_WIDTH = COLS * SLOT_SIZE;
	public static final int SEARCH_HEIGHT = 12;

	// Left hand side square buttons (outside the panel, drawn from sprites).
	public static final int BTN_SIZE = 18;
	public static final int BTN_GAP = 2;
	public static final int SORT_BTN_X = -30;
	public static final int SORT_BTN_Y = TERM_TOP;
	public static final int SORT_BTN_COUNT = 4;

	// Row count pair, placed under the sort buttons.
	public static final int ROWS_UP_BTN_Y = SORT_BTN_Y + SORT_BTN_COUNT * (BTN_SIZE + BTN_GAP) + 8;
	public static final int ROWS_DOWN_BTN_Y = ROWS_UP_BTN_Y + BTN_SIZE + BTN_GAP;

	// GUI sprites (assets/portableterminal/textures/gui/sprites/).
	private static final String SPRITE_ROOT = "terminal/";
	public static final Identifier SLOT_SPRITE = sprite("slot");
	/**
	 * One nine-sliced panel sprite covers every row count: its vertical center band is exactly one
	 * slot row and repeats ({@code stretch_inner=false}), so any number of rows renders crisply
	 * from the same texture. See panel.png.mcmeta.
	 */
	public static final Identifier PANEL_SPRITE = sprite("panel");
	public static final Identifier BUTTON_SPRITE = sprite("button");
	public static final Identifier ARROW_UP_SPRITE = sprite("arrow_up");
	public static final Identifier ARROW_DOWN_SPRITE = sprite("arrow_down");
	public static final Identifier HIGHLIGHT_SPRITE = sprite("highlight");

	public static Identifier sprite(String path) {
		return Identifier.tryBuild(PortableTerminalMod.MOD_ID, SPRITE_ROOT + path);
	}

	public static int clampRows(int rows) {
		return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
	}

	public static int terminalSlotCount(int rows) {
		return COLS * rows;
	}

	public static int invTop(int rows) {
		return TERM_TOP + rows * SLOT_SIZE + 14;
	}

	public static int hotbarTop(int rows) {
		return invTop(rows) + 3 * SLOT_SIZE + 4;
	}

	/** Offhand sits on the same row as the hotbar so both bottoms line up. */
	public static int offhandY(int rows) {
		return hotbarTop(rows);
	}

	public static int imageHeight(int rows) {
		return hotbarTop(rows) + SLOT_SIZE + PADDING;
	}

	public static int scrollbarHeight(int rows) {
		return rows * SLOT_SIZE + 2;
	}

	public static int terminalAreaBottom(int rows) {
		return TERM_TOP + rows * SLOT_SIZE;
	}

	private PortableTerminalLayout() {
	}
}
