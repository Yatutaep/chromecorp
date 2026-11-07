package com.chromecorp;

import com.chromecorp.CyberState.Data;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.event.GameEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Фармакология (Нейропозин), Оптический камуфляж и Кибердека. */
public final class ChromeTech {
    private ChromeTech() {}

    /** Амплитуда невидимости камуфляжа: по ней клиентский миксин отличает его от зелья. */
    public static final int CAMO_AMP = 3;

    // ───────────────────────── Нейропозин ─────────────────────────
    public record Tier(String id, String title, int price, int poison) {}

    /** Чем дешевле подпольный рипер — тем выше шанс отравления (10–30%). Клиника — 0%. */
    public static final Tier[] TIERS = {
        new Tier("clinic", "Сертифицированная клиника", 450, 0),
        new Tier("mid",    "Подпольный рипер (проверенный)", 260, 10),
        new Tier("cheap",  "Подпольный рипер (дешёвый)", 170, 20),
        new Tier("dirt",   "Подвальный рипер (за копейки)", 110, 30)
    };

    public static void neuroShop(ServerPlayerEntity p) {
        ChromeLogic.msg(p, "── Нейропозин ── /chrome neuro buy <клиника|тир>", Formatting.GOLD);
        for (Tier t : TIERS)
            ChromeLogic.msg(p, " " + t.id() + " — " + t.title() + ": " + t.price() + " CR, риск отравления " + t.poison() + "%", Formatting.GRAY);
        ChromeLogic.msg(p, " Крафт: бутылочка + 2-4 ингредиента (чем чище — тем меньше риск, от 1% до 55%).", Formatting.GRAY);
        ChromeLogic.msg(p, " Очистка крови: /chrome detox <clinic|shadow>", Formatting.GRAY);
    }

    public static void buyNeuro(ServerPlayerEntity p, String id) {
        Tier tier = null;
        for (Tier t : TIERS) if (t.id().equalsIgnoreCase(id)) tier = t;
        if (tier == null) { ChromeLogic.msg(p, "Нет такого поставщика. /chrome neuro shop", Formatting.RED); return; }
        Data d = ChromeLogic.data(p);
        if (d.credits < tier.price()) { ChromeLogic.msg(p, "Нужно " + tier.price() + " CR.", Formatting.RED); return; }
        d.credits -= tier.price();
        ItemStack s = new ItemStack(ChromeCorp.NEUROPOSIN);
        NbtCompound n = s.getOrCreateNbt();
        n.putInt("PoisonPct", tier.poison());
        n.putString("Source", tier.id());
        p.giveItemStack(s);
        ChromeLogic.msg(p, "Получен ингалятор «Нейропозин» (риск отравления " + tier.poison() + "%).", Formatting.GREEN);
    }

    public static void useNeuro(ServerPlayerEntity p, ItemStack stack) {
        Data d = ChromeLogic.data(p);
        var r = p.getRandom();
        NbtCompound n = stack.getNbt();
        int poisonPct = n != null ? n.getInt("PoisonPct") : 0;

        // побочный эффект: дезориентация 2-3 секунды
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40 + r.nextInt(21), 0));

        // скрытая зависимость: чем чаще используешь, тем выше шанс
        d.neuroRecent++;
        float chance = Math.min(0.6f, 0.10f + 0.04f * (d.neuroRecent - 1));
        if (d.neuroAddiction < 10 && r.nextFloat() < chance) {
            d.neuroAddiction++;
            ChromeLogic.msg(p, "Тело запомнило вкус нейропозина...", Formatting.DARK_PURPLE);
        }

        if (r.nextInt(100) < poisonPct) { // отравление: эффекты и рост риска психоза
            d.heat = Math.min(100, d.heat + 15);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 300, 1));
            ChromeLogic.msg(p, "ОТРАВЛЕНИЕ! Грязный состав ударил по нервам — нагрев растёт.", Formatting.DARK_RED);
        } else {
            d.heat = Math.max(0, d.heat - 30);
            ChromeLogic.msg(p, "Нервная система остыла на 30%.", Formatting.AQUA);
        }
    }

    public static void detox(ServerPlayerEntity p, boolean shadow) {
        Data d = ChromeLogic.data(p);
        if (d.neuroAddiction <= 0) { ChromeLogic.msg(p, "Кровь чистая.", Formatting.GRAY); return; }
        int cost = d.neuroAddiction * (shadow ? 500 : 1200);
        if (d.credits < cost) { ChromeLogic.msg(p, "Очистка стоит " + cost + " CR.", Formatting.RED); return; }
        d.credits -= cost;
        if (shadow && p.getRandom().nextFloat() < 0.25f) {
            d.heat = Math.min(100, d.heat + 20);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 0));
            ChromeLogic.msg(p, "Подвальный рипер всё испортил. Деньги не вернут.", Formatting.DARK_RED);
            return;
        }
        d.neuroAddiction = 0; d.neuroRecent = 0;
        ChromeLogic.msg(p, "Кровь очищена (-" + cost + " CR).", Formatting.GREEN);
    }

    public static void buyStim(ServerPlayerEntity p) {
        Data d = ChromeLogic.data(p);
        if (d.credits < 300) { ChromeLogic.msg(p, "Нужно 300 CR.", Formatting.RED); return; }
        d.credits -= 300;
        p.giveItemStack(new ItemStack(ChromeCorp.MEDSTIM));
    }

    // ───────────────────────── Оптический камуфляж ─────────────────────────
    /** Стирает игрока из памяти всех мобов в радиусе 30 блоков. */
    public static void wipeAggro(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(30), x -> true)) {
            if (m.getTarget() == p) m.setTarget(null);
            if (m.getAttacker() == p) m.setAttacker(null);
            if (m instanceof Angerable a && p.getUuid().equals(a.getAngryAt())) a.stopAnger();
            m.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
            m.getBrain().forget(MemoryModuleType.ANGRY_AT);
        }
        ChromeLogic.msg(p, "Голограмма активна. Преследователи тебя потеряли.", Formatting.AQUA);
    }

    // ───────────────────────── Кибердека ─────────────────────────
    private record Frozen(ServerWorld w, UUID id, int end) {}
    private static final List<Frozen> FROZEN = new ArrayList<>();

    public static void deck(ServerPlayerEntity p, Data d) {
        if (!d.has(Implant.DECK)) { ChromeLogic.msg(p, "Кибердека не установлена.", Formatting.RED); return; }
        if (d.emiTicks > 0) { ChromeLogic.msg(p, "ЭМИ-помехи: деку закоротило.", Formatting.AQUA); return; }
        if (d.symptom >= 0) { ChromeLogic.msg(p, "Психоз блокирует интерфейс деки.", Formatting.DARK_RED); return; }
        if (d.deckCd > 0) return;
        d.deckCd = 20;

        ServerWorld w = p.getServerWorld();
        double range = 40;
        Vec3d start = p.getEyePos(), look = p.getRotationVec(1f), end = start.add(look.multiply(range));
        BlockHitResult bh = w.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        double maxSq = bh.getType() == HitResult.Type.MISS ? range * range : start.squaredDistanceTo(bh.getPos());
        EntityHitResult eh = ProjectileUtil.raycast(p, start, end, p.getBoundingBox().stretch(look.multiply(range)).expand(1.0),
                e -> !e.isSpectator() && e.canHit(), maxSq);

        double heat = 0;
        boolean ok = false;
        if (eh != null) {
            Entity t = eh.getEntity();
            if (t instanceof CreeperEntity c) { overload(w, p, c); heat = 14; ok = true; }
            else if (t instanceof MobEntity m && (m instanceof IronGolemEntity || m instanceof SnowGolemEntity || m instanceof ShulkerEntity)) {
                freeze(w, m, 100 + p.getRandom().nextInt(101)); // 5-10 секунд
                heat = 10; ok = true;
            }
        } else if (bh.getType() == HitResult.Type.BLOCK) {
            ok = hackBlock(w, p, bh); heat = 4;
        }
        if (!ok) { ChromeLogic.msg(p, "Цель не имеет уязвимых протоколов.", Formatting.GRAY); return; }
        d.heat = Math.min(100, d.heat + heat * d.contract().heatMult);
    }

    private static boolean hackBlock(ServerWorld w, ServerPlayerEntity p, BlockHitResult bh) {
        BlockPos pos = bh.getBlockPos();
        BlockState st = w.getBlockState(pos);
        Block b = st.getBlock();
        if (b instanceof DoorBlock door) {
            door.setOpen(p, w, st, pos, !st.get(Properties.OPEN));
            return true;
        }
        if (b instanceof TrapdoorBlock || b instanceof FenceGateBlock) {
            boolean open = st.get(Properties.OPEN);
            w.setBlockState(pos, st.with(Properties.OPEN, !open), Block.NOTIFY_ALL);
            SoundEvent snd = b instanceof TrapdoorBlock
                    ? (open ? SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE : SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN)
                    : (open ? SoundEvents.BLOCK_FENCE_GATE_CLOSE : SoundEvents.BLOCK_FENCE_GATE_OPEN);
            w.playSound(null, pos, snd, SoundCategory.BLOCKS, 1f, 1f);
            w.emitGameEvent(p, open ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos);
            return true;
        }
        if (b instanceof LeverBlock) { st.onUse(w, p, Hand.MAIN_HAND, bh); return true; }
        if (b instanceof ButtonBlock btn) {
            if (!st.get(Properties.POWERED)) btn.powerOn(st, w, pos);
            return true;
        }
        return false;
    }

    /** Взлом крипера: взрыв без урона игроку, полный урон всем враждебным вокруг. */
    private static void overload(ServerWorld w, ServerPlayerEntity p, CreeperEntity creeper) {
        Vec3d c = creeper.getPos();
        creeper.discard();
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        w.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4f, 0.9f);
        DamageSource src = w.getDamageSources().explosion(p, p);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, Box.of(c, 12, 12, 12),
                x -> x != p && x instanceof Monster && x.squaredDistanceTo(c) <= 36)) {
            e.damage(src, 24f);
        }
    }

    private static void freeze(ServerWorld w, MobEntity m, int ticks) {
        m.setAiDisabled(true);
        m.addCommandTag("cc_frozen");
        m.setTarget(null);
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 10, false, false));
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getBodyY(0.5), m.getZ(), 25, 0.4, 0.6, 0.4, 0.1);
        FROZEN.add(new Frozen(w, m.getUuid(), w.getServer().getTicks() + ticks));
    }

    public static void unfreeze(Entity e) {
        if (e == null) return;
        if (e instanceof MobEntity m && m.getCommandTags().contains("cc_frozen")) {
            m.setAiDisabled(false);
            m.removeCommandTag("cc_frozen");
        }
    }

    public static void tickFrozen(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        for (Iterator<Frozen> it = FROZEN.iterator(); it.hasNext(); ) {
            Frozen f = it.next();
            if (server.getTicks() >= f.end()) {
                Entity e = f.w().getEntity(f.id());
                if (e != null) unfreeze(e);
                it.remove();
            }
        }
    }

    public static void unfreezeAll() {
        for (Frozen f : FROZEN) {
            Entity e = f.w().getEntity(f.id());
            if (e != null) unfreeze(e);
        }
        FROZEN.clear();
    }
}
