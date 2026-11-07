package com.chromecorp.effects;

import com.chromecorp.Implant;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Визуальные эффекты для поломки и перегрева имплантов.
 * Искры, дым, электрические разряды.
 */
public class ImplantMalfunctionEffects {
    private static final Random RANDOM = new Random();
    private static int sparkTimer = 0;

    /**
     * Эффект перегрева импланта.
     * @param heatLevel уровень нагрева (0-100)
     */
    public static void heatEffect(World world, ClientPlayerEntity player, int heatLevel) {
        if (heatLevel < 30) return;

        sparkTimer++;

        // Дым при среднем нагреве
        if (heatLevel > 40 && sparkTimer % 10 == 0) {
            spawnSmokeParticles(world, player, heatLevel);
        }

        // Искры при высоком нагреве
        if (heatLevel > 60 && sparkTimer % 5 == 0) {
            spawnSparkParticles(world, player, heatLevel);
        }

        // Критический перегрев
        if (heatLevel > 80 && sparkTimer % 20 == 0) {
            criticalOverheatEffect(world, player);
        }
    }

    /**
     * Эффект поломки конкретного импланта.
     */
    public static void malfunctionEffect(World world, ClientPlayerEntity player, Implant implant) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

        // Взрыв искр
        for (int i = 0; i < 30; i++) {
            double vx = (RANDOM.nextDouble() - 0.5) * 0.4;
            double vy = RANDOM.nextDouble() * 0.3;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.4;

            world.addParticle(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z,
                vx, vy, vz);

            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                vx * 2, vy * 2, vz * 2);
        }

        // Дым от перегоревшего импланта
        for (int i = 0; i < 15; i++) {
            world.addParticle(ParticleTypes.LARGE_SMOKE,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.5,
                pos.y,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.5,
                0, 0.1, 0);
        }

        // Звук короткого замыкания
        world.playSound(player, player.getBlockPos(),
            SoundEvents.ITEM_SHIELD_BREAK,
            SoundCategory.PLAYERS, 1.0f, 0.8f);

        world.playSound(player, player.getBlockPos(),
            SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);

        // Электрический треск
        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
            SoundCategory.PLAYERS, 0.7f, 0.6f);
    }

    /**
     * Постоянный эффект неисправного импланта.
     */
    public static void brokenImplantEffect(World world, ClientPlayerEntity player, Implant implant) {
        if (sparkTimer % 30 != 0) return;

        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

        // Редкие искры от сломанного импланта
        for (int i = 0; i < 3; i++) {
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.3,
                pos.y + (RANDOM.nextDouble() - 0.5) * 0.3,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.3,
                (RANDOM.nextDouble() - 0.5) * 0.05,
                (RANDOM.nextDouble() - 0.5) * 0.05,
                (RANDOM.nextDouble() - 0.5) * 0.05);
        }

        // Тихий треск
        if (RANDOM.nextFloat() < 0.3f) {
            world.playSound(player, player.getBlockPos(),
                SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT,
                SoundCategory.PLAYERS, 0.3f, 0.8f);
        }
    }

    /**
     * Частицы дыма при среднем нагреве.
     */
    private static void spawnSmokeParticles(World world, ClientPlayerEntity player, int heatLevel) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.6, 0);

        int count = (heatLevel - 40) / 10;
        for (int i = 0; i < count; i++) {
            world.addParticle(ParticleTypes.SMOKE,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.4,
                pos.y + (RANDOM.nextDouble() - 0.5) * 0.4,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.4,
                0, 0.05, 0);
        }
    }

    /**
     * Искры при высоком нагреве.
     */
    private static void spawnSparkParticles(World world, ClientPlayerEntity player, int heatLevel) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.6, 0);

        int count = (heatLevel - 60) / 5;
        for (int i = 0; i < count; i++) {
            double vx = (RANDOM.nextDouble() - 0.5) * 0.2;
            double vy = RANDOM.nextDouble() * 0.1;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.2;

            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                vx, vy, vz);

            // Огонь при очень высоком нагреве
            if (heatLevel > 70 && RANDOM.nextFloat() < 0.3f) {
                world.addParticle(ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z,
                    vx * 0.5, vy * 0.5, vz * 0.5);
            }
        }

        // Звук искр
        if (RANDOM.nextFloat() < 0.2f) {
            world.playSound(player, player.getBlockPos(),
                SoundEvents.BLOCK_LAVA_POP,
                SoundCategory.PLAYERS, 0.3f, 1.5f);
        }
    }

    /**
     * Критический перегрев - опасная зона.
     */
    private static void criticalOverheatEffect(World world, ClientPlayerEntity player) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

        // Интенсивный дым
        for (int i = 0; i < 10; i++) {
            world.addParticle(ParticleTypes.LARGE_SMOKE,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.5,
                pos.y + (RANDOM.nextDouble() - 0.5) * 0.5,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.5,
                (RANDOM.nextDouble() - 0.5) * 0.1,
                0.1,
                (RANDOM.nextDouble() - 0.5) * 0.1);
        }

        // Лава-частицы
        for (int i = 0; i < 5; i++) {
            world.addParticle(ParticleTypes.LAVA,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.3,
                pos.y,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.3,
                0, 0, 0);
        }

        // Предупреждающий звук
        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_FIRE_AMBIENT,
            SoundCategory.PLAYERS, 0.4f, 0.8f);

        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_BEACON_DEACTIVATE,
            SoundCategory.PLAYERS, 0.5f, 0.7f);
    }

    /**
     * Эффект успешной установки импланта.
     */
    public static void installationEffect(World world, ClientPlayerEntity player, Implant implant) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

        // Круговая волна частиц
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double radius = 1.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            world.addParticle(ParticleTypes.END_ROD,
                x, pos.y, z,
                0, 0.1, 0);

            world.addParticle(ParticleTypes.GLOW,
                x, pos.y, z,
                0, 0, 0);
        }

        // Успешный звук
        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_BEACON_ACTIVATE,
            SoundCategory.PLAYERS, 0.8f, 1.2f);

        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    /**
     * Эффект удаления импланта.
     */
    public static void removalEffect(World world, ClientPlayerEntity player, Implant implant) {
        Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

        // Кровь и боль
        for (int i = 0; i < 20; i++) {
            world.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                pos.x + (RANDOM.nextDouble() - 0.5) * 0.5,
                pos.y + (RANDOM.nextDouble() - 0.5) * 0.5,
                pos.z + (RANDOM.nextDouble() - 0.5) * 0.5,
                (RANDOM.nextDouble() - 0.5) * 0.2,
                -0.1,
                (RANDOM.nextDouble() - 0.5) * 0.2);
        }

        // Искры отключения
        for (int i = 0; i < 15; i++) {
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                (RANDOM.nextDouble() - 0.5) * 0.3,
                (RANDOM.nextDouble() - 0.5) * 0.3,
                (RANDOM.nextDouble() - 0.5) * 0.3);
        }

        // Звуки удаления
        world.playSound(player, player.getBlockPos(),
            SoundEvents.ENTITY_PLAYER_HURT,
            SoundCategory.PLAYERS, 0.7f, 0.8f);

        world.playSound(player, player.getBlockPos(),
            SoundEvents.BLOCK_PISTON_EXTEND,
            SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    /**
     * Сброс таймеров.
     */
    public static void reset() {
        sparkTimer = 0;
    }
}
