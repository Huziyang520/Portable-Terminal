package com.portableterminal.fabric;

import com.portableterminal.inventory.ItemAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric Transfer API 的 {@link Storage}（{@code ItemStorage.SIDED} 查出来的那个）到
 * {@link ItemAccess} 的薄适配。
 *
 * <p>两点要注意：
 * <ul>
 *   <li>{@code ItemStorage.SIDED} 给出的是整体 Storage，**没有槽位索引**，所以按
 *       {@link StorageView} 迭代来充当「槽位」；每次取槽都重新迭代，避免持有过期视图。</li>
 *   <li>{@link StorageView} 只有 extract，没有 insert，插入只能走父 Storage。于是
 *       {@link #insert} 忽略具体槽位（由 Storage 自行决定落到哪），上层「先并堆再寻空位」
 *       的规则在 common，结果依旧正确。</li>
 * </ul>
 * 写入都包在 {@link Transaction} 里提交，失败不会半途生效。</p>
 */
public class FabricStorageAccess implements ItemAccess {

	private final Storage<ItemVariant> storage;

	public FabricStorageAccess(Storage<ItemVariant> storage) {
		this.storage = storage;
	}

	private StorageView<ItemVariant> viewAt(int slot) {
		if (slot < 0) {
			return null;
		}
		int index = 0;
		for (StorageView<ItemVariant> view : storage) {
			if (index++ == slot) {
				return view;
			}
		}
		return null;
	}

	@Override
	public int size() {
		int count = 0;
		for (StorageView<ItemVariant> ignored : storage) {
			count++;
		}
		return count;
	}

	@Override
	public ItemStack get(int slot) {
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null || view.isResourceBlank()) {
			return ItemStack.EMPTY;
		}
		long amount = Math.min(Math.max(view.getAmount(), 0L), Integer.MAX_VALUE);
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		return view.getResource().toStack((int) amount);
	}

	@Override
	public ItemStack insert(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemVariant variant = ItemVariant.of(stack);
		try (Transaction tx = Transaction.openOuter()) {
			long moved = storage.insert(variant, stack.getCount(), tx);
			tx.commit();
			if (moved <= 0) {
				return stack;
			}
			return stack.copyWithCount(stack.getCount() - (int) moved);
		}
	}

	@Override
	public ItemStack extract(int slot, int amount) {
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null || view.isResourceBlank()) {
			return ItemStack.EMPTY;
		}
		ItemVariant variant = view.getResource();
		try (Transaction tx = Transaction.openOuter()) {
			long moved = view.extract(variant, amount, tx);
			tx.commit();
			if (moved <= 0) {
				return ItemStack.EMPTY;
			}
			return variant.toStack((int) moved);
		}
	}
}
