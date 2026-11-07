package com.chromecorp.effects;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Визуальные эффекты для киберпсихоза.
 * Глитч-эффекты, искажения, particles.
 */
public class PsychosisEffects {
    private static final Random RANDOM = new Random();
    private static int glitchTick = 0;
    private static float lastCameraShake = 0;
    private static int glitchIntensity = 0;

    /**
     * Обновление эффектов киберпсихоза каждый тик.
     * Вызывать на клиенте в ClientTickEvent.
     */
    public static void tick(MinecraftClient client, ClientPlayerEntity player, int psychosisLevel) {
        if (player == null || player.getWorld() == null) return;

        glitchTick++;
        World world = player.getWorld();

        // Интенсивность зависит от уровня психоза
        glitchIntensity = psychosisLevel;

        if (glitchIntensity > 0) {
            // Глитч-частицы вокруг игрока
            spawnGlitchParticles(world, player, glitchIntensity);

            // Периодические вспышки и искажения
            if (glitchTick % 40 == 0 && glitchIntensity > 3) {
                flashEffect(client, player);
            }

            // Тряска камеры
            if (glitchIntensity > 5) {
                applyCameraShake(player, glitchIntensity);
            }

            // Пугающие звуки
            if (glitchTick % 80 == 0 && RANDOM.nextFloat() < glitchIntensity * 0.1f) {
                playPsychosisSound(world, player);
            }

            // Визуальные артефакты
            if (glitchIntensity > 7) {
                spawnVisualArtifacts(world, player);
            }
        }
    }

    /**
     * Глитч-частицы вокруг игрока.
     */
    private static void spawnGlitchParticles(World world, ClientPlayerEntity player, int intensity) {
        if (glitchTick % 5 != 0) return;

        int count = intensity / 2;
        for (int i = 0; i < count; i++) {
            double offsetX = (RANDOM.nextDouble() - 0.5) * 3;
            double offsetY = RANDOM.nextDouble() * 2;
            double offsetZ = (RANDOM.nextDouble() - 0.5) * 3;

            // Красные глитч-частицы
            world.addParticle(ParticleTypes.CRIMSON_SPORE,
                player.getX() + offsetX,
                player.getY() + offsetY,
                player.getZ() + offsetZ,
                0, 0.05, 0);

            // Электрические разряды
            if (intensity > 5 && RANDOM.nextFloat() < 0.3f) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + offsetX,
                    player.getY() + offsetY,
                    player.getZ() + offsetZ,
                    (RANDOM.nextDouble() - 0.5) * 0.1,
                    (RANDOM.nextDouble() - 0.5) * 0.1,
                    (RANDOM.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    /**
     * Эффект вспышки и искажения.
     */
    private static void flashEffect(MinecraftClient client, ClientPlayerEntity player) {
        // Резкая вспышка света (white flash)
        for (int i = 0; i < 20; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = RANDOM.nextDouble() * 2;
            player.getWorld().addParticle(ParticleTypes.END_ROD,
                player.getX() + Math.cos(angle) * radius,
                player.getY() + player.getStandingEyeHeight(),
                player.getZ() + Math.sin(angle) * radius,
                0, 0, 0);
        }

        // Звук глитча
        player.getWorld().playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_BEACON_DEACTIVATE,
            SoundCategory.PLAYERS, 0.3f, 0.5f);
    }

    /**
     * Тряска камеры при высоком уровне психоза.
     */
    private static void applyCameraShake(ClientPlayerEntity player, int intensity) {
        float shakeAmount = intensity * 0.02f;
        float shake = (float) Math.sin(glitchTick * 0.5) * shakeAmount;

        // Применяем тряску к yaw/pitch (это будет видно игроку)
        lastCameraShake = shake;

        // Примечание: для полной реализации нужен mixin в Camera класс
        // Здесь только логика расчета
    }

    /**
     * Пугающие звуки психоза.
     */
    private static void playPsychosisSound(World world, ClientPlayerEntity player) {
        int soundType = RANDOM.nextInt(5);
        switch (soundType) {
            case 0 -> world.playSound(player, player.getBlockPos(),
                SoundEvents.ENTITY_GHAST_AMBIENT, SoundCategory.AMBIENT, 0.5f, 0.6f);
            case 1 -> world.playSound(player, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.AMBIENT, 0.4f, 0.8f);
            case 2 -> world.playSound(player, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 0.6f, 0.7f);
            case 3 -> world.playSound(player, player.getBlockPos(),
                SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.5f);
            case 4 -> world.playSound(player, player.getBlockPos(),
                SoundEvents.ENTITY_ALLAY_HURT, SoundCategory.AMBIENT, 0.5f, 0.3f);
        }
    }

    /**
     * Визуальные артефакты - "фантомные" эффекты.
     */
    private static void spawnVisualArtifacts(World world, ClientPlayerEntity player) {
        if (glitchTick % 20 != 0) return;

        // "Цифровой дождь" эффект
        for (int i = 0; i < 5; i++) {
            Vec3d pos = player.getPos().add(
                (RANDOM.nextDouble() - 0.5) * 10,
                RANDOM.nextDouble() * 5,
                (RANDOM.nextDouble() - 0.5) * 10
            );

            world.addParticle(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                0, -0.5, 0);

            world.addParticle(ParticleTypes.PORTAL,
                pos.x, pos.y, pos.z,
                (RANDOM.nextDouble() - 0.5) * 0.2,
                -0.3,
                (RANDOM.nextDouble() - 0.5) * 0.2);
        }
    }

    /**
     * Получить текущую силу тряски камеры (для использования в миксине).
     */
    public static float getCameraShake() {
        return lastCameraShake;
    }

    /**
     * Сброс эффектов (при выходе из психоза).
     */
    public static void reset() {
        glitchTick = 0;
        lastCameraShake = 0;
        glitchIntensity = 0;
    }
}
