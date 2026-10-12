package com.portableterminal.neoforge;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.config.ModConfigScreen;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.ClientSender;
import com.portableterminal.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(value = PortableTerminalMod.MOD_ID, dist = Dist.CLIENT)
public class PortableTerminalNeoForgeClient {

	public PortableTerminalNeoForgeClient(IEventBus modBus, ModContainer container) {
		PortableTerminalClient.initClient();

		ClientSender.install(ClientPacketDistributor::sendToServer);

		// Optional: the config button only exists when Cloth Config is installed.
		if (clothConfigLoaded()) {
			container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, parent) -> ModConfigScreen.create(parent));
		}

		modBus.addListener(this::registerPayloads);
		modBus.addListener(this::registerKeyMappings);
		modBus.addListener(this::registerMenuScreens);

		NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").playToClient(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				PortableTerminalClient.handleItemsSync(payload.items())));
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		KeyMapping.Category category = new KeyMapping.Category(KeyBindHandler.CATEGORY_ID);
		event.registerCategory(category);
		event.register(KeyBindHandler.createMapping(category));
	}

	private void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.get(), PortableTerminalScreen::new);
	}

	private void onClientTick(ClientTickEvent.Post event) {
		PortableTerminalClient.tick(Minecraft.getInstance());
	}

	private static boolean clothConfigLoaded() {
		return Services.PLATFORM.isModLoaded("cloth-config")
			|| Services.PLATFORM.isModLoaded("cloth_config");
	}
}
