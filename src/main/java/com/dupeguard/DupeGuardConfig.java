package com.dupeguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件（config/dupeguard.json）。
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>enabled：总开关（默认 true）</li>
 *   <li>registry：是否阻止注册表（方块/物品/实体等）重复注册（默认 true）</li>
 *   <li>network：是否阻止网络数据包重复注册（默认 true）</li>
 *   <li>resourceListener：是否阻止资源监听器重复注册（默认 true）</li>
 *   <li>showStackTrace：阻止重复注册时是否打印调用栈（默认 false）</li>
 * </ul>
 */
public final class DupeGuardConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static boolean enabled = true;
	public static boolean registry = true;
	public static boolean network = true;
	public static boolean resourceListener = true;
	public static boolean showStackTrace = false;

	private DupeGuardConfig() {
	}

	/** 读取配置文件；不存在或格式错误时使用默认值并（如缺失）写回默认配置。 */
	public static void load() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path file = dir.resolve(MOD_ID_JSON());
		try {
			if (Files.exists(file)) {
				JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				if (root.has("enabled")) {
					enabled = root.get("enabled").getAsBoolean();
				}
				if (root.has("registry")) {
					registry = root.get("registry").getAsBoolean();
				}
				if (root.has("network")) {
					network = root.get("network").getAsBoolean();
				}
				if (root.has("resourceListener")) {
					resourceListener = root.get("resourceListener").getAsBoolean();
				}
				if (root.has("showStackTrace")) {
					showStackTrace = root.get("showStackTrace").getAsBoolean();
				}
			} else {
				Files.createDirectories(dir);
				Files.writeString(file, GSON.toJson(defaults()));
			}
		} catch (IOException | RuntimeException e) {
			DupeGuard.LOGGER.warn("[DupeGuard] Failed to read the configuration file, default configuration will be used：{}", e.toString());
		}
	}

	public static boolean guardRegistry() {
		return enabled && registry;
	}

	public static boolean guardNetwork() {
		return enabled && network;
	}

	public static boolean guardResourceListener() {
		return enabled && resourceListener;
	}

	private static JsonObject defaults() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", true);
		root.addProperty("registry", true);
		root.addProperty("network", true);
		root.addProperty("resourceListener", true);
		root.addProperty("showStackTrace", false);
		return root;
	}

	private static String MOD_ID_JSON() {
		return DupeGuard.MOD_ID + ".json";
	}
}
