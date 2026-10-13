package com.portableterminal;

import com.portableterminal.client.keybind.KeyBindHandler;
import com.portableterminal.client.network.ServerTagChecker;
import com.portableterminal.config.ModConfig;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.platform.ClientSender;
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
public class PortableTerminalClient {
	public static final Logger LOGGER = LoggerFactory.getLogger(PortableTerminalMod.MOD_ID);

	private static Consumer<List<net.minecraft.world.item.ItemStack>> syncReceiver;

	/** Called by each loader's client bootstrap, after the loader is ready. */
	public static void initClient() {
		ModConfig.load();
		LOGGER.info("Portable Terminal client initialised");
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
			// 1.21.x 的 Player 只有 displayClientMessage（sendSystemMessage 是服务端 Player 的方法）。
			minecraft.player.displayClientMessage(
				net.minecraft.network.chat.Component.translatable("msg.portableterminal.server_disabled"), false
			);
			return;
		}

		// 行数随包发出去：服务端据此建菜单，两端槽位布局才一致。
		ClientSender.sendToServer(new OpenTerminalPacket(ModConfig.terminalRows));
	}
}
