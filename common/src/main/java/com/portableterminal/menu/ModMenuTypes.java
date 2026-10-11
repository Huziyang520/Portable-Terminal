package com.portableterminal.menu;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import java.util.List;

/**
 * Holds the mod's menu type.
 *
 * <p>The type is created on demand rather than in a static initializer: NeoForge keeps the vanilla
 * registries frozen until its {@code RegisterEvent} fires, so registering eagerly (from a mod
 * constructor) throws {@code IllegalStateException} — which surfaces as a bare
 * {@code ExceptionInInitializerError} and stops the whole mod from loading. Each loader therefore
 * calls {@link #create()} and registers the result from its own registration phase.</p>
 */
public final class ModMenuTypes {

	public static final Identifier ID = Identifier.tryBuild(PortableTerminalMod.MOD_ID, "portable_terminal");

	private static MenuType<PortableTerminalMenu> portableTerminal;

	private ModMenuTypes() {
	}

	/** Builds the menu type. The caller is responsible for registering it. */
	public static MenuType<PortableTerminalMenu> create() {
		return new MenuType<>((containerId, inventory) -> {
			List<BlockPos> positions = PortableTerminalMenu.takePendingPositions(inventory.player.getUUID());
			return new PortableTerminalMenu(containerId, inventory, positions);
		}, FeatureFlags.VANILLA_SET);
	}

	/** Called by the loader once the target registry accepts new entries. */
	public static void bind(MenuType<PortableTerminalMenu> type) {
		portableTerminal = type;
	}

	public static MenuType<PortableTerminalMenu> get() {
		if (portableTerminal == null) {
			throw new IllegalStateException("Portable Terminal menu type is not registered yet");
		}
		return portableTerminal;
	}
}
