package com.dupeguard.mixin;

import com.dupeguard.DupeGuard;
import com.dupeguard.DupeGuardConfig;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.IntSupplier;

/**
 * 拦截 Fabric 的网络数据包注册（PayloadTypeRegistry）。
 *
 * <p>当同一个数据包 ID 被重复注册（例如两个模组注册了相同的
 * {@code CustomPacketPayload.Type}），阻止第二次注册并返回已存在的注册结果，
 * 避免协议冲突与崩溃。</p>
 *
 * <p>该类属于 Fabric API 内部实现，跨版本可能改名，因此标记为 {@link Pseudo}
 * 并设置 {@code require = 0}：若未来版本该类/方法不存在，本防护自动跳过，
 * 不会导致游戏启动失败。</p>
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl")
public abstract class PayloadTypeRegistryMixin {

	@Inject(
			method = "register(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
			at = @At("HEAD"),
			cancellable = true,
			require = 0)
	private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayload(
			CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, CallbackInfoReturnable cir) {
		checkAndBlock(type, cir);
	}

	@Inject(
			method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
			at = @At("HEAD"),
			cancellable = true,
			require = 0)
	private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge(
			CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, int size, CallbackInfoReturnable cir) {
		checkAndBlock(type, cir);
	}

	@Inject(
			method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/IntSupplier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
			at = @At("HEAD"),
			cancellable = true,
			require = 0)
	private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge2(
			CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, IntSupplier size, CallbackInfoReturnable cir) {
		checkAndBlock(type, cir);
	}

	@SuppressWarnings("unchecked")
	private <T extends CustomPacketPayload> void checkAndBlock(CustomPacketPayload.Type<T> type, CallbackInfoReturnable cir) {
		if (!DupeGuardConfig.guardNetwork()) {
			return;
		}
		Object existing = ((PayloadTypeRegistryImpl<?>) (Object) this).get(type);
		if (existing != null) {
			DupeGuard.warnDuplicate("network packets", type.id().toString(), DupeGuard.findCaller());
			cir.setReturnValue(existing);
		}
	}
}
