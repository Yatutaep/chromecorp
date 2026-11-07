package com.chromecorp.mixin;

import com.chromecorp.ChromeTech;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Оптический камуфляж: не рисуем броню, чтобы она не висела в воздухе. */
@Mixin(ArmorFeatureRenderer.class)
public class ArmorHideMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void cc$hideArmor(MatrixStack m, VertexConsumerProvider v, int light, LivingEntity e,
                              float a, float b, float c, float d, float f, float g, CallbackInfo ci) {
        StatusEffectInstance inv = e.getStatusEffect(StatusEffects.INVISIBILITY);
        if (inv != null && inv.getAmplifier() == ChromeTech.CAMO_AMP) ci.cancel();
    }
}
