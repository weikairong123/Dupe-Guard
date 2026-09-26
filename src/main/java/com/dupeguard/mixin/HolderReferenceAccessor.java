package com.dupeguard.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Holder.Reference#bindKey}（包私有方法）。
 *
 * <p>方块/物品等对象在构造时会创建 intrusive holder（记录在注册表的
 * {@code unregisteredIntrusiveHolders} 中）。当重复注册被拦截、第二个对象
 * 没有进入注册表时，其 holder 会一直处于"未注册"状态，导致
 * {@code Registry.freeze()} 抛出 "Some intrusive holders were not registered"。
 * 通过此 Invoker 把被拦截对象的 holder 绑定到对应 key，即可消除该崩溃。</p>
 */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {

	@Invoker("bindKey")
	void dupeguard$bindKey(ResourceKey<T> key);
}
