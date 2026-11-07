package com.chromecorp;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;

public final class ChromeCommands {
    private ChromeCommands() {}

    public static void register() {
        List<String> names = Arrays.stream(Implant.values()).map(i -> i.name().toLowerCase()).toList();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
            CommandManager.literal("chrome")
                .then(CommandManager.literal("status").executes(c -> { ChromeLogic.status(c.getSource().getPlayerOrThrow()); return 1; }))
                .then(CommandManager.literal("shop").executes(c -> { ChromeLogic.shop(c.getSource().getPlayerOrThrow()); return 1; }))
                .then(CommandManager.literal("omega").executes(c -> { ChromeLogic.orderOmega(c.getSource().getPlayerOrThrow()); return 1; }))
                .then(CommandManager.literal("neuro")
                    .then(CommandManager.literal("shop").executes(c -> { ChromeTech.neuroShop(c.getSource().getPlayerOrThrow()); return 1; }))
                    .then(CommandManager.literal("buy").then(CommandManager.argument("tier", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(ChromeTech.TIERS).map(ChromeTech.Tier::id).toList(), b))
                        .executes(c -> { ChromeTech.buyNeuro(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "tier")); return 1; }))))
                .then(CommandManager.literal("detox")
                    .then(CommandManager.literal("clinic").executes(c -> { ChromeTech.detox(c.getSource().getPlayerOrThrow(), false); return 1; }))
                    .then(CommandManager.literal("shadow").executes(c -> { ChromeTech.detox(c.getSource().getPlayerOrThrow(), true); return 1; })))
                .then(CommandManager.literal("stim").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    var d = ChromeLogic.data(p);
                    if (d.credits < 300) { ChromeLogic.msg(p, "Нужно 300 CR.", Formatting.RED); return 0; }
                    d.credits -= 300;
                    p.giveItemStack(new ItemStack(ChromeCorp.MEDSTIM));
                    return 1;
                }))
                .then(CommandManager.literal("buy").then(CommandManager.argument("implant", StringArgumentType.word())
                    .suggests((c, b) -> CommandSource.suggestMatching(names, b))
                    .then(CommandManager.argument("channel", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("certified", "shadow"), b))
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            Implant i = Implant.byName(StringArgumentType.getString(c, "implant"));
                            if (i == null) { ChromeLogic.msg(p, "Нет такого импланта. /chrome shop", Formatting.RED); return 0; }
                            ChromeLogic.buy(p, i, StringArgumentType.getString(c, "channel").equalsIgnoreCase("shadow"));
                            return 1;
                        }))))
                .then(CommandManager.literal("blackmarket")
                    .then(CommandManager.literal("buy").then(CommandManager.argument("item", StringArgumentType.greedyString())
                        .executes(c -> {
                            BlackMarketShop.buyItem(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "item"));
                            return 1;
                        }))))
                .then(CommandManager.literal("grant").requires(s -> s.hasPermissionLevel(2))
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        ChromeLogic.data(p).credits += IntegerArgumentType.getInteger(c, "amount");
                        return 1;
                    })))
        ));
    }
}
