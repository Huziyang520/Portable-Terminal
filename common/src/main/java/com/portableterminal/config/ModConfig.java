package com.portableterminal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.menu.PortableTerminalLayout;
import com.portableterminal.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行期配置快照。
 *
 * <p>设置项来自各加载器注册的 spec（NeoForge 原生 / Fabric 走 FCAP / Forge 1.20.1 走 NCP），
 * 业务代码只读这里的静态字段。排序偏好与界面行数属于玩家状态，仍保存在 JSON 里。</p>
 */
public final class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STATE_FILE = "portable_terminal_client.json";

	public static volatile int scanRadius = ConfigSchema.DEFAULT_SCAN_RADIUS;
	public static volatile boolean masterSwitch = ConfigSchema.DEFAULT_MASTER_SWITCH;
	public static volatile int terminalRows = ConfigSchema.DEFAULT_TERMINAL_ROWS;
	public static volatile String sortConfig = "";
	public static volatile String insertOrder = ConfigSchema.DEFAULT_INSERT_ORDER;

	private static final List<String> BLACKLIST = new ArrayList<>();

	private ModConfig() {
	}

	public static List<String> blacklist() {
		return BLACKLIST;
	}

	/** spec 载入/重载后把值刷进快照；缺项落回登记表默认值并夹到范围内。 */
	public static void applyAll(boolean master, int radius, List<String> blacklist, String order) {
		masterSwitch = master;
		insertOrder = order != null ? order : ConfigSchema.DEFAULT_INSERT_ORDER;
		scanRadius = Math.max(ConfigSchema.MIN_SCAN_RADIUS, Math.min(ConfigSchema.MAX_SCAN_RADIUS, radius));
		BLACKLIST.clear();
		if (blacklist != null) {
			for (String id : blacklist) {
				if (id != null && !id.isBlank()) {
					BLACKLIST.add(id.trim());
				}
			}
		}
	}

	/** 界面上的 +/- 改行数：写进玩家状态文件，下次开终端沿用。 */
	public static void setRows(int rows) {
		int next = PortableTerminalLayout.clampRows(rows);
		if (next == terminalRows) {
			return;
		}
		terminalRows = next;
		saveState();
	}

	public static void loadState() {
		Path path = statePath();
		if (!Files.exists(path)) {
			return;
		}
		try {
			String raw = Files.readString(path);
			StringBuilder sb = new StringBuilder();
			for (String line : raw.split("\n")) {
				if (!line.trim().startsWith("//")) {
					sb.append(line).append("\n");
				}
			}
			JsonObject obj = GSON.fromJson(sb.toString(), JsonObject.class);
			if (obj == null) {
				return;
			}
			if (obj.has("sortConfig")) {
				sortConfig = obj.get("sortConfig").getAsString();
			}
			if (obj.has("terminalRows")) {
				terminalRows = PortableTerminalLayout.clampRows(obj.get("terminalRows").getAsInt());
			}
		} catch (Exception e) {
			PortableTerminalMod.LOGGER.error("Failed to read the terminal state file, keeping defaults.", e);
		}
	}

	public static void saveState() {
		JsonObject obj = new JsonObject();
		obj.addProperty("sortConfig", sortConfig);
		obj.addProperty("terminalRows", terminalRows);
		try {
			Files.writeString(statePath(), GSON.toJson(obj));
		} catch (IOException e) {
			PortableTerminalMod.LOGGER.error("Failed to write the terminal state file.", e);
		}
	}

	private static Path statePath() {
		return Services.PLATFORM.getConfigDir().resolve(STATE_FILE);
	}
}
