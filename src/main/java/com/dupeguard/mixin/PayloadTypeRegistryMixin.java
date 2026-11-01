package com.dupeguard.mixin;

import com.dupeguard.DupeGuard;
import com.dupeguard.DupeGuardConfig;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl")
public abstract class PayloadTypeRegistryMixin {
    /**
     * 存储【当前这个注册表实例】已经注册过的Type
     * 重点：允许不同PayloadTypeRegistryImpl实例存放完全相同的Type（clientbound/serverbound双向注册）
     * 仅在**同一个实例内部重复register同一个type才判定为重复注册**
     */
    @Unique
    private final Set<CustomPacketPayload.Type<?>> dupeguard$registeredTypes = ConcurrentHashMap.newKeySet();

    @Inject(
            method = "register(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayload(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, int size, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/IntSupplier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge2(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, IntSupplier size, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, cir);
    }

    @Unique
    private <T extends CustomPacketPayload> void checkAndBlock(CustomPacketPayload.Type<T> type, CallbackInfoReturnable<?> cir) {
        if (!DupeGuardConfig.guardNetwork()) {
            return;
        }

        // ✅关键：只拦截【当前这一个注册表实例】内部重复注册同一个Type
        // 不同实例（serverbound / clientbound）传入同一个type是正常双向注册，直接放行
        if (dupeguard$registeredTypes.contains(type)) {
            DupeGuard.warnDuplicate("network packets", type.id().toString(), DupeGuard.findCaller());
            cir.cancel();
            return;
        }
        dupeguard$registeredTypes.add(type);
    }
}
