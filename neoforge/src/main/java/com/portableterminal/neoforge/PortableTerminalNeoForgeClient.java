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
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

@Mod(value = PortableTerminalMod.MOD_ID, dist = Dist.CLIENT)
public class PortableTerminalNeoForgeClient {

	public PortableTerminalNeoForgeClient(IEventBus modBus, ModContainer container) {
		PortableTerminalClient.initClient();

		// 1.20.x 的客户端发包是 PacketDistributor.SERVER.noArg().send(...)。
		ClientSender.install(payload -> PacketDistributor.SERVER.noArg().send(payload));

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

	private void registerPayloads(RegisterPayloadHandlerEvent event) {
		event.registrar(PortableTerminalMod.MOD_ID).play(TerminalItemsSyncPacket.ID, TerminalItemsSyncPacket::read,
			(payload, context) -> context.enqueueWork(() ->
				PortableTerminalClient.handleItemsSync(payload.items())));
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		// 本档没有 KeyMapping.Category 对象，分类就是一个翻译键字符串。
		event.register(KeyBindHandler.createMapping(KeyBindHandler.CATEGORY_KEY));
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
