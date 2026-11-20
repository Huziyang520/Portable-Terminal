package com.portableterminal.fabric;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.ClientSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

public class PortableTerminalFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PortableTerminalClient.initClient();

		ClientSender.install(ClientPlayNetworking::send);

		// Global (not per-screen) receivers are the ones that may be registered at client init.
		ClientPlayNetworking.registerGlobalReceiver(TerminalItemsSyncPacket.TYPE, (payload, context) ->
			context.client().execute(() -> PortableTerminalClient.handleItemsSync(payload.items())));

		MenuScreens.register(ModMenuTypes.get(), PortableTerminalScreen::new);

		KeyMapping.Category category = KeyMapping.Category.register(KeyBindHandler.CATEGORY_ID);
		KeyBindingHelper.registerKeyBinding(KeyBindHandler.createMapping(category));

		ClientTickEvents.END_CLIENT_TICK.register(PortableTerminalClient::tick);
	}
}
