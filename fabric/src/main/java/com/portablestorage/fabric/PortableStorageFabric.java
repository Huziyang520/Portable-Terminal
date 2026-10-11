package com.portablestorage.fabric;

import com.portablestorage.PortableStorageMod;
import com.portablestorage.network.OpenTerminalPacket;
import com.portablestorage.network.TerminalItemsSyncPacket;
import com.portablestorage.network.TerminalSlotClickPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class PortableStorageFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		// Payload types have to be known on both logical sides.
		PayloadTypeRegistry.serverboundPlay().register(OpenTerminalPacket.TYPE, OpenTerminalPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TerminalSlotClickPacket.TYPE, TerminalSlotClickPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC);

		// Server side handling is shared with NeoForge, so it lives in the common packet records.
		ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPacket.TYPE,
			(payload, context) -> OpenTerminalPacket.handleServer(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(TerminalSlotClickPacket.TYPE,
			(payload, context) -> TerminalSlotClickPacket.handleServer(context.player(), payload));

		PortableStorageMod.init();
	}
}
