package com.portablestorage.neoforge;

import com.portablestorage.PortableStorageMod;
import com.portablestorage.network.OpenTerminalPacket;
import com.portablestorage.network.TerminalSlotClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Loader entry point loaded on both physical sides. Client only code lives in
 * {@link PortableStorageNeoForgeClient}, which NeoForge only constructs on the client.
 */
@Mod(PortableStorageMod.MOD_ID)
public class PortableStorageNeoForge {

	public PortableStorageNeoForge(IEventBus modBus) {
		modBus.addListener(this::registerPayloads);
		PortableStorageMod.init();
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
}
