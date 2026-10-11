package com.portablestorage.fabric;

import com.portablestorage.PortableStorageClient;
import com.portablestorage.client.gui.PortableStorageScreen;
import com.portablestorage.client.keybind.KeyBindHandler;
import com.portablestorage.menu.ModMenuTypes;
import com.portablestorage.network.TerminalItemsSyncPacket;
import com.portablestorage.platform.ClientSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

public class PortableStorageFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PortableStorageClient.initClient();

		ClientSender.install(ClientPlayNetworking::send);

		ClientPlayNetworking.registerReceiver(TerminalItemsSyncPacket.TYPE, (payload, context) ->
			context.client().execute(() -> PortableStorageClient.handleItemsSync(payload.items())));

		MenuScreens.register(ModMenuTypes.PORTABLE_STORAGE, PortableStorageScreen::new);

		KeyMapping.Category category = KeyMapping.Category.register(KeyBindHandler.CATEGORY_ID);
		KeyBindHandler.bind(KeyMappingHelper.registerKeyMapping(KeyBindHandler.createMapping(category)));

		ClientTickEvents.END_CLIENT_TICK.register(PortableStorageClient::tick);
	}
}
