package com.chromecorp.weapon;

import com.chromecorp.CyberState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Умный пистолет - стреляет снарядами с автонаведением.
 * Требует имплант "Тактическая Оптика" для полной функциональности.
 */
public class SmartPistol extends BowItem {
    private static final int COOLDOWN = 10;
    private static final double TRACKING_RANGE = 16.0;
    private static final int HEAT_COST = 3;

    public SmartPistol(Settings settings) {
        super(settings.maxDamage(500));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            var data = CyberState.get(sp.getServer()).of(sp.getUuid());

            // Проверка на наличие оптики (улучшает наведение)
            boolean hasOptics = data.has(com.chromecorp.Implant.OPTICS);

            // Поиск ближайшей цели
            LivingEntity target = findNearestTarget(world, player, hasOptics ? TRACKING_RANGE * 1.5 : TRACKING_RANGE);

            if (target != null) {
                fireSmartBullet(world, player, target, hasOptics);

                // Нагрев
                data.heat += HEAT_COST;

                // Урон оружию
                stack.damage(1, player, p -> p.sendToolBreakStatus(hand));

                player.sendMessage(Text.literal("◎ ЦЕЛЬ ЗАХВАЧЕНА").formatted(Formatting.GREEN), true);
            } else {
                // Обычный выстрел без наведения
                fireDumbBullet(world, player);
                data.heat += HEAT_COST / 2;
                stack.damage(1, player, p -> p.sendToolBreakStatus(hand));

                player.sendMessage(Text.literal("○ Цель не обнаружена").formatted(Formatting.GRAY), true);
            }

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                SoundCategory.PLAYERS, 0.5f, 1.8f);
        }

        player.getItemCooldownManager().set(this, COOLDOWN);
        return TypedActionResult.success(stack, world.isClient);
    }

    private void fireSmartBullet(World world, PlayerEntity player, LivingEntity target, boolean enhanced) {
        ArrowEntity arrow = new ArrowEntity(world, player);
        arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 3.0f, 1.0f);
        arrow.setDamage(enhanced ? 12.0 : 9.0);
        arrow.setCritical(true);
        arrow.setPierceLevel((byte) 1);

        // Эффект свечения на снаряде
        arrow.setGlowing(true);

        world.spawnEntity(arrow);

        // Частицы наведения
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.5, player.getZ(),
                5, 0.1, 0.1, 0.1, 0.05);

            // Линия наведения к цели
            double dx = target.getX() - player.getX();
            double dy = target.getY() + target.getHeight() / 2 - (player.getY() + 1.5);
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            for (int i = 0; i < 8; i++) {
                double t = i / 8.0;
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + dx * t,
                    player.getY() + 1.5 + dy * t,
                    player.getZ() + dz * t,
                    1, 0, 0, 0, 0);
            }
        }
    }

    private void fireDumbBullet(World world, PlayerEntity player) {
        ArrowEntity arrow = new ArrowEntity(world, player);
        arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 2.5f, 5.0f);
        arrow.setDamage(6.0);
        world.spawnEntity(arrow);
    }

    private LivingEntity findNearestTarget(World world, PlayerEntity player, double range) {
        return world.getEntitiesByClass(LivingEntity.class,
            player.getBoundingBox().expand(range),
            entity -> entity != player && entity.isAlive() && !entity.isTeammate(player)
        ).stream().min((a, b) -> Double.compare(
            a.squaredDistanceTo(player),
            b.squaredDistanceTo(player)
        )).orElse(null);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Умный Пистолет").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Тир: RARE").formatted(Formatting.BLUE));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("✦ Автонаведение на цели").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  Дальность: 16 блоков").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("  +50% с Тактической Оптикой").formatted(Formatting.GREEN));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("\"Промахи остались в прошлом\"").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
