package com.portablestorage;

import com.portablestorage.client.gui.PortableStorageScreen;
import com.portablestorage.client.keybind.KeyBindHandler;
import com.portablestorage.config.ModConfig;
import com.portablestorage.menu.ModMenuTypes;
import com.portablestorage.network.OpenTerminalPacket;
import com.portablestorage.network.ServerTagChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorageClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(PortableStorageMod.MOD_ID);

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		MenuScreens.register(ModMenuTypes.PORTABLE_STORAGE, PortableStorageScreen::new);
		KeyBindHandler.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeyBindHandler.tick(client);
			ServerTagChecker.tick(client);
		});
	}

	public static void openTerminal() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		if (ServerTagChecker.isModDisabled()) {
			minecraft.player.sendSystemMessage(
				net.minecraft.network.chat.Component.translatable("msg.portablestorage.server_disabled")
			);
			return;
		}

		ClientPlayNetworking.send(new OpenTerminalPacket());
	}
}
