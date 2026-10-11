package com.portablestorage;

import com.portablestorage.client.keybind.KeyBindHandler;
import com.portablestorage.client.network.ServerTagChecker;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.OpenTerminalPacket;
import com.portablestorage.platform.ClientSender;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Client side glue shared by both loaders.
 *
 * <p>Screens register themselves as the receiver of the terminal item sync packet through
 * {@link #setSyncReceiver(Consumer)}; when no screen is open the packet is dropped, which keeps
 * the original "register on open, unregister on close" behaviour without loader specific APIs.
 */
public class PortableStorageClient {
	public static final Logger LOGGER = LoggerFactory.getLogger(PortableStorageMod.MOD_ID);

	private static Consumer<List<net.minecraft.world.item.ItemStack>> syncReceiver;

	/** Called by each loader's client bootstrap, after the loader is ready. */
	public static void initClient() {
		ModConfig.load();
		LOGGER.info("Portable Storage client initialised");
	}

	public static void tick(Minecraft minecraft) {
		KeyBindHandler.tick(minecraft);
		ServerTagChecker.tick(minecraft);
	}

	public static void setSyncReceiver(Consumer<List<net.minecraft.world.item.ItemStack>> receiver) {
		syncReceiver = receiver;
	}

	public static void handleItemsSync(List<net.minecraft.world.item.ItemStack> items) {
		Consumer<List<net.minecraft.world.item.ItemStack>> receiver = syncReceiver;
		if (receiver != null) {
			receiver.accept(items);
		}
	}

	public static void openTerminal() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		if (ServerTagChecker.isModDisabled()) {
			minecraft.player.sendSystemMessage(
				net.minecraft.network.chat.Component.translatable("msg.portablestorage.server_disabled")
			);
			return;
		}

		ClientSender.sendToServer(new OpenTerminalPacket());
	}
}
