package com.chromecorp;

public enum Psychosis {
    PARANOIA("Кибер-Паранойя", "Тебе слышатся крипер и враги."),
    PYROMANIA("Пиромания", "Руки сами поджигают всё вокруг."),
    MOTOR_GLITCH("Двигательный глитч", "Тело шагает куда хочет."),
    ISOLATION("Агрессивная изоляция", "Ты бьёшь своих питомцев."),
    SENSORY("Сенсорный голод", "Разбей 8 блоков стекла, чтобы отпустило."),
    COMMS("Блокировка коммуникации", "Чат и торговля заблокированы.");

    public final String title, desc;
    Psychosis(String t, String d) { title = t; desc = d; }
}
