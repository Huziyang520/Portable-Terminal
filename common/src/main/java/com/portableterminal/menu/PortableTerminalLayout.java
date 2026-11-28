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
	public static final int MAX_ROWS = 8;
	public static final int DEFAULT_ROWS = 4;

	public static final int TERM_LEFT = PADDING + 1;
	public static final int TERM_TOP = 32;

	public static final int SCROLLBAR_LEFT = TERM_LEFT + COLS * SLOT_SIZE + 3;
	public static final int SCROLLBAR_WIDTH = 12;
	public static final int SCROLLBAR_TOP = TERM_TOP - 1;
	public static final int SCROLL_THUMB_MIN = 8;
	/** 滑块与轨道左右各留 2px，避免贴在凹槽边上。 */
	public static final int SCROLL_THUMB_INSET = 2;

	public static final int IMAGE_WIDTH = SCROLLBAR_LEFT + SCROLLBAR_WIDTH + 8;

	public static final int INV_LEFT = PADDING + 1;

	/** 副手槽左缘与左侧按钮列左缘对齐（外框再向外 5px）。 */
	public static final int OFFHAND_X = -25;

	public static final int SEARCH_X = TERM_LEFT;
	public static final int SEARCH_Y = 15;
	public static final int SEARCH_WIDTH = COLS * SLOT_SIZE;
	public static final int SEARCH_HEIGHT = 12;

	// ==================== 左侧方块按钮列 ====================
	public static final int BTN_SIZE = 18;
	public static final int BTN_GAP = 2;
	/** 一个按钮占的高度（含间隙）。 */
	public static final int BTN_PITCH = BTN_SIZE + BTN_GAP;
	public static final int SORT_BTN_X = -30;
	public static final int SORT_BTN_Y = TERM_TOP;

	/** 自上而下：排序类型 / 排序方向 / 加行 / 减行 / 合成取料 / 清空去向 / 配置。 */
	public static final int SORT_MODE_BTN_Y = SORT_BTN_Y;
	public static final int SORT_DIR_BTN_Y = SORT_MODE_BTN_Y + BTN_PITCH;
	public static final int ROWS_UP_BTN_Y = SORT_DIR_BTN_Y + BTN_PITCH;
	public static final int ROWS_DOWN_BTN_Y = ROWS_UP_BTN_Y + BTN_PITCH;
	public static final int CRAFT_SRC_BTN_Y = ROWS_DOWN_BTN_Y + BTN_PITCH;
	public static final int CLEAR_DST_BTN_Y = CRAFT_SRC_BTN_Y + BTN_PITCH;
	public static final int CONFIG_BTN_Y = CLEAR_DST_BTN_Y + BTN_PITCH;
	public static final int BUTTON_COUNT = 7;

	// ==================== 合成带 ====================
	/** 合成带高度：3 行槽位（54）+ 上下各 4px 留白。 */
	public static final int CRAFT_BAND_HEIGHT = 62;
	public static final int CRAFT_GRID_SIZE = 3;
	public static final int CRAFT_SLOT_COUNT = CRAFT_GRID_SIZE * CRAFT_GRID_SIZE;
	/** 网格 / 结果 / 箭头 / 配方书按钮 / 清空按键，坐标都相对合成带顶边。 */
	public static final int CRAFT_GRID_X = 41;
	/** 槽位在贴图里比合成带顶边低 6px（贴图里第一格是边框、第二格才是槽位内部）。 */
	public static final int CRAFT_GRID_Y = 6;
	public static final int CRAFT_RESULT_X = 120;
	public static final int CRAFT_RESULT_Y = 24;
	/** 箭头直接取原版工作台贴图里的那一块，尺寸与原版一致（22×15）。 */
	public static final int CRAFT_ARROW_X = 96;
	public static final int CRAFT_ARROW_Y = 26;
	public static final int CRAFT_ARROW_WIDTH = 22;
	public static final int CRAFT_ARROW_HEIGHT = 15;
	public static final int RECIPE_BUTTON_X = 5;
	public static final int RECIPE_BUTTON_Y = 22;
	public static final int RECIPE_BUTTON_WIDTH = 20;
	public static final int RECIPE_BUTTON_HEIGHT = 18;
	/** 清空合成栏按键：合成区右侧、贴上方。 */
	public static final int CLEAR_GRID_BTN_X = 150;
	public static final int CLEAR_GRID_BTN_Y = 4;

	// ==================== 贴图 ====================
	// GUI sprites (assets/portableterminal/textures/gui/sprites/).
	private static final String SPRITE_ROOT = "terminal/";
	public static final Identifier SLOT_SPRITE = sprite("slot");
	/**
	 * 一张 193×147 的贴图分三段 1:1 使用：表头 31px + 一行槽位 18px（按行数重复贴）+ 页脚 98px。
	 *
	 * <p>刻意不用九宫格缩放：{@code blitNineSlicedSprite} 会把每条边钳制到目标高度的一半
	 * （{@code Math.min(border.bottom(), height / 2)}），页脚 98px 高于面板一半时底边被截短，
	 * 中间带就会取到长度不对的片段，格子与物品错位，且行数越少越明显。</p>
	 */
	public static final Identifier PANEL_SPRITE = sprite("panel");
	/** 带合成带的面板：同宽，竖向多一段 62px 的合成带（四段 1:1 切片）。 */
	public static final Identifier PANEL_CRAFTING_SPRITE = sprite("panel_crafting");
	/** 面板源图尺寸与分段（与两张 panel 贴图的实际像素一一对应）。 */
	public static final int PANEL_SOURCE_WIDTH = 193;
	public static final int PANEL_SOURCE_HEIGHT = 147;
	public static final int PANEL_HEADER_HEIGHT = 31;
	public static final int PANEL_ROW_BAND_SRC_Y = 31;
	public static final int PANEL_CRAFT_BAND_SRC_Y = PANEL_ROW_BAND_SRC_Y + SLOT_SIZE;
	public static final int PANEL_CRAFTING_SOURCE_HEIGHT = PANEL_SOURCE_HEIGHT + CRAFT_BAND_HEIGHT;
	public static final int PANEL_FOOTER_CRAFTING_SRC_Y = PANEL_CRAFT_BAND_SRC_Y + CRAFT_BAND_HEIGHT;
	public static final int PANEL_FOOTER_HEIGHT = 98;
	public static final Identifier BUTTON_SPRITE = sprite("button");
	/** 加行 / 减行按键：+/− 符号。 */
	public static final Identifier PLUS_SPRITE = sprite("plus");
	public static final Identifier MINUS_SPRITE = sprite("minus");
	public static final Identifier HIGHLIGHT_SPRITE = sprite("highlight");
	/** 排序方向按键：正序 / 倒序。 */
	public static final Identifier SORT_ASC_SPRITE = sprite("sort_asc");
	public static final Identifier SORT_DESC_SPRITE = sprite("sort_desc");
	/** 合成取料来源：终端优先 / 背包优先 / 仅背包。 */
	public static final Identifier[] CRAFT_SOURCE_SPRITES = {
		sprite("source_terminal"), sprite("source_inventory"), sprite("source_terminal_only")
	};
	/** 清空去向：进背包 / 进终端。 */
	public static final Identifier[] CLEAR_DEST_SPRITES = {
		sprite("dest_inventory"), sprite("dest_terminal")
	};
	public static final Identifier CONFIG_SPRITE = sprite("config");
	public static final Identifier CLEAR_GRID_SPRITE = sprite("clear_grid");

	/** 每种排序类型一个主图标。 */
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

	/**
	 * 合成带顶边（贴图坐标）：表头 31px 之后接槽位行，所以基准是 31 而不是槽位的 32。
	 * 用 32 会让合成带/页脚整体比槽位行低 1px，两个区块之间露一条背景色缝（看着像黑线）。
	 * 合成关闭时它就是页脚顶边。
	 */
	public static int craftBandTop(int rows) {
		return PANEL_HEADER_HEIGHT + clampRows(rows) * SLOT_SIZE;
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

	/** 面板最矮时（最小行数、无合成带）按钮列底部是否还在面板内。 */
	public static int buttonColumnBottom() {
		return CONFIG_BTN_Y + BTN_SIZE;
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
