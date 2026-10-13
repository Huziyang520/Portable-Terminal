package com.portableterminal.fabric;

import java.nio.file.Path;

import com.portableterminal.inventory.ItemAccess;
import com.portableterminal.platform.IPlatformHelper;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.portableterminal.platform.TerminalPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class FabricPlatformHelper implements IPlatformHelper {

public String getPlatformName() {
		return "Fabric";
	}

public Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

public void sendToPlayer(ServerPlayer player, TerminalPayload payload) {
		// 本档按「频道名 + FriendlyByteBuf」发送，没有 StreamCodec 版的 send。
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		payload.write(buf);
		ServerPlayNetworking.send(player, payload.id(), buf);
	}

public ItemAccess findItemAccess(Level level, BlockPos pos) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
		return storage == null ? null : new FabricStorageAccess(storage);
	}
}
