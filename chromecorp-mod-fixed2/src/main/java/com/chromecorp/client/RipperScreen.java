package com.chromecorp.client;

import com.chromecorp.ChromeCorp;
import com.chromecorp.ChromeShop;
import com.chromecorp.ChromeTech;
import com.chromecorp.Contract;
import com.chromecorp.Implant;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Экран рипера: вкладки «Импланты», «Аптека», «Услуги». Покупки уходят на сервер пакетом. */
public class RipperScreen extends Screen {
    private static final int PW = 340, PH = 252;
    private static final String[] SHORT = {"Хроно-Овердрафт", "Гидропоршни", "Моно-Лезвия", "Пневмо-Сухожилия",
            "Тактич. Оптика", "Углеродная броня", "Микро-Ракетница", "Оптокамуфляж", "Кибердека"};

    private record Row(int x, int y, Identifier icon, ItemStack stack, String l1, String l2, int c2) {}

    private final boolean shady;
    private int tab, lastKey;
    private final List<Row> rows = new ArrayList<>();

    public RipperScreen(boolean shady) {
        super(Text.literal(shady ? "Теневой механик" : "Сертифицированный сплайсер"));
        this.shady = shady;
    }

    private static void send(int type, int arg) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(type); b.writeInt(arg);
        ClientPlayNetworking.send(ChromeCorp.PACKET_SHOP, b);
    }

    private int price(Implant i) {
        return (int) (i.cost * (shady ? 0.4 : Contract.values()[ClientState.contract].priceMult));
    }

    @Override
    protected void init() {
        rows.clear();
        lastKey = key();
        int px = (width - PW) / 2, py = (height - PH) / 2;

        String[] tabs = {"Импланты", "Аптека", "Услуги"};
        for (int t = 0; t < 3; t++) {
            final int tt = t;
            ButtonWidget b = ButtonWidget.builder(Text.literal(tabs[t]), x -> { tab = tt; clearAndInit(); })
                    .dimensions(px + 8 + t * 108, py + 22, 104, 18).build();
            b.active = t != tab;
            addDrawableChild(b);
        }

        if (tab == 0) {
            for (int n = 0; n < Implant.values().length; n++) {
                Implant i = Implant.values()[n];
                int x = px + 8 + (n % 2) * 166, y = py + 46 + (n / 2) * 34;
                boolean owned = ClientState.has(i);
                int pr = price(i);
                String l2 = owned ? "Установлен" + ((ClientState.shady & i.bit()) != 0 ? " (контрафакт)" : "")
                        : pr + " CR" + (shady ? "  ⚠ отторжение " + (ClientState.contract == 2 ? 5 : 20) + "%" : "  лизинг -2%/сут");
                boolean afford = ClientState.credits >= pr;
                rows.add(new Row(x, y, new Identifier(ChromeCorp.ID, "textures/gui/implant/" + i.name().toLowerCase() + ".png"), null,
                        SHORT[n], l2, owned ? 0x55FF55 : afford ? 0xAAAAAA : 0xFF5555));
                final int idx = n;
                ButtonWidget bw = ButtonWidget.builder(Text.literal(owned ? "Снять" : "Купить"),
                        b -> send(owned ? ChromeShop.REMOVE_IMPLANT : ChromeShop.BUY_IMPLANT, idx))
                        .dimensions(x + 28, y + 20, 64, 12).build();
                bw.active = owned || afford;
                addDrawableChild(bw);
            }
        } else if (tab == 1) {
            int y = py + 50;
            for (int t = 0; t < ChromeTech.TIERS.length; t++) {
                if (shady == (t == 0)) continue; // клиника — только tier 0
                ChromeTech.Tier tier = ChromeTech.TIERS[t];
                boolean afford = ClientState.credits >= tier.price();
                rows.add(new Row(px + 10, y, null, new ItemStack(ChromeCorp.NEUROPOSIN), "Нейропозин — " + tier.title(),
                        tier.price() + " CR   риск отравления " + tier.poison() + "%", afford ? 0xAAAAAA : 0xFF5555));
                final int idx = t;
                ButtonWidget bw = ButtonWidget.builder(Text.literal("Купить"), b -> send(ChromeShop.BUY_NEURO, idx))
                        .dimensions(px + PW - 84, y + 6, 70, 16).build();
                bw.active = afford;
                addDrawableChild(bw);
                y += 34;
            }
        } else {
            int y = py + 50;
            int detoxCost = ClientState.addiction * (shady ? 500 : 1200);
            rows.add(new Row(px + 10, y, null, new ItemStack(ChromeCorp.NEUROPOSIN), "Очистка крови",
                    ClientState.addiction == 0 ? "Зависимости нет" : "Зависимость " + ClientState.addiction + "/10 — " + detoxCost + " CR"
                            + (shady ? "  ⚠ 25% провал" : ""), 0xAAAAAA));
            ButtonWidget d = ButtonWidget.builder(Text.literal("Очистить"), b -> send(ChromeShop.DETOX, 0))
                    .dimensions(px + PW - 84, y + 6, 70, 16).build();
            d.active = ClientState.addiction > 0 && ClientState.credits >= detoxCost;
            addDrawableChild(d);
            y += 34;
            rows.add(new Row(px + 10, y, null, new ItemStack(ChromeCorp.MEDSTIM), "Мед-стимулятор", "300 CR — единственное, что лечит «Дитя Пробирки»", 0xAAAAAA));
            ButtonWidget s = ButtonWidget.builder(Text.literal("Купить"), b -> send(ChromeShop.BUY_STIM, 0))
                    .dimensions(px + PW - 84, y + 6, 70, 16).build();
            s.active = ClientState.credits >= 300;
            addDrawableChild(s);
        }
    }

    private int key() { return ClientState.credits * 31 + ClientState.owned * 7 + ClientState.shady * 3 + ClientState.addiction; }

    @Override
    public void tick() { if (key() != lastKey) clearAndInit(); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        int px = (width - PW) / 2, py = (height - PH) / 2;
        int edge = shady ? 0xFFFF9A1F : 0xFF00E5FF;
        ctx.fill(px, py, px + PW, py + PH, 0xF00B0F1A);
        ctx.fill(px, py, px + PW, py + 1, edge); ctx.fill(px, py + PH - 1, px + PW, py + PH, edge);
        ctx.fill(px, py, px + 1, py + PH, edge); ctx.fill(px + PW - 1, py, px + PW, py + PH, edge);
        ctx.drawTextWithShadow(textRenderer, title, px + 8, py + 8, edge);

        for (Row r : rows) {
            if (r.icon() != null) ctx.drawTexture(r.icon(), r.x(), r.y(), 24, 24, 0f, 0f, 16, 16, 16, 16);
            else ctx.drawItem(r.stack(), r.x() + 4, r.y() + 4);
            int tx = r.icon() != null ? r.x() + 28 : r.x() + 28;
            ctx.drawTextWithShadow(textRenderer, r.l1(), tx, r.y() + 1, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, r.l2(), tx, r.y() + 11, r.c2());
        }
        ctx.drawTextWithShadow(textRenderer, "¤ " + ClientState.credits + " CR", px + 8, py + PH - 14, 0xFFD700);
        ctx.drawTextWithShadow(textRenderer, shady ? "Без гарантий. Без лизинга." : "Лицензия корпорации. Лизинг -2% в сутки.",
                px + 110, py + PH - 14, 0x777777);
        super.render(ctx, mx, my, delta);
    }
}
