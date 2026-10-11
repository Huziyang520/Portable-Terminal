package com.portablestorage;

import com.portablestorage.menu.ModMenuTypes;
import com.portablestorage.network.OpenTerminalPacket;
import com.portablestorage.network.TerminalSlotClickPacket;
import com.portablestorage.network.TerminalItemsSyncPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorageMod implements ModInitializer {
	public static final String MOD_ID = "portablestorage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModMenuTypes.register();

		PayloadTypeRegistry.serverboundPlay().register(OpenTerminalPacket.TYPE, OpenTerminalPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TerminalSlotClickPacket.TYPE, TerminalSlotClickPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TerminalItemsSyncPacket.TYPE, TerminalItemsSyncPacket.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPacket.TYPE, OpenTerminalPacket::handle);
		ServerPlayNetworking.registerGlobalReceiver(TerminalSlotClickPacket.TYPE, TerminalSlotClickPacket::handle);
	}

	public static Identifier id(String path) {
		return Identifier.tryBuild(MOD_ID, path);
	}
}
