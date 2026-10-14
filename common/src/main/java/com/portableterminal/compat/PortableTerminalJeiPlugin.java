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
 * JEI ��ѡ���䣺���ն˽���ռ�õľ��α��� JEI�������Լ����ã���ѹ�����ǵ�����ϡ�
 *
 * <p>{@code IGuiContainerHandler#getGuiExtraAreas} ��������ǡ�JEI �����ƿ������򡹡���JEI ��
 * ��Ʒ�б���ݴ������Ų����������ǵĽ����ص���</p>
 *
 * <p>JEI ��**��ѡǰ��**��ֻ�ڱ��������ã�compileOnly������������������ڲ�д���κμ������ı�װ
 * ������JEI ���ڳ�ʱ����಻�ᱻ���أ�Fabric �࿿ {@code jei_mod_plugin} ��ڵ㣨ֻ��װ�� JEI
 * �Żᱻ��ѯ����NeoForge/Forge �࿿ JEI �Լ�ɨ�� {@code @JeiPlugin} ע�⡣</p>
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
