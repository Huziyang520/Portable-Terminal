package com.portableterminal.client.network;

import com.portableterminal.PortableTerminalMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 服务器标签校验层
 * 每2tick读取服务器数据包标签，实现远程管控
 * 标签规则基于Block Tag（便于服务器通过数据包下发）
 */
public class ServerTagChecker {
	private static int tickCounter = 0;
	private static final int CHECK_INTERVAL = 2;

	private static final TagKey<Block> TAG_DISABLE = TagKey.create(Registries.BLOCK, PortableTerminalMod.id("portable_terminal_disable"));
	private static final TagKey<Block> TAG_LIMIT_RANGE = TagKey.create(Registries.BLOCK, PortableTerminalMod.id("portable_terminal_limit_range"));
	private static final TagKey<Block> TAG_DISABLE_DEPOSIT = TagKey.create(Registries.BLOCK, PortableTerminalMod.id("portable_terminal_disable_deposit"));
	private static final TagKey<Block> TAG_DISABLE_EXTRACT = TagKey.create(Registries.BLOCK, PortableTerminalMod.id("portable_terminal_disable_extract"));
	private static final TagKey<Block> TAG_ONLY_SINGLEPLAYER = TagKey.create(Registries.BLOCK, PortableTerminalMod.id("portable_terminal_only_singleplayer"));

	private static boolean modDisabled = false;
	private static boolean serverLimitRange = false;
	private static boolean disableDeposit = false;
	private static boolean disableExtract = false;
	private static boolean onlySingleplayer = false;
	private static int serverMaxRange = -1;

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		tickCounter++;
		if (tickCounter % CHECK_INTERVAL != 0) return;
		tickCounter = 0;

		try {
			var registry = mc.level.registryAccess().lookupOrThrow(Registries.BLOCK);
			modDisabled = registry.get(TAG_DISABLE).filter(h -> h.size() > 0).isPresent();
			serverLimitRange = registry.get(TAG_LIMIT_RANGE).filter(h -> h.size() > 0).isPresent();
			disableDeposit = registry.get(TAG_DISABLE_DEPOSIT).filter(h -> h.size() > 0).isPresent();
			disableExtract = registry.get(TAG_DISABLE_EXTRACT).filter(h -> h.size() > 0).isPresent();
			onlySingleplayer = registry.get(TAG_ONLY_SINGLEPLAYER).filter(h -> h.size() > 0).isPresent();
		} catch (Exception e) {
			PortableTerminalMod.LOGGER.debug("Server tag check failed: {}", e.getMessage());
		}

		if (onlySingleplayer && isMultiplayer(mc)) {
			modDisabled = true;
		}
	}

	public static boolean isModDisabled() {
		return modDisabled;
	}

	public static boolean isDepositDisabled() {
		return modDisabled || disableDeposit;
	}

	public static boolean isExtractDisabled() {
		return modDisabled || disableExtract;
	}

	public static int getEffectiveMaxRange(int clientRange) {
		if (modDisabled) return 0;
		if (serverLimitRange && serverMaxRange >= 0) {
			return Math.min(clientRange, serverMaxRange);
		}
		return clientRange;
	}

	public static void reset() {
		modDisabled = false;
		serverLimitRange = false;
		disableDeposit = false;
		disableExtract = false;
		onlySingleplayer = false;
		serverMaxRange = -1;
	}

	private static boolean isMultiplayer(Minecraft mc) {
		return mc.getSingleplayerServer() == null && mc.getCurrentServer() != null;
	}
}
