package com.portableterminal.neoforge;

import com.portableterminal.inventory.ItemAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge 方块物品能力（26.2 起是 {@code Capabilities.Item.BLOCK}，处理类型为
 * {@link ResourceHandler}{@code <}{@link ItemResource}{@code >}）到 {@link ItemAccess} 的薄适配。
 *
 * <p>26.2 的 NeoForge 已把方块物品能力从旧的 {@code IItemHandler} 迁到新的 transfer API，
 * 所以这里按 {@code ResourceHandler} 的槽位索引直接映射；堆叠合并、寻空位等规则留在
 * common 的 {@link ItemAccess} 静态方法里，两端一致。写入都包在 {@link Transaction} 里提交。</p>
 */
public class NeoForgeResourceHandlerAccess implements ItemAccess {

	private final ResourceHandler<ItemResource> handler;

	public NeoForgeResourceHandlerAccess(ResourceHandler<ItemResource> handler) {
		this.handler = handler;
	}

	@Override
	public int size() {
		return handler.size();
	}

	@Override
	public ItemStack get(int slot) {
		ItemResource resource = handler.getResource(slot);
		if (resource == null || resource.isEmpty()) {
			return ItemStack.EMPTY;
		}
		long amount = Math.min(Math.max(handler.getAmountAsLong(slot), 0L), Integer.MAX_VALUE);
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		return resource.toStack((int) amount);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return !stack.isEmpty() && handler.isValid(slot, ItemResource.of(stack));
	}

	@Override
	public ItemStack insert(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Transaction tx = Transaction.openRoot();
		int moved = handler.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
		tx.commit();
		tx.close();
		if (moved <= 0) {
			return stack;
		}
		return stack.copyWithCount(stack.getCount() - moved);
	}

	@Override
	public ItemStack extract(int slot, int amount) {
		ItemResource resource = handler.getResource(slot);
		if (resource == null || resource.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Transaction tx = Transaction.openRoot();
		int moved = handler.extract(slot, resource, amount, tx);
		tx.commit();
		tx.close();
		if (moved <= 0) {
			return ItemStack.EMPTY;
		}
		return resource.toStack(moved);
	}
}
