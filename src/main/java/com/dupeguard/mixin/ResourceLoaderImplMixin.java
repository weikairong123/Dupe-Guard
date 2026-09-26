package com.dupeguard.mixin;

import com.dupeguard.DupeGuard;
import com.dupeguard.DupeGuardConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截资源监听器（ResourceReloadListener）注册。
 *
 * <p>Fabric 的 v0 {@code ResourceManagerHelper.registerReloadListener} 的两个重载
 * 最终都委托到 v1 {@code ResourceLoaderImpl.registerReloadListener(Identifier, listener)}，
 * 因此在此处统一拦截即可全覆盖。</p>
 *
 * <p>按资源加载器实例记录已注册 ID：客户端资源与服务端数据使用不同实例，
 * 同一监听器 ID 在不同 PackType 下注册互不影响；同一实例内重复注册会被阻止。</p>
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.resource.ResourceLoaderImpl")
public abstract class ResourceLoaderImplMixin {

	@Inject(
			method = "registerReloadListener(Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V",
			at = @At("HEAD"),
			cancellable = true,
			require = 0)
	private void dupeguard$blockDuplicateListener(Identifier id, PreparableReloadListener listener, CallbackInfo ci) {
		if (!DupeGuardConfig.guardResourceListener()) {
			return;
		}
		if (!DupeGuard.tryMarkListener(this, id)) {
			DupeGuard.warnDuplicate("ResourceListener", id.toString(), DupeGuard.findCaller());
			ci.cancel();
		}
	}
}
