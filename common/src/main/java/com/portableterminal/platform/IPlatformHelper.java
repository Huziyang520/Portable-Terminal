package com.portableterminal.platform;

import java.nio.file.Path;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/**
 * The only loader-specific surface the shared code depends on.
 * Implementations are discovered through {@link Services}.
 */
public interface IPlatformHelper {

	/** Human readable loader name, used for logging only. */
	String getPlatformName();

	/** Directory where the client config file lives (`config/` on both loaders). */
	Path getConfigDir();

	boolean isModLoaded(String modId);

	boolean isDevelopmentEnvironment();

	/** Server to client delivery. Safe to call from the logical server on both loaders. */
	void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

	/**
	 * 取某个方块位置上的物品栏视图（原版 {@link Container} 形状）。
	 *
	 * <p>只按原版 {@code Container} 检索会漏掉大量模组容器：它们一般只通过各加载器的能力系统
	 * 暴露物品栏，并不实现 {@code Container}。这里由各加载器去查能力并包成 {@code Container}
	 * （Fabric：{@code ItemStorage.SIDED}；NeoForge：{@code Capabilities.ItemHandler.BLOCK}），
	 * 上层就无需区分原版与模组，也不针对任何具体模组。</p>
	 *
	 * @return 该位置的能力包装；该位置没有物品栏则返回 {@code null}
	 */
	Container getItemHandlerContainer(Level level, BlockPos pos);

	default String getEnvironmentName() {
		return isDevelopmentEnvironment() ? "development" : "production";
	}
}
