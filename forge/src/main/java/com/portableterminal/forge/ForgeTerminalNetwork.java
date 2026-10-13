package com.portableterminal.forge;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import com.portableterminal.platform.TerminalPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 1.20~1.20.1 的 Forge 网络：{@code SimpleChannel} + 手写 encoder/decoder
 * （{@code CustomPacketPayload}/{@code StreamCodec} 都是 1.20.2 才有的）。
 *
 * <p>三个包全部在同一个频道上注册，序号与解码器一一对应；客户端与服务端都要注册，
 * 否则对面会被 {@code SimpleChannel} 拒绝（协议版本字符串必须两端一致）。</p>
 */
public final class ForgeTerminalNetwork {

	private static final String PROTOCOL = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		ResourceLocation.tryBuild(PortableTerminalMod.MOD_ID, "main"),
		() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private ForgeTerminalNetwork() {
	}

	public static void register() {
		CHANNEL.messageBuilder(OpenTerminalPacket.class, 0)
			.encoder(OpenTerminalPacket::write)
			.decoder(OpenTerminalPacket::read)
			.consumerMainThread((packet, context) ->
				OpenTerminalPacket.handleServer(context.get().getSender(), packet.rows()))
			.add();

		CHANNEL.messageBuilder(TerminalSlotClickPacket.class, 1)
			.encoder(TerminalSlotClickPacket::write)
			.decoder(TerminalSlotClickPacket::read)
			.consumerMainThread((packet, context) ->
				TerminalSlotClickPacket.handleServer(context.get().getSender(), packet))
			.add();

		CHANNEL.messageBuilder(TerminalItemsSyncPacket.class, 2)
			.encoder(TerminalItemsSyncPacket::write)
			.decoder(TerminalItemsSyncPacket::read)
			.consumerMainThread((packet, context) ->
				ForgeTerminalClientHandler.handleItemsSync(packet.items()))
			.add();
	}

	public static void sendToPlayer(ServerPlayer player, TerminalPayload payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}

	public static void sendToServer(TerminalPayload payload) {
		CHANNEL.sendToServer(payload);
	}
}
