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
    // @Unique：为本Mixin目标类的每一个实例附加独立集合，模拟原实例内部注册表
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

        if (dupeguard$registeredTypes.contains(type)) {
            DupeGuard.warnDuplicate("network packets", type.id().toString(), DupeGuard.findCaller());
            cir.cancel();
            return;
        }
        dupeguard$registeredTypes.add(type);
    }
}
