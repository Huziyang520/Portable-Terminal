package com.portableterminal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.portableterminal.PortableTerminalMod;
import com.portableterminal.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "portable_terminal_client.json";

	public static int scanRadius = 8;
	public static boolean masterSwitch = true;
	public static List<String> blacklistBlockIds = new ArrayList<>();
	public static String sortConfig = "";

	/** Resolved lazily: the config directory is only known once the loader is up. */
	private static Path configPath() {
		return Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = configPath();
		if (!Files.exists(path)) {
			createDefault();
			return;
		}
		try {
			String raw = Files.readString(path);
			StringBuilder sb = new StringBuilder();
			for (String line : raw.split("\n")) {
				String trimmed = line.trim();
				if (!trimmed.startsWith("//")) {
					sb.append(line).append("\n");
				}
			}
			String json = sb.toString();
			JsonObject obj = GSON.fromJson(json, JsonObject.class);
			if (obj == null) {
				createDefault();
				return;
			}

			if (obj.has("scanRadius")) {
				scanRadius = clamp(obj.get("scanRadius").getAsInt(), 0, 16);
			}
			if (obj.has("masterSwitch")) {
				masterSwitch = obj.get("masterSwitch").getAsBoolean();
			}
			if (obj.has("blacklistBlockIds") && obj.get("blacklistBlockIds").isJsonArray()) {
				JsonArray arr = obj.getAsJsonArray("blacklistBlockIds");
				blacklistBlockIds.clear();
				for (JsonElement e : arr) {
					blacklistBlockIds.add(e.getAsString());
				}
			}
			if (obj.has("sortConfig")) {
				sortConfig = obj.get("sortConfig").getAsString();
			}
		} catch (Exception e) {
			PortableTerminalMod.LOGGER.error("Failed to load config, using defaults.", e);
			createDefault();
		}
	}

	public static void save() {
		JsonObject obj = new JsonObject();
		obj.addProperty("scanRadius", scanRadius);
		obj.addProperty("masterSwitch", masterSwitch);

		JsonArray arr = new JsonArray();
		for (String id : blacklistBlockIds) {
			arr.add(id);
		}
		obj.add("blacklistBlockIds", arr);
		obj.addProperty("sortConfig", sortConfig);

		try {
			Files.writeString(configPath(), GSON.toJson(obj));
		} catch (IOException e) {
			PortableTerminalMod.LOGGER.error("Failed to save config.", e);
		}
	}

	public static void reload() {
		load();
	}

	private static void createDefault() {
		String defaultContent = "{\n" +
				"  // 容器扫描半径（切比雪夫距离），范围 0~16，超出自动修正\n" +
				"  \"scanRadius\": 8,\n" +
				"  // 模组总开关（false 完全禁用）\n" +
				"  \"masterSwitch\": true,\n" +
				"  // 黑名单方块ID列表，这些方块内的物品不会被扫描和存取\n" +
				"  // 示例：[\"minecraft:furnace\", \"minecraft:hopper\", \"minecraft:barrel\"]\n" +
				"  \"blacklistBlockIds\": []\n" +
				"}\n";
		try {
			Files.writeString(configPath(), defaultContent);
		} catch (IOException e) {
			PortableTerminalMod.LOGGER.error("Failed to create default config.", e);
		}
	}

	private static int clamp(int val, int min, int max) {
		return Math.max(min, Math.min(max, val));
	}
}
