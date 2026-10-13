package com.portableterminal.forge;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.config.ModConfigScreen;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.platform.ClientSender;
import com.portableterminal.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = PortableTerminalMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PortableTerminalForgeClient {

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			PortableTerminalClient.initClient();

			// 1.20.x 的客户端发包入口是 SimpleChannel#sendToServer。
			ClientSender.install(ForgeTerminalNetwork::sendToServer);

			MenuScreens.register(ModMenuTypes.get(), PortableTerminalScreen::new);

			// Optional: the config button only exists when Cloth Config is installed.
			if (Services.PLATFORM.isModLoaded("cloth-config")) {
				ModLoadingContext.get().registerExtensionPoint(
					ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
						(mc, parent) -> ModConfigScreen.create(parent)));
			}
		});
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		// 本档没有 KeyMapping.Category 对象，分类就是一个翻译键字符串。
		event.register(KeyBindHandler.createMapping(KeyBindHandler.CATEGORY_KEY));
	}

	@Mod.EventBusSubscriber(modid = PortableTerminalMod.MOD_ID, value = Dist.CLIENT)
	public static class ClientEvents {

		@SubscribeEvent
		public static void onClientTick(TickEvent.ClientTickEvent event) {
			if (event.phase == TickEvent.Phase.END) {
				PortableTerminalClient.tick(Minecraft.getInstance());
			}
		}
	}
}
