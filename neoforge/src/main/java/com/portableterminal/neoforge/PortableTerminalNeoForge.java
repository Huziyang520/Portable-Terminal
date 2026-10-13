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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Loader entry point loaded on both physical sides. Client only code lives in
 * {@link PortableTerminalNeoForgeClient}, which NeoForge only constructs on the client.
 *
 * <p>1.20.2~1.20.4 的 NeoForge 用的是「registrar + FriendlyByteBuf.Reader」的老注册入口
 * （{@code RegisterPayloadHandlerEvent}，单数 Handler；{@code playToServer/playToClient}
 * 是 1.20.5 才有的）。</p>
 */
@Mod(PortableTerminalMod.MOD_ID)
public class PortableTerminalNeoForge {

	public PortableTerminalNeoForge(IEventBus modBus) {
		modBus.addListener(this::registerPayloads);
		// NeoForge keeps the vanilla registries frozen until RegisterEvent fires, so the menu type
		// must not be registered from the mod constructor.
		modBus.addListener(this::registerMenuTypes);
	}

	private void registerPayloads(RegisterPayloadHandlerEvent event) {
		IPayloadRegistrar registrar = event.registrar(PortableTerminalMod.MOD_ID);

		// Server side handling is shared with Fabric, so it lives in the common packet records.
		registrar.play(OpenTerminalPacket.ID, OpenTerminalPacket::read,
			(payload, context) -> context.enqueueWork(() ->
				OpenTerminalPacket.handleServer(context.player().map(p -> (ServerPlayer) p).orElse(null), payload.rows())));
		registrar.play(TerminalSlotClickPacket.ID, TerminalSlotClickPacket::read,
			(payload, context) -> context.enqueueWork(() ->
				TerminalSlotClickPacket.handleServer(context.player().map(p -> (ServerPlayer) p).orElse(null), payload)));
	}

	private void registerMenuTypes(RegisterEvent event) {
		event.register(Registries.MENU, helper -> {
			MenuType<PortableTerminalMenu> type = ModMenuTypes.create();
			helper.register(ModMenuTypes.ID, type);
			ModMenuTypes.bind(type);
		});
	}
}
