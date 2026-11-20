package com.portableterminal.fabric;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.ClientSender;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class PortableTerminalFabricClient implements ClientModInitializer {

	@Override
public void onInitializeClient() {
		PortableTerminalClient.initClient();

		ClientSender.install(PortableTerminalFabricClient::sendToServer);

		ClientPlayNetworking.registerGlobalReceiver(TerminalItemsSyncPacket.ID,
			(client, handler, buf, responseSender) -> {
				TerminalItemsSyncPacket packet = TerminalItemsSyncPacket.read(buf);
				client.execute(() -> PortableTerminalClient.handleItemsSync(packet.items()));
			});

		MenuScreens.register(ModMenuTypes.get(), PortableTerminalScreen::new);

		KeyBindingHelper.registerKeyBinding(KeyBindHandler.createMapping(KeyBindHandler.CATEGORY_KEY));

		ClientTickEvents.END_CLIENT_TICK.register(PortableTerminalClient::tick);
	}

	/** 老写法没有 StreamCodec：自己把 payload 写进 buf，再按频道名发出去。 */
	private static void sendToServer(CustomPacketPayload payload) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		payload.write(buf);
		ClientPlayNetworking.send(payload.id(), buf);
	}
}
