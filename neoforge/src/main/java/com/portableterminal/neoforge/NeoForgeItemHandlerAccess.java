package com.portableterminal.neoforge;

import com.portableterminal.inventory.ItemAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * NeoForge 方块物品能力（{@code Capabilities.ItemHandler.BLOCK}，处理类型 {@link IItemHandler}）
 * 到 {@link ItemAccess} 的薄适配。1.20.2 ~ 1.21.11 全线都用这一份。
 *
 * <p>{@code IItemHandler} 的语义与本项目的 {@link ItemAccess} 正好一一对应：
 * {@code insertItem} 返回「没插进去的剩余」、{@code extractItem} 返回「实际取到的」，
 * 所以这里只是原语直译；堆叠合并、寻空位等规则全部留在 common 的静态方法里，两端一致。</p>
 */
public class NeoForgeItemHandlerAccess implements ItemAccess {

	private final IItemHandler handler;

	public NeoForgeItemHandlerAccess(IItemHandler handler) {
		this.handler = handler;
	}

	@Override
public int size() {
		return handler.getSlots();
	}

	@Override
public ItemStack get(int slot) {
		return handler.getStackInSlot(slot);
	}

	@Override
public boolean isValid(int slot, ItemStack stack) {
		return !stack.isEmpty() && handler.isItemValid(slot, stack);
	}

	@Override
public ItemStack insert(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return handler.insertItem(slot, stack, false);
	}

	@Override
public ItemStack extract(int slot, int amount) {
		return handler.extractItem(slot, amount, false);
	}
}
