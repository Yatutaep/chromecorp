package com.chromecorp.weapon;

import com.chromecorp.CyberState;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Мономолекулярная катана - режет на молекулярном уровне.
 * Шанс на кровотечение, критические удары со спины.
 */
public class MonoKatana extends CyberWeapon {
    private static final float BLEED_CHANCE = 0.35f;
    private static final float BACKSTAB_MULTIPLIER = 2.5f;

    public MonoKatana(Settings settings, WeaponTier tier) {
        super(ToolMaterials.NETHERITE, 7, -2.0f, settings, "Мономолекулярная Катана", tier, 5);
    }

    @Override
    protected void addSpecialTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("✦ Мономолекулярное лезвие").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  35% шанс кровотечения").formatted(Formatting.DARK_RED));
        tooltip.add(Text.literal("  х2.5 урона со спины").formatted(Formatting.RED));
        tooltip.add(Text.literal("")).formatted(Formatting.GRAY);
        tooltip.add(Text.literal("\"Одно движение - одна жизнь\"").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    public void onSpecialAttack(World world, PlayerEntity player, LivingEntity target) {
        if (world.isClient) return;

        ServerWorld sw = (ServerWorld) world;

        // Проверка атаки со спины
        boolean isBackstab = isAttackingFromBehind(player, target);

        if (isBackstab) {
            // Дополнительный урон при атаке со спины
            float bonusDamage = 8.0f * (BACKSTAB_MULTIPLIER - 1.0f);
            target.damage(world.getDamageSources().playerAttack(player), bonusDamage);

            // Эффект частиц для критического удара
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                5, 0.5, 0.5, 0.5, 0.1);
            sw.spawnParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.2);

            player.sendMessage(Text.literal("⚠ КРИТИЧЕСКИЙ УДАР!").formatted(Formatting.RED), true);
            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                SoundCategory.PLAYERS, 1.0f, 0.8f);
        }

        // Шанс на кровотечение
        if (world.getRandom().nextFloat() < BLEED_CHANCE) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));

            // Частицы крови
            sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);
        }

        // Нагрев от использования
        if (player instanceof ServerPlayerEntity sp) {
            var data = CyberState.get(sp.getServer()).of(sp.getUuid());
            data.heat += heatCost;
        }

        // Звук разреза
        world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
            SoundCategory.PLAYERS, 0.8f, 1.5f);
    }

    private boolean isAttackingFromBehind(PlayerEntity attacker, LivingEntity target) {
        // Вектор направления взгляда цели
        double targetYaw = Math.toRadians(target.getYaw());
        double targetDirX = -Math.sin(targetYaw);
        double targetDirZ = Math.cos(targetYaw);

        // Вектор от цели к атакующему
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return false;
        dx /= len;
        dz /= len;

        // Скалярное произведение (если > 0, атака со спины)
        double dot = targetDirX * dx + targetDirZ * dz;
        return dot > 0.5; // ~60° конус сзади
    }
}
