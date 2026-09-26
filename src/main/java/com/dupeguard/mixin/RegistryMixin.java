package com.dupeguard.mixin;

import com.dupeguard.DupeGuard;
import com.dupeguard.DupeGuardConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/**
 * 拦截 {@link MappedRegistry#register}（仅限经公共静态 API 进入的调用）。
 *
 * <p>26.1 中 {@code Registry.register(...)} 的所有静态重载（String / Identifier / ResourceKey 版本）
 * 最终都会委托到 {@code WritableRegistry.register(ResourceKey, T, RegistrationInfo)}，
 * 而该方法的唯一实现是 {@link MappedRegistry}（方块/物品/实体等内置注册表
 * 使用的 {@code DefaultedMappedRegistry} 也继承自它）。</p>
 *
 * <p>注意：原版数据包加载器（RegistryDataLoader / RegistryLoadTask）也会调用同一个
 * 实例方法以合法的"先有后覆写"语义装载动态注册表（维度、群系等），不能拦截。
 * 因此这里只拦截"调用链经过公共静态 {@code Registry.register / registerForHolder}"
 * 的重复注册——这正是模组注册方块/物品/实体等条目所走的路径。</p>
 */
@Mixin(MappedRegistry.class)
public abstract class RegistryMixin<T> {

	/** 已创建但尚未注册的 intrusive holder（方块/物品构造时产生）。 */
	@Shadow
	private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

	@Inject(
			method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
			at = @At("HEAD"),
			cancellable = true)
	private <V extends T> void dupeguard$blockDuplicateRegister(
			ResourceKey<T> key, V value, RegistrationInfo registrationInfo, CallbackInfoReturnable<Holder.Reference<T>> cir) {
		if (!DupeGuardConfig.guardRegistry()) {
			return;
		}
		// 只处理经公共静态 Registry.register / registerForHolder 进入的注册（模组路径），
		// 原版数据包加载、注册表同步等内部路径直接放行
		if (!calledThroughPublicRegisterApi()) {
			return;
		}
		Registry<T> self = (Registry<T>) (Object) this;
		if (self.containsKey(key)) {
			Optional<Holder.Reference<T>> existing = self.get(key);
			if (existing.isPresent()) {
				DupeGuard.warnDuplicate("Registry entry (block/item/entity, etc.)", key.identifier().toString(), DupeGuard.findCaller());
				bindBlockedIntrusiveHolder(key, value);
				cir.setReturnValue(existing.get());
			}
			// 理论上 containsKey 为真时必然可取到条目；万一取不到则放行，由原版逻辑处理
		}
	}

	/**
	 * 被拦截的对象（如第二个重复注册的方块/物品）在其构造函数中已创建了
	 * intrusive holder。若不绑定，注册表 freeze 时会抛
	 * "Some intrusive holders were not registered" 崩溃。这里把该 holder 绑定到
	 * 目标 key（不改变注册表内容），保证 freeze 校验通过。
	 */
	private void bindBlockedIntrusiveHolder(ResourceKey<T> key, T value) {
		try {
			Map<T, Holder.Reference<T>> holders = this.unregisteredIntrusiveHolders;
			if (holders == null) {
				return;
			}
			Holder.Reference<T> holder = holders.remove(value);
			if (holder != null) {
				((HolderReferenceAccessor<T>) (Object) holder).dupeguard$bindKey(key);
			}
		} catch (Throwable t) {
			DupeGuard.LOGGER.warn("[DupeGuard] Binding the intrusive holder to the intercepted object failed (duplicate registration has been blocked, which does not affect normal startup)：\n{}", t.toString());
		}
	}

	private static boolean calledThroughPublicRegisterApi() {
		for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			if (frame.getClassName().equals("net.minecraft.core.Registry")
					&& (frame.getMethodName().equals("register") || frame.getMethodName().equals("registerForHolder"))) {
				return true;
			}
		}
		return false;
	}
}
