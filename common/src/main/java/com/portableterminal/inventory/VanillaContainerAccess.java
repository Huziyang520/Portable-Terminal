package com.portableterminal.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 原版 {@link Container} 的 {@link ItemAccess} 适配。
 *
 * <p>让原版容器和模组容器走同一条存取路径：堆叠合并、寻空位等规则统一在 {@link ItemAccess}
 * 的静态方法里，这里只做原语映射。</p>
 */
public record VanillaContainerAccess(Container container) implements ItemAccess {

	@Override
	public int size() {
		return container.getContainerSize();
	}

	@Override
	public ItemStack get(int slot) {
		return container.getItem(slot);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return container.canPlaceItem(slot, stack);
	}

	@Override
	public ItemStack insert(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int limit = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
		ItemStack slotStack = container.getItem(slot);
		if (slotStack.isEmpty()) {
			int place = Math.min(stack.getCount(), limit);
			if (place <= 0) {
				return stack;
			}
			container.setItem(slot, stack.copyWithCount(place));
			container.setChanged();
			return stack.copyWithCount(stack.getCount() - place);
		}
		if (!ItemStack.isSameItemSameTags(slotStack, stack)) {
			return stack;
		}
		int space = limit - slotStack.getCount();
		if (space <= 0) {
			return stack;
		}
		int place = Math.min(stack.getCount(), space);
		slotStack.grow(place);
		container.setItem(slot, slotStack);
		container.setChanged();
		return stack.copyWithCount(stack.getCount() - place);
	}

	@Override
	public ItemStack extract(int slot, int amount) {
		ItemStack slotStack = container.getItem(slot);
		if (slotStack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int take = Math.min(amount, slotStack.getCount());
		if (take <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = slotStack.copyWithCount(take);
		slotStack.shrink(take);
		container.setItem(slot, slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
		container.setChanged();
		return taken;
	}
}
