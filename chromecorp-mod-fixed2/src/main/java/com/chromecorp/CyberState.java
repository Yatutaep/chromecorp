package com.chromecorp;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Данные всех игроков, хранятся в сейве мира. */
public class CyberState extends PersistentState {
    public final Map<UUID, Data> map = new HashMap<>();

    public static CyberState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(CyberState::fromNbt, CyberState::new, "chromecorp_state");
    }

    public Data of(UUID id) {
        return map.computeIfAbsent(id, k -> { Data d = new Data(); d.contractIdx = (int) (Math.random() * Contract.values().length); return d; });
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        map.forEach((k, v) -> all.put(k.toString(), v.toNbt()));
        nbt.put("players", all);
        return nbt;
    }

    public static CyberState fromNbt(NbtCompound nbt) {
        CyberState s = new CyberState();
        NbtCompound all = nbt.getCompound("players");
        for (String k : all.getKeys()) s.map.put(UUID.fromString(k), Data.fromNbt(all.getCompound(k)));
        return s;
    }

    public static class Data {
        public boolean initialized, omegaDelivered, hadMoney;
        public int credits, contractIdx, ownedMask, shadyMask, activeMask, selected;
        public int symptom = -1, psychosisTicks, sensoryNeed, omegaTimer = -1, leaseTimer, missed, siegeWave, hitmenTimer, psyCooldown, neuroAddiction, neuroRecent, neuroDecay;
        public double heat, threat;
        // временные поля (не сохраняются)
        public int stimGrace, rocketCd, emiTicks, lastSync, deckCd;
        public boolean doubleJumpUsed;
        public float lastHealth = 20f;

        public Contract contract() { return Contract.values()[contractIdx]; }
        public boolean has(Implant i) { return (ownedMask & i.bit()) != 0; }
        public boolean shady(Implant i) { return (shadyMask & i.bit()) != 0; }
        public boolean active(Implant i) { return has(i) && (!i.toggle || (activeMask & i.bit()) != 0); }
        public boolean symptom(Psychosis p) { return symptom == p.ordinal(); }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putBoolean("init", initialized); n.putBoolean("omegaDel", omegaDelivered); n.putBoolean("hadMoney", hadMoney);
            n.putInt("credits", credits); n.putInt("contract", contractIdx); n.putInt("owned", ownedMask);
            n.putInt("shady", shadyMask); n.putInt("active", activeMask); n.putInt("sel", selected);
            n.putInt("symptom", symptom); n.putInt("psyTicks", psychosisTicks); n.putInt("sensory", sensoryNeed);
            n.putInt("omegaTimer", omegaTimer); n.putInt("lease", leaseTimer); n.putInt("missed", missed);
            n.putInt("siege", siegeWave); n.putInt("hit", hitmenTimer); n.putInt("psyCd", psyCooldown); n.putInt("neuroAdd", neuroAddiction); n.putInt("neuroRec", neuroRecent); n.putInt("neuroDec", neuroDecay);
            n.putDouble("heat", heat); n.putDouble("threat", threat);
            return n;
        }
        static Data fromNbt(NbtCompound n) {
            Data d = new Data();
            d.initialized = n.getBoolean("init"); d.omegaDelivered = n.getBoolean("omegaDel"); d.hadMoney = n.getBoolean("hadMoney");
            d.credits = n.getInt("credits"); d.contractIdx = n.getInt("contract"); d.ownedMask = n.getInt("owned");
            d.shadyMask = n.getInt("shady"); d.activeMask = n.getInt("active"); d.selected = n.getInt("sel");
            d.symptom = n.getInt("symptom"); d.psychosisTicks = n.getInt("psyTicks"); d.sensoryNeed = n.getInt("sensory");
            d.omegaTimer = n.getInt("omegaTimer"); d.leaseTimer = n.getInt("lease"); d.missed = n.getInt("missed");
            d.siegeWave = n.getInt("siege"); d.hitmenTimer = n.getInt("hit"); d.psyCooldown = n.getInt("psyCd"); d.neuroAddiction = n.getInt("neuroAdd"); d.neuroRecent = n.getInt("neuroRec"); d.neuroDecay = n.getInt("neuroDec");
            d.heat = n.getDouble("heat"); d.threat = n.getDouble("threat");
            return d;
        }
    }
}
