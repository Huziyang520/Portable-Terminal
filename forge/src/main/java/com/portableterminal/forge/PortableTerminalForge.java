package com.portableterminal.forge;

import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.ModMenuTypes;
import com.portableterminal.menu.PortableTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.20.1 entry point, loaded on both physical sides. Client only code lives in
 * {@link PortableTerminalForgeClient}, which Forge only constructs on the client.
 */
@Mod(PortableTerminalMod.MOD_ID)
public class PortableTerminalForge {

	public static final DeferredRegister<MenuType<?>> MENUS =
		DeferredRegister.create(Registries.MENU, PortableTerminalMod.MOD_ID);

	public static final RegistryObject<MenuType<PortableTerminalMenu>> TERMINAL_MENU =
		MENUS.register(ModMenuTypes.ID.getPath(), ModMenuTypes::create);

	public PortableTerminalForge() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		MENUS.register(modBus);
		// 频道的编解码器必须在服务端就绪之前注册（两端共用一套序号）。
		ForgeTerminalNetwork.register();
		modBus.addListener(this::commonSetup);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		// DeferredRegister 的值在注册阶段结束后才可用，所以要挪到 enqueueWork 里取。
		event.enqueueWork(() -> {
			ModMenuTypes.bind(TERMINAL_MENU.get());
			com.portableterminal.config.ModConfig.load();
		});
	}
}
