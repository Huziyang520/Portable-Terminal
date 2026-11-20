package com.portableterminal.config;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/**
 * 存入时容器的遍历顺序。
 *
 * <p>选项名走 {@link TranslatableEnum}：原生配置屏幕对枚举默认只显示常量名，
 * 实现这个接口才会去查语言文件。</p>
 */
public enum InsertOrder implements TranslatableEnum {
	/** 离玩家近的先装。 */
	NEAREST,
	/** 离玩家远的先装。 */
	FARTHEST,
	/** 按坐标升序（X → Y → Z），与旧版扫描顺序一致。 */
	COORDINATES;

	@Override
	public Component getTranslatedName() {
		return Component.translatable(ConfigSchema.translationKey(ConfigSchema.KEY_INSERT_ORDER)
			+ "." + name().toLowerCase(Locale.ROOT));
	}
}
