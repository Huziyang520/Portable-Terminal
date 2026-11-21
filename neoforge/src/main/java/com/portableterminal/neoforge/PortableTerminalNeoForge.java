package com.portableterminal.neoforge;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.config.spec.PortableTerminalSpec;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Loader entry point loaded on both physical sides. Client only code lives in
 * {@link PortableTerminalNeoForgeClient}, which NeoForge only constructs on the client.
 */
@Mod(PortableTerminalMod.MOD_ID)
public class PortableTerminalNeoForge {

	public PortableTerminalNeoForge(IEventBus modBus, ModContainer container) {
		// 设置项交给 NeoForge 自己的配置系统：模组列表的「配置」按钮与界面都由它提供。
		container.registerConfig(ModConfig.Type.CLIENT, PortableTerminalSpec.SPEC, "portableterminal-client.toml");
		// 模组列表的「配置」按钮只在容器登记了工厂时才点亮（21.1 起不再自动接手）。
		if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
			container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		}
		modBus.addListener((ModConfigEvent.Loading event) -> PortableTerminalSpec.applyAll());
		modBus.addListener((ModConfigEvent.Reloading event) -> PortableTerminalSpec.applyAll());

		modBus.addListener(this::registerPayloads);
		// NeoForge keeps the vanilla registries frozen until RegisterEvent fires, so the menu type
		// must not be registered from the mod constructor.
		modBus.addListener(this::registerMenuTypes);
	}

	private void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");

		// Server side handling is shared with Fabric, so it lives in the common packet records.
		registrar.playToServer(OpenTerminalPacket.TYPE, OpenTerminalPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				OpenTerminalPacket.handleServer((ServerPlayer) context.player(), payload.rows())));
		registrar.playToServer(TerminalSlotClickPacket.TYPE, TerminalSlotClickPacket.CODEC,
			(payload, context) -> context.enqueueWork(() ->
				TerminalSlotClickPacket.handleServer((ServerPlayer) context.player(), payload)));
	}

	private void registerMenuTypes(RegisterEvent event) {
		event.register(Registries.MENU, helper -> {
			MenuType<PortableTerminalMenu> type = ModMenuTypes.create();
			helper.register(ModMenuTypes.ID, type);
			ModMenuTypes.bind(type);
		});
	}
}
