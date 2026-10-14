package com.portableterminal.fabric;

import com.portableterminal.config.ModConfigScreen;
import com.portableterminal.platform.Services;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Fabric side config button, provided through Mod Menu.
 *
 * <p>Lives in the Fabric module because {@code ModMenuApi} is a Fabric only API. The class is only
 * loaded when Mod Menu itself is present (it is declared in the {@code modmenu} entry point), and
 * the Cloth Config screen is only touched when Cloth Config is loaded — the indy lambda is not
 * resolved before the branch runs, so a missing Cloth Config cannot break class loading here.</p>
 */
public class PortableTerminalModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Cloth Config 未安装时返回 null：Mod Menu 按「工厂是否非 null」决定是否显示配置按钮。
		if (!clothConfigLoaded()) {
			return null;
		}
		return ModConfigScreen::create;
	}

	private static boolean clothConfigLoaded() {
		return Services.PLATFORM.isModLoaded("cloth-config")
			|| Services.PLATFORM.isModLoaded("cloth_config");
	}
}
