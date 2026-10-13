package com.portableterminal.network;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
			// 本档没有 Data Components，物品直接按 NBT 写入（writeItem/readItem）
			buf.writeItem(stack);
		}
	}

	public static TerminalItemsSyncPacket read(FriendlyByteBuf buf) {
		int count = buf.readShort();
		List<ItemStack> items = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			items.add(buf.readItem());
		}
		return new TerminalItemsSyncPacket(items);
	}

	public List<ItemStack> toItemStacks() {
		return items;
	}

	public static TerminalItemsSyncPacket fromItemStacks(List<ItemStack> stacks) {
		return new TerminalItemsSyncPacket(new ArrayList<>(stacks));
	}
}
