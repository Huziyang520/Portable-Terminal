package com.portableterminal.fabric;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.ClientSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

public class PortableTerminalFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PortableTerminalClient.initClient();

		ClientSender.install(ClientPlayNetworking::send);

		ClientPlayNetworking.registerReceiver(TerminalItemsSyncPacket.TYPE, (payload, context) ->
			context.client().execute(() -> PortableTerminalClient.handleItemsSync(payload.items())));

		MenuScreens.register(ModMenuTypes.get(), PortableTerminalScreen::new);

		KeyMapping.Category category = KeyMapping.Category.register(KeyBindHandler.CATEGORY_ID);
		KeyBindHandler.bind(KeyMappingHelper.registerKeyMapping(KeyBindHandler.createMapping(category)));

		ClientTickEvents.END_CLIENT_TICK.register(PortableTerminalClient::tick);
	}
}
