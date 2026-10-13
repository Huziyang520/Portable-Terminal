package com.portableterminal.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 把 Fabric Transfer API 的 {@link Storage}（{@code ItemStorage.SIDED} 查出来的那个）包成原版
 * {@link Container} 视图。
 *
 * <p>模组容器通常只通过 {@code ItemStorage.SIDED} 暴露物品栏，并不实现原版 {@code Container}，
 * 所以只按 {@code Container} 检索会漏掉它们。包成 Container 之后，上层的扫描、聚合、插入、
 * 取出都不需要区分原版与模组，也不针对任何具体模组。</p>
 *
 * <p>注意 {@code ItemStorage.SIDED} 给出的是整体 {@code Storage}，没有槽位索引，所以这里按
 * {@link StorageView} 迭代来充当「槽位」。所有写入都包在 {@link Transaction} 里提交，失败不会
 * 半途生效。</p>
 */
public class FabricItemStorageContainer implements Container {

	private final Storage<ItemVariant> storage;

	public FabricItemStorageContainer(Storage<ItemVariant> storage) {
		this.storage = storage;
	}

	/** 第 slot 个视图；越界返回 null。每次调用都重新迭代，避免持有过期视图。 */
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
	public int getContainerSize() {
		int count = 0;
		for (StorageView<ItemVariant> ignored : storage) {
			count++;
		}
		return count;
	}

	@Override
	public boolean isEmpty() {
		for (StorageView<ItemVariant> view : storage) {
			if (!view.isResourceBlank() && view.getAmount() > 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null || view.isResourceBlank()) {
			return ItemStack.EMPTY;
		}
		long amount = Math.min(view.getAmount(), Integer.MAX_VALUE);
		return view.getResource().toStack((int) amount);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null || view.isResourceBlank()) {
			return ItemStack.EMPTY;
		}
		ItemVariant variant = view.getResource();
		try (Transaction tx = Transaction.openOuter()) {
			long moved = view.extract(variant, amount, tx);
			tx.commit();
			return variant.toStack((int) moved);
		}
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null || view.isResourceBlank()) {
			return ItemStack.EMPTY;
		}
		return removeItem(slot, (int) Math.min(view.getAmount(), Integer.MAX_VALUE));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		// 能力接口没有直接 set：先清空该槽，再插入
		removeItemNoUpdate(slot);
		if (stack.isEmpty()) {
			return;
		}
		StorageView<ItemVariant> view = viewAt(slot);
		if (view == null) {
			return;
		}
		try (Transaction tx = Transaction.openOuter()) {
			storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
			tx.commit();
		}
	}

	@Override
	public void setChanged() {
		// 能力驱动的容器自行处理变更通知
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < getContainerSize(); i++) {
			removeItemNoUpdate(i);
		}
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}
}
