package com.portablestorage.neoforge;

import com.portablestorage.PortableStorageClient;
import com.portablestorage.PortableStorageMod;
import com.portablestorage.client.gui.PortableStorageScreen;
import com.portablestorage.client.keybind.KeyBindHandler;
import com.portablestorage.menu.ModMenuTypes;
import com.portablestorage.network.TerminalItemsSyncPacket;
import com.portablestorage.platform.ClientSender;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(value = PortableStorageMod.MOD_ID, dist = Dist.CLIENT)
public class PortableStorageNeoForgeClient {

	public PortableStorageNeoForgeClient(IEventBus modBus) {
		PortableStorageClient.initClient();

		ClientSender.install(ClientPacketDistributor::sendToServer);

		modBus.addListener(this::registerPayloads);
		modBus.addListener(this::registerKeyMappings);
		modBus.addListener(this::registerMenuScreens);

		NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").playToClient(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				PortableStorageClient.handleItemsSync(payload.items())));
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		KeyMapping.Category category = new KeyMapping.Category(KeyBindHandler.CATEGORY_ID);
		event.registerCategory(category);
		event.register(KeyBindHandler.createMapping(category));
	}

	private void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.PORTABLE_STORAGE, PortableStorageScreen::new);
	}

	private void onClientTick(ClientTickEvent.Post event) {
		PortableStorageClient.tick(Minecraft.getInstance());
	}
}
