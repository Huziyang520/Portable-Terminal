package com.portableterminal.config;

import com.portableterminal.menu.PortableTerminalLayout;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * Cloth Config screen for the mod's options.
 *
 * <p>Cloth Config is optional: nothing here may be loaded when it is absent, so callers gate the
 * call on {@code Services.PLATFORM.isModLoaded(...)} and only then touch this class. Entries start
 * from the live values in {@link ModConfig}, write straight back into it, and {@code
 * setSavingRunnable} persists the file.</p>
 */
public final class ModConfigScreen {

	private ModConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.portableterminal.title"))
			.setSavingRunnable(ModConfig::save);

		ConfigEntryBuilder entry = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(
			Component.translatable("config.portableterminal.category.general"));

		general.addEntry(entry.startBooleanToggle(
				Component.translatable("config.portableterminal.master_switch"), ModConfig.masterSwitch)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.portableterminal.master_switch.tooltip"))
			.setSaveConsumer(value -> ModConfig.masterSwitch = value)
			.build());

		general.addEntry(entry.startIntSlider(
				Component.translatable("config.portableterminal.scan_radius"), ModConfig.scanRadius, 0, 16)
			.setDefaultValue(8)
			.setTooltip(Component.translatable("config.portableterminal.scan_radius.tooltip"))
			.setSaveConsumer(value -> ModConfig.scanRadius = value)
			.build());

		general.addEntry(entry.startIntSlider(
				Component.translatable("config.portableterminal.terminal_rows"), ModConfig.terminalRows,
				PortableTerminalLayout.MIN_ROWS, PortableTerminalLayout.MAX_ROWS)
			.setDefaultValue(PortableTerminalLayout.DEFAULT_ROWS)
			.setTooltip(Component.translatable("config.portableterminal.terminal_rows.tooltip"))
			.setSaveConsumer(value -> ModConfig.terminalRows = PortableTerminalLayout.clampRows(value))
			.build());

		general.addEntry(entry.startStrList(
				Component.translatable("config.portableterminal.blacklist"), ModConfig.blacklistBlockIds)
			.setDefaultValue(new ArrayList<>())
			.setTooltip(Component.translatable("config.portableterminal.blacklist.tooltip"))
			.setSaveConsumer(value -> {
				ModConfig.blacklistBlockIds.clear();
				ModConfig.blacklistBlockIds.addAll(value);
			})
			.build());

		return builder.build();
	}
}
