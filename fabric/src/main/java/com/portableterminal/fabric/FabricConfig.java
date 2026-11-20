package com.portableterminal.fabric;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.config.spec.PortableTerminalSpec;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.neoforged.fml.config.ModConfig;

/**
 * Fabric 侧配置接线：config 数据交给 Forge Config API Port，界面也用它自带的那一屏。
 *
 * <p>只登记、不当场载入；载入发生在 FCAP 自己读文件时，所以必须挂 loading/reloading 事件把值
 * 刷进运行期快照，否则整个会话都在跑默认值。</p>
 */
public final class FabricConfig {

	private static boolean installed;

	private FabricConfig() {
	}

	public static void install() {
		if (installed) {
			return;
		}
		installed = true;
		ConfigRegistry.INSTANCE.register(PortableTerminalMod.MOD_ID, ModConfig.Type.CLIENT,
			PortableTerminalSpec.SPEC);
		ModConfigEvents.loading(PortableTerminalMod.MOD_ID).register(config -> PortableTerminalSpec.applyAll());
		ModConfigEvents.reloading(PortableTerminalMod.MOD_ID).register(config -> PortableTerminalSpec.applyAll());
		PortableTerminalSpec.applyAll();
	}
}
