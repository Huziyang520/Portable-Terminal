package com.portableterminal.fabric;

import com.portableterminal.config.ModConfigScreen;
import com.portableterminal.platform.Services;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.util.NullScreenFactory;
import net.minecraft.client.gui.screens.Screen;

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
		// Cloth Config 未安装时返回 Mod Menu 的哨兵工厂：它按「工厂是否非 null」决定是否显示
		// 配置按钮，返回 NullScreenFactory 即可隐藏按钮，又不会触发它对 null 值的写入异常。
		if (!clothConfigLoaded()) {
			return new NullScreenFactory<Screen>();
		}
		return ModConfigScreen::create;
	}

	private static boolean clothConfigLoaded() {
		return Services.PLATFORM.isModLoaded("cloth-config")
			|| Services.PLATFORM.isModLoaded("cloth_config");
	}
}
