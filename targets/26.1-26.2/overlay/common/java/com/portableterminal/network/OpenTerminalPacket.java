package com.portableterminal.network;

import com.portableterminal.menu.PortableTerminalLayout;
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

/**
 * Asks the server to open the terminal.
 *
 * <p>{@code rows} is the client's terminal height setting. It travels with the request so the
 * server builds the menu with exactly the same slot layout the client expects — the menu layout
 * cannot be derived independently on the two sides.</p>
 */
public record OpenTerminalPacket(int rows) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenTerminalPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild("portableterminal", "open_terminal"));

	public static final StreamCodec<FriendlyByteBuf, OpenTerminalPacket> CODEC = StreamCodec.of(
		(buf, packet) -> buf.writeByte(PortableTerminalLayout.clampRows(packet.rows())),
		buf -> new OpenTerminalPacket(buf.readByte())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Server side handling; invoked by the loader's packet bootstrap. */
	public static void handleServer(ServerPlayer player, int rows) {
		int clampedRows = PortableTerminalLayout.clampRows(rows);
		List<BlockPos> containers = PortableTerminalMenu.scanNearbyContainers(player);

		if (containers.isEmpty()) {
			player.sendSystemMessage(Component.translatable("msg.portableterminal.no_containers"));
			return;
		}

		PortableTerminalMenu.setPendingPositions(player, containers);
		player.openMenu(new PortableTerminalMenuProvider(containers, clampedRows));

		if (player.containerMenu instanceof PortableTerminalMenu psm) {
			psm.rebuildAggregatedItems();
			Services.PLATFORM.sendToPlayer(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
		}
	}

	private static class PortableTerminalMenuProvider implements net.minecraft.world.MenuProvider {
		private final List<BlockPos> containers;
		private final int rows;

		public PortableTerminalMenuProvider(List<BlockPos> containers, int rows) {
			this.containers = containers;
			this.rows = rows;
		}

		@Override
		public PortableTerminalMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
			return new PortableTerminalMenu(containerId, inventory, containers, rows);
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("gui.portableterminal.title");
		}
	}
}
