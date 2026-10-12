// Portions of this code (terminal item aggregation, container scanning, GUI layout)
// are based on work by tom5454 (Tom's Simple Storage Mod),
// distributed under the MIT License. See LICENSE.toms-simple-storage for details.

package com.portableterminal.menu;

import com.portableterminal.config.ModConfig;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.Services;
import com.portableterminal.util.PinyinSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

	/** 终端可见行数。客户端配置决定，并在打开终端时随包发给服务端，两端布局才一致。 */
	private final int rows;

	private static final Map<UUID, List<BlockPos>> PENDING_POSITIONS = new HashMap<>();
	private final List<BlockPos> containerPositions = new ArrayList<>();
	private final List<Container> containers = new ArrayList<>();
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
	private int scrollOffset = 0;
	private String searchQuery = "";

	public PortableTerminalMenu(int containerId, Inventory playerInventory, List<BlockPos> containerPositions, int rows) {
		super(ModMenuTypes.get(), containerId);
		this.rows = PortableTerminalLayout.clampRows(rows);
		this.player = playerInventory.player;
		this.containerPositions.addAll(containerPositions);

		Level level = playerInventory.player.level();
		for (BlockPos pos : containerPositions) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container container) containers.add(container);
		}

		loadSortConfig();

		// 终端虚拟槽（索引 0 .. terminalSlotCount()-1）
		VirtualTerminalContainer terminalContainer = new VirtualTerminalContainer(terminalSlotCount());
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < COLS; col++) {
				int idx = row * COLS + col;
				int sx = TERM_LEFT + col * SLOT_SIZE;
				int sy = TERM_TOP + row * SLOT_SIZE;
				TerminalSlot slot = new TerminalSlot(terminalContainer, idx, sx, sy);
				terminalSlots.add(slot);
				this.addSlot(slot);
			}
		}

		// 主背包 3x9 + 快捷栏 + 副手（副手与快捷栏同一行，下边对齐）
		int invTop = PortableTerminalLayout.invTop(this.rows);
		int hotbarTop = PortableTerminalLayout.hotbarTop(this.rows);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int sx = INV_LEFT + col * SLOT_SIZE;
				int sy = invTop + row * SLOT_SIZE;
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, sx, sy));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, INV_LEFT + col * SLOT_SIZE, hotbarTop));
		}
		this.addSlot(new Slot(playerInventory, 40, OFFHAND_X, PortableTerminalLayout.offhandY(this.rows)));
	}

	public int getRows() {
		return rows;
	}

	public int terminalSlotCount() {
		return PortableTerminalLayout.terminalSlotCount(rows);
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

	/** 客户端搜索过滤：按物品显示名或注册ID（不区分大小写） */
	public void applySearchFilter(String query) {
		this.searchQuery = query == null ? "" : query.trim().toLowerCase();
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
	}

	private boolean matchesSearch(ItemStack stack, String query) {
		// 标签搜索：#tagName 或 #namespace:tagName
		if (query.startsWith("#")) {
			String tagName = query.substring(1);
			if (tagName.isEmpty()) return false;
			try {
				Identifier tagId = tagName.contains(":")
					? Identifier.parse(tagName)
					: Identifier.withDefaultNamespace(tagName);
				TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
				return stack.is((java.util.function.Predicate<Holder<Item>>) holder -> holder.is(tagKey));
			} catch (Exception e) {
				return false;
			}
		}
		// 名称搜索：装了「通用拼音搜索」时由它接管（支持全拼、首字母、简繁），否则退化为子串匹配
		String name = stack.getItem().getName(stack).getString();
		if (PinyinSearch.matches(name, query)) return true;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
		return id.contains(query);
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
		super.initializeContents(stateId, items, carried);
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
			if (be instanceof Container container) {
				if (i < containers.size()) containers.set(i, container);
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
					ItemStack leftover = insertIntoContainers(extracted);
					if (!leftover.isEmpty()) {
						player.drop(leftover, false);
					}
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
		for (Container container : containers) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (stack.isEmpty()) continue;
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
				String na = a.getItem().getName(a).getString().toLowerCase();
				String nb = b.getItem().getName(b).getString().toLowerCase();
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
		for (Container container : containers) {
			for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
				ItemStack stack = container.getItem(i);
				if (stack.isEmpty()) continue;
				if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
				int take = Math.min(remaining, stack.getCount());
				if (extracted.isEmpty()) {
					extracted = stack.copyWithCount(take);
				} else {
					extracted.grow(take);
				}
				stack.shrink(take);
				container.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
				container.setChanged();
				remaining -= take;
			}
		}
		rebuildAggregatedItems();
		return extracted;
	}

	public ItemStack insertIntoContainers(ItemStack stack) {
		ItemStack remaining = stack.copy();
		for (Container container : containers) {
			for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
				if (!container.canPlaceItem(i, remaining)) continue;
				ItemStack slotStack = container.getItem(i);
				if (slotStack.isEmpty()) {
					int maxStackSize = Math.min(remaining.getMaxStackSize(), container.getMaxStackSize());
					int place = Math.min(remaining.getCount(), maxStackSize);
					container.setItem(i, remaining.copyWithCount(place));
					container.setChanged();
					remaining.shrink(place);
				} else if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
					int maxStackSize = Math.min(slotStack.getMaxStackSize(), container.getMaxStackSize());
					int space = maxStackSize - slotStack.getCount();
					if (space > 0) {
						int place = Math.min(remaining.getCount(), space);
						slotStack.grow(place);
						remaining.shrink(place);
						container.setItem(i, slotStack);
						container.setChanged();
					}
				}
			}
		}
		rebuildAggregatedItems();
		return remaining;
	}

	public void refreshContainers() {
		Level level = player.level();
		containers.clear();
		for (BlockPos pos : containerPositions) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container container) containers.add(container);
		}
		rebuildAggregatedItems();
	}

	public List<BlockPos> getContainerPositions() { return containerPositions; }
	public List<Container> getContainers() { return containers; }
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
				ModConfig.save();
				return;
			}
		}
		// 全部为 NONE 时清空配置
		ModConfig.sortConfig = "";
		ModConfig.save();
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

	public static List<BlockPos> scanNearbyContainers(ServerPlayer player) {
		List<BlockPos> positions = new ArrayList<>();
		Level level = player.level();
		BlockPos playerPos = player.blockPosition();
		int radius = ModConfig.scanRadius;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = playerPos.offset(dx, dy, dz);
					BlockEntity be = level.getBlockEntity(pos);
					if (!(be instanceof Container)) continue;
					String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
					if (ModConfig.blacklistBlockIds.contains(blockId)) continue;
					positions.add(pos);
				}
			}
		}
		return positions;
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
