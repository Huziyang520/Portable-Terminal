package com.portableterminal.network;

import com.portableterminal.menu.PortableTerminalLayout;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Asks the server to open the terminal.
 *
 * <p>1.20.2~1.20.4 has no {@code StreamCodec}/{@code RegistryFriendlyByteBuf}: a payload is just
 * {@code id()} + {@code write(FriendlyByteBuf)}, and the reader is a {@code FriendlyByteBuf.Reader}
 * handed to the loader's registrar. The field design is unchanged from the other branches:
 * {@code rows} is the client's terminal height setting, carried so the server builds the menu with
 * exactly the same slot layout the client expects.</p>
 */
public record OpenTerminalPacket(int rows) implements CustomPacketPayload {

	public static final ResourceLocation ID = ResourceLocation.tryBuild("portableterminal", "open_terminal");

	@Override
public ResourceLocation id() {
		return ID;
	}

	@Override
public void write(FriendlyByteBuf buf) {
		buf.writeByte(PortableTerminalLayout.clampRows(rows));
	}

	public static OpenTerminalPacket read(FriendlyByteBuf buf) {
		return new OpenTerminalPacket(buf.readByte());
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
		// 切行数时（当前已经是终端菜单）不要走 openMenu 里的 closeContainer：那会多发一个
		// ClientboundContainerClosePacket，客户端会先 setScreen(null) 再 setScreen(新界面)，
		// 中间那一帧原版压暗层与面板不一致，表现为切行数时背景「呼吸」闪烁。
		// doCloseContainer() 与 closeContainer() 做的事完全一样，只是不发那个关闭包；
		// openMenu 见当前已是 inventoryMenu 就不会再关一次，客户端直接由打开包换到新界面。
		if (player.containerMenu instanceof PortableTerminalMenu) {
			player.doCloseContainer();
		}
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
