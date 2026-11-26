package com.codegarage.c4diag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic logic for PSA (Citroën / Peugeot) cars with CAN diagnostics.
 * Each ECU is addressed by its own CAN ID pair. Reads and clears faults with
 * UDS (ISO 14229) or KWP2000-on-CAN, whichever the ECU answers.
 */
public class PsaDiag {

    public interface Progress { void update(int done, int total, String what); }

    /** Usual PSA diagnostic addresses (request, response). Names are orientative. */
    static final Object[][] KNOWN = {
            {"Motor (inyección)",            0x6A8, 0x688},
            {"ABS / ESP",                    0x6AD, 0x68D},
            {"Caja automática / pilotada",   0x6A9, 0x689},
            {"Dirección asistida",           0x6B5, 0x695},
            {"Faros direccionales",          0x6B6, 0x696},
            {"BSI (caja de servicio)",       0x752, 0x652},
            {"Airbag",                       0x772, 0x672},
            {"Columna de mandos (COM2000)",  0x75D, 0x65D},
            {"Climatización",                0x76D, 0x66D},
            {"Radio / Multimedia",           0x764, 0x664},
            {"Cuadro de instrumentos",       0x75F, 0x65F},
            {"Ayuda al aparcamiento",        0x76E, 0x66E},
            {"Freno de estacionamiento",     0x6A5, 0x685},
    };

    private final Elm327 elm;
    private final Elm327.Logger log;

    public PsaDiag(Elm327 elm, Elm327.Logger log) { this.elm = elm; this.log = log; }

    static String nameFor(int tx) {
        for (Object[] k : KNOWN) if ((Integer) k[1] == tx) return (String) k[0];
        return String.format(Locale.US, "Centralita %03X", tx);
    }

    // ------------------------------------------------------------------ scan

    public List<Ecu> scan(boolean full, Progress p) throws IOException {
        List<int[]> cand = new ArrayList<>();
        if (full) {
            for (int tx = 0x6A0; tx <= 0x6BF; tx++) cand.add(new int[]{tx, tx - 0x20});
            for (int tx = 0x740; tx <= 0x77F; tx++) cand.add(new int[]{tx, tx - 0x100});
        } else {
            for (Object[] k : KNOWN) cand.add(new int[]{(Integer) k[1], (Integer) k[2]});
        }

        List<Ecu> found = new ArrayList<>();
        elm.setTimeoutShort();
        int total = cand.size() + 1;
        for (int i = 0; i < cand.size(); i++) {
            int tx = cand.get(i)[0], rx = cand.get(i)[1];
            String nm = nameFor(tx);
            if (p != null) p.update(i, total, nm);
            Ecu e = new Ecu(nm, tx, rx);
            if (ping(e, !full)) {
                found.add(e);
                log.log("✔ Responde: " + nm + String.format(Locale.US, " (%03X)", tx));
            }
        }

        // Generic OBD-II engine (always useful as a backup for the engine light)
        if (p != null) p.update(total - 1, total, "Motor OBD-II genérico");
        Ecu obd = new Ecu("Motor (OBD-II genérico)", 0x7DF, 0x7E8);
        obd.protocol = Ecu.PROTO_OBD;
        elm.setTimeoutNormal();
        elm.setTarget(obd.tx, obd.rx, obd.flowControlId());
        byte[] r = elm.request("0100", 3000);
        if (ok(r, 0x41)) found.add(obd);

        if (p != null) p.update(total, total, "");
        return found;
    }

    private boolean ping(Ecu e, boolean tryHarder) throws IOException {
        elm.setTarget(e.tx, e.rx, e.flowControlId());
        byte[] r = elm.request("3E00", 1500);
        if (r != null) return true;
        if (tryHarder) {
            r = elm.request("10C0", 1500);
            if (r != null) {
                if (ok(r, 0x50)) e.protocol = Ecu.PROTO_KWP;
                return true;
            }
            r = elm.request("1003", 1500);
            if (r != null) {
                if (ok(r, 0x50)) e.protocol = Ecu.PROTO_UDS;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ read

    public void readDtcs(Ecu e) throws IOException {
        e.dtcs.clear();
        elm.setTimeoutNormal();
        if (e.isGeneric()) { readGeneric(e); return; }

        elm.setTarget(e.tx, e.rx, e.flowControlId());
        openSession(e);

        List<Dtc> list = null;
        if (e.protocol != Ecu.PROTO_KWP) {
            list = readUds();
            if (list != null) e.protocol = Ecu.PROTO_UDS;
        }
        if (list == null) {
            list = readKwp();
            if (list != null) e.protocol = Ecu.PROTO_KWP;
        }
        if (list == null && e.protocol == Ecu.PROTO_KWP) {
            list = readUds();
            if (list != null) e.protocol = Ecu.PROTO_UDS;
        }

        e.read = true;
        if (list == null) {
            e.status = "No contestó a la petición de averías";
        } else {
            e.dtcs.addAll(list);
            e.status = list.isEmpty() ? "Sin averías" : list.size() + " avería(s)";
        }
        closeSession(e);
    }

    private List<Dtc> readUds() throws IOException {
        for (String req : new String[]{"1902FF", "19020D", "190209"}) {
            byte[] r = elm.request(req, 3000);
            if (ok(r, 0x59)) return parseGroups(r, 3, 4, true);
            if (isNrc(r, 0x19, 0x11)) return null;   // service not supported
        }
        return null;
    }

    private List<Dtc> readKwp() throws IOException {
        byte[] r = elm.request("1800FF00", 3000);
        if (ok(r, 0x58)) return parseGroups(r, 2, 3, false);
        r = elm.request("17FF00", 3000);
        if (ok(r, 0x57)) return parseGroups(r, 2, 3, false);
        r = elm.request("18FF00", 3000);
        if (ok(r, 0x58)) return parseGroups(r, 2, 3, false);
        return null;
    }

    private void readGeneric(Ecu e) throws IOException {
        elm.setTarget(e.tx, e.rx, e.flowControlId());
        List<Dtc> list = new ArrayList<>();
        byte[] r = elm.request("03", 4000);
        boolean any = false;
        if (ok(r, 0x43)) { any = true; list.addAll(parseObd(r, 0x08)); }
        r = elm.request("07", 4000);
        if (ok(r, 0x47)) { any = true; list.addAll(parseObd(r, 0x04)); }
        e.read = true;
        if (!any) { e.status = "No contestó"; return; }
        e.dtcs.addAll(list);
        e.status = list.isEmpty() ? "Sin averías" : list.size() + " avería(s)";
    }

    private static List<Dtc> parseObd(byte[] r, int status) {
        List<Dtc> l = new ArrayList<>();
        int start = (r.length - 1) % 2 == 0 ? 1 : 2;   // CAN replies carry a count byte
        for (int i = start; i + 1 < r.length; i += 2) {
            int hi = r[i] & 0xFF, lo = r[i + 1] & 0xFF;
            if (hi == 0 && lo == 0) continue;
            l.add(new Dtc(hi, lo, -1, status));
        }
        return l;
    }

    private static List<Dtc> parseGroups(byte[] r, int start, int size, boolean uds) {
        List<Dtc> l = new ArrayList<>();
        for (int i = start; i + size <= r.length; i += size) {
            int hi = r[i] & 0xFF, lo = r[i + 1] & 0xFF;
            if (uds) {
                int ft = r[i + 2] & 0xFF, st = r[i + 3] & 0xFF;
                if (hi == 0 && lo == 0 && ft == 0) continue;
                l.add(new Dtc(hi, lo, ft, st));
            } else {
                int st = r[i + 2] & 0xFF;
                if (hi == 0 && lo == 0) continue;
                l.add(new Dtc(hi, lo, -1, st));
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ clear

    public boolean clearDtcs(Ecu e) throws IOException {
        elm.setTimeoutLong();
        try {
            if (e.isGeneric()) {
                elm.setTarget(e.tx, e.rx, e.flowControlId());
                byte[] r = elm.request("04", 6000);
                boolean good = ok(r, 0x44);
                e.status = good ? "Averías borradas" : "No aceptó el borrado";
                return good;
            }
            elm.setTarget(e.tx, e.rx, e.flowControlId());
            openSession(e);
            String[] reqs = e.protocol == Ecu.PROTO_KWP
                    ? new String[]{"14FF00", "14FFFF", "14FFFFFF"}
                    : new String[]{"14FFFFFF", "14FF00", "14FFFF"};
            boolean good = false;
            for (String q : reqs) {
                byte[] r = elm.request(q, 8000);
                if (ok(r, 0x54)) { good = true; break; }
                if (r != null && (r[0] & 0xFF) == 0x7F) {
                    log.log("  rechazo " + nrcText(r));
                }
            }
            e.status = good ? "Averías borradas" : "No aceptó el borrado";
            closeSession(e);
            return good;
        } finally {
            elm.setTimeoutNormal();
        }
    }

    // ------------------------------------------------------------------ sessions

    private void openSession(Ecu e) throws IOException {
        if (e.protocol == Ecu.PROTO_UDS) {
            if (ok(elm.request("1003", 2000), 0x50)) return;
        } else if (e.protocol == Ecu.PROTO_KWP) {
            if (ok(elm.request("10C0", 2000), 0x50)) return;
        }
        if (ok(elm.request("1003", 2000), 0x50)) { e.protocol = Ecu.PROTO_UDS; return; }
        if (ok(elm.request("10C0", 2000), 0x50)) { e.protocol = Ecu.PROTO_KWP; return; }
        elm.request("1081", 2000);
    }

    private void closeSession(Ecu e) throws IOException {
        if (e.protocol == Ecu.PROTO_UDS) elm.request("1001", 1500);
        else if (e.protocol == Ecu.PROTO_KWP) elm.request("1081", 1500);
    }

    // ------------------------------------------------------------------ helpers

    static boolean ok(byte[] r, int sid) {
        return r != null && r.length > 0 && (r[0] & 0xFF) == sid;
    }

    static boolean isNrc(byte[] r, int sid, int code) {
        return r != null && r.length >= 3 && (r[0] & 0xFF) == 0x7F
                && (r[1] & 0xFF) == sid && (r[2] & 0xFF) == code;
    }

    static String nrcText(byte[] r) {
        if (r.length < 3) return "";
        int c = r[2] & 0xFF;
        String t;
        switch (c) {
            case 0x11: t = "servicio no admitido"; break;
            case 0x12: t = "subfunción no admitida"; break;
            case 0x22: t = "condiciones no correctas (¿motor arrancado? pon solo contacto)"; break;
            case 0x31: t = "fuera de rango"; break;
            case 0x33: t = "acceso de seguridad denegado"; break;
            case 0x7F: t = "no admitido en esta sesión"; break;
            default:   t = "código " + String.format(Locale.US, "%02X", c);
        }
        return t;
    }
}
