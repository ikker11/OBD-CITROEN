package com.codegarage.c4diag;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    static final int BG = 0xFF0E1116, CARD = 0xFF1A1F27, ACCENT = 0xFFE4572E, GREEN = 0xFF3FB950,
            YELLOW = 0xFFF2B134, RED = 0xFFFF5555, TXT = 0xFFE6E6E6, SUB = 0xFF9AA4B2, BTN = 0xFF2A313C;

    interface Job { void run() throws Exception; }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Ecu> ecus = new ArrayList<>();
    private final List<View> actions = new ArrayList<>();
    private final StringBuilder logBuf = new StringBuilder();

    private Elm327 elm;
    private PsaDiag diag;
    private TextView status, logView, progressText;
    private ProgressBar progress;
    private LinearLayout ecuBox;
    private boolean busy = false;
    private boolean logScheduled = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Elm327.Logger logger = this::log;
        elm = new Elm327(logger);
        diag = new PsaDiag(elm, logger);
        buildUi();
        renderEcus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.execute(() -> elm.close());
        worker.shutdown();
    }

    // ================================================================ UI

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("C4 Picasso · Diagnosis", 24, TXT, true);
        root.addView(title);
        TextView sub = text("Lectura y borrado de averías de todas las centralitas (ELM327 Bluetooth)", 13, SUB, false);
        root.addView(sub);

        status = text("Desconectado", 15, YELLOW, true);
        status.setPadding(0, dp(12), 0, dp(4));
        root.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        progressText = text("", 12, SUB, false);
        root.addView(progressText);

        root.addView(button("Conectar ELM327", ACCENT, v -> onConnect(), false));

        LinearLayout row = hrow();
        row.addView(button("Buscar centralitas", BTN, v -> scan(false), true), weight());
        row.addView(button("Búsqueda a fondo", BTN, v -> scan(true), true), weight());
        root.addView(row);

        LinearLayout row2 = hrow();
        row2.addView(button("Leer todas", BTN, v -> readAll(), true), weight());
        row2.addView(button("Borrar todas", RED, v -> confirmClearAll(), true), weight());
        root.addView(row2);

        TextView hint = text("Pon el contacto SIN arrancar el motor para borrar. "
                + "Si una avería vuelve a salir al momento, es que el fallo sigue ahí (sensor, cable…).", 12, SUB, false);
        hint.setPadding(0, dp(8), 0, dp(8));
        root.addView(hint);

        ecuBox = new LinearLayout(this);
        ecuBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(ecuBox);

        LinearLayout logHead = hrow();
        logHead.addView(button("Ver registro", BTN, v -> {
            boolean show = logView.getVisibility() != View.VISIBLE;
            logView.setVisibility(show ? View.VISIBLE : View.GONE);
            ((Button) v).setText(show ? "Ocultar registro" : "Ver registro");
        }, false), weight());
        logHead.addView(button("Copiar", BTN, v -> copyLog(), false), weight());
        logHead.setPadding(0, dp(16), 0, 0);
        root.addView(logHead);

        logView = text("", 11, SUB, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setBackground(round(CARD));
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setVisibility(View.GONE);
        root.addView(logView);

        setContentView(scroll);
        updateButtons();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private Button button(String label, int color, View.OnClickListener l, boolean needsConnection) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(TXT);
        btn.setTextSize(14);
        btn.setMinHeight(dp(48));
        btn.setMinimumHeight(dp(48));
        btn.setPadding(dp(8), dp(6), dp(8), dp(6));
        btn.setBackground(round(color));
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        btn.setLayoutParams(lp);
        if (needsConnection) actions.add(btn);
        return btn;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBaselineAligned(false);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        return lp;
    }

    private void updateButtons() {
        boolean en = !busy && elm.isConnected();
        for (View v : actions) {
            v.setEnabled(en);
            v.setAlpha(en ? 1f : 0.4f);
        }
    }

    private void renderEcus() {
        updateButtons();
        ecuBox.removeAllViews();
        if (ecus.isEmpty()) {
            TextView t = text(elm.isConnected()
                    ? "Pulsa «Buscar centralitas». Si falta alguna, usa «Búsqueda a fondo»."
                    : "1. Empareja el ELM327 en Ajustes › Bluetooth (PIN 1234 o 0000).\n"
                    + "2. Enchúfalo al OBD y pon el contacto.\n3. Pulsa «Conectar ELM327».", 14, SUB, false);
            t.setPadding(dp(4), dp(12), dp(4), dp(12));
            ecuBox.addView(t);
            return;
        }
        for (Ecu e : ecus) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(round(CARD));
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(6), 0, dp(6));
            card.setLayoutParams(lp);

            card.addView(text(e.name, 17, TXT, true));
            card.addView(text(e.ids(), 11, SUB, false));

            int col = !e.read ? SUB : (e.dtcs.isEmpty() ? GREEN : YELLOW);
            if (e.status.startsWith("No")) col = RED;
            TextView st = text(e.status, 14, col, true);
            st.setPadding(0, dp(6), 0, dp(4));
            card.addView(st);

            for (Dtc d : e.dtcs) {
                TextView dt = text("• " + d.describe(), 14, d.isActive() ? RED : TXT, false);
                dt.setTextIsSelectable(true);
                dt.setPadding(0, dp(2), 0, dp(2));
                card.addView(dt);
            }

            LinearLayout r = hrow();
            Button rd = button("Leer", BTN, v -> run("Leyendo " + e.name, () -> diag.readDtcs(e)), true);
            Button cl = button("Borrar", RED, v -> confirmClear(e), true);
            r.addView(rd, weight());
            r.addView(cl, weight());
            card.addView(r);
            ecuBox.addView(card);
        }
        updateButtons();
    }

    // ================================================================ actions

    private void run(String label, Job job) {
        if (busy) { toast("Espera a que termine la operación en curso"); return; }
        busy = true;
        updateButtons();
        status.setText(label + "…");
        status.setTextColor(YELLOW);
        worker.execute(() -> {
            String err = null;
            try {
                job.run();
            } catch (Exception ex) {
                err = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                log("ERROR: " + err);
            }
            final String fErr = err;
            ui.post(() -> {
                busy = false;
                progress.setVisibility(View.GONE);
                progressText.setText("");
                if (fErr != null) {
                    status.setText("Error: " + fErr);
                    status.setTextColor(RED);
                } else if (elm.isConnected()) {
                    status.setText("Conectado · " + elm.version);
                    status.setTextColor(GREEN);
                } else {
                    status.setText("Desconectado");
                    status.setTextColor(YELLOW);
                }
                renderEcus();
            });
        });
    }

    private void onConnect() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) { toast("Este dispositivo no tiene Bluetooth"); return; }
        if (!ad.isEnabled()) { toast("Activa el Bluetooth primero"); return; }
        Set<BluetoothDevice> bonded = ad.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            toast("No hay dispositivos emparejados. Empareja el ELM327 en Ajustes › Bluetooth.");
            return;
        }
        final List<BluetoothDevice> devs = new ArrayList<>(bonded);
        SharedPreferences prefs = getSharedPreferences("c4diag", Context.MODE_PRIVATE);
        String last = prefs.getString("last", "");
        String[] names = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) {
            BluetoothDevice d = devs.get(i);
            String n = d.getName() != null ? d.getName() : "(sin nombre)";
            names[i] = (d.getAddress().equals(last) ? "★ " : "") + n + "\n" + d.getAddress();
        }
        new AlertDialog.Builder(this)
                .setTitle("Elige el adaptador OBD")
                .setItems(names, (dlg, which) -> {
                    BluetoothDevice d = devs.get(which);
                    prefs.edit().putString("last", d.getAddress()).apply();
                    run("Conectando", () -> {
                        elm.connect(d);
                        elm.init();
                    });
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) onConnect();
        else toast("Sin permiso de Bluetooth no puedo conectar con el adaptador");
    }

    private void scan(boolean full) {
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        run(full ? "Escaneo completo (≈1 min)" : "Buscando centralitas", () -> {
            List<Ecu> found = diag.scan(full, this::showProgress);
            ui.post(() -> {
                ecus.clear();
                ecus.addAll(found);
            });
            log("Centralitas encontradas: " + found.size());
        });
    }

    private void showProgress(int done, int total, String what) {
        ui.post(() -> {
            progress.setVisibility(View.VISIBLE);
            progress.setMax(total);
            progress.setProgress(done);
            progressText.setText(what.isEmpty() ? "" : "Probando: " + what);
        });
    }

    private void readAll() {
        final List<Ecu> snapshot = new ArrayList<>(ecus);
        progress.setVisibility(View.VISIBLE);
        run("Leyendo averías", () -> {
            List<Ecu> list = snapshot;
            if (list.isEmpty()) {
                list = diag.scan(false, this::showProgress);
                final List<Ecu> f = list;
                ui.post(() -> { ecus.clear(); ecus.addAll(f); });
            }
            for (int i = 0; i < list.size(); i++) {
                showProgress(i, list.size(), list.get(i).name);
                diag.readDtcs(list.get(i));
            }
        });
    }

    private void confirmClearAll() {
        if (ecus.isEmpty()) { toast("Primero haz un escaneo"); return; }
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar averías de TODAS las centralitas?")
                .setMessage("Apunta antes los códigos si los necesitas: una vez borrados no se pueden recuperar.\n\n"
                        + "Contacto puesto y motor apagado.")
                .setPositiveButton("Borrar todo", (d, w) -> {
                    final List<Ecu> snapshot = new ArrayList<>(ecus);
                    progress.setVisibility(View.VISIBLE);
                    run("Borrando averías", () -> {
                        int okCount = 0;
                        for (int i = 0; i < snapshot.size(); i++) {
                            Ecu e = snapshot.get(i);
                            showProgress(i, snapshot.size() * 2, "borrar " + e.name);
                            if (diag.clearDtcs(e)) okCount++;
                        }
                        Thread.sleep(1500);
                        for (int i = 0; i < snapshot.size(); i++) {
                            Ecu e = snapshot.get(i);
                            showProgress(snapshot.size() + i, snapshot.size() * 2, "comprobar " + e.name);
                            String st = e.status;
                            diag.readDtcs(e);
                            if (st.equals("No aceptó el borrado")) e.status = st + " · " + e.status;
                        }
                        log("Borrado aceptado por " + okCount + " de " + snapshot.size() + " centralitas");
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmClear(Ecu e) {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar averías de " + e.name + "?")
                .setMessage("Contacto puesto y motor apagado.")
                .setPositiveButton("Borrar", (d, w) -> run("Borrando " + e.name, () -> {
                    boolean good = diag.clearDtcs(e);
                    Thread.sleep(1000);
                    diag.readDtcs(e);
                    if (!good) e.status = "No aceptó el borrado · " + e.status;
                    else if (!e.dtcs.isEmpty()) e.status = "Borradas, pero han vuelto a aparecer: el fallo sigue presente";
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ================================================================ log

    private void log(String s) {
        synchronized (logBuf) {
            logBuf.append(s).append('\n');
            if (logBuf.length() > 30000) logBuf.delete(0, logBuf.length() - 25000);
            if (logScheduled) return;
            logScheduled = true;
        }
        ui.postDelayed(() -> {
            String t;
            synchronized (logBuf) {
                logScheduled = false;
                t = logBuf.length() > 6000 ? logBuf.substring(logBuf.length() - 6000) : logBuf.toString();
            }
            logView.setText(t);
        }, 200);
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String t;
        synchronized (logBuf) { t = logBuf.toString(); }
        cm.setPrimaryClip(ClipData.newPlainText("C4 Diag log", t));
        toast("Registro copiado");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
