package com.chromecorp;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Странствующий торговец черного рынка.
 * Появляется редко, продает уникальные импланты с повышенным риском.
 */
public class BlackMarketDealer extends PathAwareEntity {
    private static final String[] NAMES = {
        "Джек «Хром»", "Тень", "Доктор Смерть",
        "Вайпер", "Ворон", "Призрак", "Рипер-13"
    };

    private int despawnTimer = 24000; // 20 минут в мире
    private String dealerName;

    public BlackMarketDealer(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.dealerName = NAMES[random.nextInt(NAMES.length)];
        this.setCustomName(Text.literal("§8§l[Черный Рынок] §r§7" + dealerName));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.2));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            BlackMarketShop.openShop(sp, this.dealerName);
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            // Исчезает через 20 минут
            if (--despawnTimer <= 0) {
                this.getWorld().sendEntityStatus(this, (byte) 3); // Частицы смерти
                this.discard();
            }

            // Предупреждение за 2 минуты до исчезновения
            if (despawnTimer == 2400) {
                PlayerEntity nearest = this.getWorld().getClosestPlayer(this, 32.0);
                if (nearest != null) {
                    nearest.sendMessage(Text.literal("§7" + dealerName + ": §eЯ ухожу через 2 минуты. Решайся быстрее."), false);
                }
            }
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false; // Не деспавнится случайно
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
