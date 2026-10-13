// Portions of this code (terminal GUI layout, slot rendering)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.
package com.portableterminal.client.gui;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.config.ModConfig;
import com.portableterminal.menu.PortableTerminalLayout;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import com.portableterminal.platform.ClientSender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The terminal GUI.
 *
 * <p>Everything inside the panel — frame, slot wells and the scrollbar track — comes from a single
 * nine-sliced sprite ({@code terminal/panel}); its vertical center band is exactly one slot row and
 * repeats, so every row count renders crisply from one texture. Its four corners are literal copies
 * of vanilla's {@code generic_54.png} corners and the slot cells are vanilla's own
 * {@code container/slot.png}.</p>
 *
 * <p>The six buttons on the left are drawn as square sprite buttons. Nothing is ever greyed out:
 * a sort field that is active is highlighted instead, and every button stays clickable at all
 * times.</p>
 */
public class PortableTerminalScreen extends AbstractContainerScreen<PortableTerminalMenu> {

	private static final int COLS = PortableTerminalLayout.COLS;
	private static final int SLOT_SIZE = PortableTerminalLayout.SLOT_SIZE;
	private static final int TERM_LEFT = PortableTerminalLayout.TERM_LEFT;
	private static final int TERM_TOP = PortableTerminalLayout.TERM_TOP;
	private static final int INV_LEFT = PortableTerminalLayout.INV_LEFT;
	private static final int SCROLLBAR_LEFT = PortableTerminalLayout.SCROLLBAR_LEFT;
	private static final int SCROLLBAR_WIDTH = PortableTerminalLayout.SCROLLBAR_WIDTH;
	private static final int SCROLLBAR_TOP = PortableTerminalLayout.SCROLLBAR_TOP;
	private static final int SCROLL_THUMB_MIN = PortableTerminalLayout.SCROLL_THUMB_MIN;
	private static final int BTN_SIZE = PortableTerminalLayout.BTN_SIZE;

	private static final int SEARCH_X = PortableTerminalLayout.SEARCH_X;
	private static final int SEARCH_Y = PortableTerminalLayout.SEARCH_Y;
	private static final int SEARCH_WIDTH = PortableTerminalLayout.SEARCH_WIDTH;
	private static final int SEARCH_HEIGHT = PortableTerminalLayout.SEARCH_HEIGHT;

	private static final int COL_SCROLL_THUMB  = 0xFFC6C6C6;
	private static final int COL_SCROLL_THUMB2 = 0xFF808080;
	private static final int COL_TEXT          = 0xFF404040;

	private final int rows;
	private final int guiWidth;
	private final int guiHeight;

	/** 重开界面后要恢复到的光标位置（GUI 坐标）；NaN 表示无需恢复。 */
	private static double restoreGuiMouseX = Double.NaN;
	private static double restoreGuiMouseY = Double.NaN;
	private static int restoreFrames;

	private static final String[] SORT_KEYS = {"name", "count", "id", "mod"};

	private EditBox searchBox;
	private String lastSearchText = "";
	private int lastMouseX;
	private int lastMouseY;
	private Component lastTooltip;
	private boolean draggingScrollbar = false;
	/** Shift + left button drag over the terminal slots: quick move every slot entered. */
	private boolean draggingQuickMove = false;
	private int lastQuickMoveSlot = -1;

	// 滚动条状态缓存：仅在用户主动滚动时更新，数据同步不触发重算，彻底避免闪烁
	private int cachedTotalRows;
	private int cachedScrollOffset;
	private int cachedMaxScroll;
	private boolean scrollbarDirty = true;

	private final List<IconButton> sortButtons = new ArrayList<>();

	public PortableTerminalScreen(PortableTerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title,
			PortableTerminalLayout.IMAGE_WIDTH, PortableTerminalLayout.imageHeight(menu.getRows()));
		this.rows = menu.getRows();
		this.guiWidth = PortableTerminalLayout.IMAGE_WIDTH;
		this.guiHeight = PortableTerminalLayout.imageHeight(this.rows);
		this.cachedTotalRows = this.rows;
		this.titleLabelX = PortableTerminalLayout.PADDING;
		this.titleLabelY = 5;
		this.inventoryLabelX = PortableTerminalLayout.PADDING;
		this.inventoryLabelY = PortableTerminalLayout.invTop(this.rows) - 11;
	}

	// ==================== 初始化 ====================

	@Override
	protected void init() {
		super.init();

		// 打开界面即接管服务器同步包；关闭时归还（见 onClose）
		PortableTerminalClient.setSyncReceiver(this::onItemsSynced);

		this.searchBox = new EditBox(
			this.font,
			this.leftPos + SEARCH_X,
			this.topPos + SEARCH_Y,
			SEARCH_WIDTH,
			SEARCH_HEIGHT,
			Component.translatable("gui.portableterminal.search_hint")
		);
		this.searchBox.setMaxLength(60);
		this.searchBox.setBordered(true);
		this.searchBox.setValue(lastSearchText);
		this.searchBox.setResponder(this::onSearchChanged);
		this.addRenderableWidget(this.searchBox);

		createButtons();
		this.menu.updateTerminalSlots();
	}

	private void createButtons() {
		sortButtons.clear();
		for (int i = 0; i < this.menu.getSortCount(); i++) {
			final int index = i;
			IconButton button = new IconButton(
				this.leftPos + PortableTerminalLayout.SORT_BTN_X,
				this.topPos + PortableTerminalLayout.SORT_BTN_Y
					+ i * (BTN_SIZE + PortableTerminalLayout.BTN_GAP),
				() -> null,
				() -> this.menu.getSortDir(index) != PortableTerminalMenu.SortDirection.NONE,
				() -> cycleSort(index),
				index);
			sortButtons.add(button);
			this.addRenderableWidget(button);
		}

		this.addRenderableWidget(new IconButton(
			this.leftPos + PortableTerminalLayout.SORT_BTN_X,
			this.topPos + PortableTerminalLayout.ROWS_UP_BTN_Y,
			() -> PortableTerminalLayout.PLUS_SPRITE,
			() -> false,
			() -> changeRows(1),
			-1));
		this.addRenderableWidget(new IconButton(
			this.leftPos + PortableTerminalLayout.SORT_BTN_X,
			this.topPos + PortableTerminalLayout.ROWS_DOWN_BTN_Y,
			() -> PortableTerminalLayout.MINUS_SPRITE,
			() -> false,
			() -> changeRows(-1),
			-2));
	}

	/** 排序类型被选中时右下角要显示的角标；未选中返回 null（只显示类型图标）。 */
	private Identifier sortCornerArrow(int index) {
		return switch (this.menu.getSortDir(index)) {
			case NONE -> null;
			case ASC -> PortableTerminalLayout.CORNER_UP_SPRITE;
			case DESC -> PortableTerminalLayout.CORNER_DOWN_SPRITE;
		};
	}

	/** 排序按钮的悬浮提示：类型 + 当前方向，例如「数量：由多到少」。 */
	private Component sortTooltip(int index) {
		PortableTerminalMenu.SortDirection dir = this.menu.getSortDir(index);
		if (dir == PortableTerminalMenu.SortDirection.NONE) {
			return Component.translatable("gui.portableterminal.sort.none");
		}
		return Component.translatable("gui.portableterminal.sort." + SORT_KEYS[index] + "."
			+ (dir == PortableTerminalMenu.SortDirection.ASC ? "asc" : "desc"));
	}

	private void cycleSort(int index) {
		this.menu.cycleSortDir(index);
	}

	/** 切换显示行数：写进配置并重新开一次终端，服务端据此重建菜单。 */
	private void changeRows(int delta) {
		int next = PortableTerminalLayout.clampRows(this.rows + delta);
		if (next == this.rows) {
			return;
		}
		// 槽位布局变了，服务端必须重建菜单，客户端随之重开界面；原版重开时会把光标
		// 挪到屏幕正中，所以先记下当前位置，等新界面第一帧再放回去。
		restoreGuiMouseX = this.lastMouseX;
		restoreGuiMouseY = this.lastMouseY;
		restoreFrames = 5;
		ModConfig.terminalRows = next;
		ModConfig.save();
		ClientSender.sendToServer(new OpenTerminalPacket(next));
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
		PortableTerminalClient.setSyncReceiver(null);
		super.onClose();
	}

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

	// ==================== 背景：九宫格面板贴图 ====================

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// 切行数会重开界面，而原版开界面时 MouseHandler.releaseMouse() 会把光标挪到屏幕正中；
		// 它的时机可能晚于新界面的第一帧，所以连续压几帧再放弃，避免和它抢。
		if (!Double.isNaN(restoreGuiMouseX)) {
			if (restoreFrames-- > 0) {
				double scale = Minecraft.getInstance().getWindow().getGuiScale();
				GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(),
					restoreGuiMouseX * scale, restoreGuiMouseY * scale);
			} else {
				restoreGuiMouseX = Double.NaN;
			}
		}
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		int srcW = PortableTerminalLayout.PANEL_SOURCE_WIDTH;
		int srcH = PortableTerminalLayout.PANEL_SOURCE_HEIGHT;
		int headerH = PortableTerminalLayout.PANEL_HEADER_HEIGHT;
		int x = this.leftPos;
		int y = this.topPos;

		// 同一张贴图分三段 1:1 贴：表头 / 一行槽位（按行数重复）/ 页脚。
		// 不能用九宫格缩放：它会按目标高度的一半钳制每条边，页脚高于面板一半时中间带
		// 会被取成长度不对的片段，格子与物品就会错位（行数越少越明显）。
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE,
			srcW, srcH, 0, 0, x, y, this.guiWidth, headerH);
		for (int row = 0; row < this.rows; row++) {
			gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE,
				srcW, srcH, 0, headerH, x, y + headerH + row * SLOT_SIZE, this.guiWidth, SLOT_SIZE);
		}
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE,
			srcW, srcH, 0, headerH + SLOT_SIZE,
			x, y + headerH + this.rows * SLOT_SIZE, this.guiWidth, PortableTerminalLayout.PANEL_FOOTER_HEIGHT);

		// 副手槽在面板之外：原版圆角小面板外框 + 槽位底
		int offX = x + PortableTerminalLayout.OFFHAND_X;
		int offY = y + PortableTerminalLayout.offhandY(this.rows);
		drawFramePanel(gfx, offX - 5, offY - 5, SLOT_SIZE + 8, SLOT_SIZE + 8);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.SLOT_SPRITE,
			offX - 1, offY - 1, SLOT_SIZE, SLOT_SIZE);

		drawScrollbarThumb(gfx);
	}

	/**
	 * 用面板贴图的边角，画一个带原版圆角边框的小面板（副手槽的外框）。
	 * 全部取自同一张贴图，四角就是原版容器的 7×7 角块。
	 */
	private void drawFramePanel(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
		int srcW = PortableTerminalLayout.PANEL_SOURCE_WIDTH;
		int srcH = PortableTerminalLayout.PANEL_SOURCE_HEIGHT;
		// 内部
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE,
			srcW, srcH, 3, 3, x + 3, y + 3, w - 6, h - 6);
		// 四条直边
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, 7, 0, x + 7, y, w - 14, 3);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, 7, srcH - 3, x + 7, y + h - 3, w - 14, 3);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, 0, 7, x, y + 7, 3, h - 14);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, srcW - 3, 7, x + w - 3, y + 7, 3, h - 14);
		// 四角
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, 0, 0, x, y, 7, 7);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, srcW - 7, 0, x + w - 7, y, 7, 7);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, 0, srcH - 7, x, y + h - 7, 7, 7);
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.PANEL_SPRITE, srcW, srcH, srcW - 7, srcH - 7, x + w - 7, y + h - 7, 7, 7);
	}

	private void drawScrollbarThumb(GuiGraphicsExtractor gfx) {
		int sx = this.leftPos + SCROLLBAR_LEFT;
		int sy = this.topPos + SCROLLBAR_TOP;
		int trackHeight = PortableTerminalLayout.scrollbarHeight(this.rows);

		if (scrollbarDirty) {
			cachedTotalRows = Math.max(this.menu.getTotalRows(), this.rows);
			cachedScrollOffset = this.menu.getScrollOffset();
			cachedMaxScroll = this.menu.getMaxScroll();
			scrollbarDirty = false;
		}

		int inner = trackHeight - 2;
		int thumbH = Math.max(SCROLL_THUMB_MIN, inner * this.rows / Math.max(1, cachedTotalRows));
		int thumbY = sy + 1;
		if (cachedMaxScroll > 0) {
			thumbY = sy + 1 + (inner - thumbH) * cachedScrollOffset / cachedMaxScroll;
		}
		int thumbColor = cachedMaxScroll > 0 ? COL_SCROLL_THUMB : 0xFF555555;
		gfx.fill(sx + 1, thumbY, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, thumbColor);
		if (cachedMaxScroll > 0) {
			gfx.fill(sx + SCROLLBAR_WIDTH - 2, thumbY, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, COL_SCROLL_THUMB2);
			gfx.fill(sx + 1, thumbY + thumbH - 1, sx + SCROLLBAR_WIDTH - 1, thumbY + thumbH, COL_SCROLL_THUMB2);
		}
	}

	@Override
	public void extractLabels(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		gfx.text(this.font, this.title, this.titleLabelX, this.titleLabelY, COL_TEXT, false);
		gfx.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, COL_TEXT, false);
	}

	// ==================== 终端槽位自定义渲染 ====================

	@Override
	protected void extractSlot(GuiGraphicsExtractor gfx, Slot slot, int mouseX, int mouseY) {
		if (slot instanceof PortableTerminalMenu.TerminalSlot) {
			int x = slot.x;
			int y = slot.y;
			ItemStack itemStack = slot.getItem();
			if (!itemStack.isEmpty()) {
				int seed = slot.x + slot.y * this.imageWidth;
				gfx.item(itemStack, x, y, seed);
				int count = itemStack.getCount();
				if (count != 1) {
					drawScaledCount(gfx, formatCount(count), x, y);
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

	// ==================== 鼠标交互 ====================

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

		if (isInScrollbar(mouseX, mouseY) && button == 0) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}

		int hoveredSlot = getHoveredTerminalSlot((int) mouseX, (int) mouseY);
		if (hoveredSlot >= 0 && button == 0 && hasShift) {
			// shift + 左键：进入拖拽快速转移模式（每滑过一格转移一组）
			draggingQuickMove = true;
			lastQuickMoveSlot = -1;
			quickMoveHoveredSlot((int) mouseX, (int) mouseY);
			return true;
		}

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

			Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
			ClientSender.sendToServer(new TerminalSlotClickPacket(itemId, button, hasShift));
			return true;
		}

		return super.mouseClicked(event, inside);
	}

	/** 触发一次「受击槽位 → 玩家背包」的整组转移；同一格只触发一次。 */
	private void quickMoveHoveredSlot(int mouseX, int mouseY) {
		int slotIndex = getHoveredTerminalSlot(mouseX, mouseY);
		if (slotIndex < 0 || slotIndex == lastQuickMoveSlot) {
			return;
		}
		lastQuickMoveSlot = slotIndex;
		ItemStack shown = this.menu.getTerminalSlots().get(slotIndex).getItem();
		if (shown.isEmpty()) {
			return;
		}
		Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
		ClientSender.sendToServer(new TerminalSlotClickPacket(itemId, 0, true));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		if (draggingQuickMove) {
			quickMoveHoveredSlot((int) event.x(), (int) event.y());
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
		if (draggingQuickMove) {
			draggingQuickMove = false;
			lastQuickMoveSlot = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void scrollToMouse(double mouseY) {
		scrollbarDirty = true;
		int maxScroll = this.menu.getMaxScroll();
		if (maxScroll <= 0) return;
		int trackHeight = PortableTerminalLayout.scrollbarHeight(this.rows);
		int inner = trackHeight - 2;
		int thumbH = Math.max(SCROLL_THUMB_MIN,
			inner * this.rows / Math.max(1, Math.max(this.menu.getTotalRows(), this.rows)));
		int trackTop = this.topPos + SCROLLBAR_TOP + 1 + thumbH / 2;
		double ratio = (mouseY - trackTop) / (double) (inner - thumbH);
		this.menu.setScrollOffset((int) Math.round(ratio * maxScroll));
	}

	private boolean isInSearchBox(double mouseX, double mouseY) {
		int x = this.leftPos + SEARCH_X;
		int y = this.topPos + SEARCH_Y;
		return mouseX >= x && mouseX < x + SEARCH_WIDTH && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
	}

	private boolean isInScrollbar(double mouseX, double mouseY) {
		int x = this.leftPos + SCROLLBAR_LEFT;
		int y = this.topPos + SCROLLBAR_TOP;
		return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH
			&& mouseY >= y && mouseY < y + PortableTerminalLayout.scrollbarHeight(this.rows);
	}

	private int getHoveredTerminalSlot(int mouseX, int mouseY) {
		int termX = this.leftPos + TERM_LEFT;
		int termY = this.topPos + TERM_TOP;
		if (mouseX < termX || mouseX >= termX + COLS * SLOT_SIZE
			|| mouseY < termY || mouseY >= termY + this.rows * SLOT_SIZE) {
			return -1;
		}
		int col = (mouseX - termX) / SLOT_SIZE;
		int row = (mouseY - termY) / SLOT_SIZE;
		if (col < 0 || col >= COLS || row < 0 || row >= this.rows) {
			return -1;
		}
		return row * COLS + col;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int termX = this.leftPos + TERM_LEFT;
		int termY = this.topPos + TERM_TOP;
		int termW = COLS * SLOT_SIZE + SCROLLBAR_WIDTH + 4;
		int termH = this.rows * SLOT_SIZE;

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

	/**
	 * 左侧的方块贴图按钮。
	 *
	 * <p>只画自己的贴图：底图 + 可选箭头 + 可选选中高亮。永不禁用（{@code active} 一直为 true），
	 * 因此不存在"置灰"外观；被选中的排序项改用高亮贴图表意。也没有任何悬浮提示。</p>
	 */
	private class IconButton extends AbstractButton {
		private final Supplier<Identifier> arrow;
		private final BooleanSupplier highlighted;
		private final Runnable action;
		/** 排序字段索引；行数按键为负数。 */
		private final int sortIndex;

		IconButton(int x, int y, Supplier<Identifier> arrow, BooleanSupplier highlighted,
				Runnable action, int sortIndex) {
			super(x, y, BTN_SIZE, BTN_SIZE, Component.empty());
			this.arrow = arrow;
			this.highlighted = highlighted;
			this.action = action;
			this.sortIndex = sortIndex;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
			if (this.sortIndex >= 0) {
				// 底图 = 该排序类型自己的图标（名称 / 数量 / ID / 模组）
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED,
					PortableTerminalLayout.sortIconSprite(this.sortIndex),
					this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
				// 右下角角标 = 当前状态；正序朝上、倒序朝下，未选中则不画
				Identifier corner = sortCornerArrow(this.sortIndex);
				if (corner != null) {
					int cornerSize = PortableTerminalLayout.CORNER_SIZE;
					gfx.blitSprite(RenderPipelines.GUI_TEXTURED, corner,
						this.getX() + BTN_SIZE - cornerSize - 1,
						this.getY() + BTN_SIZE - cornerSize - 1,
						cornerSize, cornerSize);
				}
				// 悬浮提示：类型 + 当前方向，例如「数量：由多到少」
				Component tip = sortTooltip(this.sortIndex);
				if (!tip.equals(PortableTerminalScreen.this.lastTooltip)) {
					PortableTerminalScreen.this.lastTooltip = tip;
					this.setTooltip(Tooltip.create(tip));
				}
				if (this.highlighted.getAsBoolean()) {
					gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.HIGHLIGHT_SPRITE,
						this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
				}
			} else {
				// 行数按键：通用按钮底 + 大箭头
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.BUTTON_SPRITE,
					this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
				Identifier icon = this.arrow.get();
				if (icon != null) {
					gfx.blitSprite(RenderPipelines.GUI_TEXTURED, icon,
						this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
				}
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			// 方块按钮，无需朗读文本
		}
	}
}
