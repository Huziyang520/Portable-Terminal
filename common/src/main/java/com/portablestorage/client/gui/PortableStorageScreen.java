// Portions of this code (terminal GUI layout, slot rendering)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.
package com.portablestorage.client.gui;
import com.portablestorage.PortableStorageClient;
import com.portablestorage.menu.PortableStorageMenu;
import com.portablestorage.network.TerminalSlotClickPacket;
import com.portablestorage.platform.ClientSender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PortableStorageScreen extends AbstractContainerScreen<PortableStorageMenu> {

	// 布局常量
	private static final int PADDING = PortableStorageMenu.PADDING;
	private static final int SLOT_SIZE = PortableStorageMenu.SLOT_SIZE;
	private static final int COLS = PortableStorageMenu.COLS;
	private static final int TERMINAL_ROWS = PortableStorageMenu.TERMINAL_ROWS;

	private static final int TERM_LEFT = PortableStorageMenu.TERM_LEFT;
	private static final int TERM_TOP = PortableStorageMenu.TERM_TOP;
	private static final int INV_LEFT = PortableStorageMenu.INV_LEFT;
	private static final int INV_TOP = PortableStorageMenu.INV_TOP;
	private static final int HOTBAR_TOP = PortableStorageMenu.HOTBAR_TOP;

	private static final int GUI_WIDTH = PortableStorageMenu.IMAGE_WIDTH;
	private static final int GUI_HEIGHT = PortableStorageMenu.IMAGE_HEIGHT;

	private static final int OFFHAND_X = PortableStorageMenu.OFFHAND_X;
	private static final int OFFHAND_Y = PortableStorageMenu.OFFHAND_Y;

	private static final int SCROLLBAR_LEFT = PortableStorageMenu.SCROLLBAR_LEFT;
	private static final int SCROLLBAR_WIDTH = PortableStorageMenu.SCROLLBAR_WIDTH;
	private static final int SCROLLBAR_TOP = TERM_TOP - 1;
	private static final int SCROLLBAR_HEIGHT = TERMINAL_ROWS * SLOT_SIZE + 2;
	private static final int SCROLL_THUMB_MIN = 8;

	private static final int SEARCH_X = TERM_LEFT;
	private static final int SEARCH_Y = 15;
	private static final int SEARCH_WIDTH = COLS * SLOT_SIZE;
	private static final int SEARCH_HEIGHT = 12;

	private static final int SORT_BTN_X = -46;
	private static final int SORT_BTN_Y = TERM_TOP;
	private static final int SORT_BTN_W = 42;
	private static final int SORT_BTN_H = 14;
	private static final int SORT_BTN_GAP = 2;

	// 配色
	private static final int COL_PANEL_BG      = 0xFFC6C6C6;
	private static final int COL_BORDER_DARK   = 0xFF373737;
	private static final int COL_BORDER_LIGHT  = 0xFFFFFFFF;
	private static final int COL_SLOT_BG       = 0xFF8B8B8B;
	private static final int COL_SLOT_DARK     = 0xFF373737;
	private static final int COL_SLOT_LIGHT    = 0xFFFFFFFF;
	private static final int COL_TERM_AREA_BG  = 0xFF9E9E9E;
	private static final int COL_SCROLL_BG     = 0xFF000000;
	private static final int COL_SCROLL_THUMB  = 0xFFC6C6C6;
	private static final int COL_SCROLL_THUMB2 = 0xFF808080;
	private static final int COL_TEXT          = 0xFF404040;

	private EditBox searchBox;
	private String lastSearchText = "";
	private boolean draggingScrollbar = false;
	// 滚动条状态缓存：仅在用户主动滚动时更新，数据同步不触发重算，彻底避免闪烁
	private int cachedTotalRows = TERMINAL_ROWS;
	private int cachedScrollOffset = 0;
	private int cachedMaxScroll = 0;
	private boolean scrollbarDirty = true;

	public PortableStorageScreen(PortableStorageMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
		this.titleLabelX = PADDING;
		this.titleLabelY = 5;
		this.inventoryLabelX = PADDING;
		this.inventoryLabelY = INV_TOP - 11;
	}

	@Override
	protected void init() {
		super.init();

		// 打开界面即接管服务器同步包；关闭时归还（见 onClose）
		PortableStorageClient.setSyncReceiver(this::onItemsSynced);

		this.searchBox = new EditBox(
			this.font,
			this.leftPos + SEARCH_X,
			this.topPos + SEARCH_Y,
			SEARCH_WIDTH,
			SEARCH_HEIGHT,
			Component.translatable("gui.portablestorage.search_hint")
		);
		this.searchBox.setMaxLength(60);
		this.searchBox.setBordered(true);
		this.searchBox.setValue(lastSearchText);
		this.searchBox.setResponder(this::onSearchChanged);
		this.addRenderableWidget(this.searchBox);

		this.menu.updateTerminalSlots();
	}

	private void onItemsSynced(List<ItemStack> items) {
		this.menu.setAggregatedItemsFromServer(items);
	}

	private void onSearchChanged(String value) {
		this.lastSearchText = value;
		this.menu.applySearchFilter(value);
	}

	@Override
	public void onClose() {
		PortableStorageClient.setSyncReceiver(null);
		super.onClose();
	}

	// 键盘处理

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.searchBox != null && this.getFocused() == this.searchBox) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.searchBox.setFocused(false);
				this.setFocused(null);
				return true;
			}
			this.searchBox.keyPressed(event);
			return true;
		}
		return super.keyPressed(event);
	}

	// 纯代码绘制的背景

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		drawPanel(gfx, x, y, GUI_WIDTH, GUI_HEIGHT);

		gfx.fill(x + TERM_LEFT - 2, y + TERM_TOP - 2,
			x + TERM_LEFT + COLS * SLOT_SIZE + 2, y + TERM_TOP + TERMINAL_ROWS * SLOT_SIZE + 2,
			COL_TERM_AREA_BG);

		for (int row = 0; row < TERMINAL_ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				drawSlot(gfx, x + TERM_LEFT + col * SLOT_SIZE, y + TERM_TOP + row * SLOT_SIZE);
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlot(gfx, x + INV_LEFT + col * SLOT_SIZE, y + INV_TOP + row * SLOT_SIZE);
			}
		}

		for (int col = 0; col < 9; col++) {
			drawSlot(gfx, x + INV_LEFT + col * SLOT_SIZE, y + HOTBAR_TOP);
		}

		drawPanel(gfx, x + OFFHAND_X - 5, y + OFFHAND_Y - 5, SLOT_SIZE + 8, SLOT_SIZE + 8);
		drawSlot(gfx, x + OFFHAND_X, y + OFFHAND_Y);

		drawScrollbar(gfx, x, y);

		drawSortButton(gfx, x, y, mouseX, mouseY);
	}

	/** 原版风格面板：亮左上边、暗右下边、灰色填充 */
	private void drawPanel(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
		gfx.fill(x, y, x + w, y + h, COL_BORDER_DARK);
		gfx.fill(x + 1, y + 1, x + w - 1, y + 2, COL_BORDER_LIGHT);
		gfx.fill(x + 1, y + 1, x + 2, y + h - 1, COL_BORDER_LIGHT);
		gfx.fill(x + 2, y + 2, x + w - 2, y + h - 2, COL_PANEL_BG);
		gfx.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
		gfx.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
	}

	/** 原版风格 18x18 槽位：左上暗边、右下亮边、灰底。传入坐标为 16x16 物品区左上角 */
	private void drawSlot(GuiGraphicsExtractor gfx, int ix, int iy) {
		int x = ix - 1;
		int y = iy - 1;
		gfx.fill(x, y, x + SLOT_SIZE, y + 1, COL_SLOT_DARK);
		gfx.fill(x, y, x + 1, y + SLOT_SIZE, COL_SLOT_DARK);
		gfx.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, COL_SLOT_LIGHT);
		gfx.fill(x + 1, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, COL_SLOT_LIGHT);
		gfx.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COL_SLOT_BG);
	}

	private void drawScrollbar(GuiGraphicsExtractor gfx, int guiX, int guiY) {
		int sx = guiX + SCROLLBAR_LEFT;
		int sy = guiY + SCROLLBAR_TOP;

		gfx.fill(sx, sy, sx + SCROLLBAR_WIDTH, sy + SCROLLBAR_HEIGHT, COL_SCROLL_BG);
		gfx.fill(sx + 1, sy + 1, sx + SCROLLBAR_WIDTH - 1, sy + SCROLLBAR_HEIGHT - 1, 0xFF2B2B2B);

		// 仅在用户滚动（scrollbarDirty=true）时更新缓存，数据同步不触发，避免闪烁
		if (scrollbarDirty) {
			int rows = Math.max(this.menu.getTotalRows(), TERMINAL_ROWS);
			int off = this.menu.getScrollOffset();
			int max = this.menu.getMaxScroll();
			if (rows != cachedTotalRows || off != cachedScrollOffset || max != cachedMaxScroll) {
				cachedTotalRows = rows;
				cachedScrollOffset = off;
				cachedMaxScroll = max;
			}
			scrollbarDirty = false;
		}

		int trackH = SCROLLBAR_HEIGHT - 2;
		int thumbH = Math.max(SCROLL_THUMB_MIN, trackH * TERMINAL_ROWS / cachedTotalRows);
		int thumbY = sy + 1;
		if (cachedMaxScroll > 0) {
			int trackH2 = SCROLLBAR_HEIGHT - 2 - thumbH;
			thumbY = sy + 1 + trackH2 * cachedScrollOffset / cachedMaxScroll;
		}
		int thumbColor = (cachedMaxScroll > 0) ? COL_SCROLL_THUMB : 0xFF555555;
		gfx.fill(sx + 1, thumbY, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, thumbColor);
		if (cachedMaxScroll > 0) {
			gfx.fill(sx + SCROLLBAR_WIDTH - 2, thumbY, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, COL_SCROLL_THUMB2);
			gfx.fill(sx + 1, thumbY + thumbH - 1, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, COL_SCROLL_THUMB2);
		}
	}

	private void drawSortButton(GuiGraphicsExtractor gfx, int guiX, int guiY, int mouseX, int mouseY) {
		int count = this.menu.getSortCount();
		for (int i = 0; i < count; i++) {
			int x = guiX + SORT_BTN_X;
			int y = guiY + SORT_BTN_Y + i * (SORT_BTN_H + SORT_BTN_GAP);
			drawPanel(gfx, x - 1, y - 1, SORT_BTN_W + 2, SORT_BTN_H + 2);
			if (mouseX >= x && mouseX < x + SORT_BTN_W && mouseY >= y && mouseY < y + SORT_BTN_H) {
				gfx.fill(x - 1, y - 1, x + SORT_BTN_W + 1, y + SORT_BTN_H + 1, 0x60FFFFFF);
			}
			String name = this.menu.getSortName(i).getString();
			String arrow = switch (this.menu.getSortDir(i)) {
				case NONE -> "";
				case ASC -> " ^";
				case DESC -> " v";
			};
			String text = name + arrow;
			int textX = x + (SORT_BTN_W - this.font.width(text)) / 2;
			gfx.text(this.font, text, textX, y + 3, COL_TEXT, false);
		}
	}

	private int getThumbHeight() {
		int totalRows = Math.max(this.menu.getTotalRows(), TERMINAL_ROWS);
		int trackH = SCROLLBAR_HEIGHT - 2;
		return Math.max(SCROLL_THUMB_MIN, trackH * TERMINAL_ROWS / totalRows);
	}

	private int getThumbOffset(int maxScroll, int thumbH) {
		if (maxScroll <= 0) return 0;
		int trackH = SCROLLBAR_HEIGHT - 2 - thumbH;
		return trackH * this.menu.getScrollOffset() / maxScroll;
	}

	@Override
	public void extractLabels(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		gfx.text(this.font, this.title,
			this.titleLabelX, this.titleLabelY, COL_TEXT, false);
		gfx.text(this.font, this.playerInventoryTitle,
			this.inventoryLabelX, this.inventoryLabelY, COL_TEXT, false);
	}

	// 终端槽位自定义渲染

	@Override
	protected void extractSlot(GuiGraphicsExtractor gfx, Slot slot, int mouseX, int mouseY) {
		if (slot instanceof PortableStorageMenu.TerminalSlot) {
			int x = slot.x;
			int y = slot.y;
			ItemStack itemStack = slot.getItem();
			if (!itemStack.isEmpty()) {
				int seed = slot.x + slot.y * this.imageWidth;
				gfx.item(itemStack, x, y, seed);
				int count = itemStack.getCount();
				if (count != 1) {
					String countText = formatCount(count);
					drawScaledCount(gfx, countText, x, y);
				}
			}
		} else {
			super.extractSlot(gfx, slot, mouseX, mouseY);
		}
	}

	/** 大数量格式化：万级以上用 K/M 缩写，避免数字过长溢出槽位 */
	private String formatCount(int count) {
		if (count >= 1000000) return (count / 1000000) + "M";
		if (count >= 10000) return (count / 1000) + "K";
		return String.valueOf(count);
	}

	/** 自适应缩放绘制数量数字：基础 0.7 倍，文字过宽时自动缩小以适配 16px 槽位 */
	private void drawScaledCount(GuiGraphicsExtractor gfx, String text, int slotX, int slotY) {
		float baseScale = 0.7f;
		int maxWidth = 15;
		float textWidth = this.font.width(text);
		float scale = baseScale;
		if (textWidth * baseScale > maxWidth) {
			scale = maxWidth / textWidth;
		}
		gfx.pose().pushMatrix();
		float drawX = slotX + 16 - textWidth * scale - 1;
		float drawY = slotY + 16 - 8 * scale - 1;
		gfx.pose().translate(drawX, drawY);
		gfx.pose().scale(scale, scale);
		gfx.text(this.font, text, 0, 0, 0xFFFFFFFF, true);
		gfx.pose().popMatrix();
	}

	// 鼠标交互

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean inside) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		boolean hasShift = event.hasShiftDown();

		if (isInSearchBox(mouseX, mouseY)) {
			this.setFocused(this.searchBox);
			this.searchBox.setFocused(true);
			this.searchBox.mouseClicked(event, inside);
			return true;
		}

		if (this.getFocused() == this.searchBox) {
			this.searchBox.setFocused(false);
			this.setFocused(null);
		}

		int sortIdx = getClickedSortButton(mouseX, mouseY);
		if (sortIdx >= 0 && button == 0) {
			this.menu.cycleSortDir(sortIdx);
			return true;
		}

		if (isInScrollbar(mouseX, mouseY) && button == 0) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}

		int hoveredSlot = getHoveredTerminalSlot((int) mouseX, (int) mouseY);
		if (hoveredSlot >= 0) {
			ItemStack carried = this.menu.getCarried();
			if (button != 2 && !carried.isEmpty()) {
				ClientSender.sendToServer(TerminalSlotClickPacket.deposit());
				return true;
			}

			ItemStack shown = this.menu.getTerminalSlots().get(hoveredSlot).getItem();
			if (shown.isEmpty()) {
				return true;
			}

			net.minecraft.resources.Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
			ClientSender.sendToServer(new TerminalSlotClickPacket(itemId, button, hasShift));
			return true;
		}

		return super.mouseClicked(event, inside);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void scrollToMouse(double mouseY) {
		scrollbarDirty = true;
		int maxScroll = this.menu.getMaxScroll();
		if (maxScroll <= 0) return;
		int thumbH = getThumbHeight();
		int trackTop = this.topPos + SCROLLBAR_TOP + 1 + thumbH / 2;
		int trackH = SCROLLBAR_HEIGHT - 2 - thumbH;
		double ratio = (mouseY - trackTop) / (double) trackH;
		int offset = (int) Math.round(ratio * maxScroll);
		this.menu.setScrollOffset(offset);
	}

	private boolean isInSearchBox(double mouseX, double mouseY) {
		int x = this.leftPos + SEARCH_X;
		int y = this.topPos + SEARCH_Y;
		return mouseX >= x && mouseX < x + SEARCH_WIDTH && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
	}

	private boolean isInScrollbar(double mouseX, double mouseY) {
		int x = this.leftPos + SCROLLBAR_LEFT;
		int y = this.topPos + SCROLLBAR_TOP;
		return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= y && mouseY < y + SCROLLBAR_HEIGHT;
	}

	/** 返回点击到的排序按钮索引（0..count-1），未命中返回 -1 */
	private int getClickedSortButton(double mouseX, double mouseY) {
		int x = this.leftPos + SORT_BTN_X;
		if (mouseX < x || mouseX >= x + SORT_BTN_W) return -1;
		int count = this.menu.getSortCount();
		for (int i = 0; i < count; i++) {
			int y = this.topPos + SORT_BTN_Y + i * (SORT_BTN_H + SORT_BTN_GAP);
			if (mouseY >= y && mouseY < y + SORT_BTN_H) return i;
		}
		return -1;
	}

	private int getHoveredTerminalSlot(int mouseX, int mouseY) {
		int termX = this.leftPos + TERM_LEFT;
		int termY = this.topPos + TERM_TOP;
		int termW = COLS * SLOT_SIZE;
		int termH = TERMINAL_ROWS * SLOT_SIZE;

		if (mouseX < termX || mouseX >= termX + termW || mouseY < termY || mouseY >= termY + termH) {
			return -1;
		}
		int col = (mouseX - termX) / SLOT_SIZE;
		int row = (mouseY - termY) / SLOT_SIZE;
		if (col < 0 || col >= COLS || row < 0 || row >= TERMINAL_ROWS) {
			return -1;
		}
		return row * COLS + col;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int termX = this.leftPos + TERM_LEFT;
		int termY = this.topPos + TERM_TOP;
		int termW = COLS * SLOT_SIZE + SCROLLBAR_WIDTH + 4;
		int termH = TERMINAL_ROWS * SLOT_SIZE;

		if (mouseX >= termX && mouseX < termX + termW && mouseY >= termY && mouseY < termY + termH) {
			scrollbarDirty = true;
			int currentOffset = this.menu.getScrollOffset();
			if (scrollY > 0) {
				this.menu.setScrollOffset(currentOffset - 1);
			} else if (scrollY < 0) {
				this.menu.setScrollOffset(currentOffset + 1);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

}
