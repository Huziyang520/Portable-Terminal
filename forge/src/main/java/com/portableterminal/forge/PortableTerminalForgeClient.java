package com.portableterminal.forge;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.platform.ClientSender;
import com.huziyang.neoconfigurationscreenport.api.ListElements;
import com.huziyang.neoconfigurationscreenport.forge.ConfigScreenRegistration;
import com.portableterminal.config.ConfigSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
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

			// 配置界面由前置 Neo Configuration Screen Port 提供，这一行同时点亮模组列表的「配置」按钮。
			ConfigScreenRegistration.register(PortableTerminalMod.MOD_ID);
			// Forge 的 spec 没有「新元素工厂」，列表页的新增按钮由 NCP 这个注册点提供。
			ListElements.register(PortableTerminalMod.MOD_ID,
				ConfigSchema.SECTION_GENERAL + "." + ConfigSchema.KEY_BLACKLIST, () -> "minecraft:");
		});
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		// 本档没有 KeyMapping.Category 对象，分类就是一个翻译键字符串。
		event.register(KeyBindHandler.createMapping(KeyBindHandler.CATEGORY_KEY));
	}

	@Mod.EventBusSubscriber(modid = PortableTerminalMod.MOD_ID, value = Dist.CLIENT)
	public static class ClientEvents {

		/** 上一次 tick 是否停在配置界面上（用来判断「刚关掉配置界面」）。 */
		private static boolean inConfigScreen;

		@SubscribeEvent
		public static void onClientTick(TickEvent.ClientTickEvent event) {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			PortableTerminalClient.tick(mc);

			// 配置界面用的前置只把值写盘，不发 ModConfigEvent，快照得自己刷一次。
			boolean now = mc.screen != null
				&& mc.screen.getClass().getName().startsWith("net.neoforged.neoforge.client.gui.ConfigurationScreen");
			if (inConfigScreen && !now) {
				ForgeConfig.applyAll();
			}
			inConfigScreen = now;
		}
	}
}
