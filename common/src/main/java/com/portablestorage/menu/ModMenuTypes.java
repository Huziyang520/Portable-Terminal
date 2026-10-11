package com.portablestorage.menu;

import com.portablestorage.PortableStorageMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;

import java.util.List;

public class ModMenuTypes {
	public static final MenuType<PortableStorageMenu> PORTABLE_STORAGE = Registry.register(
		BuiltInRegistries.MENU,
		Identifier.tryBuild(PortableStorageMod.MOD_ID, "portable_storage"),
		new MenuType<>((containerId, inventory) -> {
			List<BlockPos> positions = PortableStorageMenu.takePendingPositions(inventory.player.getUUID());
			return new PortableStorageMenu(containerId, inventory, positions);
		}, FeatureFlags.VANILLA_SET)
	);

	public static void register() {
	}
}
