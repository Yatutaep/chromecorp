package com.chromecorp;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.List;

/** Серверная обработка покупок из экрана рипера. Всё проверяется на сервере. */
public final class ChromeShop {
    public static final int BUY_IMPLANT = 0, BUY_NEURO = 1, DETOX = 2, BUY_STIM = 3, REMOVE_IMPLANT = 4;

    private ChromeShop() {}

    public static void handle(ServerPlayerEntity p, int type, int arg) {
        List<RipperEntity> near = p.getServerWorld().getEntitiesByClass(RipperEntity.class, p.getBoundingBox().expand(6), e -> true);
        if (near.isEmpty()) { ChromeLogic.msg(p, "Рипер слишком далеко.", Formatting.RED); return; }
        boolean shady = near.get(0).isShady();

        switch (type) {
            case BUY_IMPLANT -> {
                if (arg < 0 || arg >= Implant.values().length) return;
                ChromeLogic.buy(p, Implant.values()[arg], shady);
            }
            case BUY_NEURO -> {
                if (arg < 0 || arg >= ChromeTech.TIERS.length) return;
                if (shady == (arg == 0)) return; // клиника продаёт только tier 0, подполье — остальные
                ChromeTech.buyNeuro(p, ChromeTech.TIERS[arg].id());
            }
            case DETOX -> ChromeTech.detox(p, shady);
            case BUY_STIM -> ChromeTech.buyStim(p);
            case REMOVE_IMPLANT -> {
                if (arg < 0 || arg >= Implant.values().length) return;
                ChromeLogic.uninstall(p, Implant.values()[arg]);
            }
            default -> { return; }
        }
        ChromeLogic.sendSync(p, ChromeLogic.data(p));
    }
}
