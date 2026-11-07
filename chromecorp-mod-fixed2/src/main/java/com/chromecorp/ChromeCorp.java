package com.chromecorp;

import com.chromecorp.weapon.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class ChromeCorp implements ModInitializer {
    public static final String ID = "chromecorp";
    public static final Identifier PACKET_SYNC = new Identifier(ID, "sync");
    public static final Identifier PACKET_ACTION = new Identifier(ID, "action");
    public static final Identifier PACKET_SHOP = new Identifier(ID, "shop");
    public static final Identifier PACKET_OPEN_SHOP = new Identifier(ID, "open_shop");
    public static Item CREDCHIP, MEDSTIM, NEUROPOSIN, RIPPER_CLINIC, RIPPER_SHADY;

    // Оружие
    public static Item MONO_KATANA, TECH_BLADE, MANTIS_BLADE, SMART_PISTOL;

    public static net.minecraft.entity.EntityType<RipperEntity> RIPPER;
    public static net.minecraft.entity.EntityType<BlackMarketDealer> BLACK_MARKET_DEALER;
    public static RecipeSerializer<NeuroCraftRecipe> NEURO_SERIALIZER;

    @Override
    public void onInitialize() {
        CREDCHIP = Registry.register(Registries.ITEM, new Identifier(ID, "credchip"), new CredChip(new FabricItemSettings().maxCount(64)));
        MEDSTIM = Registry.register(Registries.ITEM, new Identifier(ID, "medstim"), new MedStim(new FabricItemSettings().maxCount(16)));
        RIPPER = Registry.register(Registries.ENTITY_TYPE, new Identifier(ID, "ripper"),
                net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder
                        .create(net.minecraft.entity.SpawnGroup.MISC, RipperEntity::new)
                        .dimensions(net.minecraft.entity.EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64).build());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(RIPPER, RipperEntity.createAttributes());

        // Регистрация торговца черного рынка
        BLACK_MARKET_DEALER = Registry.register(Registries.ENTITY_TYPE, new Identifier(ID, "black_market_dealer"),
                net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder
                        .create(net.minecraft.entity.SpawnGroup.CREATURE, BlackMarketDealer::new)
                        .dimensions(net.minecraft.entity.EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64).build());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(BLACK_MARKET_DEALER, BlackMarketDealer.createAttributes());

        RIPPER_CLINIC = Registry.register(Registries.ITEM, new Identifier(ID, "ripper_clinic"), new RipperSpawner(new FabricItemSettings().maxCount(16), false));
        RIPPER_SHADY = Registry.register(Registries.ITEM, new Identifier(ID, "ripper_shady"), new RipperSpawner(new FabricItemSettings().maxCount(16), true));
        NEUROPOSIN = Registry.register(Registries.ITEM, new Identifier(ID, "neuroposin"), new Inhaler(new FabricItemSettings().maxCount(8)));
        NEURO_SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, new Identifier(ID, "crafting_special_neuro"),
                new SpecialRecipeSerializer<>(NeuroCraftRecipe::new));

        // Регистрация оружия
        MONO_KATANA = Registry.register(Registries.ITEM, new Identifier(ID, "mono_katana"),
                new MonoKatana(new FabricItemSettings().maxCount(1), CyberWeapon.WeaponTier.RARE));
        TECH_BLADE = Registry.register(Registries.ITEM, new Identifier(ID, "tech_blade"),
                new TechBlade(new FabricItemSettings().maxCount(1), CyberWeapon.WeaponTier.EPIC));
        MANTIS_BLADE = Registry.register(Registries.ITEM, new Identifier(ID, "mantis_blade"),
                new MantisBlade(new FabricItemSettings().maxCount(1)));
        SMART_PISTOL = Registry.register(Registries.ITEM, new Identifier(ID, "smart_pistol"),
                new SmartPistol(new FabricItemSettings().maxCount(1)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(CREDCHIP); e.add(MEDSTIM); e.add(NEUROPOSIN); e.add(RIPPER_CLINIC); e.add(RIPPER_SHADY);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(MONO_KATANA); e.add(TECH_BLADE); e.add(MANTIS_BLADE); e.add(SMART_PISTOL);
        });

        ServerPlayNetworking.registerGlobalReceiver(PACKET_ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readInt();
            server.execute(() -> ChromeLogic.handleAction(player, action));
        });
        ServerPlayNetworking.registerGlobalReceiver(PACKET_SHOP, (server, player, handler, buf, sender) -> {
            int type = buf.readInt(), arg = buf.readInt();
            server.execute(() -> ChromeShop.handle(player, type, arg));
        });
        ChromeEvents.register();
        ChromeCommands.register();
    }

    /** Кредчип: ПКМ — зачислить всю стопку на счёт. */
    public static class CredChip extends Item {
        public CredChip(Settings s) { super(s); }
        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack s = p.getStackInHand(h);
            if (!w.isClient && p instanceof ServerPlayerEntity sp) {
                var d = CyberState.get(sp.getServer()).of(sp.getUuid());
                int add = 100 * s.getCount();
                d.credits += add;
                s.decrement(s.getCount());
                sp.sendMessage(Text.literal("+" + add + " CR зачислено.").formatted(Formatting.GOLD), true);
            }
            return TypedActionResult.success(s, w.isClient);
        }
    }

    /** Ингалятор «Нейропозин»: экстренное охлаждение НС. Риск отравления хранится в NBT (PoisonPct). */
    public static class Inhaler extends Item {
        public Inhaler(Settings s) { super(s); }
        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack s = p.getStackInHand(h);
            if (p.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(s);
            if (!w.isClient && p instanceof ServerPlayerEntity sp) {
                ChromeTech.useNeuro(sp, s);
                w.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 1f, 0.6f);
            }
            p.getItemCooldownManager().set(this, 60);
            if (!p.isCreative()) s.decrement(1);
            return TypedActionResult.success(s, w.isClient);
        }
        @Override
        public void appendTooltip(ItemStack s, @Nullable World w, List<Text> tip, TooltipContext ctx) {
            NbtCompound n = s.getNbt();
            int pct = n != null ? n.getInt("PoisonPct") : 0;
            tip.add(Text.literal("Риск отравления: " + pct + "%").formatted(pct == 0 ? Formatting.GREEN : pct < 25 ? Formatting.YELLOW : Formatting.RED));
        }
    }

    /** Мед-стим: единственное, что лечит «Дитя Пробирки». */
    public static class MedStim extends Item {
        public MedStim(Settings s) { super(s); }
        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack s = p.getStackInHand(h);
            if (p.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(s);
            if (!w.isClient && p instanceof ServerPlayerEntity sp) {
                var d = CyberState.get(sp.getServer()).of(sp.getUuid());
                d.stimGrace = 5;
                sp.heal(12f);
                d.heat = Math.max(0, d.heat - 10);
                w.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1f, 1.4f);
            }
            p.getItemCooldownManager().set(this, 40);
            if (!p.isCreative()) s.decrement(1);
            return TypedActionResult.success(s, w.isClient);
        }
    }
}
