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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl")
public abstract class PayloadTypeRegistryMixin {

    /**
     * 记录已经注册过的三元组：(payloadType, isClientBound)
     * isClientBound=true = S2C（服务→客户端）；false = C2S（客户端→服务）
     * 同一个Type，既注册C2S又注册S2C是Fabric合法原生行为，不能拦截
     * 只有【同一个Type + 同一个方向】重复register才判定为重复注册
     */
    @Unique
    private static final Set<RegisteredKey> dupeguard$registeredKeys = ConcurrentHashMap.newKeySet();

    @Unique
    private record RegisteredKey(CustomPacketPayload.Type<?> type, boolean isClientBound) {}

    @Inject(
            method = "register(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayload(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, codec, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, int size, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, codec, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/IntSupplier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private <T extends CustomPacketPayload> void dupeguard$blockDuplicatePayloadLarge2(
            CustomPacketPayload.Type<T> type, StreamCodec<?, ?> codec, IntSupplier size, CallbackInfoReturnable<?> cir) {
        checkAndBlock(type, codec, cir);
    }

    @Unique
    private <T extends CustomPacketPayload> void checkAndBlock(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, ?> codec,
            CallbackInfoReturnable<?> cir) {

        if (!DupeGuardConfig.guardNetwork()) {
            return;
        }
        Objects.requireNonNull(type);
        Objects.requireNonNull(codec);

        // StreamCodec 的泛型参数：A -> B
        // A=入站类型，B=出站类型
        // 如果B是 CustomPacketPayload，代表这是服务发往客户端 S2C (isClientBound = true)
        boolean isClientBound = codec.encodeType().isAssignableFrom(CustomPacketPayload.class);
        RegisteredKey key = new RegisteredKey(type, isClientBound);

        if (dupeguard$registeredKeys.contains(key)) {
            // ✅真正重复注册：同一个数据包ID，同一个网络方向
            DupeGuard.warnDuplicate(
                    "network packets [" + (isClientBound ? "S2C" : "C2S") + "]",
                    type.id().toString(),
                    DupeGuard.findCaller()
            );
            cir.cancel();
            return;
        }
        dupeguard$registeredKeys.add(key);
    }
}
