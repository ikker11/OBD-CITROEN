package com.codegarage.c4diag;

import java.util.Locale;

public class Dtc {
    public final String code;
    public int status;      // -1 when the protocol gives none
    public final int failureType; // UDS 3rd byte, -1 if none

    public Dtc(int hi, int lo, int failureType, int status) {
        this.code = format(hi, lo);
        this.failureType = failureType;
        this.status = status;
    }

    static String format(int hi, int lo) {
        char letter = "PCBU".charAt((hi >> 6) & 3);
        int d1 = (hi >> 4) & 3;
        return String.format(Locale.US, "%c%d%X%02X", letter, d1, hi & 0x0F, lo & 0xFF);
    }

    public boolean isActive() { return status >= 0 && (status & 0x01) != 0; }

    public String describe() {
        StringBuilder sb = new StringBuilder(code);
        if (failureType >= 0) sb.append(String.format(Locale.US, "-%02X", failureType));
        sb.append("\n   ").append(DtcInfo.describe(code));
        if (status >= 0) {
            boolean conf = (status & 0x08) != 0, pend = (status & 0x04) != 0;
            if ((status & 0x01) != 0) sb.append("\n   PRESENTE AHORA");
            else if (conf && pend) sb.append("\n   Memorizada y pendiente (vuelve a detectarse)");
            else if (conf) sb.append("\n   Memorizada (no presente ahora)");
            else if (pend) sb.append("\n   Pendiente (detectada, aún sin confirmar)");
            else sb.append(String.format(Locale.US, "\n   estado %02X", status));
        }
        return sb.toString();
    }

}
