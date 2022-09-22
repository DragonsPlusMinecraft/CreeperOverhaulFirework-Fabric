package plus.dragons.creeperoverhaulfirework.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import tech.thatgravyboat.creeperoverhaul.common.entity.base.BaseCreeper;

import java.util.Collection;

@Mixin(BaseCreeper.class)
public interface SummonCloudWithEffectsMethodInvoker {
    @Invoker(value = "summonCloudWithEffects", remap = false)
    void invokeSummonCloudWithEffects(Collection<StatusEffectInstance> effects);
}
