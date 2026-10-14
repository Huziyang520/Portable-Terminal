package com.portableterminal.compat;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.client.gui.PortableTerminalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * JEI 可选适配：把终端界面占用的矩形报给 JEI，让它自己避让，别压在我们的面板上。
 *
 * <p>{@code IGuiContainerHandler#getGuiExtraAreas} 的语义就是「JEI 必须绕开的区域」——JEI 的
 * 物品列表会据此重新排布，不与我们的界面重叠。</p>
 *
 * <p>JEI 是**可选前置**：只在编译期引用（compileOnly，绝不打包），运行期不写进任何加载器的必装
 * 依赖。JEI 不在场时这个类不会被加载：Fabric 侧靠 {@code jei_mod_plugin} 入口点（只有装了 JEI
 * 才会被查询），NeoForge/Forge 侧靠 JEI 自己扫描 {@code @JeiPlugin} 注解。</p>
 */
@JeiPlugin
public class PortableTerminalJeiPlugin implements IModPlugin {

	@Override
	public Identifier getPluginUid() {
		return Identifier.tryBuild(PortableTerminalMod.MOD_ID, "jei");
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(PortableTerminalScreen.class,
			new IGuiContainerHandler<PortableTerminalScreen>() {
				@Override
				public List<Rect2i> getGuiExtraAreas(PortableTerminalScreen screen) {
					return screen.getExclusionAreas();
				}
			});
	}
}
