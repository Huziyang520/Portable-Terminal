package com.portablestorage.client.keybind;

import com.portablestorage.PortableStorageClient;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.ServerTagChecker;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class KeyBindHandler {
	public static KeyMapping OPEN_TERMINAL;
	private static int cooldownTicks = 0;
	private static final int COOLDOWN = 5;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.tryBuild("portablestorage", "custom_category")
	);

	public static void register() {
		OPEN_TERMINAL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.portablestorage.open_terminal",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_GRAVE_ACCENT,
			CATEGORY
		));
	}

	public static void tick(Minecraft minecraft) {
		if (cooldownTicks > 0) {
			cooldownTicks--;
		}

		if (!ModConfig.masterSwitch || ServerTagChecker.isModDisabled()) {
			return;
		}

		if (cooldownTicks <= 0 && OPEN_TERMINAL.consumeClick()) {
			cooldownTicks = COOLDOWN;
			PortableStorageClient.openTerminal();
		}
	}
}
