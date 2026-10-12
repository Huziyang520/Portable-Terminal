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
	 * 一张 193×147 的贴图分三段 1:1 使用：表头 31px + 一行槽位 18px（按行数重复贴）+ 页脚 98px。
	 *
	 * <p>刻意不用九宫格缩放：{@code blitNineSlicedSprite} 会把每条边钳制到目标高度的一半
	 * （{@code Math.min(border.bottom(), height / 2)}），页脚 98px 高于面板一半时底边被截短，
	 * 中间带就会取到长度不对的片段，格子与物品错位，且行数越少越明显。</p>
	 */
	public static final Identifier PANEL_SPRITE = sprite("panel");
	/** 面板源图尺寸与分段（与 panel.png 的实际像素一一对应）。 */
	public static final int PANEL_SOURCE_WIDTH = 193;
	public static final int PANEL_SOURCE_HEIGHT = 147;
	public static final int PANEL_HEADER_HEIGHT = 31;
	public static final int PANEL_FOOTER_HEIGHT = 98;
	public static final Identifier BUTTON_SPRITE = sprite("button");
	/** 加行 / 减行按键：+/− 符号。 */
	public static final Identifier PLUS_SPRITE = sprite("plus");
	public static final Identifier MINUS_SPRITE = sprite("minus");
	public static final Identifier HIGHLIGHT_SPRITE = sprite("highlight");
	/** 排序图标右下角的状态角标：正序朝上、倒序朝下。 */
	public static final Identifier CORNER_UP_SPRITE = sprite("corner_up");
	public static final Identifier CORNER_DOWN_SPRITE = sprite("corner_down");
	public static final int CORNER_SIZE = 7;

	/** 每种排序类型一个主图标；正序/倒序的区别只在右下角的角标箭头。 */
	private static final Identifier[] SORT_ICON_SPRITES = {
		sprite("sort_name"), sprite("sort_count"), sprite("sort_id"), sprite("sort_mod")
	};

	public static Identifier sortIconSprite(int index) {
		return SORT_ICON_SPRITES[Math.floorMod(index, SORT_ICON_SPRITES.length)];
	}

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
