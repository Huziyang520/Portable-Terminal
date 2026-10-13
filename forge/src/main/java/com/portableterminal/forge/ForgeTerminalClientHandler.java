package com.portableterminal.forge;

import java.util.List;

import com.portableterminal.PortableTerminalClient;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端收包处理单独放一层，避免 {@code ForgeTerminalNetwork}（两端都会加载）直接引用客户端类
 * ——专用服务端没有 {@code PortableTerminalClient} 的运行时依赖。
 */
final class ForgeTerminalClientHandler {

	private ForgeTerminalClientHandler() {
	}

	static void handleItemsSync(List<ItemStack> items) {
		PortableTerminalClient.handleItemsSync(items);
	}
}
