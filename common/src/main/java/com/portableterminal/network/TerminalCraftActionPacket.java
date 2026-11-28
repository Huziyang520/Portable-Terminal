package com.portableterminal.network;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.PortableTerminalMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端对合成区的两个动作：清空网格、改「取料来源 / 清空去向」。
 *
 * <p>合成网格的内容与取料逻辑都在服务端（配方摆放由服务端执行），所以按键不能只改本地状态。</p>
 */
public record TerminalCraftActionPacket(int action, int first, int second) implements CustomPacketPayload {

	public static final int ACTION_CLEAR_GRID = 0;
	public static final int ACTION_SET_MODES = 1;

	public static final CustomPacketPayload.Type<TerminalCraftActionPacket> TYPE =
		new CustomPacketPayload.Type<>(Identifier.tryBuild(PortableTerminalMod.MOD_ID, "craft_action"));

	public static final StreamCodec<FriendlyByteBuf, TerminalCraftActionPacket> CODEC = StreamCodec.of(
		(buf, value) -> {
			buf.writeByte(value.action());
			buf.writeByte(value.first());
			buf.writeByte(value.second());
		},
		buf -> new TerminalCraftActionPacket(buf.readByte(), buf.readByte(), buf.readByte())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static TerminalCraftActionPacket clearGrid() {
		return new TerminalCraftActionPacket(ACTION_CLEAR_GRID, 0, 0);
	}

	public static TerminalCraftActionPacket setModes(int craftSource, int clearDestination) {
		return new TerminalCraftActionPacket(ACTION_SET_MODES, craftSource, clearDestination);
	}

	public static void handleServer(ServerPlayer player, TerminalCraftActionPacket packet) {
		if (!(player.containerMenu instanceof PortableTerminalMenu menu)) {
			return;
		}
		if (packet.action() == ACTION_CLEAR_GRID) {
			menu.clearCraftingGrid();
		} else if (packet.action() == ACTION_SET_MODES) {
			menu.setCraftingModes(packet.first(), packet.second());
		}
	}
}
