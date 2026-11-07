package com.chromecorp;

import com.chromecorp.CyberState.Data;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Магазин черного рынка с уникальными имплантами и опасными товарами.
 */
public final class BlackMarketShop {
    private BlackMarketShop() {}

    /** Уникальные импланты черного рынка */
    public enum BlackMarketItem {
        // Экспериментальные импланты с особыми эффектами
        ADRENALINE_BOOST("Адреналиновый Бустер", 8500, 50,
            "§7Автоактивация при <30% HP\n§7+60% скорость и сила на 10 сек\n§cРиск отторжения: 50%"),

        NEURAL_SHIELD("Нейро-Щит", 12000, 40,
            "§7Поглощает 30% урона\n§7Конвертирует урон в нагрев НС\n§cРиск отторжения: 40%"),

        BLOOD_FILTER("Кибер-Фильтр Крови", 6500, 35,
            "§7Иммунитет к ядам и голоду\n§7Автоочистка от нейропозина\n§cРиск отторжения: 35%"),

        OVERCLOCK("Оверклок Процессор", 15000, 60,
            "§7Все импланты x1.5 мощнее\n§7Нагрев НС x2 быстрее\n§cРиск отторжения: 60%"),

        STEALTH_LEGS("Бесшумные Ноги", 5500, 30,
            "§7Бесшумное передвижение\n§7Мобы не слышат с 2x расстояния\n§cРиск отторжения: 30%"),

        COMBAT_DRUGS("Боевые Стимуляторы x3", 2000, 0,
            "§7Мгновенно: Сила II, Скорость II\n§7Длительность: 60 секунд\n§cПобочка: Слабость на 2 мин");

        public final String name;
        public final int price;
        public final int rejectChance; // Шанс отторжения %
        public final String description;

        BlackMarketItem(String name, int price, int rejectChance, String description) {
            this.name = name;
            this.price = price;
            this.rejectChance = rejectChance;
            this.description = description;
        }
    }

    public static void openShop(ServerPlayerEntity p, String dealerName) {
        Data d = ChromeLogic.data(p);

        p.sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.DARK_GRAY), false);
        p.sendMessage(Text.literal("  §8§l[ЧЕРНЫЙ РЫНОК]").formatted(Formatting.DARK_GRAY), false);
        p.sendMessage(Text.literal("  §7" + dealerName + ": §fУ меня есть то, чего нет у других...").formatted(Formatting.GRAY), false);
        p.sendMessage(Text.literal("  §7Твои кредиты: §6" + d.credits + " CR").formatted(Formatting.GRAY), false);
        p.sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.DARK_GRAY), false);
        p.sendMessage(Text.literal(""), false);

        // Случайный выбор 3-4 товаров
        Random rand = p.getRandom();
        List<BlackMarketItem> available = new ArrayList<>();
        List<BlackMarketItem> allItems = List.of(BlackMarketItem.values());

        int itemCount = 3 + rand.nextInt(2); // 3 или 4 товара
        while (available.size() < itemCount && available.size() < allItems.size()) {
            BlackMarketItem item = allItems.get(rand.nextInt(allItems.size()));
            if (!available.contains(item)) {
                available.add(item);
            }
        }

        for (int i = 0; i < available.size(); i++) {
            BlackMarketItem item = available.get(i);
            p.sendMessage(Text.literal("§8[" + (i + 1) + "] §f" + item.name + " §7- §6" + item.price + " CR"), false);
            p.sendMessage(Text.literal("    " + item.description), false);
            p.sendMessage(Text.literal(""), false);
        }

        p.sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.DARK_GRAY), false);
        p.sendMessage(Text.literal("§7Покупка: §f/chrome blackmarket buy <название>").formatted(Formatting.GRAY), false);
        p.sendMessage(Text.literal("§c⚠ Черный рынок не дает гарантий!").formatted(Formatting.RED), false);
    }

    public static void buyItem(ServerPlayerEntity p, String itemName) {
        Data d = ChromeLogic.data(p);

        // Поиск предмета
        BlackMarketItem item = null;
        for (BlackMarketItem bmi : BlackMarketItem.values()) {
            if (bmi.name().toLowerCase().replace("_", "").contains(itemName.toLowerCase().replace(" ", ""))) {
                item = bmi;
                break;
            }
        }

        if (item == null) {
            ChromeLogic.msg(p, "Такого товара нет. Используй точное название.", Formatting.RED);
            return;
        }

        if (d.credits < item.price) {
            ChromeLogic.msg(p, "Недостаточно кредитов. Нужно " + item.price + " CR.", Formatting.RED);
            return;
        }

        d.credits -= item.price;

        // Проверка на отторжение
        if (item.rejectChance > 0 && p.getRandom().nextInt(100) < item.rejectChance) {
            d.heat = Math.min(100, d.heat + 50);
            p.damage(p.getServerWorld().getDamageSources().magic(), 8f);
            ChromeLogic.msg(p, "§c§l⚠ КРИТИЧЕСКОЕ ОТТОРЖЕНИЕ!", Formatting.DARK_RED);
            ChromeLogic.msg(p, "§cОрганизм отверг имплант. Деньги потеряны.", Formatting.RED);
            ChromeLogic.msg(p, "§c+50% нагрев НС, -8 HP", Formatting.RED);
            return;
        }

        // Успешная установка (здесь нужно добавить логику для каждого импланта)
        ChromeLogic.msg(p, "§a✓ " + item.name + " установлен!", Formatting.GREEN);
        ChromeLogic.msg(p, "§7Имплант добавлен в твою систему.", Formatting.GRAY);

        // TODO: Добавить реальную логику имплантов в CyberState.Data
        // Пока просто уведомление
        ChromeLogic.msg(p, "§e[В разработке] Эффекты импланта будут добавлены в следующем обновлении.", Formatting.YELLOW);
    }
}
