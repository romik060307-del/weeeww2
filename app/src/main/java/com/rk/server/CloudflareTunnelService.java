package com.rk.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the Android-ABI cloudflared executable packaged in this APK. */
public class CloudflareTunnelService extends Service {
    public static final String ACTION_START_QUICK = "com.rk.server.TUNNEL_START_QUICK";
    public static final String ACTION_START_NAMED = "com.rk.server.TUNNEL_START_NAMED";
    public static final String ACTION_STOP = "com.rk.server.TUNNEL_STOP";
    public static final String EXTRA_TOKEN = "tunnel_token";
    public static final String PREFS = "ohli_local_admin";
    public static final String PREF_ENABLED = "cloudflare_tunnel_enabled";
    public static final String PREF_STATE = "cloudflare_tunnel_state";
    public static final String PREF_URL = "public_tunnel_url";
    public static final String PREF_ERROR = "cloudflare_tunnel_error";
    private static final String CHANNEL = "ohli_cloudflare_tunnel";
    private volatile boolean stopRequested;
    private volatile Process tunnelProcess;
    private Thread tunnelThread;

    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START_QUICK : intent.getAction();
        if (intent == null && !getPrefs().getBoolean(PREF_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopRequested = true;
            getPrefs().edit().putBoolean(PREF_ENABLED, false).putString(PREF_STATE, "stopped").putString(PREF_URL, "").apply();
            stopProcess();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (tunnelThread != null && tunnelThread.isAlive()) return START_STICKY;
        stopRequested = false;
        boolean named = ACTION_START_NAMED.equals(action);
        String token = named && intent != null ? intent.getStringExtra(EXTRA_TOKEN) : null;
        if (named && (token == null || token.trim().length() < 20)) {
            getPrefs().edit().putString(PREF_STATE, "error").putString(PREF_ERROR, "Token Named Tunnel tidak valid.").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        getPrefs().edit().putBoolean(PREF_ENABLED, true).putString(PREF_STATE, "starting")
                .putString(PREF_URL, "").putString(PREF_ERROR, "").apply();
        startForeground(4102, buildNotification("Menyiapkan engine Cloudflare Android…"));
        final boolean namedMode = named;
        final String namedToken = token;
        tunnelThread = new Thread(new Runnable() {
            @Override public void run() { runTunnel(namedMode, namedToken); }
        }, "ohli-cloudflared");
        tunnelThread.start();
        return START_STICKY;
    }

    private SharedPreferences getPrefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void runTunnel(boolean named, String token) {
        int attempt = 0;
        while (!stopRequested) {
            attempt++;
            try {
                File binary = packagedBinary();
                getPrefs().edit().putString(PREF_STATE, "connecting").putString(PREF_ERROR, "").apply();
                ProcessBuilder builder;
                if (named) {
                    builder = new ProcessBuilder(binary.getAbsolutePath(), "tunnel", "--no-autoupdate", "run", "--token", token);
                } else {
                    builder = new ProcessBuilder(binary.getAbsolutePath(), "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:" + ServerService.PORT);
                }
                builder.redirectErrorStream(true);
                builder.environment().put("HOME", getFilesDir().getAbsolutePath());
                builder.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
                // Use Android's libc resolver instead of relying on a Linux /etc/resolv.conf.
                builder.environment().put("GODEBUG", "netdns=cgo");
                builder.environment().put("NO_COLOR", "1");
                tunnelProcess = builder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream(), "UTF-8"));
                String line;
                while (!stopRequested && (line = reader.readLine()) != null) {
                    Matcher m = Pattern.compile("https://[A-Za-z0-9-]+\\.trycloudflare\\.com").matcher(line);
                    if (m.find()) {
                        String publicUrl = m.group();
                        getPrefs().edit().putString(PREF_URL, "").putString(PREF_STATE, "verifying")
                                .putString(PREF_ERROR, "Memeriksa endpoint HTTPS publik /health sebelum link dibagikan…").apply();
                        updateNotification("Memverifikasi link publik OHLI…");
                        if (!stopRequested && verifyPublicHealth(publicUrl)) {
                            getPrefs().edit().putString(PREF_URL, publicUrl).putString(PREF_STATE, "online").putString(PREF_ERROR, "").apply();
                            updateNotification("Tunnel aktif dan terverifikasi · " + publicUrl);
                        } else if (!stopRequested) {
                            getPrefs().edit().putString(PREF_URL, "").putString(PREF_STATE, "error")
                                    .putString(PREF_ERROR, "Alamat tunnel terbentuk, tetapi endpoint publik /health belum terbukti dapat diakses. Pastikan server lokal tetap aktif dan koneksi internet stabil, lalu mulai ulang tunnel.").apply();
                            updateNotification("Tunnel belum lolos health-check publik");
                        }
                    }
                    String lower = line.toLowerCase(Locale.US);
                    if (lower.contains("failed") || lower.contains("error") || lower.contains("unable")) {
                        getPrefs().edit().putString(PREF_ERROR, trimError(line)).apply();
                    }
                }
                Process ended = tunnelProcess;
                int exit = ended == null ? -1 : ended.waitFor();
                tunnelProcess = null;
                if (!stopRequested) {
                    getPrefs().edit().putString(PREF_STATE, "reconnecting").putString(PREF_URL, "")
                            .putString(PREF_ERROR, "Tunnel terputus (kode " + exit + "). Mencoba sambung kembali.").apply();
                    updateNotification("Tunnel terputus · menyambung kembali");
                }
            } catch (Exception e) {
                String message = safeMessage(e);
                getPrefs().edit().putString(PREF_STATE, "error").putString(PREF_ERROR, message).apply();
                updateNotification("Tunnel gagal · buka panel OHLI");
                String lower = message.toLowerCase(Locale.US);
                if (lower.contains("belum disertakan") || lower.contains("permission denied") || lower.contains("exec format") || lower.contains("not executable")) break;
            }
            if (!stopRequested) {
                long delay = Math.min(60000L, 2000L * Math.min(attempt, 30));
                try { Thread.sleep(delay); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (!stopRequested) {
            getPrefs().edit().putBoolean(PREF_ENABLED, false).putString(PREF_STATE, "error").apply();
            stopForegroundCompat();
            stopSelf();
        }
    }

    /** A tunnel URL is not published as ONLINE until the public route reaches this OHLI host. */
    private boolean verifyPublicHealth(String publicUrl) {
        for (int attempt = 0; attempt < 8 && !stopRequested; attempt++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(publicUrl + "/health");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status == 200) {
                    InputStream input = connection.getInputStream();
                    BufferedReader body = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                    StringBuilder text = new StringBuilder();
                    String line;
                    while ((line = body.readLine()) != null && text.length() < 4096) text.append(line);
                    body.close();
                    String json = text.toString().replaceAll("\\s+", "");
                    if (json.contains("\"ok\":true") && json.contains("\"service\":\"OHLI Social Server\"")) return true;
                }
            } catch (Exception ignored) {
                // Cloudflare edge propagation can lag behind the first URL log line.
            } finally {
                if (connection != null) connection.disconnect();
            }
            if (attempt < 7 && !stopRequested) {
                try { Thread.sleep(1800L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }

    private File packagedBinary() throws Exception {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libcloudflared.so");
        if (!binary.isFile() || binary.length() < 1024) {
            throw new IllegalStateException("Engine Cloudflare Android belum disertakan untuk ABI " + abi + ". Bangun APK melalui workflow v3.6.27 yang mengompilasi cloudflared Android.");
        }
        if (!binary.canExecute()) {
            throw new IllegalStateException("Android tidak memberi izin eksekusi pada engine Cloudflare untuk ABI " + abi + ".");
        }
        return binary;
    }

    private static String trimError(String text) {
        if (text == null) return "Kesalahan tunnel tidak diketahui.";
        String s = text.trim();
        return s.length() > 240 ? s.substring(0, 240) : s;
    }

    private static String safeMessage(Throwable e) {
        String s = e == null ? null : e.getMessage();
        return s == null || s.trim().isEmpty() ? (e == null ? "Kesalahan tidak diketahui" : e.getClass().getSimpleName()) : s;
    }

    private void stopProcess() {
        Process p = tunnelProcess;
        tunnelProcess = null;
        if (p != null) try { p.destroy(); } catch (Exception ignored) { }
        Thread t = tunnelThread;
        if (t != null) t.interrupt();
        tunnelThread = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "OHLI Cloudflare Tunnel", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 4102, open, (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0) | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ohli_notification_icon).setContentTitle("OHLI · Cloudflare Tunnel").setContentText(text).setContentIntent(pi).setOngoing(true).build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(4102, buildNotification(text));
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
    }

    @Override public void onDestroy() {
        stopRequested = true;
        stopProcess();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
