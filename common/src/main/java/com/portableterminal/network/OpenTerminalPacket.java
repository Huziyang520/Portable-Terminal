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
 * <p>{@code rows} 是客户端的行数设置：两端必须按同一套槽位布局建菜单，所以随请求带过去。
 * {@code craftSource} / {@code clearDestination} 是客户端偏好，但配方摆放与清空网格都在服务端执行，
 * 所以也要一起带上，否则服务端不知道新材料该从终端取还是从背包取。</p>
 */
public record OpenTerminalPacket(int rows, int craftSource, int clearDestination, boolean crafting)
	implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenTerminalPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild("portableterminal", "open_terminal"));

	public static final StreamCodec<FriendlyByteBuf, OpenTerminalPacket> CODEC = StreamCodec.of(
		(buf, packet) -> {
			buf.writeByte(PortableTerminalLayout.clampRows(packet.rows()));
			buf.writeByte(packet.craftSource());
			buf.writeByte(packet.clearDestination());
			buf.writeBoolean(packet.crafting());
		},
		buf -> new OpenTerminalPacket(buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Server side handling; invoked by the loader's packet bootstrap. */
	public static void handleServer(ServerPlayer player, int rows, int craftSource, int clearDestination,
			boolean crafting) {
		int clampedRows = PortableTerminalLayout.clampRows(rows);
		List<BlockPos> containers = PortableTerminalMenu.scanNearbyContainers(player);

		if (containers.isEmpty()) {
			player.sendSystemMessage(Component.translatable("msg.portableterminal.no_containers"));
			return;
		}

		PortableTerminalMenu.setPendingPositions(player, containers);
		// 切行数会重开终端：不走 openMenu 里的 closeContainer（它多发一个关闭包，客户端会先
		// setScreen(null)，可能触发重新抓取鼠标并把光标挪到屏幕正中）。doCloseContainer() 与
		// closeContainer() 做的事一样但不发包；openMenu 见当前已是 inventoryMenu 就不再关闭，
		// 客户端直接由打开包换到新界面，光标留在原处。
		if (player.containerMenu instanceof PortableTerminalMenu) {
			player.doCloseContainer();
		}
		player.openMenu(new PortableTerminalMenuProvider(containers, clampedRows, craftSource, clearDestination, crafting));

		if (player.containerMenu instanceof PortableTerminalMenu psm) {
			psm.rebuildAggregatedItems();
			Services.PLATFORM.sendToPlayer(player, TerminalItemsSyncPacket.fromItemStacks(psm.getAggregatedItems()));
		}
	}

	private static class PortableTerminalMenuProvider implements net.minecraft.world.MenuProvider {
		private final List<BlockPos> containers;
		private final int rows;
		private final int craftSource;
		private final int clearDestination;
		private final boolean crafting;

		public PortableTerminalMenuProvider(List<BlockPos> containers, int rows, int craftSource, int clearDestination,
				boolean crafting) {
			this.containers = containers;
			this.rows = rows;
			this.craftSource = craftSource;
			this.clearDestination = clearDestination;
			this.crafting = crafting;
		}

		@Override
		public PortableTerminalMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
			PortableTerminalMenu menu = new PortableTerminalMenu(containerId, inventory, containers, rows);
			menu.setCraftingModes(craftSource, clearDestination);
			// 服务端也要知道合成带是否显示：关掉时不能往看不见的格子里摆配方
			menu.applyViewLayout(rows, crafting);
			return menu;
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("gui.portableterminal.title");
		}
	}
}
