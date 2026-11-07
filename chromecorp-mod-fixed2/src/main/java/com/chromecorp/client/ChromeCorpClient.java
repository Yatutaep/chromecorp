package com.chromecorp.client;

import com.chromecorp.ChromeCorp;
import com.chromecorp.Contract;
import com.chromecorp.Implant;
import com.chromecorp.Psychosis;
import com.chromecorp.effects.ImplantMalfunctionEffects;
import com.chromecorp.effects.PsychosisEffects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ChromeCorpClient implements ClientModInitializer {
    private static KeyBinding kChrono, kRocket, kCycle, kToggle, kDeck;
    private boolean prevJump, doubleJumpUsed;
    private final Set<Integer> glowed = new HashSet<>();

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chromecorp." + id, InputUtil.Type.KEYSYM, code, "key.categories.chromecorp"));
    }

    private static void send(int action) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(action);
        ClientPlayNetworking.send(ChromeCorp.PACKET_ACTION, b);
    }

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ChromeCorp.RIPPER, RipperRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ChromeCorp.BLACK_MARKET_DEALER, DealerRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(ChromeCorp.PACKET_OPEN_SHOP, (client, handler, buf, sender) -> {
            boolean shady = buf.readBoolean();
            client.execute(() -> client.setScreen(new RipperScreen(shady)));
        });
        kChrono = key("chrono", GLFW.GLFW_KEY_V);
        kRocket = key("rocket", GLFW.GLFW_KEY_B);
        kCycle = key("cycle", GLFW.GLFW_KEY_G);
        kToggle = key("toggle", GLFW.GLFW_KEY_H);
        kDeck = key("deck", GLFW.GLFW_KEY_K);

        ClientPlayNetworking.registerGlobalReceiver(ChromeCorp.PACKET_SYNC, (client, handler, buf, sender) -> {
            int[] v = new int[12];
            for (int i = 0; i < v.length; i++) v[i] = buf.readInt();
            client.execute(() -> {
                ClientState.credits = v[0]; ClientState.heat = v[1]; ClientState.contract = v[2];
                ClientState.owned = v[3]; ClientState.active = v[4]; ClientState.shady = v[5];
                ClientState.threat = v[6]; ClientState.selected = v[7]; ClientState.symptom = v[8];
                ClientState.psySec = v[9]; ClientState.siege = v[10]; ClientState.addiction = v[11];
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;

            // Эффекты киберпсихоза
            if (ClientState.symptom >= 0) {
                PsychosisEffects.tick(mc, mc.player, ClientState.symptom + 1);
            }

            // Эффекты перегрева имплантов
            if (ClientState.heat > 30) {
                ImplantMalfunctionEffects.heatEffect(mc.world, mc.player, ClientState.heat);
            }

            while (kChrono.wasPressed()) send(0);
            while (kRocket.wasPressed()) send(1);
            while (kCycle.wasPressed()) send(2);
            while (kToggle.wasPressed()) send(3);
            while (kDeck.wasPressed()) send(5);

            // Пневмо-Сухожилия: двойной прыжок
            boolean jump = mc.options.jumpKey.isPressed();
            if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing()) doubleJumpUsed = false;
            else if (jump && !prevJump && !doubleJumpUsed && ClientState.has(Implant.TENDONS) && !mc.player.getAbilities().flying) {
                doubleJumpUsed = true; // прыжок выполнит сервер
                send(4);
            }
            prevJump = jump;

            // Тактическая Оптика: подсветка сквозь стены
            Set<Integer> now = new HashSet<>();
            if (ClientState.active(Implant.OPTICS)) {
                Box box = mc.player.getBoundingBox().expand(32);
                for (Entity e : mc.world.getEntitiesByClass(Entity.class, box,
                        x -> x != mc.player && (x instanceof LivingEntity || x instanceof ItemEntity))) {
                    e.setGlowing(true);
                    now.add(e.getId());
                }
            }
            // снимаем свечение с тех, кто вышел из зоны или когда оптика выключена
            for (Iterator<Integer> it = glowed.iterator(); it.hasNext(); ) {
                int id = it.next();
                if (now.contains(id)) continue;
                Entity e = mc.world.getEntityById(id);
                if (e != null && !(e instanceof LivingEntity le && le.hasStatusEffect(StatusEffects.GLOWING))) e.setGlowing(false);
                it.remove();
            }
            glowed.addAll(now);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            TextRenderer tr = mc.textRenderer;
            int x = 6, y = 6;
            ctx.drawTextWithShadow(tr, "¤ " + ClientState.credits + " CR", x, y, 0xFFD700); y += 11;
            ctx.drawTextWithShadow(tr, "Контракт: " + Contract.values()[ClientState.contract].title, x, y, 0xB388FF); y += 11;

            int bw = 80, col = ClientState.heat < 50 ? 0xFF00E5FF : ClientState.heat < 80 ? 0xFFFFAA00 : 0xFFFF2222;
            ctx.fill(x, y, x + bw, y + 6, 0xFF222222);
            ctx.fill(x, y, x + bw * ClientState.heat / 100, y + 6, col);
            ctx.drawTextWithShadow(tr, "НС " + ClientState.heat + "%", x + bw + 4, y - 1, col); y += 11;

            for (Implant i : Implant.values()) {
                if (!ClientState.has(i)) continue;
                String st = !i.toggle ? (i == Implant.ROCKET ? "клав. B" : i == Implant.DECK ? "клав. K" : "пасс.") : ClientState.active(i) ? "ВКЛ" : "выкл";
                boolean sh = (ClientState.shady & i.bit()) != 0;
                String line = (ClientState.selected == i.ordinal() && i.toggle ? "> " : "  ") + i.title + " [" + st + "]" + (sh ? " ⚠" : "");
                ctx.drawTextWithShadow(tr, line, x, y, ClientState.active(i) ? 0x55FF55 : 0xAAAAAA); y += 10;
            }
            if (ClientState.symptom >= 0) {
                Psychosis ps = Psychosis.values()[ClientState.symptom];
                ctx.drawTextWithShadow(tr, "⚠ " + ps.title + " (" + ClientState.psySec + "с)", x, y + 2, 0xFF3333); y += 12;
            }
            if (ClientState.addiction > 0) {
                ctx.drawTextWithShadow(tr, "Зависимость: " + ClientState.addiction + "/10", x, y + 2, 0xCC66FF); y += 12;
            }
            if (ClientState.threat > 0) {
                ctx.drawTextWithShadow(tr, "Угроза: " + ClientState.threat + "%" + (ClientState.siege > 0 ? "  ОСАДА, волна " + ClientState.siege : ""), x, y + 2, 0xFF5555);
            }
            // Оптика: точное HP цели под прицелом
            if (ClientState.active(Implant.OPTICS) && mc.targetedEntity instanceof LivingEntity le) {
                String hp = String.format("%.1f / %.1f HP", le.getHealth(), le.getMaxHealth());
                int cx = ctx.getScaledWindowWidth() / 2 - tr.getWidth(hp) / 2;
                ctx.drawTextWithShadow(tr, Text.literal(hp), cx, ctx.getScaledWindowHeight() / 2 + 12, 0x00E5FF);
            }
        });
    }
}
