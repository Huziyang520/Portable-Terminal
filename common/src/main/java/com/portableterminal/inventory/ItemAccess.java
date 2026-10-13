package com.portableterminal.inventory;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * 物品栏抽象：把「原版 {@code Container}」和「各加载器能力系统的物品栏」统一成同一套读写口。
 *
 * <p>为什么需要它：模组容器（抽屉、储物桶、各种机器的物品栏）通常只通过加载器能力暴露物品栏，
 * 并不实现原版 {@code Container}，只按 {@code Container} 检索会整片漏掉；而两端的原生 API 又
 * 完全不同（Fabric 是 {@code Storage<ItemVariant>}，NeoForge 是 {@code ResourceHandler<ItemResource>}）。</p>
 *
 * <p>分工：各加载器只实现下面 5 个原语（就四个必需 + 一个可选的可放校验），**堆叠合并、寻空位、
 * 按模板取出这些规则全部写成下面的静态方法放在 common**，两端行为天然一致，也不针对任何具体模组。</p>
 */
public interface ItemAccess {

	/** 槽位数。 */
	int size();

	/** 只读取第 slot 槽的物品；空槽返回 {@link ItemStack#EMPTY}。 */
	ItemStack get(int slot);

	/** 往第 slot 槽插入，返回**没插进去**的剩余（全插进去返回 EMPTY）。 */
	ItemStack insert(int slot, ItemStack stack);

	/** 从第 slot 槽取出至多 amount 个，返回实际取到的（没取到返回 EMPTY）。 */
	ItemStack extract(int slot, int amount);

	/** 该槽是否允许放入这种物品。 */
	default boolean isValid(int slot, ItemStack stack) {
		return true;
	}

	// ==================== 以下是两端共用的通用规则 ====================

	/** 该槽对某种物品的堆叠上限。 */
	static int maxStackSize(int slot, ItemStack template) {
		return Math.max(1, template.getMaxStackSize());
	}

	/** 只读地列出所有非空槽（每槽一份快照），用于聚合统计。 */
	static void collectSlots(ItemAccess access, List<ItemStack> out) {
		for (int i = 0; i < access.size(); i++) {
			ItemStack stack = access.get(i);
			if (!stack.isEmpty()) {
				out.add(stack.copy());
			}
		}
	}

	/**
	 * 插入整组物品：先并入同类堆，再放进空位，返回最终没插进去的部分。
	 *
	 * <p>先并堆再找空位是为了一点零散空格不被占满；这条规则只写在这里，两端不会各写一套。</p>
	 */
	static ItemStack insertAll(ItemAccess access, ItemStack stack) {
		ItemStack remaining = stack.copy();
		// 1. 并入同类堆
		for (int i = 0; i < access.size() && !remaining.isEmpty(); i++) {
			ItemStack slotStack = access.get(i);
			if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, remaining)) continue;
			if (!access.isValid(i, remaining)) continue;
			remaining = access.insert(i, remaining);
		}
		// 2. 放进空位
		for (int i = 0; i < access.size() && !remaining.isEmpty(); i++) {
			if (!access.get(i).isEmpty()) continue;
			if (!access.isValid(i, remaining)) continue;
			remaining = access.insert(i, remaining);
		}
		return remaining;
	}

	/** 按模板取出至多 count 个（跨槽累计），返回实际取到的部分。 */
	static ItemStack extractMatching(ItemAccess access, ItemStack template, int count) {
		ItemStack extracted = ItemStack.EMPTY;
		int remaining = count;
		for (int i = 0; i < access.size() && remaining > 0; i++) {
			ItemStack slotStack = access.get(i);
			if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, template)) continue;
			ItemStack taken = access.extract(i, Math.min(remaining, slotStack.getCount()));
			if (taken.isEmpty()) continue;
			if (extracted.isEmpty()) {
				extracted = taken;
			} else {
				extracted.grow(taken.getCount());
			}
			remaining -= taken.getCount();
		}
		return extracted;
	}
}
