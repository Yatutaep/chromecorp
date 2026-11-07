package com.chromecorp.weapon;

import com.chromecorp.CyberState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Энергетический клинок - пробивает блоки электромагнитным полем.
 * ПКМ - энергетический импульс, наносящий урон через стены.
 */
public class TechBlade extends CyberWeapon {
    private static final int PULSE_COOLDOWN = 60;
    private static final int PULSE_HEAT = 15;
    private static final double PULSE_RANGE = 8.0;

    public TechBlade(Settings settings, WeaponTier tier) {
        super(ToolMaterials.NETHERITE, 8, -2.4f, settings, "Энергетический Клинок", tier, 3);
    }

    @Override
    protected void addSpecialTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("⚡ Электромагнитное поле").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("  ПКМ: Энергетический импульс").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  Пробивает блоки и стены").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  Дальность: 8 блоков").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("\"Ничто не остановит прогресс\"").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    public void onSpecialAttack(World world, PlayerEntity player, LivingEntity target) {
        if (world.isClient) return;

        ServerWorld sw = (ServerWorld) world;

        // Электрические частицы при ударе
        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            12, 0.3, 0.3, 0.3, 0.1);

        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            5, 0.2, 0.2, 0.2, 0.05);

        // Небольшой электрический урон по площади
        world.getOtherEntities(target, target.getBoundingBox().expand(2.0))
            .stream()
            .filter(e -> e instanceof LivingEntity)
            .forEach(e -> {
                ((LivingEntity) e).damage(world.getDamageSources().playerAttack(player), 3.0f);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + e.getHeight() / 2, e.getZ(),
                    5, 0.2, 0.2, 0.2, 0.05);
            });

        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
            SoundCategory.PLAYERS, 0.5f, 2.0f);

        if (player instanceof ServerPlayerEntity sp) {
            var data = CyberState.get(sp.getServer()).of(sp.getUuid());
            data.heat += heatCost;
        }
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

            // Энергетический импульс
            fireEnergyPulse(world, player);

            data.heat += PULSE_HEAT;
            stack.damage(5, player, p -> p.sendToolBreakStatus(hand));

            player.sendMessage(Text.literal("⚡ ИМПУЛЬС!").formatted(Formatting.LIGHT_PURPLE), true);
        }

        player.getItemCooldownManager().set(this, PULSE_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient);
    }

    private void fireEnergyPulse(World world, PlayerEntity player) {
        ServerWorld sw = (ServerWorld) world;

        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVector();
        Vec3d end = start.add(direction.multiply(PULSE_RANGE));

        // Рейкаст БЕЗ учета блоков (пробивает стены)
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
            player.getBoundingBox().stretch(direction.multiply(PULSE_RANGE)).expand(1.0),
            entity -> entity != player && entity.isAlive()
        );

        for (LivingEntity target : targets) {
            // Проверяем, попадает ли цель в луч
            Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0).subtract(start);
            double distance = toTarget.length();
            if (distance > PULSE_RANGE) continue;

            Vec3d normalized = toTarget.normalize();
            double dot = normalized.dotProduct(direction);
            if (dot < 0.95) continue; // ~18° конус

            // Наносим урон
            target.damage(world.getDamageSources().playerAttack(player), 10.0f);

            // Эффект попадания
            sw.spawnParticles(ParticleTypes.SONIC_BOOM,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                1, 0, 0, 0, 0);

            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                20, 0.3, 0.3, 0.3, 0.2);
        }

        // Визуализация луча
        for (double t = 0; t < PULSE_RANGE; t += 0.3) {
            Vec3d pos = start.add(direction.multiply(t));
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                2, 0.05, 0.05, 0.05, 0.01);
        }

        // Звук импульса
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
            SoundCategory.PLAYERS, 0.8f, 1.5f);
    }
}
