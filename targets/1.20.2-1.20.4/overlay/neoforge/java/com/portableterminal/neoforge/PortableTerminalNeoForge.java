package com.portableterminal.neoforge;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.menu.PortableTerminalMenu;
import com.portableterminal.network.OpenTerminalPacket;
import com.portableterminal.network.TerminalSlotClickPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Loader entry point loaded on both physical sides.
 *
 * <p>1.20.2~1.20.4 的 NeoForge 用的是「registrar + FriendlyByteBuf.Reader」的老注册入口
 * （{@code RegisterPayloadHandlerEvent}，单数 Handler；{@code playToServer/playToClient}、
 * {@code @Mod(dist = ...)} 与 {@code @EventBusSubscriber} 都要 20.5 之后才有）。
 * 客户端专用代码放在 {@link PortableTerminalNeoForgeClient}，只在这个分支（{@code FMLEnvironment.dist}
 * 是 CLIENT）里被调用，因此专用服务端不会加载那个类。</p>
 */
@Mod(PortableTerminalMod.MOD_ID)
public class PortableTerminalNeoForge {

	public PortableTerminalNeoForge(IEventBus modBus) {
		modBus.addListener(this::registerPayloads);
		// NeoForge keeps the vanilla registries frozen until RegisterEvent fires, so the menu type
		// must not be registered from the mod constructor.
		modBus.addListener(this::registerMenuTypes);

		if (FMLEnvironment.dist.isClient()) {
			PortableTerminalNeoForgeClient.register(modBus);
		}
	}

	private void registerPayloads(RegisterPayloadHandlerEvent event) {
		IPayloadRegistrar registrar = event.registrar(PortableTerminalMod.MOD_ID);

		// Server side handling is shared with Fabric, so it lives in the common packet records.
		// 本档没有 IPayloadContext#enqueueWork，主线程调度走 workHandler().execute(...)。
		registrar.play(OpenTerminalPacket.ID, OpenTerminalPacket::read,
			(payload, context) -> context.workHandler().execute(() ->
				OpenTerminalPacket.handleServer(context.player().map(p -> (ServerPlayer) p).orElse(null), payload.rows())));
		registrar.play(TerminalSlotClickPacket.ID, TerminalSlotClickPacket::read,
			(payload, context) -> context.workHandler().execute(() ->
				TerminalSlotClickPacket.handleServer(context.player().map(p -> (ServerPlayer) p).orElse(null), payload)));
	}

	private void registerMenuTypes(RegisterEvent event) {
		event.register(Registries.MENU, helper -> {
			MenuType<PortableTerminalMenu> type = ModMenuTypes.create();
			helper.register(ModMenuTypes.ID, type);
			ModMenuTypes.bind(type);
		});
	}
}
