package com.portableterminal.fabric;

import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 1.20.2~1.20.4 的 Fabric 网络还是「频道名 + FriendlyByteBuf」的老写法：没有
 * {@code PayloadTypeRegistry}，注册与解码都在全局接收器里手工完成。
 */
public class PortableTerminalFabric implements ModInitializer {

	@Override
public void onInitialize() {
		ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPacket.ID,
			(server, player, handler, buf, responseSender) -> {
				OpenTerminalPacket packet = OpenTerminalPacket.read(buf);
				server.execute(() -> OpenTerminalPacket.handleServer(player, packet.rows()));
			});
		ServerPlayNetworking.registerGlobalReceiver(TerminalSlotClickPacket.ID,
			(server, player, handler, buf, responseSender) -> {
				TerminalSlotClickPacket packet = TerminalSlotClickPacket.read(buf);
				server.execute(() -> TerminalSlotClickPacket.handleServer(player, packet));
			});

		// Fabric accepts registry entries during mod initialisation.
		ModMenuTypes.bind(Registry.register(BuiltInRegistries.MENU, ModMenuTypes.ID, ModMenuTypes.create()));
	}
}
