package com.portableterminal.neoforge;

import java.nio.file.Path;

import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.platform.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePlatformHelper implements IPlatformHelper {

	public String getPlatformName() {
		return "NeoForge";
	}

	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	public boolean isDevelopmentEnvironment() {
		// 1.20.2~1.21.11 的 FancyModLoader 上是字段，不是方法（方法形态是 26.x 才有的）。
		return !FMLEnvironment.production;
	}

	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		// 20.4 的发送入口还是 PacketDistributor.PLAYER.with(player).send(...)，
		// 静态的 sendToPlayer(...) 是 20.5 之后才加的。
		PacketDistributor.PLAYER.with(player).send(payload);
	}

	public ItemAccess findItemAccess(Level level, BlockPos pos) {
		IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, (Direction) null);
		return handler == null ? null : new NeoForgeItemHandlerAccess(handler);
	}
}
