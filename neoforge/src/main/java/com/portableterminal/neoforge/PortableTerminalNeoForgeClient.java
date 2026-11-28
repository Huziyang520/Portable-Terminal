package com.portableterminal.neoforge;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.TerminalCraftActionPacket;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.platform.ClientSender;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(value = PortableTerminalMod.MOD_ID, dist = Dist.CLIENT)
public class PortableTerminalNeoForgeClient {

	public PortableTerminalNeoForgeClient(IEventBus modBus, ModContainer container) {
		PortableTerminalClient.initClient();

		ClientSender.install(ClientPacketDistributor::sendToServer);

		// 界面里的「配置」按钮：复用 NeoForge 给模组列表提供的配置屏工厂。
		NeoForgePlatformHelper.setConfigScreenFactory(parent -> container
			.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class)
			.map(factory -> factory.createScreen(container, (net.minecraft.client.gui.screens.Screen) parent))
			.orElse(null));

		modBus.addListener(this::registerPayloads);
		modBus.addListener(this::registerKeyMappings);
		modBus.addListener(this::registerMenuScreens);

		NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").playToClient(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				PortableTerminalClient.handleItemsSync(payload.items())));

		event.registrar("1").playToServer(TerminalCraftActionPacket.TYPE, TerminalCraftActionPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				TerminalCraftActionPacket.handleServer((net.minecraft.server.level.ServerPlayer) context.player(), payload)));
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
}
