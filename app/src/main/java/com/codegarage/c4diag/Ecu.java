package com.codegarage.c4diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Ecu {
    public static final int PROTO_UNKNOWN = 0, PROTO_UDS = 1, PROTO_KWP = 2, PROTO_OBD = 3;

    public final String name;
    public final int tx, rx;
    public int protocol = PROTO_UNKNOWN;
    public final List<Dtc> dtcs = new ArrayList<>();
    public String status = "Sin leer";
    public boolean read = false;

    public Ecu(String name, int tx, int rx) {
        this.name = name; this.tx = tx; this.rx = rx;
    }

    public boolean isGeneric() { return protocol == PROTO_OBD; }

    public String ids() {
        String p;
        switch (protocol) {
            case PROTO_UDS: p = "UDS"; break;
            case PROTO_KWP: p = "KWP2000"; break;
            case PROTO_OBD: p = "OBD-II"; break;
            default: p = "?";
        }
        return String.format(Locale.US, "TX %03X · RX %03X · %s", tx, rx, p);
    }

    /** Flow-control header the ELM must use when this ECU sends a long answer. */
    public int flowControlId() {
        return protocol == PROTO_OBD ? 0x7E0 : tx;
    }
}
