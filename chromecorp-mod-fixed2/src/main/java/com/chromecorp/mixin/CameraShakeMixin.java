package com.chromecorp.mixin;

import com.chromecorp.effects.PsychosisEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для добавления тряски камеры при киберпсихозе.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow private float yaw;
    @Shadow private float pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        float shake = PsychosisEffects.getCameraShake();

        if (shake != 0) {
            // Применяем тряску к камере
            this.yaw += shake;
            this.pitch += shake * 0.5f;
        }
    }
}
