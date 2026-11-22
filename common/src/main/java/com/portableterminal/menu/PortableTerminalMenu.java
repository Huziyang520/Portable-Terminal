// Portions of this code (terminal item aggregation, container scanning, GUI layout)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.

package com.portableterminal.menu;

import com.portableterminal.config.ModConfig;
import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.inventory.VanillaContainerAccess;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.Services;
import com.portableterminal.util.PinyinSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PortableTerminalMenu extends AbstractContainerMenu {

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

	private static final Map<UUID, List<BlockPos>> PENDING_POSITIONS = new HashMap<>();
	private final List<BlockPos> containerPositions = new ArrayList<>();
	private final List<ItemAccess> containers = new ArrayList<>();
	private final Player player;
	private final List<ItemStack> aggregatedItems = new ArrayList<>();
	private final List<ItemStack> displayedItems = new ArrayList<>();

	// ===== 排序模式（多条件并列，各自独立切换 无→↑→↓→无）=====
	public enum SortDirection {
		NONE, ASC, DESC
	}

	// 0=名称, 1=数量, 2=ID, 3=模组；默认选中「数量 · 由多到少」
	private final SortDirection[] sortDirs = new SortDirection[]{
		SortDirection.NONE, SortDirection.DESC, SortDirection.NONE, SortDirection.NONE
	};

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
		super(ModMenuTypes.get(), containerId);
		this.rows = PortableTerminalLayout.clampRows(rows);
		this.player = playerInventory.player;
		this.containerPositions.addAll(containerPositions);

		Level level = playerInventory.player.level();
		for (BlockPos pos : containerPositions) {
			BlockEntity be = level.getBlockEntity(pos);
			ItemAccess access = accessAt(be);
			if (access != null) containers.add(access);
		}

		this.playerInventory = playerInventory;
		this.terminalContainer = new VirtualTerminalContainer(terminalSlotCount());

		loadSortConfig();
		rebuildSlots();
	}

	public int getRows() {
		return rows;
	}

	public int terminalSlotCount() {
		return PortableTerminalLayout.terminalSlotCount(CAPACITY_ROWS);
	}

	/**
	 * 改显示行数：只按新行数重建槽位（数量与顺序不变），不重开界面。
	 * 槽位坐标在 26.x 里是 final，改不了，所以重建同一批槽位对象。
	 */
	public void applyViewRows(int viewRows) {
		int next = PortableTerminalLayout.clampRows(viewRows);
		if (next == this.rows) {
			return;
		}
		this.rows = next;
		rebuildSlots();
		this.scrollOffset = Math.min(this.scrollOffset, getMaxScroll());
		updateTerminalSlots();
	}

	/**
	 * 按当前 {@link #rows} 重建全部槽位：终端槽（固定最大行数）+ 主背包 + 快捷栏 + 副手。
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

		int invTop = PortableTerminalLayout.invTop(this.rows);
		int hotbarTop = PortableTerminalLayout.hotbarTop(this.rows);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				fresh.add(new Slot(playerInventory, col + row * 9 + 9,
					INV_LEFT + col * SLOT_SIZE, invTop + row * SLOT_SIZE));
			}
		}
		for (int col = 0; col < 9; col++) {
			fresh.add(new Slot(playerInventory, col, INV_LEFT + col * SLOT_SIZE, hotbarTop));
		}
		fresh.add(new Slot(playerInventory, 40, OFFHAND_X, PortableTerminalLayout.offhandY(this.rows)));

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

	// ==================== 终端显示 / 滚动 / 搜索 ====================
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
		displayedItems.sort((a, b) -> {
			for (int i = 0; i < sortDirs.length; i++) {
				SortDirection dir = sortDirs[i];
				if (dir == SortDirection.NONE) continue;
				int cmp = compareBy(a, b, i);
				if (cmp != 0) {
					return dir == SortDirection.ASC ? cmp : -cmp;
				}
			}
			return 0;
		});
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
		if (PinyinSearch.matches(stack.getHoverName().getString(), query)) {
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
		for (TagKey<Item> tag : stack.getTags().toList()) {
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
			List<Component> lines = stack.getTooltipLines(this.player, TooltipFlag.Default.NORMAL);
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

		if (slotIndex < terminalSlotCount()) {
			// 从终端取出一组到玩家背包（不含副手槽）
			ItemStack template = slot.getItem();
			ItemStack extracted = extractFromContainers(template, template.getMaxStackSize());
			if (!extracted.isEmpty()) {
				this.moveItemStackTo(extracted, terminalSlotCount(), terminalSlotCount() + 36, true);
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
				if (stack.getTag() != null && !stack.getTag().isEmpty()) {
					// 本档没有 Data Components，也没有 ItemStack.hashItemAndTags：
					// 同一物品靠「可疑 NBT 的 SNBT 串」区分（与原档 components hash 的用途一致）。
					key = key + "|" + stack.getTag();
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
		// 多条件并列排序：按 sortDirs 顺序应用，遇到 NONE 跳过
		aggregatedItems.sort((a, b) -> {
			for (int i = 0; i < sortDirs.length; i++) {
				SortDirection dir = sortDirs[i];
				if (dir == SortDirection.NONE) continue;
				int cmp = compareBy(a, b, i);
				if (cmp != 0) {
					return dir == SortDirection.ASC ? cmp : -cmp;
				}
			}
			return 0;
		});
		rebuildDisplayedItems();
	}

	/** 按第 index 个条件比较两个物品（升序结果） */
	private int compareBy(ItemStack a, ItemStack b, int index) {
		switch (index) {
			case 0: {
				String na = a.getHoverName().getString().toLowerCase();
				String nb = b.getHoverName().getString().toLowerCase();
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
			if (access != null) containers.add(access);
		}
		rebuildAggregatedItems();
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

	// ===== 排序方向 API（多条件并列，各自独立切换 无→↑→↓→无）=====
	public SortDirection getSortDir(int index) {
		return sortDirs[index];
	}

	public Component getSortName(int index) {
		return Component.translatable(SORT_NAME_KEYS[index]);
	}

	public int getSortCount() {
		return sortDirs.length;
	}

	public void cycleSortDir(int index) {
		SortDirection cur = sortDirs[index];
		// 单条件互斥：点击时先清除所有方向
		for (int i = 0; i < sortDirs.length; i++) sortDirs[i] = SortDirection.NONE;
		// 当前按钮两态切换：NONE→顺序(↑)，ASC→逆序(↓)，DESC→顺序(↑)
		// 不会变回 NONE，只有点击其他按钮时本按钮才会被清除
		sortDirs[index] = switch (cur) {
			case NONE -> SortDirection.ASC;
			case ASC -> SortDirection.DESC;
			case DESC -> SortDirection.ASC;
		};
		// 基于已聚合的物品重新排序，避免从客户端容器重建导致物品消失
		resortAggregatedItems();
		updateTerminalSlots();
		// 持久化排序配置
		saveSortConfig();
	}

	/** 从配置文件加载排序设置（使用语言无关的 key） */
	public void loadSortConfig() {
		String config = ModConfig.sortConfig;
		if (config == null || config.isEmpty()) return;
		// 格式: "name:ASC" 或 "count:DESC"（语言无关）
		String[] parts = config.split(":");
		if (parts.length != 2) return;
		for (int i = 0; i < SORT_PERSIST_KEYS.length; i++) {
			if (SORT_PERSIST_KEYS[i].equals(parts[0])) {
				// 先清掉内置默认（数量↓），否则默认项会和玩家真正选中的项一起显示为选中
				for (int j = 0; j < sortDirs.length; j++) sortDirs[j] = SortDirection.NONE;
				sortDirs[i] = SortDirection.valueOf(parts[1]);
				break;
			}
		}
	}

	/** 保存当前排序设置到配置文件（使用语言无关的 key） */
	private void saveSortConfig() {
		for (int i = 0; i < sortDirs.length; i++) {
			if (sortDirs[i] != SortDirection.NONE) {
				ModConfig.sortConfig = SORT_PERSIST_KEYS[i] + ":" + sortDirs[i].name();
				ModConfig.saveState();
				return;
			}
		}
		// 全部为 NONE 时清空配置
		ModConfig.sortConfig = "";
		ModConfig.saveState();
	}

	/** 仅对已聚合的物品重新排序并刷新显示，不重新扫描容器 */
	private void resortAggregatedItems() {
		aggregatedItems.sort((a, b) -> {
			for (int i = 0; i < sortDirs.length; i++) {
				SortDirection dir = sortDirs[i];
				if (dir == SortDirection.NONE) continue;
				int cmp = compareBy(a, b, i);
				if (cmp != 0) {
					return dir == SortDirection.ASC ? cmp : -cmp;
				}
			}
			return 0;
		});
		rebuildDisplayedItems();
	}

	/**
	 * 扫描玩家周围的容器。
	 *
	 * <p>不逐格遍历立方体：半径 128 时那是一个 257³ 的循环（约一千七百万次 getBlockEntity），
	 * 打开终端会明显卡顿。改为只枚举范围内**已加载区块的方块实体表**，代价与容器数量成正比。</p>
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
		if (com.portableterminal.config.ConfigSchema.ORDER_FARTHEST.equals(order)) {
			positions.sort((a, b) -> Double.compare(b.distSqr(origin), a.distSqr(origin)));
		} else if (com.portableterminal.config.ConfigSchema.ORDER_COORDINATES.equals(order)) {
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
