package com.codegarage.c4diag;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Driver for ELM327 (and compatible) adapters over Bluetooth classic (SPP).
 * Works in CAN 11-bit 500 kbit/s (ATSP6), which is what PSA cars of 2010-2013 use
 * on the diagnostic socket.
 */
@SuppressLint("MissingPermission")
public class Elm327 {

    public interface Logger { void log(String s); }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Logger logger;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    private int curTx = -1, curRx = -1;
    private String curTimeout = "";
    public String version = "";
    public boolean flowControlOk = true;

    public Elm327(Logger logger) { this.logger = logger; }

    public boolean isConnected() { return socket != null && socket.isConnected(); }

    public void connect(BluetoothDevice dev) throws IOException {
        close();
        IOException last = null;
        // 1) standard secure SPP
        try {
            socket = dev.createRfcommSocketToServiceRecord(SPP);
            socket.connect();
        } catch (IOException e) {
            last = e;
            safeClose();
            // 2) insecure SPP (many cheap clones)
            try {
                socket = dev.createInsecureRfcommSocketToServiceRecord(SPP);
                socket.connect();
                last = null;
            } catch (IOException e2) {
                last = e2;
                safeClose();
                // 3) channel 1 via reflection (last resort for clones)
                try {
                    Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                    socket = (BluetoothSocket) m.invoke(dev, 1);
                    socket.connect();
                    last = null;
                } catch (Exception e3) {
                    safeClose();
                }
            }
        }
        if (socket == null || last != null && !socket.isConnected()) {
            throw new IOException("No se pudo conectar al adaptador: " + (last != null ? last.getMessage() : "?"));
        }
        in = socket.getInputStream();
        out = socket.getOutputStream();
        curTx = -1; curRx = -1; curTimeout = "";
    }

    private void safeClose() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        socket = null;
    }

    public void close() {
        safeClose();
        in = null; out = null;
    }

    /** Initialise the adapter for PSA CAN diagnostics. */
    public void init() throws IOException {
        String z = send("ATZ", 4000);
        try { Thread.sleep(500); } catch (InterruptedException ignored) { }
        version = z.replace("\r", " ").replace("\n", " ").trim();
        send("ATE0", 1500);
        send("ATL0", 1500);
        send("ATS0", 1500);
        send("ATH0", 1500);
        send("ATAT1", 1500);
        send("ATSP6", 1500);   // ISO 15765-4 CAN 11 bit 500 kbaud
        send("ATCAF1", 1500);
        setTimeoutNormal();
        String v = send("ATI", 1500);
        if (v.length() > 0) version = v.trim();
        String volt = send("ATRV", 1500);
        logger.log("Adaptador: " + version + "  ·  Batería: " + volt.trim());
    }

    public void setTimeoutShort() throws IOException { setTimeout("20"); }   // ~128 ms
    public void setTimeoutNormal() throws IOException { setTimeout("64"); }  // ~400 ms
    public void setTimeoutLong() throws IOException { setTimeout("FF"); }    // ~1 s

    private void setTimeout(String hex) throws IOException {
        if (hex.equals(curTimeout)) return;
        send("ATST" + hex, 1500);
        curTimeout = hex;
    }

    /** Point the adapter at one ECU: requests go to tx, only replies from rx are accepted. */
    public void setTarget(int tx, int rx, int flowControlId) throws IOException {
        if (tx == curTx && rx == curRx) return;
        String txs = String.format(Locale.US, "%03X", tx);
        String rxs = String.format(Locale.US, "%03X", rx);
        String fcs = String.format(Locale.US, "%03X", flowControlId);
        send("ATSH" + txs, 1500);
        send("ATCRA" + rxs, 1500);
        String a = send("ATFCSH" + fcs, 1500);
        String b = send("ATFCSD300000", 1500);
        String c = send("ATFCSM1", 1500);
        if (a.contains("?") || b.contains("?") || c.contains("?")) {
            if (flowControlOk) logger.log("AVISO: el adaptador no admite control de flujo (ATFCSH). Las respuestas largas pueden fallar.");
            flowControlOk = false;
        }
        curTx = tx; curRx = rx;
    }

    public String send(String cmd, long timeoutMs) throws IOException {
        if (out == null) throw new IOException("Adaptador no conectado");
        while (in.available() > 0) in.read();
        out.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String r = readUntilPrompt(timeoutMs);
        logger.log("> " + cmd + "   < " + r.replace("\r", " ").replace("\n", " ").trim());
        return r;
    }

    private String readUntilPrompt(long timeoutMs) throws IOException {
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (in.available() > 0) {
                int c = in.read();
                if (c < 0) throw new IOException("El adaptador cerró la conexión");
                if (c == '>') return sb.toString();
                if (c != 0) sb.append((char) c);
            } else {
                try { Thread.sleep(4); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logger.log("(sin '>' tras " + timeoutMs + " ms)");
        return sb.toString();
    }

    /**
     * Send a diagnostic request (hex string, no spaces) and return the payload bytes of the
     * reply (PCI already removed by the ELM in CAF mode). Returns null when nothing came back.
     * "Response pending" frames (7F xx 78) are skipped.
     */
    public byte[] request(String hex, long timeoutMs) throws IOException {
        String raw = send(hex, timeoutMs);
        return parse(raw);
    }

    static byte[] parse(String raw) {
        String[] parts = raw.toUpperCase(Locale.US).split("[\\r\\n]+");
        List<String> lines = new ArrayList<>();
        for (String p : parts) {
            String l = p.trim().replace(" ", "");
            if (l.isEmpty()) continue;
            if (l.startsWith("SEARCHING") || l.startsWith("BUSINIT") || l.equals("OK")) continue;
            if (l.contains("NODATA") || l.contains("ERROR") || l.contains("STOPPED")
                    || l.contains("UNABLE") || l.contains("BUFFERFULL") || l.equals("?")) {
                continue;
            }
            if (l.matches("^7F[0-9A-F]{2}78$")) continue;   // response pending
            lines.add(l);
        }
        if (lines.isEmpty()) return null;

        boolean multi = false;
        for (String l : lines) if (l.matches("^[0-9A-F]:.*")) { multi = true; break; }

        String data;
        if (multi) {
            int len = -1;
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                if (l.matches("^[0-9A-F]:.*")) {
                    sb.append(l.substring(2));
                } else if (len < 0 && l.matches("^[0-9A-F]{1,3}$")) {
                    len = Integer.parseInt(l, 16);
                }
            }
            data = sb.toString().replaceAll("[^0-9A-F]", "");
            if (len > 0 && data.length() > len * 2) data = data.substring(0, len * 2);
        } else {
            data = lines.get(0).replaceAll("[^0-9A-F]", "");
        }
        if (data.length() < 2) return null;
        if (data.length() % 2 == 1) data = data.substring(0, data.length() - 1);
        byte[] b = new byte[data.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(data.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
