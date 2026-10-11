package com.portableterminal.client.keybind;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.config.ModConfig;
import com.portableterminal.client.network.ServerTagChecker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * The key mapping itself is loader agnostic (the vanilla constructor already publishes the
 * mapping into the global table), but each loader registers the category through its own API:
 * Fabric through {@code KeyMappingHelper}, NeoForge through {@code RegisterKeyMappingsEvent}.
 */
public class KeyBindHandler {

	public static final Identifier CATEGORY_ID = Identifier.tryBuild(PortableTerminalMod.MOD_ID, "custom_category");
	private static final String MAPPING_TRANSLATION_KEY = "key.portableterminal.open_terminal";

	private static KeyMapping openTerminal;
	private static int cooldownTicks = 0;
	private static final int COOLDOWN = 5;

	private KeyBindHandler() {
	}

	/** Builds the mapping; the calling loader is responsible for registering it. */
	public static KeyMapping createMapping(KeyMapping.Category category) {
		return new KeyMapping(
			MAPPING_TRANSLATION_KEY,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_GRAVE_ACCENT,
			category
		);
	}

	public static void bind(KeyMapping mapping) {
		openTerminal = mapping;
	}

	public static void tick(Minecraft minecraft) {
		if (cooldownTicks > 0) {
			cooldownTicks--;
		}

		if (openTerminal == null) {
			return;
		}

		if (!ModConfig.masterSwitch || ServerTagChecker.isModDisabled()) {
			return;
		}

		if (cooldownTicks <= 0 && openTerminal.consumeClick()) {
			cooldownTicks = COOLDOWN;
			PortableTerminalClient.openTerminal();
		}
	}
}
