package com.portableterminal.neoforge;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Loader entry point loaded on both physical sides. Client only code lives in
 * {@link PortableTerminalNeoForgeClient}, which NeoForge only constructs on the client.
 */
@Mod(PortableTerminalMod.MOD_ID)
public class PortableTerminalNeoForge {

	public PortableTerminalNeoForge(IEventBus modBus) {
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
				OpenTerminalPacket.handleServer((ServerPlayer) context.player())));
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
