package com.portableterminal.network;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record TerminalItemsSyncPacket(List<ItemStack> items) implements CustomPacketPayload {

	public static final ResourceLocation ID = ResourceLocation.tryBuild(PortableTerminalMod.MOD_ID, "terminal_items_sync");

	@Override
public ResourceLocation id() {
		return ID;
	}

	@Override
public void write(FriendlyByteBuf buf) {
		buf.writeShort(items.size());
		for (ItemStack stack : items) {
			writeStack(buf, stack);
		}
	}

	public static TerminalItemsSyncPacket read(FriendlyByteBuf buf) {
		int count = buf.readShort();
		List<ItemStack> items = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			items.add(readStack(buf));
		}
		return new TerminalItemsSyncPacket(items);
	}

	/**
	 * 自己写物品，数量用 VarInt。
	 *
	 * <p>本档原版的 {@code FriendlyByteBuf.writeItem/readItem} 把数量写成**一字节有符号数**
	 * （1.20.1 字节码：{@code readByte()} → {@code new ItemStack(item, count)}），终端聚合起来
	 * 的数量一旦到 128 就会读成 -128 —— 负数数量的 ItemStack 即视为空，于是整个槽位不可见
	 * （127 及以下正常）。这里把数量改成 VarInt，128 以上也能正确同步。</p>
	 */
	private static void writeStack(FriendlyByteBuf buf, ItemStack stack) {
		if (stack.isEmpty()) {
			buf.writeBoolean(false);
			return;
		}
		buf.writeBoolean(true);
		buf.writeVarInt(Item.getId(stack.getItem()));
		buf.writeVarInt(stack.getCount());
		buf.writeNbt(stack.getTag());
	}

	private static ItemStack readStack(FriendlyByteBuf buf) {
		if (!buf.readBoolean()) {
			return ItemStack.EMPTY;
		}
		Item item = Item.byId(buf.readVarInt());
		if (item == null) {
			buf.readVarInt();
			buf.readNbt();
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item, buf.readVarInt());
		stack.setTag(buf.readNbt());
		return stack;
	}

	public List<ItemStack> toItemStacks() {
		return items;
	}

	public static TerminalItemsSyncPacket fromItemStacks(List<ItemStack> stacks) {
		return new TerminalItemsSyncPacket(new ArrayList<>(stacks));
	}
}
