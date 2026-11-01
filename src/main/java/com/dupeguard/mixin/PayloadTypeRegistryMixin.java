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
     * @Unique：每个 PayloadTypeRegistryImpl 实例拥有独立集合
     * C2S注册表实例、S2C注册表实例是两个不同对象，各自一套Set，互相隔离
     * 只拦截【同一个实例内部】对同一个Type的重复register调用
     * 不同实例传入相同Type（双向注册）直接放行，属于Fabric合法行为
     */
    @Unique
    private final Set<CustomPacketPayload.Type<?>> dupeguard$instanceRegisteredTypes = ConcurrentHashMap.newKeySet();

    @Inject(
            method = "register(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends CustomPacketPayload> void dupeguard$onRegister(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, ?> codec,
            CallbackInfoReturnable<?> cir
    ) {
        this.checkDuplicate(type, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends CustomPacketPayload> void dupeguard$onRegisterLargeInt(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, ?> codec,
            int maxSize,
            CallbackInfoReturnable<?> cir
    ) {
        this.checkDuplicate(type, cir);
    }

    @Inject(
            method = "registerLarge(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/IntSupplier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends CustomPacketPayload> void dupeguard$onRegisterLargeSupplier(
            CustomPacketPayload.Type<T> type,
            StreamCodec<?, ?> codec,
            IntSupplier maxSizeSupplier,
            CallbackInfoReturnable<?> cir
    ) {
        this.checkDuplicate(type, cir);
    }

    @Unique
    private <T extends CustomPacketPayload> void checkDuplicate(
            CustomPacketPayload.Type<T> type,
            CallbackInfoReturnable<?> cir
    ) {
        if (!DupeGuardConfig.guardNetwork()) {
            return;
        }

        // 当前实例中已经注册过该Type → 真正重复注册，拦截
        if (dupeguard$instanceRegisteredTypes.contains(type)) {
            DupeGuard.warnDuplicate(
                    "network packets",
                    type.id().toString(),
                    DupeGuard.findCaller()
            );
            cir.cancel();
            return;
        }

        // 当前实例首次注册，记录到本实例的集合
        dupeguard$instanceRegisteredTypes.add(type);
    }
}
