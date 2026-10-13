package com.portableterminal.forge;

import java.nio.file.Path;

import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.platform.IPlatformHelper;
import com.portableterminal.platform.TerminalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;

public class ForgePlatformHelper implements IPlatformHelper {

	public String getPlatformName() {
		return "Forge";
	}

	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	public boolean isDevelopmentEnvironment() {
		return !FMLEnvironment.production;
	}

	public void sendToPlayer(ServerPlayer player, TerminalPayload payload) {
		ForgeTerminalNetwork.sendToPlayer(player, payload);
	}

	public ItemAccess findItemAccess(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return null;
		}
		// 传 null 方向表示不限定侧面，取方块自己注册的默认面。
		IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
		return handler == null ? null : new ForgeItemHandlerAccess(handler);
	}
}
