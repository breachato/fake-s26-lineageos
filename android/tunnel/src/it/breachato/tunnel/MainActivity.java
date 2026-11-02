package it.breachato.tunnel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Control panel for breachato-tunneld (root daemon): shows Wi-Fi / MAC / VPN server, sends
 * commands through a file, and connects to Wi-Fi networks. Talks to the daemon only through
 * files in this app's device-protected storage: "cmd" (written here) and "status" (read here).
 */
public class MainActivity extends Activity {
    private static final int BG = Color.BLACK, CARD = Color.rgb(28, 28, 30), DIM = Color.rgb(142, 142, 147);

    private final Handler h = new Handler(Looper.getMainLooper());
    private File dir;
    private WifiManager wifi;
    private float dp;
    private TextView net, mac, ip, server, pub;
    private Switch rotate;
    private LinearLayout networks;
    private boolean updatingSwitch;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        dir = createDeviceProtectedStorageContext().getFilesDir();
        dir.setExecutable(true, false);
        wifi = getSystemService(WifiManager.class);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        h.post(poll);
        scan();
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            showStatus(readStatus());
            h.postDelayed(this, 1000);
        }
    };

    // --- daemon IPC --------------------------------------------------------------------
    private Map<String, String> readStatus() {
        Map<String, String> m = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(new File(dir, "status")))) {
            String line;
            while ((line = r.readLine()) != null) {
                int k = line.indexOf('=');
                if (k > 0) m.put(line.substring(0, k), line.substring(k + 1));
            }
        } catch (Exception ignored) { }
        return m;
    }

    private void send(String cmd) {
        try (FileOutputStream o = new FileOutputStream(new File(dir, "cmd"))) {
            o.write(cmd.getBytes("UTF-8"));
        } catch (Exception e) {
            toast("Tunnel service unreachable");
        }
    }

    private void showStatus(Map<String, String> s) {
        if (s.isEmpty()) {
            net.setText("Tunnel service not running");
            return;
        }
        String ssid = wifi.getConnectionInfo() != null ? wifi.getConnectionInfo().getSSID() : null;
        boolean up = "up".equals(s.get("wifi"));
        net.setText(up && ssid != null && !ssid.contains("unknown ssid")
                ? "Connected to " + ssid.replace("\"", "") : "Wi-Fi not connected");
        mac.setText("MAC  " + val(s, "mac"));
        ip.setText("Local IP  " + val(s, "ip"));
        server.setText("VPN server  " + val(s, "tunnel"));
        updatingSwitch = true;
        rotate.setChecked(!"off".equals(s.get("rotate")));
        updatingSwitch = false;
    }

    private static String val(Map<String, String> s, String k) {
        String v = s.get(k);
        return v == null || v.isEmpty() ? "—" : v;
    }

    // --- Wi-Fi -------------------------------------------------------------------------
    private void scan() {
        wifi.startScan();
        h.postDelayed(new Runnable() { @Override public void run() { listNetworks(); } }, 2500);
    }

    private void listNetworks() {
        networks.removeAllViews();
        List<ScanResult> res = new ArrayList<>(wifi.getScanResults());
        Collections.sort(res, new Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) { return b.level - a.level; }
        });
        Set<String> seen = new HashSet<>();
        for (final ScanResult r : res) {
            if (r.SSID == null || r.SSID.isEmpty() || !seen.add(r.SSID)) continue;
            final boolean open = !(r.capabilities.contains("WPA") || r.capabilities.contains("WEP")
                    || r.capabilities.contains("SAE"));
            TextView t = row(r.SSID + (open ? "" : "  🔒"), 17, Color.WHITE);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { askAndConnect(r.SSID, open); }
            });
            networks.addView(t);
        }
        if (networks.getChildCount() == 0) networks.addView(row("No networks found", 15, DIM));
    }

    private void askAndConnect(final String ssid, boolean open) {
        Integer saved = savedId(ssid);
        if (saved != null) { enable(saved); return; }
        if (open) { connect(ssid, null); return; }
        final EditText pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(ssid).setView(pass)
                .setPositiveButton("Connect", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        connect(ssid, pass.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    @SuppressWarnings("deprecation")
    private Integer savedId(String ssid) {
        for (WifiConfiguration c : wifi.getConfiguredNetworks()) {
            if (("\"" + ssid + "\"").equals(c.SSID)) return c.networkId;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private void connect(String ssid, String password) {
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = "\"" + ssid + "\"";
        if (password == null) {
            c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            c.preSharedKey = "\"" + password + "\"";
        }
        int id = wifi.addNetwork(c);
        if (id < 0) { toast("Could not save the network"); return; }
        enable(id);
    }

    @SuppressWarnings("deprecation")
    private void enable(int id) {
        wifi.enableNetwork(id, true);
        toast("Connecting…");
    }

    // --- public IP (through the tunnel) -------------------------------------------------
    private void checkPublicIp() {
        pub.setText("Public IP  …");
        new Thread(new Runnable() {
            @Override public void run() {
                String r;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    r = new BufferedReader(new InputStreamReader(c.getInputStream())).readLine();
                } catch (Exception e) {
                    r = "no connection";
                }
                final String text = "Public IP  " + r;
                h.post(new Runnable() { @Override public void run() { pub.setText(text); } });
            }
        }).start();
    }

    // --- UI ----------------------------------------------------------------------------
    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(36), px(20), px(20));
        root.setBackgroundColor(BG);

        root.addView(row("Tunnel", 32, Color.WHITE));
        LinearLayout card = card();
        net = row("", 18, Color.WHITE);
        mac = row("", 15, DIM);
        ip = row("", 15, DIM);
        server = row("", 15, DIM);
        pub = row("Public IP  tap Check", 15, DIM);
        card.addView(net); card.addView(mac); card.addView(ip); card.addView(server); card.addView(pub);
        root.addView(card);

        root.addView(button("New identity  (new MAC and server)", "new_identity"));
        root.addView(button("Change server", "next_tunnel"));
        TextView check = button("Check public IP", null);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkPublicIp(); }
        });
        root.addView(check);

        rotate = new Switch(this);
        rotate.setText("Different server on every connection");
        rotate.setTextColor(Color.WHITE);
        rotate.setTextSize(15);
        rotate.setPadding(px(4), px(14), px(4), px(14));
        rotate.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (!updatingSwitch) send(on ? "rotate:on" : "rotate:off");
            }
        });
        root.addView(rotate);

        TextView title = row("Wi-Fi networks", 20, Color.WHITE);
        title.setPadding(0, px(18), 0, px(6));
        root.addView(title);
        TextView refresh = button("Refresh list", null);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scan(); }
        });
        root.addView(refresh);
        networks = card();
        root.addView(networks);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        return sv;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(12), px(16), px(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(px(16));
        l.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(10), 0, px(10));
        l.setLayoutParams(lp);
        return l;
    }

    private TextView button(String label, final String cmd) {
        TextView t = row(label, 16, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(14), px(12), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(44, 44, 46));
        bg.setCornerRadius(px(14));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(6), 0, px(6));
        t.setLayoutParams(lp);
        if (cmd != null) {
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { send(cmd); toast("Done"); }
            });
        }
        return t;
    }

    private TextView row(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setPadding(0, px(5), 0, px(5));
        return t;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int px(int v) { return Math.round(v * dp); }
}
