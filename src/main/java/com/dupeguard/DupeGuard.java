package com.dupeguard;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 重复注册防护（Dupe Guard）
 *
 * <p>适用于 Fabric + Minecraft 26.1 及以上版本。
 * 当多个模组（或同一模组重复执行初始化）以相同的 ID 重复注册
 * 方块、物品、实体类型等注册表条目、网络数据包、资源监听器时，
 * 阻止第二次注册并记录警告，避免游戏崩溃。</p>
 */
public class DupeGuard implements ModInitializer {

	public static final String MOD_ID = "dupeguard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 已注册的资源监听器 ID，按资源加载器实例（即按 PackType 区分）保存。
	 * 同一个加载器实例内相同 ID 只允许注册一次。
	 */
	private static final Map<Object, Set<Identifier>> SEEN_LISTENER_IDS =
			Collections.synchronizedMap(new IdentityHashMap<>());

	@Override
	public void onInitialize() {
		DupeGuardConfig.load();
		LOGGER.info("[DupeGuard] Duplicate registration protection is enabled: block/item/entity IDs, network packets, resource listeners.");
	}

	/**
	 * 记录一次被阻止的重复注册。
	 *
	 * @param category 类别，如 "注册表条目"、"网络数据包"、"资源监听器"
	 * @param id       发生重复的 ID
	 * @param caller   调用来源（尽力推断的模组 id / 类名）
	 */
	public static void warnDuplicate(String category, String id, String caller) {
		LOGGER.warn("[DupeGuard] Duplicate registration detected and blocked：{} = \'{}\'，Calling Source：{}", category, id, caller);
		if (DupeGuardConfig.showStackTrace) {
			LOGGER.warn("[DupeGuard] Duplicate registration call stack：", new Exception("duplicate registration stack trace"));
		}
	}

	/**
	 * 标记某个资源加载器实例下已注册的监听器 ID。
	 *
	 * @param owner 资源加载器实例（不同 PackType 为不同实例，天然隔离客户端/服务端）
	 * @param id    监听器 ID
	 * @return true 表示首次注册（允许）；false 表示重复注册（应阻止）
	 */
	public static boolean tryMarkListener(Object owner, Identifier id) {
		synchronized (SEEN_LISTENER_IDS) {
			Set<Identifier> ids = SEEN_LISTENER_IDS.get(owner);
			if (ids == null) {
				ids = new HashSet<>();
				SEEN_LISTENER_IDS.put(owner, ids);
			}
			return ids.add(id);
		}
	}

	/**
	 * 尽力推断重复注册的调用来源（哪个模组干的）。
	 * 通过栈帧类名前缀匹配已加载模组的 id，匹配不到则返回类名本身。
	 */
	public static String findCaller() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		for (StackTraceElement frame : stack) {
			String className = frame.getClassName();
			if (isFrameworkClass(className)) {
				continue;
			}
			String[] parts = className.split("\\.");
			if (parts.length >= 2) {
				// 依次尝试取前 2 段、前 1 段作为模组 id（很多模组包名以 id 开头）
				for (int len = Math.min(2, parts.length); len >= 1; len--) {
					String candidate = String.join(".", Arrays.copyOf(parts, len));
					if (FabricLoader.getInstance().getModContainer(candidate).isPresent()) {
						return candidate;
					}
				}
			}
			return className;
		}
		return "unknow";
	}

	private static boolean isFrameworkClass(String className) {
		return className.startsWith("net.minecraft.")
				|| className.startsWith("net.fabricmc.")
				|| className.startsWith("com.dupeguard.mixin.")
				|| className.equals("com.dupeguard.DupeGuard")
				|| className.equals("com.dupeguard.DupeGuardConfig")
				|| className.startsWith("java.")
				|| className.startsWith("jdk.")
				|| className.startsWith("com.google.")
				|| className.startsWith("com.mojang.")
				|| className.startsWith("it.unimi.")
				|| className.startsWith("io.netty.")
				|| className.startsWith("org.spongepowered.")
				|| className.startsWith("org.slf4j.")
				|| className.startsWith("org.objectweb.")
				|| className.startsWith("org.apache.");
	}
}
