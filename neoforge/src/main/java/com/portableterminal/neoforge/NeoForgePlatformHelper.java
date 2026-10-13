package com.portableterminal.neoforge;

import java.nio.file.Path;

import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.platform.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;



public class NeoForgePlatformHelper implements IPlatformHelper {

	@Override
	public String getPlatformName() {
		return "NeoForge";
	}

	@Override
	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLEnvironment.isProduction();
	}

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	@Override
	public ItemAccess findItemAccess(Level level, BlockPos pos) {
		// 26.2 起方块物品能力是 Capabilities.Item.BLOCK，处理类型为 ResourceHandler<ItemResource>。
		// 上下文类型是 Direction，传 null 表示不限定侧面（方块自己注册的默认面）。
		ResourceHandler<ItemResource> handler =
			level.getCapability(Capabilities.Item.BLOCK, pos, (Direction) null);
		return handler == null ? null : new NeoForgeResourceHandlerAccess(handler);
	}
}