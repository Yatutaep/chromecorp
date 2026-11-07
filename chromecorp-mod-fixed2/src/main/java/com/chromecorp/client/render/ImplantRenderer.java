package com.chromecorp.client.render;

import com.chromecorp.Implant;
import com.chromecorp.client.ClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Рендерер для визуализации имплантов на теле игрока.
 * Отображает 3D модели имплантов в зависимости от установленных кибермодификаций.
 */
public class ImplantRenderer extends FeatureRenderer<PlayerEntity, PlayerEntityModel<PlayerEntity>> {
    private static final Identifier CYBER_EYE_TEXTURE = new Identifier("chromecorp", "textures/implant/cyber_eye.png");
    private static final Identifier CYBER_ARM_TEXTURE = new Identifier("chromecorp", "textures/implant/cyber_arm.png");
    private static final Identifier MANTIS_BLADE_TEXTURE = new Identifier("chromecorp", "textures/implant/mantis_blade.png");
    private static final Identifier CYBER_LEGS_TEXTURE = new Identifier("chromecorp", "textures/implant/cyber_legs.png");
    private static final Identifier ARMOR_PLATING_TEXTURE = new Identifier("chromecorp", "textures/implant/armor_plating.png");

    public ImplantRenderer(FeatureRendererContext<PlayerEntity, PlayerEntityModel<PlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {

        if (player.isInvisible()) return;

        // Получаем установленные импланты игрока (нужно будет получать из CyberState)
        // Пока используем заглушку для демонстрации
        int installedImplants = getPlayerImplants(player);

        // Рендер киберглаза (Тактическая Оптика)
        if ((installedImplants & Implant.OPTICS.bit()) != 0) {
            renderCyberEye(matrices, vertexConsumers, light, player, headYaw, headPitch);
        }

        // Рендер кибер-руки (Гидравлические Поршни)
        if ((installedImplants & Implant.PISTONS.bit()) != 0) {
            renderCyberArm(matrices, vertexConsumers, light, player, limbAngle, limbDistance);
        }

        // Рендер Моно-Лезвий (Mantis Blades)
        if ((installedImplants & Implant.BLADES.bit()) != 0) {
            renderMantisBlade(matrices, vertexConsumers, light, player);
        }

        // Рендер кибер-ног (Пневмо-Сухожилия)
        if ((installedImplants & Implant.TENDONS.bit()) != 0) {
            renderCyberLegs(matrices, vertexConsumers, light, player, limbAngle, limbDistance);
        }

        // Рендер брони (Подкожная Углеродная Броня)
        if ((installedImplants & Implant.ARMOR.bit()) != 0) {
            renderArmorPlating(matrices, vertexConsumers, light, player);
        }
    }

    /**
     * Рендер киберглаза - светящийся эффект на голове.
     */
    private void renderCyberEye(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, PlayerEntity player, float headYaw, float headPitch) {
        matrices.push();

        // Позиционирование на голове
        this.getContextModel().head.rotate(matrices);
        matrices.translate(0.15, -0.15, -0.3); // Правый глаз
        matrices.scale(0.1f, 0.1f, 0.1f);

        // Светящийся куб (упрощенная модель)
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(CYBER_EYE_TEXTURE));

        // Простая кубическая модель с эмиссией
        renderGlowingCube(matrices, vertexConsumer, 15728880); // Максимальная яркость

        matrices.pop();
    }

    /**
     * Рендер кибер-руки - металлические пластины на руке.
     */
    private void renderCyberArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, PlayerEntity player, float limbAngle, float limbDistance) {
        matrices.push();

        // Позиционирование на правой руке
        this.getContextModel().rightArm.rotate(matrices);
        matrices.translate(0, 0.4, 0);
        matrices.scale(0.55f, 0.8f, 0.55f);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CYBER_ARM_TEXTURE));

        // Кибер-рука как расширенный цилиндр
        renderCyberArmModel(matrices, vertexConsumer, light);

        matrices.pop();
    }

    /**
     * Рендер Мантис-клинков - выдвижные лезвия из рук.
     */
    private void renderMantisBlade(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                   int light, PlayerEntity player) {
        matrices.push();

        // Позиционирование на обеих руках
        for (int side = 0; side < 2; side++) {
            matrices.push();

            if (side == 0) {
                this.getContextModel().leftArm.rotate(matrices);
            } else {
                this.getContextModel().rightArm.rotate(matrices);
            }

            matrices.translate(0, 0.8, 0.1);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.scale(0.08f, 0.6f, 0.08f);

            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(MANTIS_BLADE_TEXTURE));

            // Лезвие как длинный тонкий куб с эмиссией
            renderBlade(matrices, vertexConsumer, 15728640); // Светящееся лезвие

            matrices.pop();
        }

        matrices.pop();
    }

    /**
     * Рендер кибер-ног - механические усиления.
     */
    private void renderCyberLegs(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 int light, PlayerEntity player, float limbAngle, float limbDistance) {
        matrices.push();

        // Обе ноги
        for (int side = 0; side < 2; side++) {
            matrices.push();

            if (side == 0) {
                this.getContextModel().leftLeg.rotate(matrices);
            } else {
                this.getContextModel().rightLeg.rotate(matrices);
            }

            matrices.translate(0, 0.5, 0);
            matrices.scale(0.52f, 0.9f, 0.52f);

            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CYBER_LEGS_TEXTURE));

            renderCyberLegModel(matrices, vertexConsumer, light);

            matrices.pop();
        }

        matrices.pop();
    }

    /**
     * Рендер брони - панели на теле.
     */
    private void renderArmorPlating(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    int light, PlayerEntity player) {
        matrices.push();

        // Позиционирование на торсе
        this.getContextModel().body.rotate(matrices);
        matrices.translate(0, 0.3, 0);
        matrices.scale(1.05f, 1.05f, 1.05f);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(ARMOR_PLATING_TEXTURE));

        renderArmorPlates(matrices, vertexConsumer, light);

        matrices.pop();
    }

    // Вспомогательные методы для рендеринга геометрии

    private void renderGlowingCube(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        // Упрощенный светящийся куб (для киберглаза)
        MatrixStack.Entry entry = matrices.peek();

        // 6 граней куба
        float size = 1.0f;
        // Передняя грань
        vertexConsumer.vertex(entry.getPositionMatrix(), -size, -size, size).color(255, 0, 0, 255)
            .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), size, -size, size).color(255, 0, 0, 255)
            .texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), size, size, size).color(255, 0, 0, 255)
            .texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), -size, size, size).color(255, 0, 0, 255)
            .texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
    }

    private void renderCyberArmModel(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        // Модель кибер-руки (цилиндр с панелями)
        // Упрощенная версия - можно расширить
        renderGlowingCube(matrices, vertexConsumer, light);
    }

    private void renderBlade(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        // Тонкое длинное лезвие
        MatrixStack.Entry entry = matrices.peek();
        float w = 0.1f, h = 2.0f, d = 0.05f;

        // Упрощенная модель лезвия
        vertexConsumer.vertex(entry.getPositionMatrix(), -w, 0, 0).color(200, 200, 255, 200)
            .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), w, 0, 0).color(200, 200, 255, 200)
            .texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), w, h, 0).color(200, 200, 255, 200)
            .texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
        vertexConsumer.vertex(entry.getPositionMatrix(), -w, h, 0).color(200, 200, 255, 200)
            .texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0, 0, 1).next();
    }

    private void renderCyberLegModel(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        // Модель кибер-ноги
        renderGlowingCube(matrices, vertexConsumer, light);
    }

    private void renderArmorPlates(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        // Броневые пластины на торсе
        renderGlowingCube(matrices, vertexConsumer, light);
    }

    /**
     * Получить установленные импланты игрока.
     */
    private int getPlayerImplants(PlayerEntity player) {
        return ClientState.owned;
    }
}
