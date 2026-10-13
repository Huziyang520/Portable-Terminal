package com.portableterminal.forge;

import com.portableterminal.inventory.ItemAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge 方块物品能力（{@code ForgeCapabilities.ITEM_HANDLER}，处理类型为 {@link IItemHandler}）
 * 到 {@link ItemAccess} 的薄适配。
 *
 * <p>IItemHandler 不是原版 {@code Container}，所以按 {@code Container} 检索会漏掉只走能力的模组
 * 容器；这里只做槽位索引的直接映射，堆叠合并、寻空位等规则留在 common 的 {@link ItemAccess}。</p>
 */
public class ForgeItemHandlerAccess implements ItemAccess {

	private final IItemHandler handler;

	public ForgeItemHandlerAccess(IItemHandler handler) {
		this.handler = handler;
	}

	public int size() {
		return handler.getSlots();
	}

	public ItemStack get(int slot) {
		return handler.getStackInSlot(slot);
	}

	public boolean isValid(int slot, ItemStack stack) {
		return !stack.isEmpty() && handler.isItemValid(slot, stack);
	}

	public ItemStack insert(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		// simulate=false 才是真插入；返回的是「没插进去」的剩余，正好是 ItemAccess 的约定。
		return handler.insertItem(slot, stack, false);
	}

	public ItemStack extract(int slot, int amount) {
		return handler.extractItem(slot, amount, false);
	}
}
