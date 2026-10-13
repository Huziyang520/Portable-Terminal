package com.portableterminal.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 本区间（1.20~1.20.1）的最小网络载荷抽象。
 *
 * <p>为什么不用 {@code CustomPacketPayload}：那个接口 1.20.2 才随配置阶段一起加入，1.20.1 里
 * 根本不存在。两端的网络层在本区间都是「频道名 + {@link FriendlyByteBuf}」的老写法，所以这里
 * 只声明老写法真正需要的两件事：包自己的频道名和解码/编码体，由各加载器按自己的注册入口接上。</p>
 */
public interface TerminalPayload {

	/** 本包使用的频道名。 */
	ResourceLocation id();

	/** 写入缓冲区（不含频道名，频道名由加载器的发送入口带上）。 */
	void write(FriendlyByteBuf buf);
}
