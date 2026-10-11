package com.portablestorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.portablestorage.PortableStorageMod;

import java.util.ArrayList;
import java.util.List;

public record TerminalItemsSyncPacket(List<ItemStack> items) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TerminalItemsSyncPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild(PortableStorageMod.MOD_ID, "terminal_items_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalItemsSyncPacket> CODEC = StreamCodec.of(
		(buf, val) -> {
			buf.writeShort(val.items().size());
			for (ItemStack stack : val.items()) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
			}
		},
		buf -> {
			int count = buf.readShort();
			List<ItemStack> items = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
			}
			return new TerminalItemsSyncPacket(items);
		}
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public List<ItemStack> toItemStacks() {
		return items;
	}

	public static TerminalItemsSyncPacket fromItemStacks(List<ItemStack> stacks) {
		return new TerminalItemsSyncPacket(new ArrayList<>(stacks));
	}
}
