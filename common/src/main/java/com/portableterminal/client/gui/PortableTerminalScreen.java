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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal GUI.
 *
 * <p>The whole panel — frame, slot wells and the scrollbar track — comes from a baked sprite
 * ({@code terminal/rows_N}), one sprite per supported row count. The sprite's four corners are
 * literal copies of vanilla's {@code generic_54.png} corners, so the rounded corners match
 * containers exactly, and slot cells are vanilla's own {@code container/slot.png}. Nothing is drawn
 * from raw rectangles any more, which is also what removes the stray grey block that used to
 * stick out past the slot grid.</p>
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

	private static final int SEARCH_X = PortableTerminalLayout.SEARCH_X;
	private static final int SEARCH_Y = PortableTerminalLayout.SEARCH_Y;
	private static final int SEARCH_WIDTH = PortableTerminalLayout.SEARCH_WIDTH;
	private static final int SEARCH_HEIGHT = PortableTerminalLayout.SEARCH_HEIGHT;

	private static final int SORT_BTN_X = PortableTerminalLayout.SORT_BTN_X;
	private static final int SORT_BTN_Y = PortableTerminalLayout.SORT_BTN_Y;
	private static final int SORT_BTN_W = PortableTerminalLayout.SORT_BTN_W;
	private static final int SORT_BTN_H = PortableTerminalLayout.SORT_BTN_H;
	private static final int SORT_BTN_GAP = PortableTerminalLayout.SORT_BTN_GAP;
	private static final int ROWS_BTN_Y = PortableTerminalLayout.ROWS_BTN_Y;

	private static final int COL_SCROLL_BG     = 0xFF000000;
	private static final int COL_SCROLL_THUMB  = 0xFFC6C6C6;
	private static final int COL_SCROLL_THUMB2 = 0xFF808080;
	private static final int COL_TEXT          = 0xFF404040;

	private final int rows;
	private final int guiWidth;
	private final int guiHeight;

	private EditBox searchBox;
	private String lastSearchText = "";
	private boolean draggingScrollbar = false;
	/** Shift + left button drag over the terminal slots: quick move every slot entered. */
	private boolean draggingQuickMove = false;
	private int lastQuickMoveSlot = -1;

	// 滚动条状态缓存：仅在用户主动滚动时更新，数据同步不触发重算，彻底避免闪烁
	private int cachedTotalRows;
	private int cachedScrollOffset;
	private int cachedMaxScroll;
	private boolean scrollbarDirty = true;

	private final List<Button> sortButtons = new ArrayList<>();
	private Button rowsButton;

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

	/** 左侧控件全部用原版 Button：自带点击音效与禁用态贴图。 */
	private void createButtons() {
		sortButtons.clear();
		int count = this.menu.getSortCount();
		for (int i = 0; i < count; i++) {
			final int index = i;
			Button button = Button.builder(
					sortButtonLabel(i),
					b -> cycleSort(index))
				.bounds(this.leftPos + SORT_BTN_X,
					this.topPos + SORT_BTN_Y + i * (SORT_BTN_H + SORT_BTN_GAP),
					SORT_BTN_W, SORT_BTN_H)
				.build();
			button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.translatable("gui.portableterminal.sort.tooltip")));
			sortButtons.add(button);
			this.addRenderableWidget(button);
		}

		rowsButton = Button.builder(
				Component.translatable("gui.portableterminal.rows", this.rows),
				b -> cycleRows())
			.bounds(this.leftPos + SORT_BTN_X, this.topPos + ROWS_BTN_Y, SORT_BTN_W, SORT_BTN_H)
			.build();
		rowsButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
			Component.translatable("gui.portableterminal.rows.tooltip")));
		this.addRenderableWidget(rowsButton);

		refreshSortButtons();
	}

	/** 选中的那一项显示为原版禁用态（置灰），但仍然可以点击以切换升序/降序。 */
	private void refreshSortButtons() {
		for (int i = 0; i < sortButtons.size(); i++) {
			Button button = sortButtons.get(i);
			button.setMessage(sortButtonLabel(i));
			button.active = this.menu.getSortDir(i) == PortableTerminalMenu.SortDirection.NONE;
		}
	}

	private Component sortButtonLabel(int index) {
		String arrow = switch (this.menu.getSortDir(index)) {
			case NONE -> "";
			case ASC -> " ^";
			case DESC -> " v";
		};
		return Component.literal(this.menu.getSortName(index).getString() + arrow);
	}

	private void cycleSort(int index) {
		this.menu.cycleSortDir(index);
		refreshSortButtons();
	}

	/** 切换显示行数：写进配置并重新开一次终端，服务端据此重建菜单。 */
	private void cycleRows() {
		int next = this.rows >= PortableTerminalLayout.MAX_ROWS
			? PortableTerminalLayout.MIN_ROWS
			: this.rows + 1;
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

	// ==================== 背景：整块贴图 ====================

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// 面板 + 全部槽位（含滚动条轨道）都在贴图里，见类注释
		gfx.blit(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.panelSprite(this.rows),
			this.leftPos, this.topPos, 0f, 0f, this.guiWidth, this.guiHeight, this.guiWidth, this.guiHeight);

		// 副手槽在面板之外，单独贴一次槽位贴图
		gfx.blit(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.SLOT_SPRITE,
			this.leftPos + PortableTerminalLayout.OFFHAND_X - 1,
			this.topPos + PortableTerminalLayout.offhandY(this.rows) - 1,
			0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

		drawScrollbarThumb(gfx);
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

		// 排序按钮：选中的那个 active=false（显示为禁用态），在交给原版控件之前先处理，
		// 这样「外观置灰」不会把「点击切换升降序」一起吃掉。
		if (button == 0) {
			int sortIdx = getClickedSortButton(mouseX, mouseY);
			if (sortIdx >= 0) {
				cycleSort(sortIdx);
				playButtonClick();
				return true;
			}
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

			net.minecraft.resources.Identifier itemId =
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
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
		net.minecraft.resources.Identifier itemId =
			net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
		ClientSender.sendToServer(new TerminalSlotClickPacket(itemId, 0, true));
	}

	private void playButtonClick() {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
			net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
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

	/** 返回点击到的排序按钮索引（0..count-1），未命中返回 -1 */
	private int getClickedSortButton(double mouseX, double mouseY) {
		int x = this.leftPos + SORT_BTN_X;
		if (mouseX < x || mouseX >= x + SORT_BTN_W) return -1;
		for (int i = 0; i < this.menu.getSortCount(); i++) {
			int y = this.topPos + SORT_BTN_Y + i * (SORT_BTN_H + SORT_BTN_GAP);
			if (mouseY >= y && mouseY < y + SORT_BTN_H) return i;
		}
		return -1;
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
}
