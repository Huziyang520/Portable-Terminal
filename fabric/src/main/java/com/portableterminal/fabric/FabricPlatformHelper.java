package com.portableterminal.fabric;

import java.nio.file.Path;

import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.platform.IPlatformHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class FabricPlatformHelper implements IPlatformHelper {

	@Override
	public String getPlatformName() {
		return "Fabric";
	}

	@Override
	public Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public String getModDisplayName(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(container -> container.getMetadata().getName())
			.orElse(null);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	@Override
	public boolean openConfigScreen(Object parent) {
		if (!(parent instanceof Screen screen)) {
			return false;
		}
		// 界面由 Forge Config API Port 提供（必选前置），与模组列表里的「配置」按钮同一套。
		Minecraft.getInstance().setScreenAndShow(
			new ConfigurationScreen(com.portableterminal.PortableTerminalMod.MOD_ID, screen));
		return true;
	}

	@Override
	public ItemAccess findItemAccess(Level level, BlockPos pos) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
		return storage == null ? null : new FabricStorageAccess(storage);
	}
}
