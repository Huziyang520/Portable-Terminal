package com.portableterminal.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Fabric 侧模组列表的「配置」按钮。
 *
 * <p>界面由 Forge Config API Port 自带，构造器签名是 {@code (modId, parent)}；工厂在打开列表时才
 * 构造界面，客户端初始化阶段不碰任何第三方配置类。</p>
 */
public class PortableTerminalModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new ConfigurationScreen(com.portableterminal.PortableTerminalMod.MOD_ID, parent);
	}
}
