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
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

/**
 * 客户端专用引导。20.4 没有 {@code @Mod(dist = ...)} 也没有 {@code @EventBusSubscriber}，
 * 所以由主类在确认是客户端后再调用这里的 {@link #register(IEventBus)}：专用服务端永远不会
 * 触碰这个类，客户端 API 也就不会被解析。
 */
public final class PortableTerminalNeoForgeClient {

	private PortableTerminalNeoForgeClient() {
	}

	static void register(IEventBus modBus) {
		PortableTerminalClient.initClient();

		// 1.20.x 的客户端发包是 PacketDistributor.SERVER.noArg().send(...)。
		ClientSender.install(payload -> PacketDistributor.SERVER.noArg().send(payload));

		modBus.addListener(PortableTerminalNeoForgeClient::onClientSetup);
		modBus.addListener(PortableTerminalNeoForgeClient::onRegisterPayloads);
		modBus.addListener(PortableTerminalNeoForgeClient::onRegisterKeyMappings);
		modBus.addListener(PortableTerminalNeoForgeClient::onRegisterMenuScreens);
		NeoForge.EVENT_BUS.addListener(PortableTerminalNeoForgeClient::onClientTick);
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(ModMenuTypes.get(), PortableTerminalScreen::new);

			// Optional: the config button only exists when Cloth Config is installed.
			// Cloth Config 在 NeoForge / Forge 端的 modId 是 cloth_config（下划线），Fabric 端才是 cloth-config。
			if (Services.PLATFORM.isModLoaded("cloth-config")
				|| Services.PLATFORM.isModLoaded("cloth_config")) {
				ModLoadingContext.get().registerExtensionPoint(
					ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
						(mc, parent) -> ModConfigScreen.create(parent)));
			}
		});
	}

	private static void onRegisterPayloads(RegisterPayloadHandlerEvent event) {
		event.registrar(PortableTerminalMod.MOD_ID).play(TerminalItemsSyncPacket.ID, TerminalItemsSyncPacket::read,
			(payload, context) -> context.workHandler().execute(() ->
				PortableTerminalClient.handleItemsSync(payload.items())));
	}

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		// 本档没有 KeyMapping.Category 对象，分类就是一个翻译键字符串。
		event.register(KeyBindHandler.createMapping(KeyBindHandler.CATEGORY_KEY));
	}

	private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.get(), PortableTerminalScreen::new);
	}

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			PortableTerminalClient.tick(Minecraft.getInstance());
		}
	}
}
