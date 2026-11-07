package com.chromecorp;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Рипер: NPC-торговец хромом. shady=false — сертифицированный сплайсер, true — подвальный теневой механик. */
public class RipperEntity extends PathAwareEntity {
    private static final TrackedData<Boolean> SHADY = DataTracker.registerData(RipperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public RipperEntity(EntityType<? extends RipperEntity> type, World world) { super(type, world); }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(SHADY, false);
    }

    public boolean isShady() { return dataTracker.get(SHADY); }
    public void setShady(boolean v) { dataTracker.set(SHADY, v); }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            PacketByteBuf b = PacketByteBufs.create();
            b.writeBoolean(isShady());
            ServerPlayNetworking.send(sp, ChromeCorp.PACKET_OPEN_SHOP, b);
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** Риперов нельзя убить, кроме /kill и пустоты. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !(source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL));
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) { super.writeCustomDataToNbt(nbt); nbt.putBoolean("Shady", isShady()); }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) { super.readCustomDataFromNbt(nbt); setShady(nbt.getBoolean("Shady")); }
}
