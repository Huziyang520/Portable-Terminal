package com.portablestorage.network;

import com.portablestorage.menu.PortableStorageMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

public record OpenTerminalPacket() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenTerminalPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild("portablestorage", "open_terminal"));

	public static final StreamCodec<FriendlyByteBuf, OpenTerminalPacket> CODEC =
		StreamCodec.unit(new OpenTerminalPacket());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(OpenTerminalPacket packet, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		List<BlockPos> containers = PortableStorageMenu.scanNearbyContainers(player);

		if (containers.isEmpty()) {
			player.sendSystemMessage(Component.translatable("msg.portablestorage.no_containers"));
			return;
		}

		PortableStorageMenu.setPendingPositions(player, containers);
		player.openMenu(new PortableStorageMenuProvider(containers));

		if (player.containerMenu instanceof PortableStorageMenu psm) {
			psm.rebuildAggregatedItems();
			ServerPlayNetworking.send(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
		}
	}

	private static class PortableStorageMenuProvider implements net.minecraft.world.MenuProvider {
		private final List<BlockPos> containers;

		public PortableStorageMenuProvider(List<BlockPos> containers) {
			this.containers = containers;
		}

		@Override
		public PortableStorageMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
			return new PortableStorageMenu(containerId, inventory, containers);
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("gui.portablestorage.title");
		}
	}
}
