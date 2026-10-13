package com.portableterminal.neoforge;

import com.portableterminal.inventory.ItemAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * NeoForge 方块物品能力（{@code Capabilities.ItemHandler.BLOCK}，处理类型为 {@link IItemHandler}）
 * 到 {@link ItemAccess} 的薄适配。20.4 用的还是经典 IItemHandler，不是 26.x 的 transfer API。
 */
public class NeoForgeItemHandlerAccess implements ItemAccess {

	private final IItemHandler handler;

	public NeoForgeItemHandlerAccess(IItemHandler handler) {
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
		return handler.insertItem(slot, stack, false);
	}

	public ItemStack extract(int slot, int amount) {
		return handler.extractItem(slot, amount, false);
	}
}
