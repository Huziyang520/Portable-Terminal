package com.portableterminal.platform;

import java.nio.file.Path;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

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

	default String getEnvironmentName() {
		return isDevelopmentEnvironment() ? "development" : "production";
	}
}
