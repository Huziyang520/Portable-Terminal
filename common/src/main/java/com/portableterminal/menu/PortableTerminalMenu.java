// Portions of this code (terminal item aggregation, container scanning, GUI layout)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.

package com.portableterminal.menu;

import com.portableterminal.PortableTerminalMod;

import com.portableterminal.config.ConfigSchema;
import com.portableterminal.config.ModConfig;
import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.inventory.VanillaContainerAccess;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.Services;
import com.portableterminal.util.PinyinSearch;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.recipebook.PlaceRecipeHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PortableTerminalMenu extends AbstractCraftingMenu {

	// 槽位坐标为 16x16 物品区左上角（遵循原版 +1 约定）。
	// 所有随行数变化的值一律走 PortableTerminalLayout，保证「菜单槽位 / 界面尺寸 / 贴图」三者永远一致。
	public static final int PADDING = PortableTerminalLayout.PADDING;
	public static final int SLOT_SIZE = PortableTerminalLayout.SLOT_SIZE;
	public static final int COLS = PortableTerminalLayout.COLS;
	public static final int TERM_LEFT = PortableTerminalLayout.TERM_LEFT;
	public static final int TERM_TOP = PortableTerminalLayout.TERM_TOP;
	public static final int INV_LEFT = PortableTerminalLayout.INV_LEFT;
	public static final int SCROLLBAR_LEFT = PortableTerminalLayout.SCROLLBAR_LEFT;
	public static final int SCROLLBAR_WIDTH = PortableTerminalLayout.SCROLLBAR_WIDTH;
	public static final int OFFHAND_X = PortableTerminalLayout.OFFHAND_X;

	// ==================== 槽位索引 ====================
	// 新增的合成槽一律排在末尾：终端 / 背包 / 快捷栏 / 副手的索引与顺序保持原样。
	public static final int PLAYER_INV_START = PortableTerminalLayout.MAX_ROWS * PortableTerminalLayout.COLS;
	public static final int PLAYER_MAIN_SLOTS = 27;
	public static final int PLAYER_HOTBAR_SLOTS = 9;
	public static final int OFFHAND_INDEX = PLAYER_INV_START + PLAYER_MAIN_SLOTS + PLAYER_HOTBAR_SLOTS;
	/** 背包（主背包 + 快捷栏，不含副手）的槽位区间，Shift 移动用。 */
	public static final int PLAYER_INV_END = OFFHAND_INDEX;
	public static final int CRAFT_SLOT_START = OFFHAND_INDEX + 1;
	public static final int CRAFT_SLOT_END = CRAFT_SLOT_START + PortableTerminalLayout.CRAFT_SLOT_COUNT;
	public static final int RESULT_SLOT_INDEX = CRAFT_SLOT_END;

	/**
	 * 终端槽位固定按最大行数创建，行数只影响「显示几行」。
	 * 这样改行数就只是客户端重排布局，不必重开界面（重开会让原版把光标拽回屏幕中心），
	 * 两端槽位总数也永远一致，不会出现同步包长度对不上导致的越界。
	 */
	public static final int CAPACITY_ROWS = PortableTerminalLayout.MAX_ROWS;
	/** 不可见行的槽位挪到屏幕外，否则会与上移后的背包槽重叠、被原版命中。 */
	private static final int HIDDEN_SLOT_X = -10000;
	private static final int HIDDEN_SLOT_Y = -10000;

	/** 当前显示行数（只影响布局，不影响槽位总数）。 */
	private int rows;
	/** 是否显示合成带：只影响槽位坐标与面板贴图，槽位本身两端永远存在。 */
	private boolean craftingVisible = true;
	/** 摆放配方期间不逐格重算结果，交给 finishPlacingRecipe 统一算一次。 */
	private boolean placingRecipe;
	/** 合成取料来源（需要服务端也知道，随打开包与切换包同步）。 */
	private int craftSource = ConfigSchema.DEFAULT_CRAFT_SOURCE;
	/** 清空合成栏的去向。 */
	private int clearDestination = ConfigSchema.DEFAULT_CLEAR_DEST;

	/** 合成网格与结果槽：直接持引用，不靠下标取，布局异常时也不会越界。 */
	private final List<Slot> craftGridSlots = new ArrayList<>();
	private Slot craftResultSlot;

	private static final Map<UUID, List<BlockPos>> PENDING_POSITIONS = new HashMap<>();
	private final List<BlockPos> containerPositions = new ArrayList<>();
	private final List<ItemAccess> containers = new ArrayList<>();
	private final Player player;
	private final List<ItemStack> aggregatedItems = new ArrayList<>();
	private final List<ItemStack> displayedItems = new ArrayList<>();

	// ===== 排序：一个类型 + 一个方向 =====
	public enum SortDirection {
		NONE, ASC, DESC
	}

	/** 0=名称, 1=数量, 2=ID, 3=模组；默认「数量 · 由多到少」。 */
	private int sortMode = 1;
	private boolean sortAscending = false;

	// 排序按钮翻译键（语言文件驱动）
	private static final String[] SORT_NAME_KEYS = {
		"gui.portableterminal.sort.name",
		"gui.portableterminal.sort.count",
		"gui.portableterminal.sort.id",
		"gui.portableterminal.sort.mod"
	};
	// 持久化用的语言无关 key
	private static final String[] SORT_PERSIST_KEYS = {"name", "count", "id", "mod"};

	private final List<TerminalSlot> terminalSlots = new ArrayList<>();
	private final Inventory playerInventory;
	private final VirtualTerminalContainer terminalContainer;
	private int scrollOffset = 0;
	private String searchQuery = "";
	/** 上一次输入切出来的条件（每个都必须匹配）。 */
	private List<String> searchTerms = List.of();
	/** 提示框文本缓存，键见 {@link #tooltipKey(ItemStack)}；只在搜索用到 {@code $} 时才会填充。 */
	private static final Map<String, String> TOOLTIP_CACHE = new HashMap<>();

	public PortableTerminalMenu(int containerId, Inventory playerInventory, List<BlockPos> containerPositions, int rows) {
		super(ModMenuTypes.get(), containerId,
			PortableTerminalLayout.CRAFT_GRID_SIZE, PortableTerminalLayout.CRAFT_GRID_SIZE);
		this.rows = PortableTerminalLayout.clampRows(rows);
		this.player = playerInventory.player;
		this.containerPositions.addAll(containerPositions);

		Level level = playerInventory.player.level();
		for (BlockPos pos : containerPositions) {
			BlockEntity be = level.getBlockEntity(pos);
			addContainerAccess(accessAt(be));
		}

		this.playerInventory = playerInventory;
		this.terminalContainer = new VirtualTerminalContainer(terminalSlotCount());

		loadSortConfig();
		rebuildSlots();

		// 诊断用：合成槽必须真的进了槽位表，否则合成格点了没反应（只在建菜单时打一条）。
		int expected = terminalSlotCount() + PLAYER_MAIN_SLOTS + PLAYER_HOTBAR_SLOTS + 1
			+ PortableTerminalLayout.CRAFT_SLOT_COUNT + 1;
		if (this.slots.size() == expected && this.craftGridSlots.size() == PortableTerminalLayout.CRAFT_SLOT_COUNT) {
			PortableTerminalMod.LOGGER.info(
				"Terminal menu built: {} slots (terminal {}, crafting {})", this.slots.size(),
				terminalSlotCount(), PortableTerminalLayout.CRAFT_SLOT_COUNT);
		} else {
			PortableTerminalMod.LOGGER.warn(
				"Terminal menu slot layout mismatch: slots={} expected={} grid={} result={}",
				this.slots.size(), expected, this.craftGridSlots.size(), this.craftResultSlot != null);
		}
	}

	public int getRows() {
		return rows;
	}

	public int terminalSlotCount() {
		return PortableTerminalLayout.terminalSlotCount(CAPACITY_ROWS);
	}

	// ==================== 客户端偏好 ====================

	public int getCraftSource() {
		return craftSource;
	}

	public int getClearDestination() {
		return clearDestination;
	}

	/** 打开终端时由打开包带上；界面内切换时由切换包带上。 */
	public void setCraftingModes(int craftSource, int clearDestination) {
		this.craftSource = Math.max(0, Math.min(ConfigSchema.CRAFT_SOURCE_COUNT - 1, craftSource));
		this.clearDestination = Math.max(0, Math.min(ConfigSchema.CLEAR_DEST_COUNT - 1, clearDestination));
	}

	/**
	 * 改显示行数 / 合成带：只按新布局重建槽位（数量与顺序不变），不重开界面。
	 *
	 * <p>槽位坐标在 26.x 里是 final，改不了，所以重建同一批槽位对象；总数不变，
	 * 原版按索引做的槽位同步缓存仍然有效。</p>
	 */
	public void applyViewLayout(int viewRows, boolean crafting) {
		int next = PortableTerminalLayout.clampRows(viewRows);
		if (next == this.rows && crafting == this.craftingVisible) {
			return;
		}
		this.rows = next;
		this.craftingVisible = crafting;
		rebuildSlots();
		this.scrollOffset = Math.min(this.scrollOffset, getMaxScroll());
		updateTerminalSlots();
	}

	public void applyViewRows(int viewRows) {
		applyViewLayout(viewRows, this.craftingVisible);
	}

	/**
	 * 按当前 {@link #rows} 重建全部槽位：终端槽（固定最大行数）+ 主背包 + 快捷栏 + 副手 + 合成区。
	 *
	 * <p>槽位总数与顺序永远不变，所以第二次起是**逐位替换槽位对象**而不是清空重加：
	 * 原版 {@code lastSlots} / {@code remoteSlots} 是按索引跟着 {@code addSlot} 扩容的，
	 * 清空会让它们与 {@code slots} 长度对不上。</p>
	 */
	private void rebuildSlots() {
		List<Slot> fresh = new ArrayList<>();
		List<TerminalSlot> freshTerminal = new ArrayList<>();
		for (int row = 0; row < CAPACITY_ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				int idx = row * COLS + col;
				boolean visible = row < this.rows;
				TerminalSlot slot = new TerminalSlot(terminalContainer, idx,
					visible ? TERM_LEFT + col * SLOT_SIZE : HIDDEN_SLOT_X,
					visible ? TERM_TOP + row * SLOT_SIZE : HIDDEN_SLOT_Y);
				fresh.add(slot);
				freshTerminal.add(slot);
			}
		}

		int invTop = PortableTerminalLayout.invTop(this.rows, this.craftingVisible);
		int hotbarTop = PortableTerminalLayout.hotbarTop(this.rows, this.craftingVisible);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				fresh.add(new Slot(playerInventory, col + row * 9 + 9,
					INV_LEFT + col * SLOT_SIZE, invTop + row * SLOT_SIZE));
			}
		}
		for (int col = 0; col < 9; col++) {
			fresh.add(new Slot(playerInventory, col, INV_LEFT + col * SLOT_SIZE, hotbarTop));
		}
		fresh.add(new Slot(playerInventory, 40, OFFHAND_X,
			PortableTerminalLayout.offhandY(this.rows, this.craftingVisible)));

		fresh.addAll(createCraftingSlots());

		terminalSlots.clear();
		terminalSlots.addAll(freshTerminal);

		if (this.slots.isEmpty()) {
			for (Slot slot : fresh) {
				this.addSlot(slot);
			}
			return;
		}
		for (int i = 0; i < fresh.size() && i < this.slots.size(); i++) {
			Slot slot = fresh.get(i);
			slot.index = i;
			this.slots.set(i, slot);
		}
	}

	public void updateTerminalSlots() {
		int maxScroll = getMaxScroll();
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;
		if (scrollOffset < 0) scrollOffset = 0;

		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < COLS; col++) {
				int slotIdx = row * COLS + col;
				int itemIdx = (scrollOffset + row) * COLS + col;
				ItemStack stack = (itemIdx >= 0 && itemIdx < displayedItems.size())
					? displayedItems.get(itemIdx).copy()
					: ItemStack.EMPTY;
				terminalSlots.get(slotIdx).set(stack);
			}
		}
		for (int i = this.rows * COLS; i < terminalSlots.size(); i++) {
			terminalSlots.get(i).set(ItemStack.EMPTY);
		}
	}

	// ==================== 合成区 ====================

	/** 3×3 网格 + 结果槽。关闭合成时挪出屏幕，但槽位对象仍然存在：两端槽位数必须一致。 */
	private List<Slot> createCraftingSlots() {
		List<Slot> slots = new ArrayList<>(PortableTerminalLayout.CRAFT_SLOT_COUNT + 1);
		List<Slot> grid = new ArrayList<>(PortableTerminalLayout.CRAFT_SLOT_COUNT);
		int bandTop = PortableTerminalLayout.craftBandTop(this.rows);
		for (int row = 0; row < PortableTerminalLayout.CRAFT_GRID_SIZE; row++) {
			for (int col = 0; col < PortableTerminalLayout.CRAFT_GRID_SIZE; col++) {
				int idx = col + row * PortableTerminalLayout.CRAFT_GRID_SIZE;
				int x = this.craftingVisible
					? PortableTerminalLayout.CRAFT_GRID_X + col * SLOT_SIZE : HIDDEN_SLOT_X;
				int y = this.craftingVisible
					? bandTop + PortableTerminalLayout.CRAFT_GRID_Y + row * SLOT_SIZE : HIDDEN_SLOT_Y;
				Slot slot = new Slot(this.craftSlots, idx, x, y);
				slots.add(slot);
				grid.add(slot);
			}
		}
		int resultX = this.craftingVisible ? PortableTerminalLayout.CRAFT_RESULT_X : HIDDEN_SLOT_X;
		int resultY = this.craftingVisible ? bandTop + PortableTerminalLayout.CRAFT_RESULT_Y : HIDDEN_SLOT_Y;
		Slot result = new ResultSlot(this.player, this.craftSlots, this.resultSlots, 0, resultX, resultY);
		slots.add(result);

		this.craftGridSlots.clear();
		this.craftGridSlots.addAll(grid);
		this.craftResultSlot = result;
		return slots;
	}

	@Override
	public Slot getResultSlot() {
		return this.craftResultSlot;
	}

	@Override
	public List<Slot> getInputGridSlots() {
		return this.craftGridSlots;
	}

	@Override
	protected Player owner() {
		return this.player;
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	/** 网格内容一变就重算结果；摆放配方期间跳过，由 finishPlacingRecipe 统一算一次。 */
	@Override
	public void slotsChanged(Container container) {
		if (!this.placingRecipe && container == this.craftSlots) {
			updateCraftingResult(null);
		}
	}

	@Override
	protected void beginPlacingRecipe() {
		this.placingRecipe = true;
	}

	@Override
	protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
		this.placingRecipe = false;
		updateCraftingResult(recipe);
	}

	/**
	 * 服务端算一次合成结果并推给客户端。
	 *
	 * <p>结果槽不在原版的槽位同步缓存里（它每次都由网格内容算出），所以必须像原版工作台那样
	 * 显式发 set-slot 包，否则客户端不会有任何显示。</p>
	 */
	private void updateCraftingResult(RecipeHolder<CraftingRecipe> hint) {
		if (!(this.player instanceof ServerPlayer serverPlayer) || !(this.player.level() instanceof ServerLevel level)) {
			return;
		}
		CraftingInput input = this.craftSlots.asCraftInput();
		ItemStack result = ItemStack.EMPTY;
		Optional<RecipeHolder<CraftingRecipe>> match =
			level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, hint);
		if (match.isPresent()) {
			RecipeHolder<CraftingRecipe> holder = match.get();
			if (this.resultSlots.setRecipeUsed(serverPlayer, holder)) {
				ItemStack assembled = holder.value().assemble(input);
				if (assembled.isItemEnabled(level.enabledFeatures())) {
					result = assembled;
				}
			}
		}
		this.resultSlots.setItem(0, result);
		// 用槽位自己的 index，不要用常量推算：槽位表是「终端固定 8 行 + 背包 + 合成」拼出来的，
		// 任何一处数量对不上，常量就会指到别人的槽位上（曾因此把服务端 tick 打崩）。
		if (this.craftResultSlot == null || this.craftResultSlot.index >= this.slots.size()) {
			return;
		}
		int resultIndex = this.craftResultSlot.index;
		this.setRemoteSlot(resultIndex, result);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
			this.containerId, this.incrementStateId(), resultIndex, result));
	}

	/**
	 * 配方书点配方：自己摆放，材料可以从终端容器里取。
	 *
	 * <p>原版 {@code AbstractCraftingMenu} 只从玩家背包取料，终端里的物品它看不见，
	 * 所以这里照原版同一套 {@code PlacementInfo} / {@code PlaceRecipeHelper} 重写摆放，
	 * 取料顺序由配置项「合成取料来源」决定。排列不进去时退化成原版行为：清空网格、只显示 ghost。</p>
	 */
	@Override
	public PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear,
			RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
		if (!this.craftingVisible || this.craftGridSlots.size() < PortableTerminalLayout.CRAFT_SLOT_COUNT) {
			PortableTerminalMod.LOGGER.info("[terminal] place rejected: crafting={} grid={}",
				this.craftingVisible, this.craftGridSlots.size());
			return PostPlaceAction.NOTHING;
		}
		PlacementInfo info = recipe.value().placementInfo();
		if (info.isImpossibleToPlace()) {
			return PostPlaceAction.NOTHING;
		}
		List<Ingredient> ingredients = info.ingredients();

		// 每个 ingredient 在网格里占几格
		int[] slotsPerIngredient = new int[ingredients.size()];
		for (int index : info.slotsToIngredientIndex()) {
			if (index >= 0 && index < slotsPerIngredient.length) {
				slotsPerIngredient[index]++;
			}
		}

		// 摆几套：Shift 一次摆满；否则在网格里现有套数上加一套（原版也是点几次摆几套）
		int alreadyPlaced = currentGridSets(info);
		int sets = useMaxItems ? 64 : alreadyPlaced + 1;
		for (int i = 0; i < slotsPerIngredient.length && sets > 0; i++) {
			if (slotsPerIngredient[i] <= 0) continue;
			int available = countAvailable(ingredients.get(i));
			sets = Math.min(sets, available / slotsPerIngredient[i]);
		}
		PortableTerminalMod.LOGGER.info(
			"[terminal] place {} useMax={} crafting={} grid={} containers={} agg={} sets={}",
			recipe.id(), useMaxItems, this.craftingVisible, this.craftGridSlots.size(),
			this.containers.size(), this.aggregatedItems.size(), sets);
		if (sets <= 0) {
			clearCraftingGrid(false);
			updateCraftingResult(null);
			return PostPlaceAction.PLACE_GHOST_RECIPE;
		}
		final int perSlot = Math.min(sets, 64);

		if (!clearCraftingGrid(false)) {
			return PostPlaceAction.NOTHING;
		}
		PlaceRecipeHelper.placeRecipe(PortableTerminalLayout.CRAFT_GRID_SIZE,
			PortableTerminalLayout.CRAFT_GRID_SIZE, recipe.value(), info.slotsToIngredientIndex(),
			(ingredientIndex, gridIndex, gridXPos, gridYPos) -> {
				if (ingredientIndex == null || ingredientIndex < 0 || gridIndex >= this.craftGridSlots.size()) {
					return;
				}
				ItemStack taken = takeForIngredient(ingredients.get(ingredientIndex), perSlot);
				if (!taken.isEmpty()) {
					this.craftGridSlots.get(gridIndex).set(taken);
				}
			});
		updateCraftingResult(null);
		return PostPlaceAction.NOTHING;
	}

	/**
	 * 网格里当前摆着这份配方的几套；不是这份配方（或为空）时返回 0。
	 *
	 * <p>用来支持「连点几次配方就摆几套」：原版每次点击会在已有基础上再加一套，
	 * 而不是每次都重摆一套。</p>
	 */
	private int currentGridSets(PlacementInfo info) {
		IntList slotsToIngredient = info.slotsToIngredientIndex();
		List<Ingredient> ingredients = info.ingredients();
		int sets = Integer.MAX_VALUE;
		boolean any = false;
		for (int i = 0; i < this.craftGridSlots.size(); i++) {
			int ingredientIndex = i < slotsToIngredient.size() ? slotsToIngredient.getInt(i) : -1;
			ItemStack stack = this.craftGridSlots.get(i).getItem();
			if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
				if (!stack.isEmpty()) {
					return 0;
				}
				continue;
			}
			if (stack.isEmpty() || !ingredients.get(ingredientIndex).test(stack)) {
				return 0;
			}
			any = true;
			sets = Math.min(sets, stack.getCount());
		}
		return any ? Math.max(0, sets) : 0;
	}

	/** 按「合成取料来源」统计某材料当前能拿到多少。 */
	private int countAvailable(Ingredient ingredient) {
		int total = countInContainers(ingredient);
		if (this.craftSource != ConfigSchema.CRAFT_SOURCE_TERMINAL_ONLY) {
			total += countInInventory(ingredient);
		}
		return total;
	}

	/** 按「合成取料来源」取指定数量的材料，返回实取到的栈（可能少于请求量）。 */
	private ItemStack takeForIngredient(Ingredient ingredient, int count) {
		ItemStack result = ItemStack.EMPTY;
		int remaining = count;
		if (this.craftSource == ConfigSchema.CRAFT_SOURCE_TERMINAL_FIRST) {
			ItemStack taken = takeFromContainers(ingredient, remaining);
			result = taken;
			remaining -= taken.getCount();
			if (remaining > 0) {
				ItemStack fromInv = takeFromInventory(ingredient, remaining);
				result = merge(result, fromInv);
				remaining -= fromInv.getCount();
			}
		} else if (this.craftSource == ConfigSchema.CRAFT_SOURCE_INVENTORY_FIRST) {
			ItemStack taken = takeFromInventory(ingredient, remaining);
			result = taken;
			remaining -= taken.getCount();
			if (remaining > 0) {
				ItemStack fromTerm = takeFromContainers(ingredient, remaining);
				result = merge(result, fromTerm);
			}
		} else {
			// 只用终端容器：不碰背包
			result = takeFromContainers(ingredient, remaining);
		}
		if (!this.containers.isEmpty()) {
			rebuildAggregatedItems();
		}
		return result;
	}

	private static ItemStack merge(ItemStack a, ItemStack b) {
		if (a.isEmpty()) return b;
		if (b.isEmpty()) return a;
		ItemStack merged = a.copy();
		merged.grow(b.getCount());
		return merged;
	}

	private int countInContainers(Ingredient ingredient) {
		int total = 0;
		List<ItemStack> slots = new ArrayList<>();
		for (ItemAccess access : this.containers) {
			slots.clear();
			ItemAccess.collectSlots(access, slots);
			for (ItemStack stack : slots) {
				if (!stack.isEmpty() && ingredient.test(stack)) {
					total += stack.getCount();
				}
			}
		}
		return total;
	}

	private int countInInventory(Ingredient ingredient) {
		Inventory inventory = this.player.getInventory();
		int total = 0;
		for (int i = 0; i < PLAYER_MAIN_SLOTS + PLAYER_HOTBAR_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && ingredient.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private ItemStack takeFromContainers(Ingredient ingredient, int count) {
		ItemStack result = ItemStack.EMPTY;
		int remaining = count;
		List<ItemStack> slots = new ArrayList<>();
		for (ItemAccess access : this.containers) {
			if (remaining <= 0) break;
			slots.clear();
			ItemAccess.collectSlots(access, slots);
			for (ItemStack stack : slots) {
				if (remaining <= 0) break;
				if (stack.isEmpty() || !ingredient.test(stack)) continue;
				ItemStack taken = ItemAccess.extractMatching(access, stack, remaining);
				if (taken.isEmpty()) continue;
				result = merge(result, taken);
				remaining -= taken.getCount();
			}
		}
		return result;
	}

	private ItemStack takeFromInventory(Ingredient ingredient, int count) {
		Inventory inventory = this.player.getInventory();
		ItemStack result = ItemStack.EMPTY;
		int remaining = count;
		for (int i = 0; i < PLAYER_MAIN_SLOTS + PLAYER_HOTBAR_SLOTS && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !ingredient.test(stack)) continue;
			int take = Math.min(remaining, stack.getCount());
			ItemStack part = stack.split(take);
			result = merge(result, part);
			remaining -= take;
		}
		inventory.setChanged();
		return result;
	}

	/** 清空合成网格（允许放不下时落地）：按键、Shift 转移、关界面都走这个。 */
	public void clearCraftingGrid() {
		clearCraftingGrid(true);
	}

	/**
	 * 清空合成网格：去向按「清空去向」设置——背包优先或终端优先，两边都放不下才落地。
	 *
	 * @param allowDrop {@code false} 用于「摆放配方前腾地方」：格子里的东西归不了位就整体放弃，
	 *                  绝不能因为点了配方反而把玩家的物品丢在地上
	 * @return 是否已清空（{@code false} 表示放不下且没有改动任何东西）
	 */
	public boolean clearCraftingGrid(boolean allowDrop) {
		if (!allowDrop && !gridReturnsFit()) {
			return false;
		}
		List<ItemStack> leftovers = new ArrayList<>();
		for (Slot slot : this.craftGridSlots) {
			if (slot.hasItem()) {
				leftovers.add(slot.remove(slot.getItem().getCount()));
			}
		}
		this.resultSlots.setItem(0, ItemStack.EMPTY);
		// 只有服务端负责把东西还回去：客户端这边清掉槽位就够了，否则会改动本地的容器副本。
		if (this.player instanceof ServerPlayer) {
			for (ItemStack stack : leftovers) {
				if (stack.isEmpty()) continue;
				ItemStack remaining = relocateToDestination(stack);
				if (!remaining.isEmpty()) {
					this.player.drop(remaining, false, Prediction.PREDICTED);
				}
			}
		}
		if (this.player instanceof ServerPlayer serverPlayer) {
			updateCraftingResult(null);
			this.broadcastChanges();
			Services.PLATFORM.sendToPlayer(serverPlayer,
				TerminalItemsSyncPacket.fromItemStacks(this.aggregatedItems));
		}
		return true;
	}

	/**
	 * 按「清空去向」把一堆东西搬到背包或终端，搬不动的部分原样返回。
	 * 传进来的 stack 会被消耗，返回的是剩余的部分。
	 */
	private ItemStack relocateToDestination(ItemStack stack) {
		ItemStack rest = stack.copy();
		if (this.clearDestination == ConfigSchema.CLEAR_DEST_TERMINAL) {
			rest = insertIntoContainers(rest);
			if (rest.isEmpty()) {
				return rest;
			}
		}
		this.player.getInventory().add(rest);
		if (rest.isEmpty()) {
			return rest;
		}
		// 背包塞不下时再退回终端（反之亦然），两边都不行才把剩下的交回去
		if (this.clearDestination == ConfigSchema.CLEAR_DEST_INVENTORY) {
			rest = insertIntoContainers(rest);
		}
		return rest;
	}

	/** 网格里的东西按当前「清空去向」能不能全部归位（不能就别动，交给调用方放弃）。 */
	private boolean gridReturnsFit() {
		List<ItemStack> stacks = new ArrayList<>();
		for (Slot slot : this.craftGridSlots) {
			if (slot.hasItem()) {
				stacks.add(slot.getItem());
			}
		}
		if (stacks.isEmpty()) {
			return true;
		}
		// 去向是终端且确实连着容器时，终端就是主要存放处，归不下的部分会回落到背包
		if (this.clearDestination == ConfigSchema.CLEAR_DEST_TERMINAL && !this.containers.isEmpty()) {
			return true;
		}
		for (ItemStack stack : stacks) {
			if (freeSpaceIn(this.player, stack) < stack.getCount()) {
				return false;
			}
		}
		return true;
	}

	/** 关终端时把网格里的东西还回去（去向同上），绝不留在一个已关闭的菜单里。 */
	@Override
	public void removed(Player player) {
		super.removed(player);
		clearCraftingGrid();
	}

	/** 配方书/合成判定的「可合成」提示要把终端物品也算进去。 */
	@Override
	public void fillCraftSlotsStackedContents(StackedItemContents contents) {
		super.fillCraftSlotsStackedContents(contents);
		for (ItemStack stack : this.aggregatedItems) {
			if (!stack.isEmpty()) {
				contents.accountSimpleStack(stack);
			}
		}
	}

	public void setScrollOffset(int offset) {
		this.scrollOffset = Math.max(0, Math.min(offset, getMaxScroll()));
		updateTerminalSlots();
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public int getMaxScroll() {
		return Math.max(0, getTotalRows() - rows);
	}

	public int getTotalRows() {
		return (displayedItems.size() + COLS - 1) / COLS;
	}

	/**
	 * 客户端搜索过滤：空白分隔的多个条件必须同时满足。
	 *
	 * <p>条件前缀：{@code @} 按模组（命名空间或模组名）、{@code #} 按物品标签、{@code $} 按提示框文本；
	 * 没有前缀的条件按显示名 / 注册 ID 匹配。双引号里的空格不切分，便于搜索带空格的名称。</p>
	 */
	public void applySearchFilter(String query) {
		this.searchQuery = query == null ? "" : query.trim();
		this.searchTerms = splitSearchTerms(this.searchQuery);
		rebuildDisplayedItems();
		this.scrollOffset = 0;
		updateTerminalSlots();
	}

	private void rebuildDisplayedItems() {
		displayedItems.clear();
		if (searchQuery.isEmpty()) {
			displayedItems.addAll(aggregatedItems);
		} else {
			for (ItemStack stack : aggregatedItems) {
				if (matchesSearch(stack, searchQuery)) {
					displayedItems.add(stack);
				}
			}
		}
		// 取出/存入后服务端同步回来的顺序未必仍是当前排序结果（数量变了、物品没了），
		// 这里统一按当前条件再排一次。所有显示路径都经过本方法，排序不会再错乱。
		displayedItems.sort(this::compare);
	}

	/** 把搜索串切成条件：空白分隔，双引号内不切（空条件已丢弃）。 */
	private static List<String> splitSearchTerms(String query) {
		List<String> terms = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < query.length(); i++) {
			char c = query.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted && Character.isWhitespace(c)) {
				if (!current.isEmpty()) {
					terms.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (!current.isEmpty()) {
			terms.add(current.toString());
		}
		return terms;
	}

	private boolean matchesSearch(ItemStack stack, String query) {
		for (String term : this.searchTerms) {
			if (!matchesTerm(stack, term)) {
				return false;
			}
		}
		return true;
	}

	private boolean matchesTerm(ItemStack stack, String term) {
		if (term.isEmpty()) {
			return true;
		}
		String value = term.substring(1).toLowerCase(Locale.ROOT);
		return switch (term.charAt(0)) {
			case '@' -> matchesMod(stack, value);
			case '#' -> matchesTag(stack, value);
			case '$' -> matchesTooltip(stack, value);
			default -> matchesName(stack, term.toLowerCase(Locale.ROOT));
		};
	}

	/** 显示名 / 注册 ID；装了「通用拼音搜索」时由它接管（全拼、首字母、简繁）。 */
	private boolean matchesName(ItemStack stack, String query) {
		if (query.isEmpty()) {
			return true;
		}
		if (PinyinSearch.matches(stack.getItem().getName(stack).getString(), query)) {
			return true;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT).contains(query);
	}

	/**
	 * 模组搜索：命名空间本身就是 mod id；模组展示名由加载器提供，缺失时只比 id。
	 * 走平台接口而不是加载器 API，common 才能保持裸 MC 编译。
	 */
	private boolean matchesMod(ItemStack stack, String query) {
		if (query.isEmpty()) {
			return true;
		}
		String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
		if (namespace.toLowerCase(Locale.ROOT).contains(query)) {
			return true;
		}
		return PinyinSearch.matches(Services.PLATFORM.getModDisplayName(namespace), query);
	}

	/** 标签搜索：{@code #logs}、{@code #minecraft:logs} 都按「标签 ID 包含该串」判定。 */
	private boolean matchesTag(ItemStack stack, String query) {
		if (query.isEmpty()) {
			return true;
		}
		for (TagKey<Item> tag : stack.typeHolder().tags().toList()) {
			if (tag.location().toString().toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 提示框搜索：按物品自带的提示文本匹配（附魔、药水效果、模组说明都能命中）。
	 * 提示文本生成代价高，按「id + 损伤 + 显示名」缓存，输入过程中不会反复重算。
	 */
	private boolean matchesTooltip(ItemStack stack, String query) {
		if (query.isEmpty()) {
			return true;
		}
		if (TOOLTIP_CACHE.size() > 8192) {
			TOOLTIP_CACHE.clear();
		}
		String text = TOOLTIP_CACHE.computeIfAbsent(tooltipKey(stack), key -> buildTooltipText(stack));
		return text.contains(query);
	}

	private static String tooltipKey(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getDamageValue() + "|" + stack.getHoverName().getString();
	}

	private String buildTooltipText(ItemStack stack) {
		try {
			List<Component> lines = stack.getTooltipLines(
				Item.TooltipContext.of(this.player.level()), this.player, TooltipFlag.NORMAL);
			StringBuilder text = new StringBuilder();
			for (Component line : lines) {
				text.append(line.getString()).append('\n');
			}
			return text.toString().toLowerCase(Locale.ROOT);
		} catch (Exception e) {
			// 物品自己的提示框实现可能依赖上下文；单件失败不该让整次过滤报错。
			return "";
		}
	}

	/** 接收服务器同步的聚合物品列表 */
	public void setAggregatedItemsFromServer(List<ItemStack> items) {
		this.aggregatedItems.clear();
		this.aggregatedItems.addAll(items);
		rebuildDisplayedItems();
		this.scrollOffset = Math.min(this.scrollOffset, getMaxScroll());
		updateTerminalSlots();
	}

	// ==================== 原版同步兼容 ====================
	@Override
	public void setItem(int slotIndex, int stateId, ItemStack stack) {
		if (slotIndex < terminalSlotCount()) return;
		super.setItem(slotIndex, stateId, stack);
	}

	@Override
	public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
		if (items.size() == this.slots.size()) {
			super.initializeContents(stateId, items, carried);
		} else {
			// 两端槽位数本该一致；真不一致时原版会直接越界崩客户端，这里退化成按较小长度同步。
			int count = Math.min(items.size(), this.slots.size());
			for (int i = 0; i < count; i++) {
				this.slots.get(i).set(items.get(i));
			}
			this.setCarried(carried);
		}
		updateTerminalSlots();
	}

	// ==================== 打开时的位置传递 ====================
	public static void setPendingPositions(ServerPlayer player, List<BlockPos> positions) {
		PENDING_POSITIONS.put(player.getUUID(), positions);
	}

	public static List<BlockPos> takePendingPositions(UUID playerUuid) {
		List<BlockPos> pos = PENDING_POSITIONS.remove(playerUuid);
		return pos != null ? pos : new ArrayList<>();
	}

	@Override
	public boolean stillValid(Player player) {
		BlockPos playerPos = player.blockPosition();
		Level level = player.level();
		for (int i = 0; i < containerPositions.size(); i++) {
			BlockPos pos = containerPositions.get(i);
			if (!playerPos.closerThan(pos, ModConfig.scanRadius + 1)) continue;
			BlockEntity be = level.getBlockEntity(pos);
			ItemAccess access = accessAt(be);
			if (access != null) {
				return true;
			}
		}
		return false;
	}

	// ==================== Shift 移动 ====================
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) return ItemStack.EMPTY;

		// 合成网格：Shift 点击按「清空去向」转移，与清空按键同一套规则
		if (this.craftGridSlots.contains(slot)) {
			ItemStack stack = slot.remove(slot.getItem().getCount());
			ItemStack leftover = relocateToDestination(stack);
			if (!leftover.isEmpty()) {
				// 背包和终端都塞不下：原样留在格子里，绝不静默丢弃
				slot.set(leftover);
			}
			slot.setChanged();
			this.broadcastChanges();
			updateCraftingResult(null);
			return ItemStack.EMPTY;
		}

		// 结果槽：背包放得下才取，取完由 ResultSlot.onTake 扣网格材料
		if (slot == this.craftResultSlot) {
			return quickMoveCraftedResult(slot);
		}

		if (slotIndex < terminalSlotCount()) {
			// 从终端取出一组到玩家背包（不含副手槽）
			ItemStack template = slot.getItem();
			ItemStack extracted = extractFromContainers(template, template.getMaxStackSize());
			if (!extracted.isEmpty()) {
				this.moveItemStackTo(extracted, PLAYER_INV_START, PLAYER_INV_END, true);
				if (!extracted.isEmpty()) {
					// 装不下的部分放回容器，绝不丢出：原版 shift 取物在背包满时什么都不发生
					insertIntoContainers(extracted);
				}
			}
			this.broadcastChanges();
			// 同步给客户端（取出后终端物品数量变化）
			if (player instanceof ServerPlayer serverPlayer) {
				Services.PLATFORM.sendToPlayer(serverPlayer, TerminalItemsSyncPacket.fromItemStacks(this.aggregatedItems));
			}
			return ItemStack.EMPTY;
		}

		// 从玩家背包存入容器
		ItemStack stack = slot.getItem();
		ItemStack remaining = insertIntoContainers(stack);
		slot.set(remaining);
		slot.setChanged();
		this.broadcastChanges();
		// 同步给客户端（客户端需要更新 displayedItems）
		if (player instanceof ServerPlayer serverPlayer) {
			Services.PLATFORM.sendToPlayer(serverPlayer, TerminalItemsSyncPacket.fromItemStacks(this.aggregatedItems));
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Shift 点击合成结果：先把结果放进背包，再让 {@link ResultSlot#onTake} 扣掉网格材料。
	 *
	 * <p>背包放不下时什么都不做——原版工作台在满包时的行为就是不动作，绝不能把结果丢到地上。</p>
	 */
	private ItemStack quickMoveCraftedResult(Slot slot) {
		ItemStack crafted = slot.getItem();
		if (crafted.isEmpty() || !slot.mayPickup(this.player)) {
			return ItemStack.EMPTY;
		}
		int count = crafted.getCount();
		if (freeSpaceIn(this.player, crafted) < count) {
			return ItemStack.EMPTY;
		}
		ItemStack moving = crafted.copy();
		this.moveItemStackTo(moving, PLAYER_INV_START, PLAYER_INV_END, true);
		if (!moving.isEmpty()) {
			return ItemStack.EMPTY;
		}
		slot.remove(count);
		slot.onTake(this.player, crafted.copy());
		this.broadcastChanges();
		return ItemStack.EMPTY;
	}

	/** 玩家主背包 + 快捷栏还能装下多少个这种物品（与点击包的判定同源）。 */
	private static int freeSpaceIn(Player player, ItemStack template) {
		Inventory inventory = player.getInventory();
		int space = 0;
		for (int i = 0; i < PLAYER_MAIN_SLOTS + PLAYER_HOTBAR_SLOTS; i++) {
			ItemStack inSlot = inventory.getItem(i);
			if (inSlot.isEmpty()) {
				space += template.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(inSlot, template)) {
				space += Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
			}
		}
		return space;
	}

	// ==================== 聚合 / 存取 ====================
	public List<ItemStack> getAggregatedItems() {
		return aggregatedItems;
	}

	public void rebuildAggregatedItems() {
		aggregatedItems.clear();
		// 使用 LinkedHashMap 保持插入顺序，避免存取后物品排列错乱
		Map<String, ItemStack> map = new LinkedHashMap<>();
		List<ItemStack> slots = new ArrayList<>();
		for (ItemAccess access : containers) {
			slots.clear();
			ItemAccess.collectSlots(access, slots);
			for (ItemStack stack : slots) {
				String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				if (!stack.getComponentsPatch().isEmpty()) {
					key = key + "|" + ItemStack.hashItemAndComponents(stack);
				}
				ItemStack existing = map.get(key);
				if (existing != null) {
					existing.grow(stack.getCount());
				} else {
					map.put(key, stack.copy());
				}
			}
		}
		aggregatedItems.addAll(map.values());
		aggregatedItems.sort(this::compare);
		rebuildDisplayedItems();
	}

	/** 按「当前排序类型 + 方向」比较两个物品。 */
	private int compare(ItemStack a, ItemStack b) {
		int cmp = compareBy(a, b, this.sortMode);
		if (cmp == 0) {
			// 同值再用注册 ID 兜底，保证顺序稳定（不会在两次同步间抖动）
			cmp = BuiltInRegistries.ITEM.getKey(a.getItem()).toString()
				.compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()).toString());
		}
		return sortAscending ? cmp : -cmp;
	}

	/** 按第 index 个条件比较两个物品（升序结果） */
	private int compareBy(ItemStack a, ItemStack b, int index) {
		switch (index) {
			case 0: {
				String na = a.getItem().getName(a).getString().toLowerCase(Locale.ROOT);
				String nb = b.getItem().getName(b).getString().toLowerCase(Locale.ROOT);
				return na.compareTo(nb);
			}
			case 1:
				return Integer.compare(a.getCount(), b.getCount());
			case 2:
				return BuiltInRegistries.ITEM.getKey(a.getItem()).toString()
					.compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()).toString());
			case 3:
				return BuiltInRegistries.ITEM.getKey(a.getItem()).getNamespace()
					.compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace());
			default:
				return 0;
		}
	}

	public ItemStack extractFromContainers(ItemStack template, int count) {
		ItemStack extracted = ItemStack.EMPTY;
		int remaining = count;
		// 跨容器按模板取；「怎么找、怎么扣」的规则在 ItemAccess.extractMatching，两端一致
		for (ItemAccess access : containers) {
			if (remaining <= 0) break;
			ItemStack taken = ItemAccess.extractMatching(access, template, remaining);
			if (taken.isEmpty()) continue;
			if (extracted.isEmpty()) {
				extracted = taken;
			} else {
				extracted.grow(taken.getCount());
			}
			remaining -= taken.getCount();
		}
		rebuildAggregatedItems();
		return extracted;
	}

	public ItemStack insertIntoContainers(ItemStack stack) {
		ItemStack remaining = stack.copy();
		// 「先并同类堆、再找空位」的规则在 ItemAccess.insertAll，两端一致
		for (ItemAccess access : containers) {
			if (remaining.isEmpty()) break;
			remaining = ItemAccess.insertAll(access, remaining);
		}
		rebuildAggregatedItems();
		return remaining;
	}

	public void refreshContainers() {
		Level level = player.level();
		containers.clear();
		for (BlockPos pos : containerPositions) {
			BlockEntity be = level.getBlockEntity(pos);
			ItemAccess access = accessAt(be);
			if (access != null) addContainerAccess(access);
		}
		rebuildAggregatedItems();
	}

	/**
	 * 登记一个容器访问器；同一个底层物品栏只登记一次。
	 *
	 * <p>多方块容器（大箱子之类）会把同一份物品栏挂在多个方块位置，逐位置登记会把物品统计两遍。</p>
	 */
	private void addContainerAccess(ItemAccess access) {
		if (access == null) {
			return;
		}
		Object backing = access.backing();
		for (ItemAccess existing : containers) {
			if (existing.backing() == backing) {
				return;
			}
		}
		containers.add(access);
	}

	public List<BlockPos> getContainerPositions() { return containerPositions; }
	public List<ItemAccess> getContainers() { return containers; }

	/**
	 * 求某个方块位置的物品栏视图。
	 *
	 * <p>先看它是不是原版 {@link Container}；不是的话再问平台要能力适配。模组容器（抽屉、储物桶、
	 * 各种机器的物品栏）一般只通过加载器的能力系统暴露物品栏，并不实现 {@code Container}，
	 * 只按 {@code Container} 检索会整片漏掉。这里走能力是为了对所有模组通用，不针对任何具体模组。</p>
	 */
	public static ItemAccess accessAt(BlockEntity be) {
		if (be == null) {
			return null;
		}
		if (be instanceof Container container) {
			return new VanillaContainerAccess(container);
		}
		Level level = be.getLevel();
		if (level == null) {
			return null;
		}
		return Services.PLATFORM.findItemAccess(level, be.getBlockPos());
	}
	public List<TerminalSlot> getTerminalSlots() { return terminalSlots; }

	// ===== 排序 API（一个类型 + 一个方向）=====
	public int getSortMode() {
		return sortMode;
	}

	public boolean isSortAscending() {
		return sortAscending;
	}

	public Component getSortName(int index) {
		return Component.translatable(SORT_NAME_KEYS[Math.floorMod(index, SORT_NAME_KEYS.length)]);
	}

	public int getSortModeCount() {
		return SORT_NAME_KEYS.length;
	}

	/** 点排序类型键：名称 → 数量 → ID → 模组 → 名称。 */
	public void cycleSortMode() {
		this.sortMode = (this.sortMode + 1) % SORT_NAME_KEYS.length;
		resortAggregatedItems();
		updateTerminalSlots();
		saveSortConfig();
	}

	/** 点方向键：正序 ↔ 倒序。 */
	public void toggleSortDirection() {
		this.sortAscending = !this.sortAscending;
		resortAggregatedItems();
		updateTerminalSlots();
		saveSortConfig();
	}

	/** 从配置文件加载排序设置（使用语言无关的 key） */
	public void loadSortConfig() {
		String config = ModConfig.sortConfig;
		if (config == null || config.isEmpty()) {
			return;
		}
		// 格式: "name:ASC" 或 "count:DESC"（语言无关）
		String[] parts = config.split(":");
		if (parts.length != 2) return;
		for (int i = 0; i < SORT_PERSIST_KEYS.length; i++) {
			if (SORT_PERSIST_KEYS[i].equals(parts[0])) {
				this.sortMode = i;
				this.sortAscending = "ASC".equals(parts[1]);
				return;
			}
		}
	}

	/** 保存当前排序设置到配置文件（使用语言无关的 key） */
	private void saveSortConfig() {
		ModConfig.sortConfig = SORT_PERSIST_KEYS[this.sortMode] + ":" + (sortAscending ? "ASC" : "DESC");
		ModConfig.saveState();
	}

	/** 仅对已聚合的物品重新排序并刷新显示，不重新扫描容器 */
	private void resortAggregatedItems() {
		aggregatedItems.sort(this::compare);
		rebuildDisplayedItems();
	}

	/**
	 * 扫描玩家周围的容器。
	 *
	 * <p>不逐格遍历立方体：半径 128 时那是一个 257³ 的循环（约一千七百万次 getBlockEntity），
	 * 打开终端会明显卡顿。改为只枚举范围内**已加载区块的方块实体表**，代价与容器数量成正比。
	 * 未加载的区块直接跳过（没有方块实体可枚举，也不该为了开界面去加载/生成区块）。</p>
	 */
	public static List<BlockPos> scanNearbyContainers(ServerPlayer player) {
		List<BlockPos> positions = new ArrayList<>();
		int radius = ModConfig.scanRadius;
		if (radius <= 0 || !(player.level() instanceof ServerLevel level)) {
			return positions;
		}
		BlockPos origin = player.blockPosition();
		double radiusSqr = (double) radius * radius;
		int minChunkX = (origin.getX() - radius) >> 4;
		int maxChunkX = (origin.getX() + radius) >> 4;
		int minChunkZ = (origin.getZ() - radius) >> 4;
		int maxChunkZ = (origin.getZ() + radius) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) continue;
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					BlockPos pos = entry.getKey();
					if (pos.distSqr(origin) > radiusSqr) continue;
					BlockEntity be = entry.getValue();
					if (accessAt(be) == null) continue;
					String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
					if (ModConfig.blacklist().contains(blockId)) continue;
					positions.add(pos.immutable());
				}
			}
		}

		sortByInsertOrder(positions, origin);
		return positions;
	}

	/** 存入顺序：默认由近到远；也可配成由远到近或坐标升序。 */
	private static void sortByInsertOrder(List<BlockPos> positions, BlockPos origin) {
		String order = ModConfig.insertOrder;
		if (ConfigSchema.ORDER_FARTHEST.equals(order)) {
			positions.sort((a, b) -> Double.compare(b.distSqr(origin), a.distSqr(origin)));
		} else if (ConfigSchema.ORDER_COORDINATES.equals(order)) {
			positions.sort((a, b) -> {
				int cmp = Integer.compare(a.getX(), b.getX());
				if (cmp != 0) return cmp;
				cmp = Integer.compare(a.getY(), b.getY());
				return cmp != 0 ? cmp : Integer.compare(a.getZ(), b.getZ());
			});
		} else {
			positions.sort((a, b) -> Double.compare(a.distSqr(origin), b.distSqr(origin)));
		}
	}

	// ==================== 内部类 ====================
	static class VirtualTerminalContainer implements Container {
		private final ItemStack[] items;

		public VirtualTerminalContainer(int size) {
			this.items = new ItemStack[size];
			for (int i = 0; i < size; i++) {
				items[i] = ItemStack.EMPTY;
			}
		}

		@Override
		public int getContainerSize() { return items.length; }

		@Override
		public boolean isEmpty() {
			for (ItemStack item : items) {
				if (!item.isEmpty()) return false;
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) { return items[slot]; }

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack stack = items[slot];
			if (stack.isEmpty()) return ItemStack.EMPTY;
			int take = Math.min(amount, stack.getCount());
			ItemStack result = stack.copyWithCount(take);
			stack.shrink(take);
			if (stack.isEmpty()) items[slot] = ItemStack.EMPTY;
			return result;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = items[slot];
			items[slot] = ItemStack.EMPTY;
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			items[slot] = stack;
		}

		@Override
		public void setChanged() {}

		@Override
		public boolean stillValid(Player player) { return true; }

		@Override
		public void clearContent() {
			for (int i = 0; i < items.length; i++) {
				items[i] = ItemStack.EMPTY;
			}
		}
	}

	public static class TerminalSlot extends Slot {
		public TerminalSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}
}
