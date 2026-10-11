package com.portableterminal.network;

import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.platform.Services;
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
		new CustomPacketPayload.Type<>(Identifier.tryBuild("portableterminal", "open_terminal"));

	public static final StreamCodec<FriendlyByteBuf, OpenTerminalPacket> CODEC =
		StreamCodec.unit(new OpenTerminalPacket());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Server side handling; invoked by the loader's packet bootstrap. */
	public static void handleServer(ServerPlayer player) {
		List<BlockPos> containers = PortableTerminalMenu.scanNearbyContainers(player);

		if (containers.isEmpty()) {
			player.sendSystemMessage(Component.translatable("msg.portableterminal.no_containers"));
			return;
		}

		PortableTerminalMenu.setPendingPositions(player, containers);
		player.openMenu(new PortableTerminalMenuProvider(containers));

		if (player.containerMenu instanceof PortableTerminalMenu psm) {
			psm.rebuildAggregatedItems();
			Services.PLATFORM.sendToPlayer(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
		}
	}

	private static class PortableTerminalMenuProvider implements net.minecraft.world.MenuProvider {
		private final List<BlockPos> containers;

		public PortableTerminalMenuProvider(List<BlockPos> containers) {
			this.containers = containers;
		}

		@Override
		public PortableTerminalMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
			return new PortableTerminalMenu(containerId, inventory, containers);
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("gui.portableterminal.title");
		}
	}
}
