package com.portableterminal.client.keybind;

import com.portableterminal.PortableTerminalClient;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.config.ModConfig;
import com.portableterminal.client.network.ServerTagChecker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * The key mapping itself is loader agnostic (the vanilla constructor already publishes the
 * mapping into the global table). The category is a plain translation key on this line -- the
 * {@code KeyMapping.Category} object only exists from 26.x on -- so each loader only has to
 * register the returned instance with its own API.
 */
public class KeyBindHandler {

	public static final ResourceLocation CATEGORY_ID = ResourceLocation.fromNamespaceAndPath(PortableTerminalMod.MOD_ID, "custom_category");
	/** Vanilla looks this string up directly, so it is the full translation key already present in the lang files. */
	public static final String CATEGORY_KEY = "key.category.portableterminal.custom_category";
	private static final String MAPPING_TRANSLATION_KEY = "key.portableterminal.open_terminal";

	private static KeyMapping openTerminal;
	private static int cooldownTicks = 0;
	private static final int COOLDOWN = 5;

	private KeyBindHandler() {
	}

	/**
	 * Builds the mapping and publishes it to this handler. The calling loader only has to register
	 * the returned instance with its own API -- forgetting to "bind" is not possible this way.
	 */
	public static KeyMapping createMapping(String category) {
		KeyMapping mapping = new KeyMapping(
			MAPPING_TRANSLATION_KEY,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_GRAVE_ACCENT,
			category
		);
		openTerminal = mapping;
		return mapping;
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
