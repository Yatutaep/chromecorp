package com.chromecorp;

import com.chromecorp.CyberState.Data;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public final class ChromeEvents {
    private ChromeEvents() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ChromeLogic::tick);
        ServerTickEvents.END_SERVER_TICK.register(BlackMarketSpawner::tick);

        // старт игры = контракт
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            Data d = ChromeLogic.data(p);
            if (!d.initialized) { d.initialized = true; ChromeLogic.assignContract(p, d, true); }
            ChromeLogic.sendSync(p, d);
        });
        // каждое возрождение = новый контракт
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> ChromeLogic.assignContract(newP, ChromeLogic.data(newP), false));

        // опыта нет — только кредиты
        ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (e instanceof ExperienceOrbEntity) e.discard();
            ChromeTech.unfreeze(e); // моб, сохранённый замороженным, не остаётся парализованным навсегда
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(s -> ChromeTech.unfreezeAll());
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity sp && !(entity instanceof ServerPlayerEntity)) {
                int reward = (int) (entity.getMaxHealth() * 5);
                ChromeLogic.data(sp).credits += reward;
                sp.sendMessage(Text.literal("+" + reward + " CR").formatted(Formatting.GOLD), true);
            }
        });

        // урон: падение (сухожилия) и уклонение (дитя улиц)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                Data d = ChromeLogic.data(sp);
                if (source.isOf(DamageTypes.FALL) && d.has(Implant.TENDONS)) return false;
                if (d.contract() == Contract.STREET && source.getAttacker() != null && sp.getRandom().nextFloat() < 0.12f) {
                    sp.sendMessage(Text.literal("Уклонение!").formatted(Formatting.GREEN), true);
                    return false;
                }
            }
            return true;
        });

        // торговцы: призрак и блокировка коммуникации
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && entity instanceof MerchantEntity && player instanceof ServerPlayerEntity sp) {
                Data d = ChromeLogic.data(sp);
                if (d.contract() == Contract.GHOST) { sp.sendMessage(Text.literal("Торговец не видит тебя в сети. Контракт «Цифровой призрак».").formatted(Formatting.GRAY), true); return ActionResult.FAIL; }
                if (d.symptom(Psychosis.COMMS)) { sp.sendMessage(Text.literal("ОШИБКА ИНТЕРФЕЙСА: торговля заблокирована.").formatted(Formatting.DARK_RED), true); return ActionResult.FAIL; }
            }
            return ActionResult.PASS;
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Data d = ChromeLogic.data(sender);
            if (d.symptom(Psychosis.COMMS)) { sender.sendMessage(Text.literal("ОШИБКА ИНТЕРФЕЙСА: чат заблокирован.").formatted(Formatting.DARK_RED), true); return false; }
            return true;
        });

        // удары: поршни (отбрасывание) и моно-лезвия (иссушение)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp && entity instanceof LivingEntity target) {
                Data d = ChromeLogic.data(sp);
                if (sp.getAttackCooldownProgress(0.5f) < 0.9f) return ActionResult.PASS; // спам кликом не даёт эффектов
                if (d.active(Implant.PISTONS)) {
                    float yaw = sp.getYaw() * 0.017453292f;
                    target.takeKnockback(1.6, Math.sin(yaw), -Math.cos(yaw));
                    d.heat = Math.min(100, d.heat + 0.5);
                }
                if (d.active(Implant.BLADES)) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
                    d.heat = Math.min(100, d.heat + 1.0);
                }
            }
            return ActionResult.PASS;
        });

        // поршни ломают камень голыми руками; сенсорный голод
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp && !sp.isCreative()) {
                Data d = ChromeLogic.data(sp);
                if (d.active(Implant.PISTONS) && state.isIn(BlockTags.PICKAXE_MINEABLE) && state.getHardness(world, pos) <= 3.5f
                        && !state.isIn(BlockTags.NEEDS_DIAMOND_TOOL) && !sp.getMainHandStack().isSuitableFor(state)) {
                    Block.dropStacks(state, world, pos, be, sp, new ItemStack(Items.IRON_PICKAXE));
                    d.heat = Math.min(100, d.heat + 0.3);
                }
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                Data d = ChromeLogic.data(sp);
                if (d.symptom(Psychosis.SENSORY) && d.sensoryNeed > 0 && state.getBlock().getTranslationKey().contains("glass")) {
                    if (--d.sensoryNeed <= 0) {
                        d.symptom = -1;
                        sp.sendMessage(Text.literal("Сенсорный голод утолён.").formatted(Formatting.GREEN), true);
                    }
                }
            }
        });
    }
}
