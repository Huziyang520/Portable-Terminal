package com.portableterminal.fabric;

import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalItemsSyncPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class PortableTerminalFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		// Payload types have to be known on both logical sides.
		PayloadTypeRegistry.playC2S().register(OpenTerminalPacket.TYPE, OpenTerminalPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(TerminalSlotClickPacket.TYPE, TerminalSlotClickPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC);

		// Server side handling is shared with NeoForge, so it lives in the common packet records.
		ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPacket.TYPE,
			(payload, context) -> OpenTerminalPacket.handleServer(context.player(), payload.rows()));
		ServerPlayNetworking.registerGlobalReceiver(TerminalSlotClickPacket.TYPE,
			(payload, context) -> TerminalSlotClickPacket.handleServer(context.player(), payload));

		// Fabric accepts registry entries during mod initialisation.
		ModMenuTypes.bind(Registry.register(BuiltInRegistries.MENU, ModMenuTypes.ID, ModMenuTypes.create()));
	}
}
