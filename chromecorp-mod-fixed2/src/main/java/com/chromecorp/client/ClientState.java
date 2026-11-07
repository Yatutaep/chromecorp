package com.chromecorp.client;

import com.chromecorp.Implant;

public final class ClientState {
    public static int credits, heat, contract, owned, active, shady, threat, selected, symptom = -1, psySec, siege, addiction;
    public static boolean has(Implant i) { return (owned & i.bit()) != 0; }
    public static boolean active(Implant i) { return has(i) && (!i.toggle || (active & i.bit()) != 0); }
}
