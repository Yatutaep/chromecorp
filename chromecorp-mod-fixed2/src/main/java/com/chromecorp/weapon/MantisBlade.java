package com.chromecorp.weapon;

import com.chromecorp.CyberState;
import com.chromecorp.Implant;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Мантис-клинки - встроенные лезвия в руках.
 * Требует имплант "Моно-Лезвия" для использования.
 * ПКМ - рывок вперед с серией ударов.
 */
public class MantisBlade extends CyberWeapon {
    private static final int DASH_COOLDOWN = 100;
    private static final int DASH_HEAT = 20;
    private static final double DASH_DISTANCE = 6.0;

    public MantisBlade(Settings settings) {
        super(ToolMaterials.NETHERITE, 9, -1.8f, settings.maxCount(1),
              "Мантис-клинки", WeaponTier.LEGENDARY, 4);
    }

    @Override
    protected void addSpecialTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("⚔ Встроенное кибероружие").formatted(Formatting.RED));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("ТРЕБУЕТСЯ: Моно-Лезвия").formatted(Formatting.DARK_RED));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("✦ Молниеносные атаки").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  +30% скорость атаки").formatted(Formatting.GREEN));
        tooltip.add(Text.literal("  ПКМ: Рывок и серия ударов").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("\"Они даже не поняли, что мертвы\"").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    public void onSpecialAttack(World world, PlayerEntity player, LivingEntity target) {
        if (world.isClient) return;

        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        var data = CyberState.get(sp.getServer()).of(sp.getUuid());

        // Проверка наличия импланта
        if (!data.has(Implant.BLADES)) {
            player.sendMessage(Text.literal("⚠ Требуется имплант: Моно-Лезвия!").formatted(Formatting.RED), true);
            return;
        }

        ServerWorld sw = (ServerWorld) world;

        // Серия быстрых ударов с эффектами
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            3, 0.4, 0.4, 0.4, 0.1);

        sw.spawnParticles(ParticleTypes.CRIT,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            8, 0.3, 0.3, 0.3, 0.15);

        // Кровотечение
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 2));

        // Игрок получает краткий буст скорости
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 1, false, false));

        data.heat += heatCost;

        world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT,
            SoundCategory.PLAYERS, 1.0f, 1.3f);
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

            // Проверка наличия импланта
            if (!data.has(Implant.BLADES)) {
                player.sendMessage(Text.literal("⚠ Требуется имплант: Моно-Лезвия!").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }

            // Рывок вперед
            performDashAttack(world, player);

            data.heat += DASH_HEAT;
            player.sendMessage(Text.literal("⚡ РЫВОК!").formatted(Formatting.RED), true);
        }

        player.getItemCooldownManager().set(this, DASH_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient);
    }

    private void performDashAttack(World world, PlayerEntity player) {
        ServerWorld sw = (ServerWorld) world;

        // Рывок вперед
        var velocity = player.getRotationVector().multiply(DASH_DISTANCE / 5.0);
        player.setVelocity(velocity.x, 0.3, velocity.z);
        player.velocityModified = true;

        // Эффекты буста
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 1, false, false));

        // Частицы следа
        for (int i = 0; i < 10; i++) {
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                player.getX(), player.getY() + 1, player.getZ(),
                2, 0.5, 0.3, 0.5, 0.1);
        }

        // Поиск целей в пути рывка
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
            player.getBoundingBox().stretch(velocity.multiply(5)).expand(1.5),
            entity -> entity != player && entity.isAlive() && !entity.isTeammate(player)
        );

        // Атака всех целей на пути
        for (LivingEntity target : targets) {
            target.damage(world.getDamageSources().playerAttack(player), 12.0f);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));

            sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.2);
        }

        // Звуковые эффекты
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
            SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
            SoundCategory.PLAYERS, 0.5f, 2.0f);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            var data = CyberState.get(sp.getServer()).of(sp.getUuid());

            // Проверка наличия импланта перед атакой
            if (!data.has(Implant.BLADES)) {
                player.sendMessage(Text.literal("⚠ Оружие неактивно! Требуется имплант.").formatted(Formatting.RED), true);
                return false;
            }
        }
        return super.postHit(stack, target, attacker);
    }
}
