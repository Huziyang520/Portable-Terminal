package com.portablestorage.platform;

import java.util.function.Consumer;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server delivery hook.
 *
 * <p>Clientbound sending cannot live on {@link IPlatformHelper}: that class is loaded on the
 * dedicated server too, and the Fabric / NeoForge client APIs are not. Each loader installs its
 * own sender from a client-only bootstrap class, so no shared class ever hard-references a
 * client-only type.
 */
public final class ClientSender {

	private static Consumer<CustomPacketPayload> sender = payload -> {
	};

	private ClientSender() {
	}

	/** Called once by the loader's client bootstrap, before any packet can be sent. */
	public static void install(Consumer<CustomPacketPayload> impl) {
		sender = impl;
	}

	public static void sendToServer(CustomPacketPayload payload) {
		sender.accept(payload);
	}
}
