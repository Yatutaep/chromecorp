package com.chromecorp.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CyberWeapon extends SwordItem {
    protected final String weaponName;
    protected final WeaponTier tier;
    protected final int heatCost;

    public CyberWeapon(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings,
                       String name, WeaponTier tier, int heatCost) {
        super(material, attackDamage, attackSpeed, settings);
        this.weaponName = name;
        this.tier = tier;
        this.heatCost = heatCost;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(weaponName).formatted(tier.color));
        tooltip.add(Text.literal("Тир: " + tier.name()).formatted(Formatting.GRAY));
        if (heatCost > 0) {
            tooltip.add(Text.literal("Нагрев: " + heatCost + "/атака").formatted(Formatting.YELLOW));
        }
        addSpecialTooltip(stack, world, tooltip, context);
    }

    protected abstract void addSpecialTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context);

    public abstract void onSpecialAttack(World world, PlayerEntity player, LivingEntity target);

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player && !attacker.getWorld().isClient) {
            onSpecialAttack(attacker.getWorld(), player, target);
        }
        return super.postHit(stack, target, attacker);
    }

    public enum WeaponTier {
        COMMON(Formatting.WHITE),
        UNCOMMON(Formatting.GREEN),
        RARE(Formatting.BLUE),
        EPIC(Formatting.LIGHT_PURPLE),
        LEGENDARY(Formatting.GOLD);

        public final Formatting color;
        WeaponTier(Formatting color) {
            this.color = color;
        }
    }
}
