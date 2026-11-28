package com.portableterminal.menu;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.resources.Identifier;

/**
 * Layout constants. Everything that depends on the row count and on whether the crafting block is
 * shown is computed here, so slot positions, screen size and the baked GUI sprites never drift apart.
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

	/** 副手槽左缘与左侧按钮列左缘对齐（外框再向外 5px）。 */
	public static final int OFFHAND_X = -25;

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
	// 行数按键紧贴排序按钮组：留 8px 时行数最少的面板上，减行键会与副手槽外框重叠。
	public static final int ROWS_UP_BTN_Y = SORT_BTN_Y + SORT_BTN_COUNT * (BTN_SIZE + BTN_GAP) + 2;
	public static final int ROWS_DOWN_BTN_Y = ROWS_UP_BTN_Y + BTN_SIZE + BTN_GAP;

	// ==================== 合成带 ====================
	/** 合成带高度：3 行槽位（54）+ 上下各 4px 留白。 */
	public static final int CRAFT_BAND_HEIGHT = 62;
	public static final int CRAFT_GRID_SIZE = 3;
	public static final int CRAFT_SLOT_COUNT = CRAFT_GRID_SIZE * CRAFT_GRID_SIZE;
	/** 网格 / 结果 / 箭头 / 配方书按钮，坐标都相对合成带顶边。 */
	public static final int CRAFT_GRID_X = 41;
	public static final int CRAFT_GRID_Y = 5;
	public static final int CRAFT_RESULT_X = 120;
	public static final int CRAFT_RESULT_Y = 23;
	public static final int CRAFT_ARROW_X = 95;
	public static final int CRAFT_ARROW_Y = 26;
	public static final int RECIPE_BUTTON_X = 5;
	public static final int RECIPE_BUTTON_Y = 22;
	public static final int RECIPE_BUTTON_WIDTH = 20;
	public static final int RECIPE_BUTTON_HEIGHT = 18;

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
	/** 带合成带的面板：同样宽度，竖向多一段 62px 的合成带（四段 1:1 切片）。 */
	public static final Identifier PANEL_CRAFTING_SPRITE = sprite("panel_crafting");
	/** 面板源图尺寸与分段（与两张 panel 贴图的实际像素一一对应）。 */
	public static final int PANEL_SOURCE_WIDTH = 193;
	public static final int PANEL_SOURCE_HEIGHT = 147;
	public static final int PANEL_CRAFTING_SOURCE_HEIGHT = PANEL_SOURCE_HEIGHT + CRAFT_BAND_HEIGHT;
	public static final int PANEL_HEADER_HEIGHT = 31;
	public static final int PANEL_ROW_BAND_SRC_Y = 31;
	public static final int PANEL_CRAFT_BAND_SRC_Y = PANEL_ROW_BAND_SRC_Y + SLOT_SIZE;
	public static final int PANEL_FOOTER_HEIGHT = 98;
	/** 带合成带那张图里页脚的起始行。 */
	public static final int PANEL_FOOTER_CRAFTING_SRC_Y = PANEL_CRAFT_BAND_SRC_Y + CRAFT_BAND_HEIGHT;
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

	// ==================== 竖向分段 ====================

	/** 槽位带顶边（表头之下）。 */
	public static int rowsBandTop() {
		return TERM_TOP;
	}

	/** 合成带顶边；合成关闭时它就是页脚顶边。 */
	public static int craftBandTop(int rows) {
		return TERM_TOP + clampRows(rows) * SLOT_SIZE;
	}

	public static int footerTop(int rows, boolean crafting) {
		return craftBandTop(rows) + (crafting ? CRAFT_BAND_HEIGHT : 0);
	}

	public static int invTop(int rows, boolean crafting) {
		return footerTop(rows, crafting) + 14;
	}

	public static int hotbarTop(int rows, boolean crafting) {
		return invTop(rows, crafting) + 3 * SLOT_SIZE + 4;
	}

	/** Offhand sits on the same row as the hotbar so both bottoms line up. */
	public static int offhandY(int rows, boolean crafting) {
		return hotbarTop(rows, crafting);
	}

	public static int imageHeight(int rows, boolean crafting) {
		return hotbarTop(rows, crafting) + SLOT_SIZE + PADDING;
	}

	public static int scrollbarHeight(int rows) {
		return clampRows(rows) * SLOT_SIZE + 2;
	}

	public static int terminalAreaBottom(int rows) {
		return TERM_TOP + clampRows(rows) * SLOT_SIZE;
	}

	// ==================== 兼容旧调用（等同合成关闭） ====================

	public static int invTop(int rows) {
		return invTop(rows, false);
	}

	public static int hotbarTop(int rows) {
		return hotbarTop(rows, false);
	}

	public static int offhandY(int rows) {
		return offhandY(rows, false);
	}

	public static int imageHeight(int rows) {
		return imageHeight(rows, false);
	}

	private PortableTerminalLayout() {
	}
}
