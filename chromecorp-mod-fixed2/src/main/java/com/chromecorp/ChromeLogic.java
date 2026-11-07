package com.chromecorp;

import com.chromecorp.CyberState.Data;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public final class ChromeLogic {
    private static final UUID ARMOR_ID = UUID.fromString("c0a1c0de-0000-4000-8000-000000000001");
    private static final UUID BLADE_ID = UUID.fromString("c0a1c0de-0000-4000-8000-000000000002");
    private static final UUID STREET_DMG = UUID.fromString("c0a1c0de-0000-4000-8000-000000000003");
    private static final UUID STREET_SPD = UUID.fromString("c0a1c0de-0000-4000-8000-000000000004");

    private ChromeLogic() {}

    // ───────────────────────── tick ─────────────────────────
    public static void tick(MinecraftServer server) {
        CyberState st = CyberState.get(server);
        ChromeTech.tickFrozen(server);
        boolean sec = server.getTicks() % 20 == 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            Data d = st.of(p.getUuid());
            tickPlayer(server, p, d);
            if (sec) perSecond(server, p, d);
            if (syncHash(d) != d.lastSync) sendSync(p, d);
        }
        if (sec) st.markDirty();
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity p, Data d) {
        ServerWorld w = p.getServerWorld();
        Contract c = d.contract();

        if (c == Contract.TUBE) { // еда не лечит
            if (d.stimGrace > 0) d.stimGrace--;
            else if (p.getHealth() > d.lastHealth) p.setHealth(d.lastHealth);
            d.lastHealth = p.getHealth();
        }
        if (c == Contract.LIVER && p.hasStatusEffect(StatusEffects.HUNGER)) p.removeStatusEffect(StatusEffects.HUNGER);
        if (c == Contract.GHOST && server.getTicks() % 10 == 0) {
            for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(24),
                    m -> (m instanceof IronGolemEntity || m instanceof SnowGolemEntity) && m.getTarget() == p)) m.setTarget(null);
        }

        if (p.isOnGround() || p.isTouchingWater() || p.isClimbing()) d.doubleJumpUsed = false;
        if (d.emiTicks > 0) { d.emiTicks--; d.activeMask = 0; }
        if (d.rocketCd > 0) d.rocketCd--;
        if (d.deckCd > 0) d.deckCd--;
        if (d.active(Implant.CAMO))
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 4, ChromeTech.CAMO_AMP, false, false, false));

        if (d.active(Implant.CHRONO)) { // «замедление мира» вокруг игрока
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 4, 3, false, false, false));
            for (Entity e : w.getOtherEntities(p, p.getBoundingBox().expand(16))) {
                if (e instanceof ProjectileEntity pe) { if (pe.getOwner() != p) pe.setVelocity(pe.getVelocity().multiply(0.7)); }
                else if (e instanceof MobEntity m) m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 3, false, false, false));
            }
        }
        if (d.active(Implant.PISTONS)) p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 4, 0, false, false, false));
        if (d.has(Implant.ARMOR) && p.isTouchingWater())
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 1, false, false, false));
        if (d.symptom(Psychosis.SENSORY) && d.sensoryNeed > 0 && server.getTicks() % 40 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
        }
    }

    private static void perSecond(MinecraftServer server, ServerPlayerEntity p, Data d) {
        ServerWorld w = p.getServerWorld();
        Contract c = d.contract();

        // атрибуты
        setMod(p, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, "cc_armor", 6, EntityAttributeModifier.Operation.ADDITION, d.has(Implant.ARMOR));
        setMod(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, BLADE_ID, "cc_blade", 4, EntityAttributeModifier.Operation.ADDITION, d.active(Implant.BLADES));
        setMod(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, STREET_DMG, "cc_street_dmg", 1.5, EntityAttributeModifier.Operation.ADDITION, c == Contract.STREET);
        setMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, STREET_SPD, "cc_street_spd", 0.1, EntityAttributeModifier.Operation.MULTIPLY_BASE, c == Contract.STREET);

        // нагрев: использование хрома либо жрёт кредиты (нагрев ×0.4), либо греет НС полностью
        double delta = -1.5 + d.neuroAddiction * 0.2; // зависимость от нейропозина разгоняет пассивный нагрев
        for (Implant i : Implant.values()) {
            if (i.toggle && d.active(i)) {
                int fee = (int) Math.ceil(i.heatPerSec);
                if (i != Implant.CAMO && d.credits >= fee) { d.credits -= fee; delta += i.heatPerSec * c.heatMult * 0.4; }
                else delta += i.heatPerSec * c.heatMult;
            }
        }
        d.heat = MathHelper.clamp(d.heat + delta, 0, 100);
        if (d.neuroRecent > 0 && ++d.neuroDecay >= 180) { d.neuroRecent--; d.neuroDecay = 0; }
        if (d.psyCooldown > 0) { d.psyCooldown -= 20; d.heat = Math.min(d.heat, 99); } // иммунитет после приступа
        if (d.heat >= 100 && d.symptom < 0 && d.psyCooldown <= 0) triggerPsychosis(p, d);

        // психоз
        if (d.symptom >= 0) {
            d.psychosisTicks -= 20;
            if (d.psychosisTicks <= 0) {
                d.symptom = -1; d.sensoryNeed = 0; d.psyCooldown = 20 * 120;
                p.sendMessage(Text.literal("Контроль над телом возвращён. 2 минуты иммунитета.").formatted(Formatting.GREEN), true);
            } else applyPsychosis(w, p, d);
        }

        // глитчи кустарного хрома
        for (Implant i : Implant.values())
            if (d.has(i) && d.shady(i) && w.random.nextInt(250) == 0) glitch(w, p, d, i);

        // лизинг раз в игровые сутки
        d.leaseTimer += 20;
        if (d.leaseTimer >= 24000) { d.leaseTimer = 0; payLease(w, p, d); }

        // киллеры при банкротстве
        if (c == Contract.CORP) {
            if (d.credits >= 1000) d.hadMoney = true;
            if (d.hadMoney && d.credits <= 0) {
                if (++d.hitmenTimer >= 120) {
                    d.hitmenTimer = 0;
                    p.sendMessage(Text.literal("Корпорация выставила тебе счёт. Киллеры уже рядом.").formatted(Formatting.DARK_RED), false);
                    for (int k = 0; k < 2; k++) spawnMob(w, p, EntityType.PILLAGER, "Корпоративный киллер", "cc_hit");
                }
            } else d.hitmenTimer = 0;
        }

        // Омега и осада
        if (d.omegaTimer > 0) { d.omegaTimer -= 20; if (d.omegaTimer <= 0) deliverOmega(w, p, d); }
        if (d.omegaDelivered) d.threat = Math.min(100, d.threat + 0.1);
        tickSiege(w, p, d);
    }

    // ───────────────────────── психоз и глитчи ─────────────────────────
    private static void triggerPsychosis(ServerPlayerEntity p, Data d) {
        Psychosis s = Psychosis.values()[p.getRandom().nextInt(Psychosis.values().length)];
        d.symptom = s.ordinal(); d.psychosisTicks = 20 * 90; d.heat = 55; d.activeMask = 0;
        if (s == Psychosis.SENSORY) d.sensoryNeed = 8;
        p.sendMessage(Text.literal("⚠ СИНТЕТИЧЕСКИЙ ПСИХОЗ: " + s.title + " — " + s.desc).formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    private static void applyPsychosis(ServerWorld w, ServerPlayerEntity p, Data d) {
        var r = w.random;
        switch (Psychosis.values()[d.symptom]) {
            case PARANOIA -> {
                if (r.nextFloat() < 0.5f) {
                    var snd = r.nextBoolean() ? SoundEvents.ENTITY_CREEPER_PRIMED : SoundEvents.ENTITY_ZOMBIE_AMBIENT;
                    double x = p.getX() + r.nextInt(9) - 4, z = p.getZ() + r.nextInt(9) - 4;
                    p.networkHandler.sendPacket(new PlaySoundS2CPacket(RegistryEntry.of(snd), SoundCategory.HOSTILE, x, p.getY(), z, 1f, 1f, r.nextLong()));
                }
            }
            case PYROMANIA -> {
                if (r.nextFloat() < 0.4f) {
                    BlockPos f = p.getBlockPos().offset(p.getHorizontalFacing());
                    if (w.getBlockState(f).isAir()) {
                        if (r.nextFloat() < 0.2f) w.setBlockState(f, Blocks.LAVA.getDefaultState());
                        else if (Blocks.FIRE.getDefaultState().canPlaceAt(w, f)) w.setBlockState(f, Blocks.FIRE.getDefaultState());
                    }
                }
            }
            case MOTOR_GLITCH -> {
                if (r.nextFloat() < 0.4f) {
                    Vec3d dir = Vec3d.of(p.getHorizontalFacing().getVector()).multiply(0.7);
                    p.addVelocity(dir.x, 0.1, dir.z);
                    p.velocityModified = true;
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            }
            case ISOLATION -> {
                if (r.nextFloat() < 0.3f)
                    for (TameableEntity t : w.getEntitiesByClass(TameableEntity.class, p.getBoundingBox().expand(4),
                            t -> t.isTamed() && p.getUuid().equals(t.getOwnerUuid())))
                        t.damage(w.getDamageSources().playerAttack(p), 3f);
            }
            default -> {}
        }
    }

    private static void glitch(ServerWorld w, ServerPlayerEntity p, Data d, Implant i) {
        String msg = "Глитч: " + i.title + "!";
        switch (i) {
            case CHRONO -> { d.activeMask |= Implant.CHRONO.bit(); d.heat = Math.min(100, d.heat + 30); }
            case BLADES -> {
                p.damage(w.getDamageSources().generic(), 3f);
                for (IronGolemEntity g : w.getEntitiesByClass(IronGolemEntity.class, p.getBoundingBox().expand(16), x -> true)) g.setTarget(p);
                msg = "Моно-лезвия выскочили рядом с охраной!";
            }
            case TENDONS -> {
                p.addVelocity(0, 0.9, 0); p.velocityModified = true;
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }
            case PISTONS -> p.damage(w.getDamageSources().generic(), 2f);
            case OPTICS -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            case ARMOR -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
            case CAMO -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
                d.heat = Math.min(100, d.heat + 20);
                msg = "Голограмма камуфляжа мигнула — тебя видно!";
            }
            case DECK -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
                d.heat = Math.min(100, d.heat + 10);
                msg = "Кибердека ушла в сбой: скрипт ударил по тебе самому!";
            }
            default -> { return; }
        }
        p.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
    }

    // ───────────────────────── лизинг ─────────────────────────
    private static void payLease(ServerWorld w, ServerPlayerEntity p, Data d) {
        int total = 0;
        for (Implant i : Implant.values()) if (d.has(i) && !d.shady(i)) total += (int) (i.cost * 0.02);
        if (d.contract() == Contract.CORP) p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 24000, 4));
        if (total == 0) return;
        if (d.credits >= total) {
            d.credits -= total; d.missed = 0;
            p.sendMessage(Text.literal("Платёж по био-лизингу: -" + total + " CR").formatted(Formatting.YELLOW), false);
        } else {
            d.missed++;
            p.sendMessage(Text.literal("Просрочка по лизингу (" + d.missed + "/3). Нужно " + total + " CR.").formatted(Formatting.RED), false);
            if (d.missed >= 3) {
                d.missed = 0;
                for (Implant i : Implant.values()) {
                    if (d.has(i) && !d.shady(i)) {
                        d.ownedMask &= ~i.bit(); d.activeMask &= ~i.bit();
                        p.sendMessage(Text.literal("РЕПОССЕССИЯ: корпорация изъяла «" + i.title + "».").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                    }
                }
            }
        }
    }

    // ───────────────────────── Омега и осада ─────────────────────────
    public static void orderOmega(ServerPlayerEntity p) {
        Data d = data(p);
        if (d.omegaTimer > 0 || d.omegaDelivered) { msg(p, "Прототип «Омега» уже заказан.", Formatting.RED); return; }
        if (d.credits < 60000) { msg(p, "Нужно 60000 CR. У теневых фиксеров кредит не дают.", Formatting.RED); return; }
        d.credits -= 60000; d.omegaTimer = 24000;
        msg(p, "Сделка закрыта. «Омега» упадёт с орбиты через сутки. Корпорации тоже следят за небом.", Formatting.LIGHT_PURPLE);
    }

    private static void deliverOmega(ServerWorld w, ServerPlayerEntity p, Data d) {
        d.omegaDelivered = true; d.threat = Math.max(d.threat, 60);
        int bx = p.getBlockX() + 6, bz = p.getBlockZ();
        int by = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.setBlockState(new BlockPos(bx + dx, by, bz + dz), Blocks.IRON_BLOCK.getDefaultState());
        for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) for (int h = 1; h <= 3; h++) w.setBlockState(new BlockPos(bx + dx, by + h, bz + dz), Blocks.IRON_BLOCK.getDefaultState());
        w.setBlockState(new BlockPos(bx, by + 1, bz), Blocks.BEACON.getDefaultState());
        LightningEntity l = EntityType.LIGHTNING_BOLT.create(w);
        if (l != null) { l.refreshPositionAfterTeleport(bx, by, bz); l.setCosmetic(true); w.spawnEntity(l); }
        p.sendMessage(Text.literal("«ОМЕГА» упал в точке " + bx + " " + by + " " + bz + ". Уровень угрозы растёт.").formatted(Formatting.RED, Formatting.BOLD), false);
    }

    private static void tickSiege(ServerWorld w, ServerPlayerEntity p, Data d) {
        if (d.siegeWave == 0 && d.threat >= 100) {
            d.siegeWave = 1; spawnWave(w, p, 1);
            p.sendMessage(Text.literal("▶ ПРОТОКОЛ ЛИКВИДАЦИИ. Волна 1: штурмовики с ЭМИ.").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            return;
        }
        if (d.siegeWave == 0) return;
        List<MobEntity> alive = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(96), m -> m.getCommandTags().contains("cc_siege"));
        // ЭМИ: штурмовики рядом отключают импланты
        if (!w.getEntitiesByClass(PillagerEntity.class, p.getBoundingBox().expand(8), m -> m.getCommandTags().contains("cc_emi")).isEmpty()) {
            if (d.emiTicks <= 0) p.sendMessage(Text.literal("ЭМИ-импульс! Импланты отключены.").formatted(Formatting.AQUA), true);
            d.emiTicks = 100; d.activeMask = 0;
        }
        // Хакеры: слепота и замедление
        if (!w.getEntitiesByClass(EvokerEntity.class, p.getBoundingBox().expand(16), m -> m.getCommandTags().contains("cc_hacker")).isEmpty()) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
        }
        if (!alive.isEmpty()) return;
        d.siegeWave++;
        if (d.siegeWave > 3) {
            d.siegeWave = 0; d.threat = 0; d.credits += 30000;
            d.omegaDelivered = false; // счётчик угрозы больше не растёт
            p.sendMessage(Text.literal("Осада отбита. Награда: 30000 CR. Корпорация отзывает ликвидаторов... пока.").formatted(Formatting.GREEN, Formatting.BOLD), false);
        } else {
            spawnWave(w, p, d.siegeWave);
            p.sendMessage(Text.literal("▶ Волна " + d.siegeWave + (d.siegeWave == 2 ? ": меха-големы и хакеры." : ": босс-ликвидатор.")).formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        }
    }

    private static void spawnWave(ServerWorld w, ServerPlayerEntity p, int wave) {
        switch (wave) {
            case 1 -> { for (int i = 0; i < 6; i++) spawnMob(w, p, EntityType.PILLAGER, "Штурмовик (ЭМИ)", "cc_siege", "cc_emi"); }
            case 2 -> {
                for (int i = 0; i < 3; i++) spawnMob(w, p, EntityType.IRON_GOLEM, "Мех-голем", "cc_siege");
                for (int i = 0; i < 2; i++) spawnMob(w, p, EntityType.EVOKER, "Хакер", "cc_siege", "cc_hacker");
            }
            default -> {
                MobEntity boss = spawnMob(w, p, EntityType.RAVAGER, "Ликвидатор", "cc_siege");
                if (boss != null) {
                    boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(400);
                    boss.setHealth(400f);
                }
                for (int i = 0; i < 2; i++) spawnMob(w, p, EntityType.VINDICATOR, "Охрана ликвидатора", "cc_siege");
            }
        }
    }

    static MobEntity spawnMob(ServerWorld w, ServerPlayerEntity p, EntityType<? extends MobEntity> type, String name, String... tags) {
        double a = w.random.nextDouble() * Math.PI * 2, r = 14 + w.random.nextInt(8);
        int x = (int) (p.getX() + Math.cos(a) * r), z = (int) (p.getZ() + Math.sin(a) * r);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        MobEntity m = type.create(w);
        if (m == null) return null;
        m.refreshPositionAndAngles(x + 0.5, y, z + 0.5, w.random.nextFloat() * 360f, 0f);
        m.initialize(w, w.getLocalDifficulty(m.getBlockPos()), SpawnReason.EVENT, null, null);
        m.setCustomName(Text.literal(name));
        m.setPersistent();
        for (String t : tags) m.addCommandTag(t);
        m.setTarget(p);
        w.spawnEntity(m);
        return m;
    }

    // ───────────────────────── действия игрока ─────────────────────────
    public static void handleAction(ServerPlayerEntity p, int action) {
        Data d = data(p);
        switch (action) {
            case 0 -> toggle(p, d, Implant.CHRONO);
            case 1 -> fireRocket(p, d);
            case 2 -> { // выбрать следующий переключаемый имплант
                for (int k = 1; k <= Implant.values().length; k++) {
                    Implant i = Implant.values()[(d.selected + k) % Implant.values().length];
                    if (i.toggle && d.has(i)) { d.selected = i.ordinal(); msg(p, "Выбрано: " + i.title, Formatting.AQUA); break; }
                }
            }
            case 3 -> toggle(p, d, Implant.values()[d.selected]);
            case 5 -> ChromeTech.deck(p, d);
            case 4 -> {
                if (d.has(Implant.TENDONS) && !p.isOnGround() && !d.doubleJumpUsed && d.emiTicks <= 0) {
                    d.doubleJumpUsed = true;
                    d.heat = Math.min(100, d.heat + 2 * d.contract().heatMult);
                    Vec3d v = p.getVelocity();
                    p.setVelocity(v.x, 0.62, v.z);
                    p.velocityModified = true;
                    p.fallDistance = 0;
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            }
        }
        sendSync(p, d);
    }

    private static void toggle(ServerPlayerEntity p, Data d, Implant i) {
        if (!d.has(i) || !i.toggle) { msg(p, "Имплант не установлен.", Formatting.RED); return; }
        if (d.emiTicks > 0) { msg(p, "ЭМИ-помехи: имплант недоступен.", Formatting.AQUA); return; }
        if (d.symptom >= 0) { msg(p, "Психоз блокирует управление хромом.", Formatting.DARK_RED); return; }
        d.activeMask ^= i.bit();
        if (i == Implant.CAMO && (d.activeMask & i.bit()) != 0) ChromeTech.wipeAggro(p);
        msg(p, i.title + ((d.activeMask & i.bit()) != 0 ? ": ВКЛ" : ": выкл"), Formatting.AQUA);
    }

    private static void fireRocket(ServerPlayerEntity p, Data d) {
        if (!d.has(Implant.ROCKET)) { msg(p, "Микро-Ракетница не установлена.", Formatting.RED); return; }
        if (d.rocketCd > 0 || d.emiTicks > 0) return;
        boolean ok = p.isCreative();
        if (!ok) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(Items.GUNPOWDER)) { s.decrement(1); ok = true; break; }
            }
        }
        if (!ok) { msg(p, "Нужен порох в инвентаре.", Formatting.RED); return; }
        d.rocketCd = 20; d.heat = Math.min(100, d.heat + 8 * d.contract().heatMult);
        ServerWorld w = p.getServerWorld();
        if (d.shady(Implant.ROCKET) && w.random.nextFloat() < 0.15f) {
            w.createExplosion(p, p.getX(), p.getY(), p.getZ(), 1.5f, World.ExplosionSourceType.MOB);
            msg(p, "Контрафактная ракетница взорвалась в руках!", Formatting.RED);
            return;
        }
        // настоящий снаряд: летит, сталкивается с сущностями и блоками
        Vec3d look = p.getRotationVec(1f);
        FireballEntity rocket = new FireballEntity(w, p, look.x, look.y, look.z, 2);
        rocket.setPosition(p.getX() + look.x * 1.5, p.getEyeY() - 0.2 + look.y * 1.5, p.getZ() + look.z * 1.5);
        rocket.setVelocity(look.multiply(1.4));
        w.spawnEntity(rocket);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1f, 0.7f);
    }

    // ───────────────────────── покупка ─────────────────────────
    public static int price(Data d, Implant i, boolean shady) {
        return (int) (i.cost * (shady ? 0.4 : d.contract().priceMult));
    }

    public static void buy(ServerPlayerEntity p, Implant i, boolean shady) {
        Data d = data(p);
        if (d.has(i)) { msg(p, "«" + i.title + "» уже установлен.", Formatting.RED); return; }
        int price = price(d, i, shady);
        if (d.credits < price) { msg(p, "Недостаточно CR: нужно " + price + ".", Formatting.RED); return; }
        d.credits -= price;
        float reject = shady ? (d.contract() == Contract.LIVER ? 0.05f : 0.2f) : 0f;
        if (p.getRandom().nextFloat() < reject) {
            d.heat = Math.min(100, d.heat + 40);
            p.damage(p.getServerWorld().getDamageSources().generic(), 4f);
            msg(p, "ОТТОРЖЕНИЕ! Подвальный механик напортачил. Деньги не вернут.", Formatting.DARK_RED);
            return;
        }
        d.ownedMask |= i.bit();
        if (shady) d.shadyMask |= i.bit(); else d.shadyMask &= ~i.bit();
        msg(p, "Установлено: " + i.title + (shady ? " (контрафакт — жди глитчей)" : " (лизинг -2% в сутки)") + " за " + price + " CR.", Formatting.GREEN);
    }

    public static void uninstall(ServerPlayerEntity p, Implant i) {
        Data d = data(p);
        if (!d.has(i)) return;
        d.ownedMask &= ~i.bit(); d.shadyMask &= ~i.bit(); d.activeMask &= ~i.bit();
        msg(p, "«" + i.title + "» снят. Возврата средств нет.", Formatting.YELLOW);
    }

    public static void shop(ServerPlayerEntity p) {
        Data d = data(p);
        p.sendMessage(Text.literal("── Каталог хрома ──  /chrome buy <id> <certified|shadow>").formatted(Formatting.GOLD), false);
        for (Implant i : Implant.values())
            p.sendMessage(Text.literal(" " + i.name().toLowerCase() + " — " + i.title + " | клиника " + price(d, i, false) + " CR | подвал " + price(d, i, true) + " CR"), false);
        p.sendMessage(Text.literal(" omega — Прототип «Омега» (60000 CR) | /chrome omega | /chrome stim — мед-стим 300 CR"), false);
    }

    public static void status(ServerPlayerEntity p) {
        Data d = data(p);
        msg(p, "Контракт: " + d.contract().title + " — " + d.contract().desc, Formatting.AQUA);
        msg(p, "Кредиты: " + d.credits + " | НС: " + (int) d.heat + "% | Угроза: " + (int) d.threat + "%", Formatting.GOLD);
        if (d.neuroAddiction > 0) msg(p, "Зависимость от нейропозина: " + d.neuroAddiction + "/10 (чистка: /chrome detox)", Formatting.DARK_PURPLE);
    }

    // ───────────────────────── утилиты ─────────────────────────
    public static Data data(ServerPlayerEntity p) { return CyberState.get(p.getServer()).of(p.getUuid()); }

    public static void msg(ServerPlayerEntity p, String s, Formatting f) { p.sendMessage(Text.literal(s).formatted(f), false); }

    public static void assignContract(ServerPlayerEntity p, Data d, boolean first) {
        d.contractIdx = p.getRandom().nextInt(Contract.values().length);
        Contract c = d.contract();
        d.credits = first ? d.credits + c.startCredits : Math.max(d.credits / 2, c.startCredits);
        d.heat = 0; d.symptom = -1; d.activeMask = 0; d.lastHealth = 20f; d.hadMoney = false;
        if (c == Contract.CORP) p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 24000, 4));
        p.sendMessage(Text.literal("═ НОВЫЙ КОНТРАКТ: " + c.title + " ═").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        p.sendMessage(Text.literal(c.desc).formatted(Formatting.GRAY), false);
    }

    private static void setMod(ServerPlayerEntity p, EntityAttribute attr, UUID id, String name, double v, EntityAttributeModifier.Operation op, boolean on) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        boolean has = inst.getModifier(id) != null;
        if (on && !has) inst.addPersistentModifier(new EntityAttributeModifier(id, name, v, op));
        else if (!on && has) inst.removeModifier(id);
    }

    private static int syncHash(Data d) {
        return java.util.Objects.hash(d.credits, (int) d.heat, d.contractIdx, d.ownedMask, d.activeMask, d.shadyMask,
                (int) d.threat, d.selected, d.symptom, Math.max(0, d.psychosisTicks / 20), d.siegeWave, d.neuroAddiction);
    }

    public static void sendSync(ServerPlayerEntity p, Data d) {
        d.lastSync = syncHash(d);
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(d.credits); b.writeInt((int) d.heat); b.writeInt(d.contractIdx);
        b.writeInt(d.ownedMask); b.writeInt(d.activeMask); b.writeInt(d.shadyMask);
        b.writeInt((int) d.threat); b.writeInt(d.selected); b.writeInt(d.symptom);
        b.writeInt(Math.max(0, d.psychosisTicks / 20)); b.writeInt(d.siegeWave); b.writeInt(d.neuroAddiction);
        ServerPlayNetworking.send(p, ChromeCorp.PACKET_SYNC, b);
    }
}
