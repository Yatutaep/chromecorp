package com.chromecorp;

public enum Implant {
    CHRONO("Хроно-Овердрафт", 8000, 6.0, true),
    PISTONS("Гидравлические Поршни", 5000, 0.4, true),
    BLADES("Моно-Лезвия", 4500, 0.2, true),
    TENDONS("Пневмо-Сухожилия", 3500, 0.0, false),
    OPTICS("Тактическая Оптика", 3000, 0.8, true),
    ARMOR("Подкожная Углеродная Броня", 6000, 0.0, false),
    ROCKET("Микро-Ракетница", 7000, 0.0, false),
    CAMO("Оптический камуфляж", 9000, 10.0, true),
    DECK("Кибердека", 6500, 0.0, false);

    public final String title;
    public final int cost;
    public final double heatPerSec;
    public final boolean toggle;

    Implant(String title, int cost, double heatPerSec, boolean toggle) {
        this.title = title; this.cost = cost; this.heatPerSec = heatPerSec; this.toggle = toggle;
    }
    public int bit() { return 1 << ordinal(); }
    public static Implant byName(String s) {
        for (Implant i : values()) if (i.name().equalsIgnoreCase(s)) return i;
        return null;
    }
}
