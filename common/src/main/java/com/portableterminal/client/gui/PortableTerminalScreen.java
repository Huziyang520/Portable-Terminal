// Portions of this code (terminal GUI layout, slot rendering)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.
package com.portableterminal.client.gui;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.config.ConfigSchema;
import com.portableterminal.config.ModConfig;
import com.portableterminal.menu.PortableTerminalLayout;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.TerminalCraftActionPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import com.portableterminal.platform.ClientSender;
import com.portableterminal.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The terminal GUI.
 *
 * <p>面板贴图按「表头 / 一行槽位 / 合成带 / 页脚」四段 1:1 分片：槽位带按行数重复，合成带只在
 * 合成功能开启时出现，因此同一套代码能画出任意行数、两种形态的界面。四角是原版
 * {@code generic_54.png} 的逐像素拷贝，格子底图是原版 {@code container/slot.png} 原样。</p>
 *
 * <p>左侧一列方块按钮是自绘贴图按钮。没有禁用态：状态靠图标与悬浮提示表达。</p>
 *
 * <p>合成带开启时挂原版 {@link CraftingRecipeBookComponent}：配方书按钮、面板右移避让、
 * ghost 材料显示与「点配方自动摆放」全部走原版那一套，取料来源由本模组的设置决定。</p>
 */
public class PortableTerminalScreen extends AbstractContainerScreen<PortableTerminalMenu>
	implements RecipeUpdateListener {

	private static final int COLS = PortableTerminalLayout.COLS;
	private static final int SLOT_SIZE = PortableTerminalLayout.SLOT_SIZE;
	private static final int TERM_LEFT = PortableTerminalLayout.TERM_LEFT;
	private static final int TERM_TOP = PortableTerminalLayout.TERM_TOP;
	private static final int SCROLLBAR_LEFT = PortableTerminalLayout.SCROLLBAR_LEFT;
	private static final int SCROLLBAR_WIDTH = PortableTerminalLayout.SCROLLBAR_WIDTH;
	private static final int SCROLLBAR_TOP = PortableTerminalLayout.SCROLLBAR_TOP;
	private static final int SCROLL_THUMB_MIN = PortableTerminalLayout.SCROLL_THUMB_MIN;
	private static final int SCROLL_THUMB_INSET = PortableTerminalLayout.SCROLL_THUMB_INSET;
	private static final int BTN_SIZE = PortableTerminalLayout.BTN_SIZE;

	private static final int SEARCH_X = PortableTerminalLayout.SEARCH_X;
	private static final int SEARCH_Y = PortableTerminalLayout.SEARCH_Y;
	private static final int SEARCH_WIDTH = PortableTerminalLayout.SEARCH_WIDTH;
	private static final int SEARCH_HEIGHT = PortableTerminalLayout.SEARCH_HEIGHT;

	/** 配方书与面板之间留给左侧按钮列的空隙：书右缘 + 它 + 面板左缘。 */
	private static final int RECIPE_BOOK_GAP = 34;
	/** 原版配方书的 x 偏移（{@code RecipeBookComponent} 内部常量 86，界面宽度 147）。 */
	private static final int RECIPE_BOOK_OFFSET = 86;

	private static final int COL_EDGE          = 0xFF555555;
	private static final int COL_THUMB         = 0xFFC6C6C6;
	private static final int COL_HIGHLIGHT     = 0xFFFFFFFF;
	private static final int COL_TEXT          = 0xFF404040;

	/** 合成带是否显示（来自客户端配置，打开界面时定死；改配置需要重开终端）。 */
	private final boolean crafting;
	private final CraftingRecipeBookComponent recipeBook;
	private boolean widthTooNarrow;

	private int rows;
	private int guiWidth;
	private int guiHeight;

	private EditBox searchBox;
	private String lastSearchText = "";
	private int lastMouseX;
	private int lastMouseY;
	private boolean draggingScrollbar = false;
	/** 左键按下时落到的终端槽位（-1 = 未按下 / 没有落在槽位上）。 */
	private int pressSlot = -1;
	/** 按下时是否按住 shift；拖拽经过新槽位时沿用这个取值，保持与首格动作一致。 */
	private boolean pressShift = false;
	/** 指针真的滑到另一格之后才升级成拖拽：单击路径完全不受影响。 */
	private boolean draggingQuickMove = false;
	private int lastQuickMoveSlot = -1;

	// 滚动条状态缓存：仅在用户主动滚动时更新，数据同步不触发重算，彻底避免闪烁
	private int cachedTotalRows;
	private int cachedScrollOffset;
	private int cachedMaxScroll;
	private boolean scrollbarDirty = true;

	public PortableTerminalScreen(PortableTerminalMenu menu, Inventory playerInventory, Component title) {
		this(menu, playerInventory, title, ModConfig.craftingEnabled);
	}

	private PortableTerminalScreen(PortableTerminalMenu menu, Inventory playerInventory, Component title,
			boolean crafting) {
		super(menu, playerInventory, title, PortableTerminalLayout.IMAGE_WIDTH,
			PortableTerminalLayout.imageHeight(menu.getRows(), crafting));
		this.crafting = crafting;
		this.recipeBook = new CraftingRecipeBookComponent(menu);
		this.rows = PortableTerminalLayout.clampRows(menu.getRows());
		this.guiWidth = PortableTerminalLayout.IMAGE_WIDTH;
		this.guiHeight = PortableTerminalLayout.imageHeight(this.rows, crafting);
		this.cachedTotalRows = this.rows;
		this.titleLabelX = PortableTerminalLayout.PADDING;
		this.titleLabelY = 5;
		this.inventoryLabelX = PortableTerminalLayout.PADDING;
		this.inventoryLabelY = PortableTerminalLayout.invTop(this.rows, crafting) - 11;
	}

	// ==================== 初始化 ====================

	/** 合成带 + 配方书都开启时，配方书才有意义。 */
	private boolean recipeBookEnabled() {
		return this.crafting && ModConfig.showRecipeBook;
	}

	private boolean recipeBookOpen() {
		return recipeBookEnabled() && this.recipeBook.isVisible();
	}

	@Override
	protected void init() {
		super.init();
		// 面板高度跟着显示行数走。26.x 的 imageHeight 是 final，不能改，所以自己按 guiHeight 居中。
		this.rows = Math.min(this.rows, maxRowsForScreen());
		this.menu.applyViewLayout(this.rows, this.crafting);
		// 客户端自己那份菜单不会收到打开包里的偏好，这里按本地状态对齐一次（服务端由打开包带上）
		this.menu.setCraftingModes(ModConfig.craftSource, ModConfig.clearDestination);
		this.guiHeight = PortableTerminalLayout.imageHeight(this.rows, this.crafting);
		this.inventoryLabelY = PortableTerminalLayout.invTop(this.rows, this.crafting) - 11;
		this.topPos = (this.height - this.guiHeight) / 2;

		this.widthTooNarrow = this.width < 379;
		this.recipeBook.init(this.width, this.height, this.minecraft, this.widthTooNarrow);
		this.addWidget(this.recipeBook);
		this.leftPos = screenLeft();

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

		addRecipeBookButton();
		addClearGridButton();
		createButtons();
		this.menu.updateTerminalSlots();
	}

	/**
	 * 行数上限按屏幕高度算：整块面板（表头 + 槽位带 + 合成带 + 页脚）不得超出屏幕，
	 * 否则会把背包行裁掉。下限仍是 3 行。
	 */
	private int maxRowsForScreen() {
		int fixed = PortableTerminalLayout.imageHeight(0, this.crafting);
		int fit = (this.height - fixed) / SLOT_SIZE;
		return Math.max(PortableTerminalLayout.MIN_ROWS,
			Math.min(PortableTerminalLayout.MAX_ROWS, fit));
	}

	/**
	 * 配方书打开且屏幕够宽时面板整体右移，给书与左侧按钮列让出位置。
	 *
	 * <p>书的实际位置由 {@code (width - 147) / 2 - 86} 决定，所以面板左缘必须按书的右缘算，
	 * 不能沿用原版 {@code updateScreenPosition}（它默认界面紧贴书，会把左侧按钮压在书下面）。</p>
	 */
	private int screenLeft() {
		if (recipeBookOpen() && !this.widthTooNarrow) {
			int bookLeft = (this.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - RECIPE_BOOK_OFFSET;
			int bookRight = bookLeft + RecipeBookComponent.IMAGE_WIDTH;
			int left = bookRight + RECIPE_BOOK_GAP;
			int maxLeft = this.width - this.guiWidth;
			return Math.max(0, Math.min(left, maxLeft));
		}
		return (this.width - this.guiWidth) / 2;
	}

	/** 配方书按钮贴在合成带左端（与工作台一样靠左）；关闭合成时挪出屏幕。 */
	private ScreenPosition recipeBookButtonPosition() {
		if (!this.crafting) {
			return new ScreenPosition(-10000, -10000);
		}
		int bandTop = this.topPos + PortableTerminalLayout.craftBandTop(this.rows);
		return new ScreenPosition(this.leftPos + PortableTerminalLayout.RECIPE_BUTTON_X,
			bandTop + PortableTerminalLayout.RECIPE_BUTTON_Y);
	}

	private void addRecipeBookButton() {
		if (!recipeBookEnabled()) {
			return;
		}
		ScreenPosition pos = recipeBookButtonPosition();
		this.addRenderableWidget(new ImageButton(pos.x(), pos.y(),
			PortableTerminalLayout.RECIPE_BUTTON_WIDTH, PortableTerminalLayout.RECIPE_BUTTON_HEIGHT,
			RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
				this.recipeBook.toggleVisibility();
				// 面板位置、搜索框、左侧按钮、配方书按钮全都跟着书的状态走，重建一次最稳。
				this.rebuildWidgets();
			}));
	}

	/** 合成带右上角的「清空合成栏」按键。 */
	private void addClearGridButton() {
		if (!this.crafting) {
			return;
		}
		int bandTop = this.topPos + PortableTerminalLayout.craftBandTop(this.rows);
		this.addRenderableWidget(new IconButton(
			this.leftPos + PortableTerminalLayout.CLEAR_GRID_BTN_X,
			bandTop + PortableTerminalLayout.CLEAR_GRID_BTN_Y,
			PortableTerminalLayout.CLEAR_GRID_SPRITE, true,
			() -> Component.translatable("gui.portableterminal.clear_grid"),
			() -> false,
			this::clearCraftingGrid));
	}

	private void createButtons() {
		int x = this.leftPos + PortableTerminalLayout.SORT_BTN_X;

		// 排序类型：图标就是当前类型，点击循环切换
		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.SORT_MODE_BTN_Y,
			() -> PortableTerminalLayout.sortIconSprite(this.menu.getSortMode()), false,
			() -> this.menu.getSortName(this.menu.getSortMode()),
			() -> false,
			() -> this.menu.cycleSortMode()));

		// 排序方向：正序 / 倒序
		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.SORT_DIR_BTN_Y,
			() -> this.menu.isSortAscending()
				? PortableTerminalLayout.SORT_ASC_SPRITE : PortableTerminalLayout.SORT_DESC_SPRITE, true,
			() -> Component.translatable(this.menu.isSortAscending()
				? "gui.portableterminal.sort.asc" : "gui.portableterminal.sort.desc"),
			() -> false,
			() -> this.menu.toggleSortDirection()));

		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.ROWS_UP_BTN_Y,
			() -> PortableTerminalLayout.PLUS_SPRITE, true,
			() -> Component.translatable("gui.portableterminal.rows.up"),
			() -> false,
			() -> changeRows(1)));
		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.ROWS_DOWN_BTN_Y,
			() -> PortableTerminalLayout.MINUS_SPRITE, true,
			() -> Component.translatable("gui.portableterminal.rows.down"),
			() -> false,
			() -> changeRows(-1)));

		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.CRAFT_SRC_BTN_Y,
			() -> PortableTerminalLayout.CRAFT_SOURCE_SPRITES[this.menu.getCraftSource()], true,
			() -> Component.translatable("gui.portableterminal.craft_source."
				+ ConfigSchema.CRAFT_SOURCE_KEYS[this.menu.getCraftSource()]),
			() -> false,
			this::cycleCraftSource));

		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.CLEAR_DST_BTN_Y,
			() -> PortableTerminalLayout.CLEAR_DEST_SPRITES[this.menu.getClearDestination()], true,
			() -> Component.translatable("gui.portableterminal.clear_dest."
				+ ConfigSchema.CLEAR_DEST_KEYS[this.menu.getClearDestination()]),
			() -> false,
			this::toggleClearDestination));

		this.addRenderableWidget(new IconButton(
			x, this.topPos + PortableTerminalLayout.CONFIG_BTN_Y,
			() -> PortableTerminalLayout.CONFIG_SPRITE, true,
			() -> Component.translatable("gui.portableterminal.open_config"),
			() -> false,
			() -> Services.PLATFORM.openConfigScreen(this)));
	}

	private void cycleCraftSource() {
		int next = (this.menu.getCraftSource() + 1) % ConfigSchema.CRAFT_SOURCE_COUNT;
		this.menu.setCraftingModes(next, this.menu.getClearDestination());
		ModConfig.craftSource = next;
		ModConfig.saveState();
		ClientSender.sendToServer(TerminalCraftActionPacket.setModes(next, this.menu.getClearDestination()));
	}

	private void toggleClearDestination() {
		int next = (this.menu.getClearDestination() + 1) % ConfigSchema.CLEAR_DEST_COUNT;
		this.menu.setCraftingModes(this.menu.getCraftSource(), next);
		ModConfig.clearDestination = next;
		ModConfig.saveState();
		ClientSender.sendToServer(TerminalCraftActionPacket.setModes(this.menu.getCraftSource(), next));
	}

	private void clearCraftingGrid() {
		ClientSender.sendToServer(TerminalCraftActionPacket.clearGrid());
	}

	/** 排序按钮的悬浮提示：类型 + 当前方向，例如「数量：由多到少」。 */
	private void cycleSort(int index) {
		this.menu.cycleSortMode();
	}

	/**
	 * 切换显示行数。
	 *
	 * <p>槽位总数固定，行数只是布局：就地重建槽位与面板尺寸即可，**不重开界面**。
	 * 重开（{@code Minecraft.setScreen}）会让原版 {@code MouseHandler.releaseMouse()}
	 * 把光标挪到窗口正中，那不是按键该有的行为。</p>
	 */
	private void changeRows(int delta) {
		int next = Math.max(PortableTerminalLayout.MIN_ROWS,
			Math.min(maxRowsForScreen(), PortableTerminalLayout.clampRows(this.rows + delta)));
		if (next == this.rows) {
			return;
		}
		this.rows = next;
		this.scrollbarDirty = true;
		ModConfig.setRows(next);
		this.menu.applyViewLayout(next, this.crafting);
		this.guiHeight = PortableTerminalLayout.imageHeight(this.rows, this.crafting);
		this.inventoryLabelY = PortableTerminalLayout.invTop(next, this.crafting) - 11;
		this.topPos = (this.height - this.guiHeight) / 2;
		this.leftPos = screenLeft();
		this.rebuildWidgets();
	}

	private void onItemsSynced(List<ItemStack> items) {
		this.menu.setAggregatedItemsFromServer(items);
		// 物品数变了，滑块长度/位置都得跟着重算，否则会一直显示开界面那一帧的旧比例。
		this.scrollbarDirty = true;
	}

	private void onSearchChanged(String value) {
		this.lastSearchText = value;
		this.menu.applySearchFilter(value);
		// 搜索结果的行数变了，滑块长度要跟着重算。
		this.scrollbarDirty = true;
	}

	@Override
	public void onClose() {
		PortableTerminalClient.setSyncReceiver(null);
		super.onClose();
	}

	// ==================== 配方书 ====================

	@Override
	public void recipesUpdated() {
		this.recipeBook.recipesUpdated();
	}

	@Override
	public void fillGhostRecipe(RecipeDisplay display) {
		this.recipeBook.fillGhostRecipe(display);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.recipeBook.tick();
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
		super.slotClicked(slot, slotId, buttonNum, containerInput);
		this.recipeBook.slotClicked(slot);
	}

	@Override
	public boolean isInputCaptured() {
		return super.isInputCaptured() || this.recipeBook.capturesInput();
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.recipeBook.charTyped(event) || super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.searchBox != null && this.getFocused() == this.searchBox) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				this.searchBox.setFocused(false);
				this.setFocused(null);
				return true;
			}
			this.searchBox.keyPressed(event);
			return true;
		}
		// 配方书自己的搜索框与「ESC 关书」先处理，剩下的才交回原版（含 ESC 关界面）
		if (this.recipeBook.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
		// 屏幕太窄时配方书压住面板，此时面板上的槽位不该再响应悬停
		return (!this.widthTooNarrow || !recipeBookOpen()) && super.isHovering(left, top, w, h, xm, ym);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		boolean outside = mx < xo || my < yo || mx >= xo + this.guiWidth || my >= yo + this.guiHeight;
		return this.recipeBook.hasClickedOutside(mx, my, this.leftPos, this.topPos, this.guiWidth, this.guiHeight)
			&& outside;
	}

	// ==================== 背景：四段面板贴图 ====================

	/**
	 * 外部 UI（JEI）需要避让的区域，屏幕坐标系：面板本身、面板左侧那一列按钮，以及打开着的配方书。
	 */
	public List<Rect2i> getExclusionAreas() {
		List<Rect2i> areas = new ArrayList<>();
		areas.add(new Rect2i(this.leftPos, this.topPos, this.guiWidth, this.guiHeight));
		int colLeft = this.leftPos + PortableTerminalLayout.SORT_BTN_X;
		int colTop = this.topPos + PortableTerminalLayout.SORT_BTN_Y;
		int colRight = this.leftPos + PortableTerminalLayout.SORT_BTN_X + PortableTerminalLayout.BTN_SIZE;
		int colBottom = this.topPos + Math.max(PortableTerminalLayout.buttonColumnBottom(),
			PortableTerminalLayout.offhandY(this.rows, this.crafting) + SLOT_SIZE);
		areas.add(new Rect2i(colLeft, colTop, colRight - colLeft, colBottom - colTop));
		if (recipeBookOpen()) {
			int bookLeft = (this.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - RECIPE_BOOK_OFFSET;
			int bookTop = (this.height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
			areas.add(new Rect2i(bookLeft, bookTop,
				RecipeBookComponent.IMAGE_WIDTH, RecipeBookComponent.IMAGE_HEIGHT));
		}
		return areas;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// 26.x 的 Screen.extractBackground 只做两件事：容器界面走 extractTransparentBackground（一层压暗
		// 渐变）+ 延迟字幕。26.3 上实测这层压暗没出来（26.1/26.2 用同样的 super 调用是正常的），
		// 所以这里不依赖 super，按原版同一组颜色自绘，并按「菜单背景模糊」设置补上模糊；观感与其它区间一致。
		if (Minecraft.getInstance().options.getMenuBackgroundBlurriness() > 0) {
			gfx.blurBeforeThisStratum();
		}
		gfx.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
		Minecraft.getInstance().gui.hud.extractDeferredSubtitles();
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;

		Identifier sprite = this.crafting
			? PortableTerminalLayout.PANEL_CRAFTING_SPRITE : PortableTerminalLayout.PANEL_SPRITE;
		int srcW = PortableTerminalLayout.PANEL_SOURCE_WIDTH;
		int srcH = this.crafting
			? PortableTerminalLayout.PANEL_CRAFTING_SOURCE_HEIGHT : PortableTerminalLayout.PANEL_SOURCE_HEIGHT;
		int footerSrcY = this.crafting
			? PortableTerminalLayout.PANEL_FOOTER_CRAFTING_SRC_Y
			: PortableTerminalLayout.PANEL_ROW_BAND_SRC_Y + SLOT_SIZE;
		int x = this.leftPos;
		int y = this.topPos;

		// 同一张贴图分四段 1:1 贴：表头 / 一行槽位（按行数重复）/ 合成带 / 页脚。
		// 不能用九宫格缩放：它会按目标高度的一半钳制每条边，页脚高于面板一半时中间带
		// 会被取成长度不对的片段，格子与物品就会错位（行数越少越明显）。
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
			srcW, srcH, 0, 0, x, y, this.guiWidth, PortableTerminalLayout.PANEL_HEADER_HEIGHT);
		for (int row = 0; row < this.rows; row++) {
			gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				srcW, srcH, 0, PortableTerminalLayout.PANEL_ROW_BAND_SRC_Y,
				x, y + PortableTerminalLayout.PANEL_HEADER_HEIGHT + row * SLOT_SIZE,
				this.guiWidth, SLOT_SIZE);
		}
		if (this.crafting) {
			gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				srcW, srcH, 0, PortableTerminalLayout.PANEL_CRAFT_BAND_SRC_Y,
				x, y + PortableTerminalLayout.craftBandTop(this.rows),
				this.guiWidth, PortableTerminalLayout.CRAFT_BAND_HEIGHT);
		}
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
			srcW, srcH, 0, footerSrcY,
			x, y + PortableTerminalLayout.footerTop(this.rows, this.crafting),
			this.guiWidth, PortableTerminalLayout.PANEL_FOOTER_HEIGHT);

		// 副手槽在面板之外：原版圆角小面板外框 + 槽位底
		int offX = x + PortableTerminalLayout.OFFHAND_X;
		int offY = y + PortableTerminalLayout.offhandY(this.rows, this.crafting);
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

	/** 滑块：8px 宽、带深色描边与顶部高光，居中在 12px 的凹槽里。 */
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
		int thumbH = Math.min(inner, Math.max(SCROLL_THUMB_MIN, inner * this.rows / Math.max(1, cachedTotalRows)));
		int thumbY = sy + 1;
		if (cachedMaxScroll > 0) {
			thumbY = sy + 1 + (inner - thumbH) * cachedScrollOffset / cachedMaxScroll;
		}
		int x0 = sx + SCROLL_THUMB_INSET;
		int x1 = sx + SCROLLBAR_WIDTH - SCROLL_THUMB_INSET;
		gfx.fill(x0, thumbY, x1, thumbY + thumbH, COL_EDGE);
		gfx.fill(x0 + 1, thumbY + 1, x1 - 1, thumbY + thumbH - 1, COL_THUMB);
		gfx.fill(x0 + 1, thumbY + 1, x1 - 1, thumbY + 2, COL_HIGHLIGHT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// 与 AbstractRecipeBookScreen 同一顺序：内容 → 配方书 → 手持物品 → 提示框（含 ghost）
		if (recipeBookOpen() && this.widthTooNarrow) {
			this.extractBackground(gfx, mouseX, mouseY, partialTick);
		} else {
			this.extractContents(gfx, mouseX, mouseY, partialTick);
		}
		if (recipeBookOpen()) {
			gfx.nextStratum();
			this.recipeBook.extractRenderState(gfx, mouseX, mouseY, partialTick);
		}
		gfx.nextStratum();
		this.extractCarriedItem(gfx, mouseX, mouseY);
		this.extractTooltip(gfx, mouseX, mouseY);
		if (recipeBookOpen()) {
			this.recipeBook.extractTooltip(gfx, mouseX, mouseY, this.hoveredSlot);
		}
	}

	@Override
	protected void extractSlots(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		super.extractSlots(gfx, mouseX, mouseY);
		// 配方书点过配方后，在网格上叠一层半透明材料提示
		if (recipeBookOpen()) {
			this.recipeBook.extractGhostRecipe(gfx, false);
		}
	}

	@Override
	public void extractLabels(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		gfx.text(this.font, this.title, this.titleLabelX, this.titleLabelY, COL_TEXT, false);
		gfx.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, COL_TEXT, false);
	}

	// ==================== 鼠标交互 ====================

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean inside) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		// 26.3 起输入层由 GLFW 换成 SDL：鼠标事件里的修饰键在个别情况下取不到，再兜一层 SDL
		// 键盘实时状态，保证「按住 shift 拖动快速转移」稳定触发（与旧版 Screen.hasShiftDown 等价）。
		boolean hasShift = event.hasShiftDown()
			|| InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
			|| InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);

		if (recipeBookOpen()) {
			if (this.recipeBook.mouseClicked(event, inside)) {
				this.setFocused(this.recipeBook);
				return true;
			}
			if (this.widthTooNarrow) {
				// 书压住整个面板：除书以外的地方不再响应
				return true;
			}
			// 书左边的空白一并吞掉，别穿透到面板外的按钮
			if (mouseX < this.leftPos - RECIPE_BOOK_GAP) {
				return true;
			}
		}

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

		if (isInScrollbar(mouseX, mouseY) && button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}

		int hoveredSlot = getHoveredTerminalSlot((int) mouseX, (int) mouseY);
		if (hoveredSlot >= 0 && button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) {
			// 单击路径必须保持原样：一次点击只发一次 TerminalSlotClickPacket。
			// 拖拽只是「按下之后再滑到别的格子」时附加的行为（见 mouseDragged），
			// 所以这里绝不能提前把 draggingQuickMove 置真，否则释放事件和单点语义都会被改掉。
			pressSlot = hoveredSlot;
			pressShift = hasShift;
			draggingQuickMove = false;
			lastQuickMoveSlot = hoveredSlot;
			clickTerminalSlot(hoveredSlot, button, hasShift);
			return true;
		}

		return super.mouseClicked(event, inside);
	}

	/** 对某个终端槽位执行一次点击动作（与单点完全相同；拖拽经过其它槽位时重复调用）。 */
	private void clickTerminalSlot(int slotIndex, int button, boolean hasShift) {
		ItemStack carried = this.menu.getCarried();
		if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE && !carried.isEmpty()) {
			ClientSender.sendToServer(TerminalSlotClickPacket.deposit());
			return;
		}

		ItemStack shown = this.menu.getTerminalSlots().get(slotIndex).getItem();
		if (shown.isEmpty()) {
			return;
		}

		Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem());
		ClientSender.sendToServer(new TerminalSlotClickPacket(itemId, wireButton(button), hasShift));
	}

	/** 拖拽经过的新槽位触发一次点击动作；同一格只触发一次。 */
	private void quickMoveHoveredSlot(int mouseX, int mouseY) {
		int slotIndex = getHoveredTerminalSlot(mouseX, mouseY);
		if (slotIndex < 0 || slotIndex == lastQuickMoveSlot) {
			return;
		}
		lastQuickMoveSlot = slotIndex;
		clickTerminalSlot(slotIndex, 0, pressShift);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.recipeBook.mouseDragged(event, dragX, dragY)) {
			return true;
		}
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		// 拖拽只在「已按下某格 + 指针进入另一格」时升级，单击因此保持原有的单发语义。
		if (pressSlot >= 0 && pressShift && !draggingQuickMove) {
			int slotIndex = getHoveredTerminalSlot((int) event.x(), (int) event.y());
			if (slotIndex >= 0 && slotIndex != pressSlot) {
				draggingQuickMove = true;
				quickMoveHoveredSlot((int) event.x(), (int) event.y());
				return true;
			}
		} else if (draggingQuickMove) {
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
		// 释放语义一律交回原版：这里只是清掉自己记的按压状态，绝不吞事件。
		pressSlot = -1;
		draggingQuickMove = false;
		lastQuickMoveSlot = -1;
		return super.mouseReleased(event);
	}

	private void scrollToMouse(double mouseY) {
		scrollbarDirty = true;
		int maxScroll = this.menu.getMaxScroll();
		if (maxScroll <= 0) return;
		int trackHeight = PortableTerminalLayout.scrollbarHeight(this.rows);
		int inner = trackHeight - 2;
		int thumbH = Math.min(inner, Math.max(SCROLL_THUMB_MIN,
			inner * this.rows / Math.max(1, Math.max(this.menu.getTotalRows(), this.rows))));
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

	/**
	 * 终端格命中的唯一依据是槽位自己的矩形：槽位坐标由布局给出，屏幕上再偏移面板原点。
	 * 不再按常量重算网格，避免布局与命中区各算一套。
	 */
	private int getHoveredTerminalSlot(int mouseX, int mouseY) {
		List<PortableTerminalMenu.TerminalSlot> slots = this.menu.getTerminalSlots();
		for (int i = 0; i < slots.size(); i++) {
			PortableTerminalMenu.TerminalSlot slot = slots.get(i);
			if (slot.x < 0 || slot.y < 0) continue;
			int sx = this.leftPos + slot.x;
			int sy = this.topPos + slot.y;
			if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
				return i;
			}
		}
		return -1;
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
	 * <p>底图可选（排序类型键直接用类型图标当键面），上面叠图标，永不禁用；
	 * 状态全部靠图标与悬浮提示表达。</p>
	 */
	private class IconButton extends AbstractButton {
		private final Supplier<Identifier> icon;
		private final boolean drawBase;
		private final Supplier<Component> tooltip;
		private final BooleanSupplier highlighted;
		private final Runnable action;
		private Component lastTooltipText;

		IconButton(int x, int y, Supplier<Identifier> icon, boolean drawBase, Supplier<Component> tooltip,
				BooleanSupplier highlighted, Runnable action) {
			super(x, y, BTN_SIZE, BTN_SIZE, Component.empty());
			this.icon = icon;
			this.drawBase = drawBase;
			this.tooltip = tooltip;
			this.highlighted = highlighted;
			this.action = action;
		}

		IconButton(int x, int y, Identifier icon, boolean drawBase, Supplier<Component> tooltip,
				BooleanSupplier highlighted, Runnable action) {
			this(x, y, () -> icon, drawBase, tooltip, highlighted, action);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
			if (this.drawBase) {
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.BUTTON_SPRITE,
					this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
			}
			Identifier sprite = this.icon.get();
			if (sprite != null) {
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
					this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
			}
			if (this.highlighted.getAsBoolean()) {
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED, PortableTerminalLayout.HIGHLIGHT_SPRITE,
					this.getX(), this.getY(), BTN_SIZE, BTN_SIZE);
			}
			if (this.tooltip != null) {
				Component tip = this.tooltip.get();
				if (!tip.equals(this.lastTooltipText)) {
					this.lastTooltipText = tip;
					this.setTooltip(Tooltip.create(tip));
				}
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			// 方块按钮，无需朗读文本
		}
	}

	/**
	 * 协议里 0=左键（整组）、1=右键（半组）、其余按键原样。
	 * 输入层给的键码按版本不同（高版本用 SDL 编号），这里统一成协议值再发包。
	 */
	private static int wireButton(int button) {
		if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return 0;
		if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) return 1;
		return button;
	}
}
