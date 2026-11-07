package com.chromecorp;

public enum Contract {
    TUBE("Дитя Пробирки", "Нервная система греется вдвое медленнее. Еда не лечит — нужны мед-стимуляторы.", 500, 0.5, 1.0),
    GHOST("Цифровой призрак", "Големы-охранники тебя не видят. Легальные торговцы отказываются торговать.", 300, 1.0, 1.0),
    LIVER("Синтетическая печень", "Гнилая плоть без дебаффов, редкое отторжение кустарного хрома.", 500, 1.0, 1.0),
    CORP("Ребёнок Корпората", "Огромный капитал и элитный щит. Обанкротишься — придут киллеры.", 20000, 1.0, 1.0),
    STREET("Дитя улиц", "Бонус к урону, скорости и шанс уклонения. Легальный хром дороже в 3 раза.", 200, 1.0, 3.0);

    public final String title, desc;
    public final int startCredits;
    public final double heatMult, priceMult;

    Contract(String title, String desc, int startCredits, double heatMult, double priceMult) {
        this.title = title; this.desc = desc; this.startCredits = startCredits;
        this.heatMult = heatMult; this.priceMult = priceMult;
    }
}
