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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;


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
		// 1.20.2~1.21.11 的 FML 里生产环境标记是 public static 字段 production（isProduction() 是 26.x 才有的方法）
		return !FMLEnvironment.production;
	}

	@Override
public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	@Override
public ItemAccess findItemAccess(Level level, BlockPos pos) {
		// 1.20.2~1.21.11 线：方块物品能力仍是 Capabilities.ItemHandler.BLOCK，处理类型为 IItemHandler
		//（26.x 才迁到 Capabilities.Item.BLOCK + ResourceHandler<ItemResource>）。
		// 上下文类型是 Direction，传 null 表示不限定侧面（方块自己注册的默认面）。
		IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, (Direction) null);
		return handler == null ? null : new NeoForgeItemHandlerAccess(handler);
	}
}
