package com.rk.server;

import android.app.Notification;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadPoolExecutor;

/** Lightweight RK Chat backend. Designed for Android API 19 and low-memory tablets. */
public class ServerService extends Service {
    public static final String ACTION_START = "com.rk.server.START";
    public static final String ACTION_STOP = "com.rk.server.STOP";
    private static final String PREFS = "ohli_server_service";
    private static final String PREF_ENABLED = "enabled_by_user";
    public static final int PORT = 8080;
    public static volatile boolean isRunning = false;
    public static volatile String lastError = "";
    private static volatile ServerService INSTANCE;

    private static final int MAX_MESSAGES = 500;
    private static final int MAX_CALL_SIGNALS = 100;
    private static final int MAX_STATUSES = 100;
    private static final int MAX_HEADER_BYTES = 16384;
    private static final int MAX_BODY_BYTES = 280 * 1024;
    private static final int MAX_IMAGE_BYTES = 180 * 1024;
    // Binary media uploads are streamed to disk rather than accumulated in RAM.
    private static final int MAX_MEDIA_BYTES = 1024 * 1024 * 1024;
    private static final long MAX_MEDIA_STORAGE_BYTES = 16384L * 1024L * 1024L;
    private static final int MAX_LIVE_SIGNALS = 1200;
    private static final int MAX_LIVE_COMMENTS = 1200;
    private static final int MAX_LIVE_SESSIONS = 100;
    private static final int MAX_AUTH_CHALLENGES = 500;
    private static final long AUTH_OTP_TTL_MS = 5L * 60L * 1000L;
    private static final long AUTH_REQUEST_COOLDOWN_MS = 60L * 1000L;
    private static final SecureRandom AUTH_RANDOM = new SecureRandom();
    private static final Object DATA_LOCK = new Object();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private volatile boolean stopRequested = false;
    private volatile boolean dataReady = false;
    private volatile boolean pendingServerStart = false;
    private final Object DATA_READY_LOCK = new Object();
    private Thread dataLoaderThread;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;
    private ThreadPoolExecutor workers;
    private final OhliAdaptiveEngine adaptiveEngine = new OhliAdaptiveEngine();
    private final ThreadLocal<Boolean> gzipPreference = new ThreadLocal<Boolean>();
    private volatile Map<String, JSONObject> userIndex = Collections.emptyMap();
    private volatile Map<String, JSONObject> sessionIndex = Collections.emptyMap();
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private BroadcastReceiver wifiStateReceiver;
    private final Object START_LOCK = new Object();
    private final Handler operationalHandler = new Handler(Looper.getMainLooper());
    private volatile long serverStartRequestedAt = 0L;
    private volatile long lastRecoveryAttemptAt = 0L;
    private volatile Boolean lastWifiConnected = null;
    private volatile boolean serverDownAlertShown = false;
    private volatile boolean monitorStarted = false;
    private static final int NOTIFICATION_WIFI_ALERT = 4201;
    private static final int NOTIFICATION_SERVER_ALERT = 4202;
    private static final String ALERT_CHANNEL = "ohli_server_alerts";
    private final Runnable operationalMonitor = new Runnable() {
        @Override public void run() {
            checkOperationalState();
            if (!stopRequested && getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) {
                operationalHandler.postDelayed(this, 12000L);
            } else {
                monitorStarted = false;
            }
        }
    };
    private JSONArray messages = new JSONArray();
    private JSONArray users = new JSONArray();
    private JSONArray sessions = new JSONArray();
    private JSONArray callSignals = new JSONArray();
    private JSONArray statuses = new JSONArray();
    private JSONArray savedContacts = new JSONArray();
    private JSONArray liveSessions = new JSONArray();
    private JSONArray liveSignals = new JSONArray();
    private JSONArray liveComments = new JSONArray();
    // OHLI Social data stores. These are atomically replaced JSON files on-device; not SQL.
    private JSONArray socialPosts = new JSONArray();
    private JSONArray socialFollows = new JSONArray();
    private JSONArray socialFriends = new JSONArray();
    private JSONArray socialBookmarks = new JSONArray();
    private JSONArray socialNotifications = new JSONArray();
    private JSONArray socialCommunities = new JSONArray();
    private JSONArray statusViews = new JSONArray();
    private JSONArray socialProfileViews = new JSONArray();
    private JSONArray socialBlocks = new JSONArray();
    private JSONArray socialMusic = new JSONArray();
    private JSONArray socialMusicFavorites = new JSONArray();
    private JSONArray socialActivityHistory = new JSONArray();
    private JSONArray socialStoryCollections = new JSONArray();
    private JSONArray socialStoryHistory = new JSONArray();
    private JSONArray socialCallHistory = new JSONArray();
    private JSONArray serverInbox = new JSONArray();
    private JSONArray socialStickerUploads = new JSONArray();
    private JSONArray socialStickerFavorites = new JSONArray();
    private JSONArray socialHighlights = new JSONArray();
    private JSONArray socialGroupMessages = new JSONArray();
    private JSONArray authChallenges = new JSONArray();
    private File authChallengesFile, authSecretFile;
    private byte[] authSecret;
    private final HashMap<String, ArrayList<Long>> authIpRequestTimes = new HashMap<String, ArrayList<Long>>();
    private final HashMap<String, Long> authPhoneLastRequest = new HashMap<String, Long>();
    private final HashMap<String, byte[]> assetCache = new HashMap<String, byte[]>();
    private final HashMap<String, byte[]> gzipAssetCache = new HashMap<String, byte[]>();
    private JSONArray socialChannelPosts = new JSONArray();
    private File messagesFile, usersFile, sessionsFile, callsFile, statusesFile, contactsFile, imagesDir, profilesDir, mediaDir;
    private File liveFile, liveSignalsFile, liveCommentsFile;
    private File socialPostsFile, socialFollowsFile, socialFriendsFile, socialBookmarksFile, socialNotificationsFile, socialCommunitiesFile, statusViewsFile;
    private File socialProfileViewsFile, socialHighlightsFile, socialGroupMessagesFile, socialChannelPostsFile, socialBlocksFile;
    private File socialMusicFile, socialMusicFavoritesFile, socialActivityHistoryFile, socialStoryCollectionsFile, socialStoryHistoryFile, serverInboxFile;
    private File socialStickerUploadsFile, socialStickerFavoritesFile, socialCallHistoryFile;
    private long nextSignalId = System.currentTimeMillis();
    private long nextLiveSignalId = System.currentTimeMillis();
    private volatile long lastSessionPresenceSaveAt = 0L;

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        try { adaptiveEngine.importModelState(getSharedPreferences("ohli_ai_performance", MODE_PRIVATE).getString("model_state", "")); } catch (Exception ignored) { }
        createOperationalNotificationChannel();
        // Capture the initial network state so a real Wi-Fi drop immediately after startup is observable.
        lastWifiConnected = Boolean.valueOf(isWifiConnected());
        wifiStateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                        || ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    refreshWifiLock();
                    operationalHandler.removeCallbacks(operationalMonitor);
                    operationalHandler.post(operationalMonitor);
                }
            }
        };
        try {
            IntentFilter networkFilter = new IntentFilter();
            networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(wifiStateReceiver, networkFilter);
        } catch (Exception ignored) { }
        messagesFile = new File(getFilesDir(), "rk-messages.json");
        usersFile = new File(getFilesDir(), "rk-users.json");
        sessionsFile = new File(getFilesDir(), "rk-sessions.json");
        callsFile = new File(getFilesDir(), "rk-call-signals.json");
        statusesFile = new File(getFilesDir(), "rk-statuses.json");
        contactsFile = new File(getFilesDir(), "rk-contacts.json");
        imagesDir = new File(getFilesDir(), "rk-images");
        profilesDir = new File(getFilesDir(), "rk-profiles");
        mediaDir = new File(getFilesDir(), "rk-media");
        liveFile = new File(getFilesDir(), "rk-live-sessions.json");
        liveSignalsFile = new File(getFilesDir(), "rk-live-signals.json");
        liveCommentsFile = new File(getFilesDir(), "rk-live-comments.json");
        socialPostsFile = new File(getFilesDir(), "ohli-posts.json");
        socialFollowsFile = new File(getFilesDir(), "ohli-follows.json");
        socialFriendsFile = new File(getFilesDir(), "ohli-friends.json");
        socialBookmarksFile = new File(getFilesDir(), "ohli-bookmarks.json");
        socialNotificationsFile = new File(getFilesDir(), "ohli-notifications.json");
        socialCommunitiesFile = new File(getFilesDir(), "ohli-communities.json");
        statusViewsFile = new File(getFilesDir(), "ohli-status-views.json");
        socialProfileViewsFile = new File(getFilesDir(), "ohli-profile-views.json");
        socialHighlightsFile = new File(getFilesDir(), "ohli-highlights.json");
        socialGroupMessagesFile = new File(getFilesDir(), "ohli-group-messages.json");
        socialChannelPostsFile = new File(getFilesDir(), "ohli-channel-posts.json");
        socialBlocksFile = new File(getFilesDir(), "ohli-blocks.json");
        socialMusicFile = new File(getFilesDir(), "ohli-music.json");
        socialMusicFavoritesFile = new File(getFilesDir(), "ohli-music-favorites.json");
        socialActivityHistoryFile = new File(getFilesDir(), "ohli-activity-history.json");
        socialStoryCollectionsFile = new File(getFilesDir(), "ohli-story-collections.json");
        socialStoryHistoryFile = new File(getFilesDir(), "ohli-story-history.json");
        socialCallHistoryFile = new File(getFilesDir(), "ohli-call-history.json");
        serverInboxFile = new File(getFilesDir(), "ohli-server-inbox.json");
        socialStickerUploadsFile = new File(getFilesDir(), "ohli-comment-stickers.json");
        socialStickerFavoritesFile = new File(getFilesDir(), "ohli-sticker-favorites.json");
        authChallengesFile = new File(getFilesDir(), "ohli-auth-challenges.json");
        authSecretFile = new File(getFilesDir(), "ohli-auth-secret.bin");
        authSecret = loadOrCreateAuthSecret();
        if (!imagesDir.exists()) imagesDir.mkdirs();
        if (!profilesDir.exists()) profilesDir.mkdirs();
        if (!mediaDir.exists()) mediaDir.mkdirs();
        /* Large JSON stores used to be parsed on Android's service main thread. */
        dataLoaderThread = new Thread(new Runnable() { @Override public void run() {
            boolean startPending = false;
            try {
                loadAll();
            } catch (Exception e) {
                lastError = "Sebagian data server gagal dimuat: " + cleanError(e);
            } finally {
                synchronized (DATA_READY_LOCK) {
                    dataReady = true;
                    startPending = pendingServerStart && !stopRequested;
                    pendingServerStart = false;
                }
            }
            if (startPending && !stopRequested) startServer();
        }}, "RKServer-DataLoad");
        dataLoaderThread.setDaemon(true);
        dataLoaderThread.start();
    }


    public static JSONArray adminListUsersLocal() {
        ServerService service = INSTANCE;
        JSONArray result = new JSONArray();
        if (service == null || !service.dataReady) return result;
        synchronized (DATA_LOCK) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < service.users.length(); i++) {
                JSONObject user = service.users.optJSONObject(i);
                if (user == null) continue;
                JSONObject row = new JSONObject();
                try {
                    row.put("phone", user.optString("phone"));
                    row.put("name", service.socialDisplayName(user));
                    row.put("username", user.optString("username", service.makeSocialUsername(user.optString("name"), user.optString("phone"))));
                    String status = user.optString("accessStatus", "active");
                    long until = user.optLong("suspendedUntil", 0L);
                    if ("suspended".equals(status) && until > 0 && until <= now) status = "active";
                    row.put("accessStatus", status);
                    row.put("suspendedUntil", until);
                    row.put("createdAt", user.optLong("phoneVerifiedAt", 0L));
                    long lastSeen = 0L;
                    for (int j = 0; j < service.sessions.length(); j++) {
                        JSONObject session = service.sessions.optJSONObject(j);
                        if (session != null && user.optString("phone").equals(session.optString("phone")))
                            lastSeen = Math.max(lastSeen, session.optLong("lastActive", session.optLong("time", 0L)));
                    }
                    row.put("lastSeen", lastSeen);
                    row.put("online", lastSeen > 0 && now - lastSeen < 120000L && service.isUserAccessAllowed(user));
                    result.put(row);
                } catch (JSONException ignored) { }
            }
        }
        return result;
    }

    public static boolean moderateUserLocal(String phone, String action) {
        ServerService service = INSTANCE;
        if (service == null || !service.dataReady || phone == null || phone.length() == 0) return false;
        synchronized (DATA_LOCK) {
            JSONObject user = service.findUser(phone);
            if (user == null) return false;
            try {
                long now = System.currentTimeMillis();
                if ("restore".equals(action)) {
                    user.remove("accessStatus"); user.remove("suspendedUntil"); user.remove("accessReason");
                } else if ("suspend1d".equals(action) || "suspend7d".equals(action)) {
                    user.put("accessStatus", "suspended");
                    user.put("suspendedUntil", now + ("suspend1d".equals(action) ? 86400000L : 604800000L));
                    user.put("accessReason", "Dibatasi sementara oleh pengelola server");
                } else if ("disable".equals(action)) {
                    user.put("accessStatus", "disabled"); user.remove("suspendedUntil");
                    user.put("accessReason", "Dinonaktifkan oleh pengelola server");
                } else if ("softdelete".equals(action)) {
                    // Soft-delete only: preserve content and account data for audit/recovery.
                    user.put("accessStatus", "deleted"); user.remove("suspendedUntil");
                    user.put("accessReason", "Akses akun dicabut oleh pengelola server");
                } else return false;
                JSONArray kept = new JSONArray();
                for (int i = 0; i < service.sessions.length(); i++) {
                    JSONObject session = service.sessions.optJSONObject(i);
                    if (session != null && !phone.equals(session.optString("phone"))) kept.put(session);
                }
                service.sessions = kept;
                service.saveArray(service.usersFile, service.users);
                service.saveArray(service.sessionsFile, service.sessions);
                service.addServerInboxNotification("user_moderation", "Akses akun diperbarui", service.socialDisplayName(user) + " · " + action, phone, null, 0L);
                return true;
            } catch (Exception e) { lastError = "Gagal mengubah akses akun: " + service.cleanError(e); return false; }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            // ACTION_STOP may be delivered through startForegroundService on Android 8+.
            // Enter foreground immediately to satisfy the platform startup deadline,
            // then remove the notification and stop cleanly below.
            startForeground(41, makeNotification());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply();
            operationalHandler.removeCallbacks(operationalMonitor);
            monitorStarted = false;
            cancelRestartAlarm();
            stopRequested = true;
            synchronized (DATA_READY_LOCK) { pendingServerStart = false; }
            addServerInboxNotification("server_stopped", "Server OHLI dimatikan", "Server dihentikan dari aplikasi pengelola.", "", null, 0);
            closeServerSocket();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        // Explicit start or Android's sticky restart keeps the last user-enabled state.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, true).apply();
        startForeground(41, makeNotification());
        ensureOperationalMonitor();
        if (acceptThread == null || !acceptThread.isAlive()) startServer();
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // A swipe from Recents is not treated as an explicit stop. Android may still
        // kill foreground services under force-stop, severe memory pressure, or OEM rules.
        scheduleRestartIfEnabled();
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleRestartIfEnabled() {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) return;
        try {
            Intent again = new Intent(getApplicationContext(), ServerService.class);
            again.setAction(ACTION_START);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = android.os.Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(getApplicationContext(), 7, again, pendingFlags)
                : PendingIntent.getService(getApplicationContext(), 7, again, pendingFlags);
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                long when = android.os.SystemClock.elapsedRealtime() + 1800L;
                if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
                else am.set(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
            }
        } catch (Exception ignored) { }
    }

    private void cancelRestartAlarm() {
        try {
            Intent again = new Intent(getApplicationContext(), ServerService.class);
            again.setAction(ACTION_START);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = android.os.Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(getApplicationContext(), 7, again, pendingFlags)
                : PendingIntent.getService(getApplicationContext(), 7, again, pendingFlags);
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            pi.cancel();
        } catch (Exception ignored) { }
    }

    private Notification makeNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, openIntent, pendingFlags);
        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel("rk_server_status", "Status OHLI Social", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Menunjukkan server lokal sedang berjalan");
                manager.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, "rk_server_status");
        } else builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ohli_notification_icon).setContentTitle("OHLI Social Server aktif")
                .setContentText("Chat, konten sosial, media dan Live · port " + PORT)
                .setOngoing(true).setContentIntent(pending).setWhen(System.currentTimeMillis());
        return builder.build();
    }

    private void startServer() {
        synchronized (START_LOCK) {
            ServerSocket active = serverSocket;
            if (isRunning && active != null && !active.isClosed()) return;
            if (acceptThread != null && acceptThread.isAlive()) return;
            serverStartRequestedAt = System.currentTimeMillis();
            startServerInternal();
        }
    }

    private void startServerInternal() {
        synchronized (DATA_READY_LOCK) {
            if (!dataReady) { pendingServerStart = true; lastError = "Menyiapkan data OHLI…"; return; }
            pendingServerStart = false;
        }
        stopRequested = false; lastError = "";
        /* The offline adaptive engine caps concurrency using this device's memory class and live heap pressure. */
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager == null ? 128 : activityManager.getMemoryClass();
        workers = adaptiveEngine.createExecutor(memoryClass);
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && (wakeLock == null || !wakeLock.isHeld())) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RKServer:Cpu");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
            refreshWifiLock();
        } catch (Exception ignored) { }
        acceptThread = new Thread(new Runnable() { @Override public void run() {
            try {
                ServerSocket socket = new ServerSocket(); socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("0.0.0.0", PORT), 24); serverSocket = socket; isRunning = true;
                addServerInboxNotification("server_started", "Server OHLI aktif", "Server berjalan pada port " + PORT + ".", "", null, 0);
                while (!stopRequested) {
                    try {
                        final Socket client = socket.accept(); client.setSoTimeout(8000);
                        try { workers.execute(new Runnable() { @Override public void run() { handleClient(client); }}); }
                        catch (java.util.concurrent.RejectedExecutionException rejected) {
                            adaptiveEngine.recordRejected(workers);
                            try { sendJson(client.getOutputStream(), 503, "Service Unavailable", errorJson("Server sedang sibuk. Coba lagi sebentar.")); } catch (Exception ignored) { }
                            closeQuietly(client);
                        }
                    } catch (SocketException closed) { if (!stopRequested) lastError = "Koneksi server terputus"; break; }
                }
            } catch (Exception e) { if (!stopRequested) { lastError = cleanError(e); addServerInboxNotification("server_error", "Server OHLI mengalami kendala", lastError, "", null, 0); } }
            finally {
                isRunning = false; closeServerSocket(); releaseLocks(); ThreadPoolExecutor pool = workers;
                if (pool != null) pool.shutdownNow();
            }
        }}, "RKServer-Accept");
        acceptThread.setDaemon(true); acceptThread.start();
    }

    private String cleanError(Exception e) {
        String message = e.getMessage();
        if (e instanceof java.net.BindException) return "Port " + PORT + " sedang dipakai aplikasi lain";
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }

    private void handleClient(Socket client) {
        final long requestStartedNs = System.nanoTime();
        String metricRoute = "/invalid";
        boolean metricFailed = false;
        try {
            InputStream input = client.getInputStream(); OutputStream output = client.getOutputStream();
            byte[] headerBytes = readHeaders(input); if (headerBytes == null) return;
            String rawHeaders = new String(headerBytes, LATIN1); String[] lines = rawHeaders.split("\r\n");
            if (lines.length == 0) { sendJson(output, 400, "Bad Request", errorJson("Request tidak valid")); return; }
            String[] request = lines[0].split(" ");
            if (request.length < 2) { sendJson(output, 400, "Bad Request", errorJson("Request tidak valid")); return; }
            String method = request[0].toUpperCase(Locale.US); String rawPath = request[1];
            String query = "", path = rawPath; int qi = rawPath.indexOf('?');
            if (qi >= 0) { query = rawPath.substring(qi + 1); path = rawPath.substring(0, qi); }
            metricRoute = path;
            int contentLength = 0; String contentType = "application/json"; String headerToken = ""; String rangeHeader = ""; String forwardedIp = ""; String acceptEncoding = "";
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) {
                    String headerName = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
                    String headerValue = lines[i].substring(colon + 1).trim();
                    if ("content-length".equals(headerName)) {
                        try { contentLength = Integer.parseInt(headerValue); } catch (NumberFormatException ignored) { contentLength = -1; }
                    } else if ("content-type".equals(headerName)) contentType = headerValue;
                    else if ("x-rkm-token".equals(headerName)) headerToken = headerValue;
                    else if ("cf-connecting-ip".equals(headerName)) forwardedIp = headerValue;
                    else if ("range".equals(headerName)) rangeHeader = headerValue;
                    else if ("accept-encoding".equals(headerName)) acceptEncoding = headerValue;
                }
            }
            if ("OPTIONS".equals(method)) { sendResponse(output, 204, "No Content", "text/plain; charset=utf-8", new byte[0]); return; }
            // Prefer the Authorization-like custom header over query-string tokens.
            if (headerToken.length() > 0 && queryParam(query, "token").length() == 0) {
                try { query += (query.length() > 0 ? "&" : "") + "token=" + URLEncoder.encode(headerToken, "UTF-8"); } catch (Exception ignored) { }
            }
            if (contentLength < 0) { sendJson(output, 400, "Bad Request", errorJson("Content-Length tidak valid")); return; }
            if ("POST".equals(method) && "/api/media/upload".equals(path)) {
                uploadMediaStream(output, input, contentLength, query, contentType, headerToken);
                return;
            }
            if (contentLength > MAX_BODY_BYTES) { sendJson(output, 413, "Payload Too Large", errorJson("Data JSON terlalu besar. Unggah video/dokumen melalui endpoint media.")); return; }
            boolean gzipAllowed = acceptsGzip(acceptEncoding);
            gzipPreference.set(Boolean.valueOf(gzipAllowed));
            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                sendAssetResponse(output, 200, "OK", "text/html; charset=utf-8", "index.html", 0, gzipAllowed);
            } else if ("GET".equals(method) && "/assets/ohli-social.css".equals(path)) {
                sendAssetResponse(output, 200, "OK", "text/css; charset=utf-8", "ohli-social.css", 86400, gzipAllowed);
            } else if ("GET".equals(method) && "/assets/ohli-social.js".equals(path)) {
                sendAssetResponse(output, 200, "OK", "application/javascript; charset=utf-8", "ohli-social.js", 86400, gzipAllowed);
            } else if ("GET".equals(method) && "/health".equals(path)) {
                // Public health is deliberately minimal: never disclose account counts or device resource metrics.
                JSONObject r = new JSONObject(); r.put("ok", true); r.put("service", "OHLI Social Server"); r.put("version", "3.6.27"); sendJson(output, 200, "OK", r.toString());
            } else if ("GET".equals(method) && "/api/performance".equals(path)) {
                getPerformance(output, query);
            } else if ("GET".equals(method) && "/api/me".equals(path)) {
                JSONObject u = userForToken(queryParam(query, "token"));
                if (u == null) { sendJson(output, 401, "Unauthorized", errorJson("Sesi habis. Silakan masuk lagi.")); return; }
                sendJson(output, 200, "OK", userPublic(u).toString());
            } else if ("GET".equals(method) && "/api/contacts".equals(path)) {
                getContacts(output, query);
            } else if ("GET".equals(method) && "/api/user-exists".equals(path)) {
                getUserExists(output, query);
            } else if ("GET".equals(method) && "/api/chat/resolve".equals(path)) {
                getChatResolve(output, query);
            } else if ("GET".equals(method) && "/api/users".equals(path)) {
                // Legacy Chat endpoint: phone numbers are returned only for accounts
                // whose Direct Message privacy permits the signed-in user to contact them.
                getChatUsers(output, query);
            } else if ("GET".equals(method) && "/api/social/users".equals(path)) {
                // Social discovery uses opaque public IDs, never phone numbers.
                getSocialUsers(output, query);
            } else if ("GET".equals(method) && "/api/messages".equals(path)) {
                getMessages(output, query);
            } else if ("GET".equals(method) && "/api/calls/history".equals(path)) {
                getCallHistory(output, query);
            } else if ("GET".equals(method) && "/api/calls".equals(path)) {
                getCallSignals(output, query);
            } else if ("GET".equals(method) && "/api/status".equals(path)) {
                getStatuses(output, query);
            } else if ("GET".equals(method) && "/api/image".equals(path)) {
                getImage(output, query);
            } else if ("GET".equals(method) && "/api/voice".equals(path)) {
                getVoice(output, query);
            } else if ("GET".equals(method) && "/api/avatar".equals(path)) {
                getAvatar(output, query);
            } else if ("GET".equals(method) && "/api/social-avatar".equals(path)) {
                getSocialAvatar(output, query);
            } else if ("GET".equals(method) && "/api/cover".equals(path)) {
                getCover(output, query);
            } else if ("GET".equals(method) && "/api/media".equals(path)) {
                getMedia(output, query, rangeHeader);
            } else if ("GET".equals(method) && "/api/stickers".equals(path)) {
                getStickerLibrary(output, query);
            } else if ("GET".equals(method) && "/api/stickers/favorites".equals(path)) {
                getStickerFavorites(output, query);
            } else if ("GET".equals(method) && "/api/live".equals(path)) {
                getLiveSessions(output, query);
            } else if ("GET".equals(method) && "/api/live/signals".equals(path)) {
                getLiveSignals(output, query);
            } else if ("GET".equals(method) && "/api/live/requests".equals(path)) {
                getLiveRequests(output, query);
            } else if ("GET".equals(method) && "/api/live/invites".equals(path)) {
                getLiveInvites(output, query);
            } else if ("GET".equals(method) && "/api/live/participants".equals(path)) {
                getLiveParticipants(output, query);
            } else if ("GET".equals(method) && "/api/live/comments".equals(path)) {
                getLiveComments(output, query);
            } else if ("GET".equals(method) && "/api/feed".equals(path)) {
                getSocialFeed(output, query);
            } else if ("GET".equals(method) && "/api/search".equals(path)) {
                getSocialSearch(output, query);
            } else if ("GET".equals(method) && "/api/profile".equals(path)) {
                getSocialProfile(output, query);
            } else if ("GET".equals(method) && "/api/posts/archive".equals(path)) {
                getSocialArchive(output, query);
            } else if ("GET".equals(method) && "/api/posts/saved".equals(path)) {
                getSocialSaved(output, query);
            } else if ("GET".equals(method) && "/api/notifications".equals(path)) {
                getSocialNotifications(output, query);
            } else if ("GET".equals(method) && "/api/groups".equals(path)) {
                getSocialCommunities(output, query, "group");
            } else if ("GET".equals(method) && "/api/channels".equals(path)) {
                getSocialCommunities(output, query, "channel");
            } else if ("GET".equals(method) && "/api/activity/history".equals(path)) {
                getSocialActivityHistory(output, query);
            } else if ("GET".equals(method) && "/api/status/collections".equals(path)) {
                getStoryCollections(output, query);
            } else if ("GET".equals(method) && "/api/status/history".equals(path)) {
                getStoryHistory(output, query);
            } else if ("GET".equals(method) && "/api/status/views".equals(path)) {
                getStatusViews(output, query);
            } else if ("GET".equals(method) && "/api/friends".equals(path)) {
                getSocialFriends(output, query);
            } else if ("GET".equals(method) && "/api/friends/requests".equals(path)) {
                getSocialFriendRequests(output, query);
            } else if ("GET".equals(method) && "/api/followers".equals(path)) {
                getSocialFollowList(output, query);
            } else if ("GET".equals(method) && "/api/follow/requests".equals(path)) {
                getPendingFollowRequests(output, query);
            } else if ("GET".equals(method) && "/api/profile/views".equals(path)) {
                getSocialProfileViews(output, query);
            } else if ("GET".equals(method) && "/api/blocks".equals(path)) {
                getBlockedUsers(output, query);
            } else if ("GET".equals(method) && "/api/trending".equals(path)) {
                getSocialTrending(output, query);
            } else if ("GET".equals(method) && "/api/posts/liked".equals(path)) {
                getSocialLiked(output, query);
            } else if ("GET".equals(method) && "/api/posts/reposted".equals(path)) {
                getSocialReposted(output, query);
            } else if ("GET".equals(method) && "/api/music".equals(path)) {
                getMusicCatalog(output, query, false);
            } else if ("GET".equals(method) && "/api/music/mine".equals(path)) {
                getMyMusic(output, query);
            } else if ("GET".equals(method) && "/api/music/favorites".equals(path)) {
                getMusicCatalog(output, query, true);
            } else if ("GET".equals(method) && "/api/music/detail".equals(path)) {
                getMusicDetail(output, query);
            } else if ("GET".equals(method) && "/api/highlights".equals(path)) {
                getSocialHighlights(output, query);
            } else if ("GET".equals(method) && "/api/groups/messages".equals(path)) {
                getGroupMessages(output, query);
            } else if ("GET".equals(method) && "/api/groups/members".equals(path)) {
                getGroupMembers(output, query);
            } else if ("GET".equals(method) && "/api/channels/posts".equals(path)) {
                getChannelPosts(output, query);
            } else if ("GET".equals(method) && "/api/contact-qr".equals(path)) {
                getContactQr(output, query);
            } else if ("POST".equals(method) && (path.startsWith("/api/"))) {
                byte[] body = readBody(input, contentLength);
                if (body == null) { sendJson(output, 400, "Bad Request", errorJson("Isi permintaan tidak lengkap")); return; }
                String requestIp = client.getInetAddress() == null ? "unknown" : client.getInetAddress().getHostAddress();
                // Trust Cloudflare client IP only when the TCP peer is loopback (local cloudflared proxy).
                if (client.getInetAddress() != null && client.getInetAddress().isLoopbackAddress() && forwardedIp.matches("[0-9a-fA-F:.]{3,64}")) requestIp = forwardedIp;
                handlePost(output, path, new String(body, UTF8), headerToken, requestIp);
            } else sendJson(output, 404, "Not Found", errorJson("Alamat tidak ditemukan"));
        } catch (Exception e) {
            metricFailed = true;
            try { sendJson(client.getOutputStream(), 500, "Internal Server Error", errorJson("Kesalahan server. Coba lagi.")); } catch (Exception ignored) { }
        } finally {
            long elapsedMs = Math.max(0L, (System.nanoTime() - requestStartedNs) / 1000000L);
            adaptiveEngine.recordRequest(metricRoute, elapsedMs, metricFailed, workers);
            persistAdaptiveModelIfDue(false);
            gzipPreference.remove();
            closeQuietly(client);
        }
    }

    private void handlePost(OutputStream output, String path, String body, String headerToken, String remoteIp) throws IOException {
        try {
            JSONObject data = new JSONObject(body);
            if (data.optString("token", "").length() == 0 && headerToken != null && headerToken.length() > 0) data.put("token", headerToken);
            if ("/api/auth/request".equals(path)) { requestAuthCode(output, data, remoteIp); return; }
            if ("/api/auth/verify".equals(path)) { verifyAuthCode(output, data); return; }
            if ("/api/register".equals(path) || "/api/login".equals(path)) { sendJson(output, 410, "Gone", errorJson("Login dan pendaftaran sekarang wajib memakai OTP WhatsApp OHLI.")); return; }
            if ("/api/profile".equals(path)) { updateProfile(output, data); return; }
            if ("/api/contacts".equals(path)) { updateContact(output, data); return; }
            if ("/api/profile-photo".equals(path)) { updateProfilePhoto(output, data); return; }
            if ("/api/social-profile-photo".equals(path)) { updateSocialProfilePhoto(output, data); return; }
            if ("/api/social-avatar-mode".equals(path)) { updateSocialAvatarMode(output, data); return; }
            if ("/api/cover-photo".equals(path)) { updateCoverPhoto(output, data); return; }
            if ("/api/photo/remove".equals(path)) { removeProfileImage(output, data); return; }
            if ("/api/posts/repost".equals(path)) { toggleSocialRepost(output, data); return; }
            if ("/api/posts/share".equals(path)) { countSocialShare(output, data); return; }
            if ("/api/messages".equals(path)) { postMessage(output, data); return; }
            if ("/api/messages/edit".equals(path)) { editMessage(output, data); return; }
            if ("/api/messages/delete".equals(path)) { deleteMessage(output, data); return; }
            if ("/api/messages/hide".equals(path)) { hideMessage(output, data); return; }
            if ("/api/messages/read".equals(path)) { markMessagesRead(output, data); return; }
            if ("/api/messages/once/open".equals(path)) { openViewOnceMessage(output, data); return; }
            if ("/api/activity/history".equals(path)) { recordSocialActivityHistory(output, data); return; }
            if ("/api/activity/history/clear".equals(path)) { clearSocialActivityHistory(output, data); return; }
            if ("/api/images".equals(path)) { postImage(output, data); return; }
            if ("/api/voice".equals(path)) { postVoice(output, data); return; }
            if ("/api/calls/history".equals(path)) { recordCallHistory(output, data); return; }
            if ("/api/calls/history/clear".equals(path)) { clearCallHistory(output, data); return; }
            if ("/api/calls".equals(path)) { postCallSignal(output, data); return; }
            if ("/api/status".equals(path)) { postStatus(output, data); return; }
            if ("/api/live/start".equals(path)) { startLive(output, data); return; }
            if ("/api/live/stop".equals(path)) { stopLive(output, data); return; }
            if ("/api/live/join".equals(path)) { joinLive(output, data); return; }
            if ("/api/live/leave".equals(path)) { leaveLive(output, data); return; }
            if ("/api/live/heartbeat".equals(path)) { heartbeatLive(output, data); return; }
            if ("/api/live/invite".equals(path)) { inviteLive(output, data); return; }
            if ("/api/live/invite/respond".equals(path)) { respondLiveInvite(output, data); return; }
            if ("/api/live/join-request".equals(path)) { requestLiveJoin(output, data); return; }
            if ("/api/live/join-request/respond".equals(path)) { respondLiveJoinRequest(output, data); return; }
            if ("/api/live/participant/leave".equals(path)) { participantLeaveLive(output, data); return; }
            if ("/api/live/participant/ready".equals(path)) { participantReadyLive(output, data); return; }
            if ("/api/live/participant/state".equals(path)) { participantStateLive(output, data); return; }
            if ("/api/live/signal".equals(path)) { postLiveSignal(output, data); return; }
            if ("/api/live/comment".equals(path)) { postLiveComment(output, data); return; }
            if ("/api/live/like".equals(path)) { toggleLiveLike(output, data); return; }
            if ("/api/posts".equals(path)) { createSocialPost(output, data); return; }
            if ("/api/posts/like".equals(path)) { toggleSocialLike(output, data); return; }
            if ("/api/posts/edit".equals(path)) { editSocialPost(output, data); return; }
            if ("/api/posts/archive".equals(path)) { toggleSocialArchive(output, data); return; }
            if ("/api/posts/comment".equals(path)) { addSocialComment(output, data); return; }
            if ("/api/posts/comments/like".equals(path)) { toggleSocialCommentLike(output, data); return; }
            if ("/api/posts/download-settings".equals(path)) { updateSocialDownloadSettings(output, data); return; }
            if ("/api/stickers/favorites".equals(path)) { toggleStickerFavorite(output, data); return; }
            if ("/api/stickers/delete".equals(path)) { deleteOwnSticker(output, data); return; }
            if ("/api/posts/comments/edit".equals(path)) { editSocialComment(output, data); return; }
            if ("/api/posts/comments/delete".equals(path)) { deleteSocialComment(output, data); return; }
            if ("/api/posts/comments/settings".equals(path)) { updateSocialCommentSettings(output, data); return; }
            if ("/api/posts/delete".equals(path)) { deleteSocialPost(output, data); return; }
            if ("/api/posts/bookmark".equals(path)) { toggleSocialBookmark(output, data); return; }
            if ("/api/friends".equals(path)) { updateSocialFriendship(output, data); return; }
            if ("/api/follow".equals(path)) { toggleSocialFollow(output, data); return; }
            if ("/api/blocks".equals(path)) { toggleBlock(output, data); return; }
            if ("/api/notifications/read".equals(path)) { markSocialNotificationsRead(output, data); return; }
            if ("/api/groups".equals(path)) { createSocialCommunity(output, data, "group"); return; }
            if ("/api/channels".equals(path)) { createSocialCommunity(output, data, "channel"); return; }
            if ("/api/groups/join".equals(path) || "/api/channels/follow".equals(path)) { joinSocialCommunity(output, data, path); return; }
            if ("/api/groups/delete".equals(path)) { deleteSocialCommunity(output, data, "group"); return; }
            if ("/api/channels/delete".equals(path)) { deleteSocialCommunity(output, data, "channel"); return; }
            if ("/api/status/view".equals(path)) { recordStatusView(output, data); return; }
            if ("/api/status/collections".equals(path)) { updateStoryCollection(output, data); return; }
            if ("/api/status/history/delete".equals(path)) { deleteStoryHistoryItem(output, data); return; }
            if ("/api/status/history/repost".equals(path)) { repostStoryHistoryItem(output, data); return; }
            if ("/api/status/delete".equals(path)) { deleteStatus(output, data); return; }
            if ("/api/status/like".equals(path)) { toggleStatusLike(output, data); return; }
            if ("/api/settings/privacy".equals(path)) { updateSocialPrivacy(output, data); return; }
            if ("/api/presence/heartbeat".equals(path)) { heartbeatSocialPresence(output, data); return; }
            if ("/api/follow/requests".equals(path)) { respondFollowRequest(output, data); return; }
            if ("/api/password".equals(path)) { changePassword(output, data); return; }
            if ("/api/music/favorites".equals(path)) { toggleMusicFavorite(output, data); return; }
            if ("/api/music/delete".equals(path)) { deleteMusicTrack(output, data); return; }
            if ("/api/highlights".equals(path)) { createSocialHighlight(output, data); return; }
            if ("/api/highlights/delete".equals(path)) { deleteSocialHighlight(output, data); return; }
            if ("/api/highlights/edit".equals(path)) { editSocialHighlight(output, data); return; }
            if ("/api/highlights/item/delete".equals(path)) { deleteSocialHighlightItem(output, data); return; }
            if ("/api/groups/messages".equals(path)) { postGroupMessage(output, data); return; }
            if ("/api/groups/members".equals(path)) { manageGroupMembers(output, data); return; }
            if ("/api/groups/vote".equals(path)) { voteGroupPoll(output, data); return; }
            if ("/api/channels/posts".equals(path)) { createChannelPost(output, data); return; }
            if ("/api/channels/react".equals(path)) { toggleChannelReaction(output, data); return; }
            sendJson(output, 404, "Not Found", errorJson("API tidak ditemukan"));
        } catch (JSONException e) { sendJson(output, 400, "Bad Request", errorJson("Format data tidak valid")); }
    }

    private byte[] loadOrCreateAuthSecret() {
        try {
            if (authSecretFile != null && authSecretFile.exists() && authSecretFile.length() == 32) {
                FileInputStream in = new FileInputStream(authSecretFile); byte[] key = new byte[32]; int off = 0, n;
                while (off < key.length && (n = in.read(key, off, key.length - off)) > 0) off += n; in.close();
                if (off == 32) return key;
            }
            byte[] key = new byte[32]; AUTH_RANDOM.nextBytes(key);
            if (authSecretFile != null) { FileOutputStream out = new FileOutputStream(authSecretFile); out.write(key); out.flush(); out.getFD().sync(); out.close(); }
            return key;
        } catch (Exception e) { lastError = "Kunci verifikasi tidak dapat disiapkan"; return null; }
    }

    private String otpDigest(String challengeId, String phone, String purpose, String code) {
        try {
            if (authSecret == null || authSecret.length != 32) return "";
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(authSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((challengeId + "|" + phone + "|" + purpose + "|" + code).getBytes(UTF8));
            StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 255)); return hex.toString();
        } catch (Exception e) { return ""; }
    }

    private JSONObject findAuthChallenge(String id) {
        if (id == null || id.length() < 20 || id.length() > 64) return null;
        for (int i = authChallenges.length() - 1; i >= 0; i--) { JSONObject c = authChallenges.optJSONObject(i); if (c != null && id.equals(c.optString("id"))) return c; }
        return null;
    }

    private void trimAuthChallenges(long now) throws JSONException {
        JSONArray next = new JSONArray();
        for (int i = 0; i < authChallenges.length(); i++) { JSONObject c = authChallenges.optJSONObject(i); if (c != null && now - c.optLong("createdAt", 0) < 24L * 60L * 60L * 1000L) next.put(c); }
        int from = Math.max(0, next.length() - MAX_AUTH_CHALLENGES + 1); JSONArray limited = new JSONArray(); for (int i = from; i < next.length(); i++) limited.put(next.get(i)); authChallenges = limited;
    }

    private boolean allowAuthRequest(String phone, String ip, long now) {
        // Keep per-IP/per-phone limiter maps bounded on a public server.
        if (authIpRequestTimes.size() > 2048) {
            java.util.Iterator<Map.Entry<String, ArrayList<Long>>> it = authIpRequestTimes.entrySet().iterator();
            while (it.hasNext()) { Map.Entry<String, ArrayList<Long>> entry = it.next(); ArrayList<Long> values = entry.getValue();
                if (values == null || values.isEmpty() || now - values.get(values.size() - 1).longValue() > 10L * 60L * 1000L) it.remove();
            }
            while (authIpRequestTimes.size() > 2048) authIpRequestTimes.remove(authIpRequestTimes.keySet().iterator().next());
        }
        if (authPhoneLastRequest.size() > 5000) {
            java.util.Iterator<Map.Entry<String, Long>> it = authPhoneLastRequest.entrySet().iterator();
            while (it.hasNext()) { Map.Entry<String, Long> entry = it.next(); if (now - entry.getValue().longValue() > 24L * 60L * 60L * 1000L) it.remove(); }
            while (authPhoneLastRequest.size() > 5000) authPhoneLastRequest.remove(authPhoneLastRequest.keySet().iterator().next());
        }
        Long last = authPhoneLastRequest.get(phone); if (last != null && now - last.longValue() < AUTH_REQUEST_COOLDOWN_MS) return false;
        int phoneCount = 0;
        for (int i = Math.max(0, authChallenges.length() - MAX_AUTH_CHALLENGES); i < authChallenges.length(); i++) { JSONObject c = authChallenges.optJSONObject(i); if (c != null && phone.equals(c.optString("phone")) && now - c.optLong("createdAt", 0) < 24L * 60L * 60L * 1000L) phoneCount++; }
        if (phoneCount >= 5) return false;
        ArrayList<Long> times = authIpRequestTimes.get(ip); if (times == null) { times = new ArrayList<Long>(); authIpRequestTimes.put(ip, times); }
        for (int i = times.size() - 1; i >= 0; i--) if (now - times.get(i).longValue() > 10L * 60L * 1000L) times.remove(i);
        if (times.size() >= 20) return false;
        times.add(Long.valueOf(now)); authPhoneLastRequest.put(phone, Long.valueOf(now)); return true;
    }

    private void addServerInboxNotification(String type, String title, String message, String phone, String otp, long expiresAt) {
        try {
            synchronized (DATA_LOCK) {
                // Re-read so read-state changes made from the admin screen are not overwritten.
                if (serverInboxFile != null && serverInboxFile.exists()) serverInbox = loadArray(serverInboxFile, 2000);
                long now = System.currentTimeMillis(); JSONArray next = new JSONArray();
                for (int i = 0; i < serverInbox.length(); i++) {
                    JSONObject old = serverInbox.optJSONObject(i); if (old == null) continue;
                    if (old.optLong("expiresAt", 0) > 0 && old.optLong("expiresAt", 0) < now) old.remove("otp");
                    if (now - old.optLong("time", now) <= 30L * 24L * 60L * 60L * 1000L) next.put(old);
                }
                serverInbox = next;
                JSONObject item = new JSONObject(); item.put("id", UUID.randomUUID().toString().replace("-", ""));
                item.put("type", type); item.put("title", title); item.put("message", message); item.put("phone", phone == null ? "" : phone);
                if (otp != null && otp.length() == 6) item.put("otp", otp);
                item.put("time", now); item.put("expiresAt", expiresAt); item.put("read", false);
                serverInbox.put(item); while (serverInbox.length() > 500) serverInbox.remove(0);
                saveArray(serverInboxFile, serverInbox);
            }
        } catch (Exception ignored) { }
    }

    public static String readServerInbox(Context context) {
        try { File f = new File(context.getFilesDir(), "ohli-server-inbox.json"); if (!f.exists()) return "[]";
            FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n); in.close(); return new String(out.toByteArray(), UTF8);
        } catch (Exception e) { return "[]"; }
    }

    public static void markServerInboxRead(Context context) {
        try {
            File f = new File(context.getFilesDir(), "ohli-server-inbox.json"); JSONArray a = new JSONArray(readServerInbox(context));
            long now = System.currentTimeMillis(); for (int i = 0; i < a.length(); i++) { JSONObject item = a.optJSONObject(i); if (item != null) { item.put("read", true); long expiry = item.optLong("expiresAt", 0); if (expiry > 0 && expiry <= now) item.remove("otp"); } }
            FileOutputStream out = new FileOutputStream(f); out.write(a.toString().getBytes(UTF8)); out.flush(); out.getFD().sync(); out.close();
        } catch (Exception ignored) { }
    }

    private void notifyAuthOtp(String phone, String purpose, String code, long expiresAt) {
        String action = "register".equals(purpose) ? "PENDAFTARAN" : ("reset".equals(purpose) ? "RESET KATA SANDI" : "LOGIN");
        String content = "WhatsApp: +" + phone + " · OTP: " + code + " · berlaku 5 menit";
        addServerInboxNotification("auth_otp", "Permintaan " + action + " OHLI", content, phone, code, expiresAt);
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); if (manager == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel("ohli_auth_otp", "Permintaan OTP OHLI", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Kode verifikasi yang harus dikirim manual oleh admin OHLI melalui WhatsApp");
                channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE); manager.createNotificationChannel(channel);
            }
            Intent openIntent = new Intent(this, MainActivity.class); int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pending = PendingIntent.getActivity(this, 991, openIntent, flags);
            Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "ohli_auth_otp") : new Notification.Builder(this);
            builder.setSmallIcon(R.drawable.ohli_notification_icon).setContentTitle("Permintaan " + action + " OHLI")
                .setContentText(content).setStyle(new Notification.BigTextStyle().bigText(content))
                .setPriority(Notification.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pending).setWhen(System.currentTimeMillis());
            if (android.os.Build.VERSION.SDK_INT >= 21) builder.setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE);
            manager.notify((int)(System.currentTimeMillis() & 0x7fffffff), builder.build());
        } catch (Exception ignored) { }
    }

    private void requestAuthCode(OutputStream out, JSONObject d, String remoteIp) throws IOException, JSONException {
        String phone = normalizePhone(d.optString("phone", "")); String purpose = d.optString("purpose", "login");
        if (!validPhone(phone) || !("login".equals(purpose) || "register".equals(purpose) || "reset".equals(purpose))) { sendJson(out, 400, "Bad Request", errorJson("Nomor WhatsApp atau jenis permintaan tidak valid")); return; }
        if ("reset".equals(purpose) && findUser(phone) == null) { sendJson(out, 404, "Not Found", errorJson("Akun dengan nomor tersebut tidak ditemukan.")); return; }
        if (authSecret == null) { sendJson(out, 503, "Service Unavailable", errorJson("Layanan verifikasi belum siap. Hubungi admin server.")); return; }
        long now = System.currentTimeMillis(); String challengeId = UUID.randomUUID().toString().replace("-", "");
        String code = String.format(Locale.US, "%06d", AUTH_RANDOM.nextInt(1000000));
        synchronized (DATA_LOCK) {
            if (!allowAuthRequest(phone, remoteIp == null ? "unknown" : remoteIp, now)) { sendJson(out, 429, "Too Many Requests", errorJson("Terlalu banyak permintaan. Coba lagi setelah 1 menit; batas 5 permintaan per nomor per hari.")); return; }
            trimAuthChallenges(now);
            JSONObject challenge = new JSONObject(); challenge.put("id", challengeId); challenge.put("phone", phone); challenge.put("purpose", purpose);
            challenge.put("requestName", cleanName(d.optString("name", ""))); challenge.put("otpHash", otpDigest(challengeId, phone, purpose, code));
            challenge.put("createdAt", now); challenge.put("expiresAt", now + AUTH_OTP_TTL_MS); challenge.put("attempts", 0); challenge.put("state", "issued");
            authChallenges.put(challenge); saveArray(authChallengesFile, authChallenges);
        }
        notifyAuthOtp(phone, purpose, code, now + AUTH_OTP_TTL_MS);
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("challengeId", challengeId); result.put("expiresIn", 300);
        result.put("message", "Permintaan diteruskan ke server admin. Masukkan kode yang admin kirim lewat WhatsApp."); sendJson(out, 200, "OK", result.toString());
    }

    private void verifyAuthCode(OutputStream out, JSONObject d) throws IOException, JSONException {
        String challengeId = d.optString("challengeId", ""); String phone = normalizePhone(d.optString("phone", ""));
        String purpose = d.optString("purpose", "login"); String code = d.optString("otp", ""); String password = d.optString("password", "");
        String name = cleanName(d.optString("name", ""));
        if (!validPhone(phone) || !code.matches("[0-9]{6}") || password.length() < 6 || password.length() > 100 || !("login".equals(purpose) || "register".equals(purpose) || "reset".equals(purpose)) || ("register".equals(purpose) && name.length() < 1)) {
            sendJson(out, 400, "Bad Request", errorJson("Periksa nomor, kode OTP, nama, dan kata sandi.")); return;
        }
        synchronized (DATA_LOCK) {
            JSONObject challenge = findAuthChallenge(challengeId); long now = System.currentTimeMillis();
            if (challenge == null || !phone.equals(challenge.optString("phone")) || !purpose.equals(challenge.optString("purpose")) || !"issued".equals(challenge.optString("state"))) {
                sendJson(out, 401, "Unauthorized", errorJson("Permintaan OTP tidak valid atau sudah digunakan. Minta kode baru.")); return;
            }
            if (now > challenge.optLong("expiresAt", 0)) { challenge.put("state", "expired"); saveArray(authChallengesFile, authChallenges); sendJson(out, 410, "Gone", errorJson("Kode OTP sudah kedaluwarsa. Minta kode baru.")); return; }
            int attempts = challenge.optInt("attempts", 0); if (attempts >= 5) { challenge.put("state", "locked"); saveArray(authChallengesFile, authChallenges); sendJson(out, 429, "Too Many Requests", errorJson("Kode terkunci setelah 5 percobaan. Minta kode baru.")); return; }
            challenge.put("attempts", attempts + 1);
            String supplied = otpDigest(challengeId, phone, purpose, code);
            if (!constantTimeEquals(challenge.optString("otpHash", ""), supplied)) { if (attempts + 1 >= 5) challenge.put("state", "locked"); saveArray(authChallengesFile, authChallenges); sendJson(out, 401, "Unauthorized", errorJson("Kode OTP salah.")); return; }
            JSONObject user = findUser(phone);
            if ("register".equals(purpose)) {
                if (user != null) { challenge.put("state", "consumed"); saveArray(authChallengesFile, authChallenges); sendJson(out, 409, "Conflict", errorJson("Nomor sudah memiliki akun. Pilih Masuk.")); return; }
                String salt = UUID.randomUUID().toString(); JSONObject created = new JSONObject(); created.put("phone", phone); created.put("name", name);
                created.put("about", "Hai, aku menggunakan OHLI Social"); created.put("salt", salt); created.put("passwordHash", hashPassword(salt, password)); created.put("phoneVerifiedAt", now);
                users.put(created); saveArray(usersFile, users); user = created;
            } else if ("reset".equals(purpose)) {
                if (user == null) { challenge.put("state", "consumed"); saveArray(authChallengesFile, authChallenges); sendJson(out, 404, "Not Found", errorJson("Akun tidak ditemukan.")); return; }
                String resetSalt = UUID.randomUUID().toString(); user.put("salt", resetSalt); user.put("passwordHash", hashPassword(resetSalt, password)); user.put("phoneVerifiedAt", now); saveArray(usersFile, users);
                JSONArray kept = new JSONArray(); for (int si=0; si<sessions.length(); si++) { JSONObject ss=sessions.optJSONObject(si); if(ss!=null && !phone.equals(ss.optString("phone"))) kept.put(ss); } sessions=kept; saveArray(sessionsFile,sessions);
            } else {
                if (user == null) { challenge.put("state", "consumed"); saveArray(authChallengesFile, authChallenges); sendJson(out, 401, "Unauthorized", errorJson("Nomor atau kata sandi tidak cocok.")); return; }
                String salt = user.optString("salt", ""), stored = user.optString("passwordHash", "");
                boolean valid = stored.startsWith("pbkdf2$") ? constantTimeEquals(stored, hashPassword(salt, password)) : constantTimeEquals(stored, legacyHashPassword(salt, password));
                if (!valid) { if (attempts + 1 >= 5) challenge.put("state", "locked"); saveArray(authChallengesFile, authChallenges); sendJson(out, 401, "Unauthorized", errorJson("Nomor atau kata sandi tidak cocok.")); return; }
                if (!isUserAccessAllowed(user)) { challenge.put("state", "consumed"); challenge.remove("otpHash"); saveArray(authChallengesFile, authChallenges); sendJson(out, 403, "Forbidden", errorJson("Akses akun sedang dibatasi oleh pengelola OHLI. Hubungi admin server.")); return; }
                if (!stored.startsWith("pbkdf2$")) { user.put("passwordHash", hashPassword(salt, password)); saveArray(usersFile, users); }
                user.put("phoneVerifiedAt", user.optLong("phoneVerifiedAt", now));
            }
            challenge.put("state", "consumed"); challenge.put("consumedAt", now); challenge.remove("otpHash"); saveArray(authChallengesFile, authChallenges);
            String sessionToken = createSession(phone); JSONObject result = userPublic(user); result.put("ok", true); result.put("token", sessionToken);
            String successKind = "register".equals(purpose) ? "pendaftaran" : ("reset".equals(purpose) ? "reset kata sandi" : "login");
            addServerInboxNotification("auth_success", "Akun OHLI " + successKind, socialDisplayName(user) + " · +" + phone + " berhasil " + successKind, phone, null, 0);
            sendJson(out, 200, "OK", result.toString());
        }
    }

    private void register(OutputStream out, JSONObject d) throws IOException, JSONException {
        String phone = normalizePhone(d.optString("phone", "")); String name = cleanName(d.optString("name", "")); String password = d.optString("password", "");
        if (!validPhone(phone)) { sendJson(out, 400, "Bad Request", errorJson("Nomor HP harus 8–15 angka")); return; }
        if (name.length() < 1) { sendJson(out, 400, "Bad Request", errorJson("Nama wajib diisi")); return; }
        if (password.length() < 6 || password.length() > 100) { sendJson(out, 400, "Bad Request", errorJson("Kata sandi minimal 6 karakter")); return; }
        synchronized (DATA_LOCK) {
            if (findUser(phone) != null) { sendJson(out, 409, "Conflict", errorJson("Nomor ini sudah terdaftar. Silakan masuk.")); return; }
            String salt = UUID.randomUUID().toString(); JSONObject u = new JSONObject();
            u.put("phone", phone); u.put("name", name); u.put("about", "Hai, aku menggunakan OHLI Social"); u.put("salt", salt); u.put("passwordHash", hashPassword(salt, password));
            users.put(u); saveArray(usersFile, users);
            String token = createSession(phone); JSONObject result = userPublic(u); result.put("ok", true); result.put("token", token);
            sendJson(out, 201, "Created", result.toString());
        }
    }

    private void login(OutputStream out, JSONObject d) throws IOException, JSONException {
        String phone = normalizePhone(d.optString("phone", "")); String password = d.optString("password", "");
        synchronized (DATA_LOCK) {
            JSONObject u = findUser(phone);
            if (u == null) { sendJson(out, 401, "Unauthorized", errorJson("Nomor HP atau kata sandi salah")); return; }
            String salt=u.optString("salt", ""), stored=u.optString("passwordHash", "");
            boolean valid;
            if (stored.startsWith("pbkdf2$")) valid=constantTimeEquals(stored, hashPassword(salt,password));
            else valid=constantTimeEquals(stored, legacyHashPassword(salt,password));
            if (!valid) { sendJson(out, 401, "Unauthorized", errorJson("Nomor HP atau kata sandi salah")); return; }
            if (!isUserAccessAllowed(u)) { sendJson(out, 403, "Forbidden", errorJson("Akses akun sedang dibatasi oleh pengelola OHLI. Hubungi admin server.")); return; }
            if (!stored.startsWith("pbkdf2$")) { u.put("passwordHash",hashPassword(salt,password)); saveArray(usersFile,users); }
            String token = createSession(phone); JSONObject result = userPublic(u); result.put("ok", true); result.put("token", token);
            sendJson(out, 200, "OK", result.toString());
        }
    }

    private void updateProfile(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject sessionUser = userForToken(d.optString("token", ""));
        if (sessionUser == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String phone = sessionUser.optString("phone"); String name = cleanName(d.optString("name", sessionUser.optString("name")));
        String about = d.optString("about", sessionUser.optString("about", ""));
        String username = cleanSocialUsername(d.optString("username", sessionUser.optString("username", makeSocialUsername(name, phone))));
        if (name.length()==0 || name.length()>40 || about.length()>120 || username.length()<3 || username.length()>24 || !username.matches("[a-z0-9._]+")) { sendJson(out, 400, "Bad Request", errorJson("Nama 1–40 karakter, bio maksimal 120 karakter, dan username 3–24 karakter (huruf kecil, angka, titik, garis bawah)")); return; }
        synchronized (DATA_LOCK) { JSONObject u=findUser(phone); if(u==null){sendJson(out,404,"Not Found",errorJson("Akun tidak ditemukan"));return;} for(int i=0;i<users.length();i++){JSONObject other=users.optJSONObject(i);if(other!=null&&!phone.equals(other.optString("phone"))&&username.equalsIgnoreCase(other.optString("username",makeSocialUsername(other.optString("name"),other.optString("phone"))))){sendJson(out,409,"Conflict",errorJson("Username sudah dipakai akun lain"));return;}} u.put("name",name);u.put("about",about);u.put("username",username);saveArray(usersFile,users);JSONObject r=userPublic(u);r.put("ok",true);sendJson(out,200,"OK",r.toString()); }
    }

    /** Authenticated diagnostics for the local host owner/user; public health remains non-sensitive. */
    private void getPerformance(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me = userForToken(queryParam(query, "token"));
        if (me == null) {
            sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid"));
            return;
        }
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("performance", adaptiveEngine.snapshot(workers));
        sendJson(out, 200, "OK", result.toString());
    }

    private void getContacts(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me = userForToken(queryParam(query, "token"));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"); JSONArray list = new JSONArray();
        synchronized (DATA_LOCK) {
            for (int i = 0; i < savedContacts.length(); i++) {
                JSONObject c = savedContacts.optJSONObject(i); if (c == null || !samePhone(owner, c.optString("owner"))) continue;
                String phone = c.optString("phone"); JSONObject u = findUser(phone); if (u == null) continue;
                JSONObject item = new JSONObject(); item.put("phone", phone); item.put("alias", c.optString("alias", ""));
                item.put("name", u.optString("name", phone)); item.put("about", u.optString("about", "")); item.put("socialId", socialId(u)); item.put("username", u.optString("username", makeSocialUsername(u.optString("name"), phone))); list.put(item);
            }
        }
        JSONObject r = new JSONObject(); r.put("ok", true); r.put("contacts", list); sendJson(out, 200, "OK", r.toString());
    }

    private void updateContact(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"), phone = normalizePhone(d.optString("phone", ""));
        String action = d.optString("action", "add"), alias = cleanName(d.optString("alias", ""));
        if (!validPhone(phone) || samePhone(owner, phone)) { sendJson(out, 400, "Bad Request", errorJson("Nomor kontak tidak valid")); return; }
        synchronized (DATA_LOCK) {
            JSONArray next = new JSONArray(); boolean found = false;
            for (int i = 0; i < savedContacts.length(); i++) {
                JSONObject c = savedContacts.optJSONObject(i); if (c == null) continue;
                boolean match = samePhone(owner, c.optString("owner")) && samePhone(phone, c.optString("phone"));
                if (match) { found = true; if ("delete".equals(action)) continue; }
                if (!match) next.put(c);
            }
            if ("delete".equals(action)) {
                savedContacts = next; saveArray(contactsFile, savedContacts);
                JSONObject r = new JSONObject(); r.put("ok", true); r.put("deleted", found); sendJson(out, 200, "OK", r.toString()); return;
            }
            JSONObject u = findUser(phone);
            if (u == null) { sendJson(out, 404, "Not Found", errorJson("Nomor itu belum terdaftar di OHLI Social")); return; }
            JSONObject c = new JSONObject(); c.put("owner", owner); c.put("phone", phone); c.put("alias", alias); c.put("time", System.currentTimeMillis());
            next.put(c); savedContacts = next; saveArray(contactsFile, savedContacts);
            JSONObject r = new JSONObject(); r.put("ok", true); r.put("contact", c); sendJson(out, 200, "OK", r.toString());
        }
    }

    private boolean hasSavedContact(String owner, String phone) {
        for (int i = 0; i < savedContacts.length(); i++) {
            JSONObject c = savedContacts.optJSONObject(i);
            if (c != null && samePhone(owner, c.optString("owner")) && samePhone(phone, c.optString("phone"))) return true;
        }
        return false;
    }

    private boolean canInviteToGroup(String owner, String target) {
        return hasSavedContact(owner, target) || isFollowing(owner, target) || isFollowing(target, owner);
    }

    private void getChatUsers(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String viewer=me.optString("phone");JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=0;i<users.length();i++){
                JSONObject u=users.optJSONObject(i);if(u==null)continue;
                String target=u.optString("phone");
                if(samePhone(viewer,target)||isBlockedEither(viewer,target)||!canDirectMessage(viewer,target))continue;
                // This is intentionally a Chat-only payload. Social UI must use
                // /api/social/users, whose serializer substitutes an opaque ID.
                list.put(userPublic(u));
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("users",list);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialUsers(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String viewer=me.optString("phone");JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=0;i<users.length();i++){
                JSONObject u=users.optJSONObject(i);if(u==null)continue;
                String target=u.optString("phone");
                if(samePhone(viewer,target)||isBlockedEither(viewer,target))continue;
                JSONObject row=userPublicSocial(u,viewer);
                addSocialPresence(row,u,viewer);
                row.put("isFollowing",isFollowing(viewer,target));
                row.put("followsYou",isFollowing(target,viewer));
                row.put("followRequested",hasPendingFollow(viewer,target));
                list.put(row);
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("users",list);sendJson(out,200,"OK",r.toString());
    }

    private void getUserExists(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=normalizePhone(queryParam(query,"phone"));JSONObject u=findUser(phone);boolean allowed=u!=null&&!samePhone(phone,me.optString("phone"))&&!isBlockedEither(me.optString("phone"),phone)&&canDirectMessage(me.optString("phone"),phone);JSONObject r=new JSONObject();r.put("ok",true);r.put("exists",allowed);if(allowed){r.put("name",u.optString("name"));r.put("username",u.optString("username",makeSocialUsername(u.optString("name"),phone)));}sendJson(out,200,"OK",r.toString());
    }

    private void getChatResolve(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),target=resolveAccountKey(queryParam(query,"target"));JSONObject user=findUser(target);if(user==null||samePhone(viewer,target)||isBlockedEither(viewer,target)||!canDirectMessage(viewer,target)){sendJson(out,403,"Forbidden",errorJson("Akun ini tidak mengizinkan chat langsung atau tidak tersedia"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("phone",target);r.put("name",user.optString("name"));r.put("username",user.optString("username",makeSocialUsername(user.optString("name"),target)));sendJson(out,200,"OK",r.toString());
    }

    private void postMessage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String sender = me.optString("phone"); String recipient = resolveAccountKey(d.optString("recipient", "")); String text = d.optString("text", "").trim();
        if (!validPhone(recipient) || samePhone(sender, recipient) || findUser(recipient)==null) { sendJson(out, 400, "Bad Request", errorJson("Pilih nomor teman yang sudah terdaftar")); return; }
        if (isBlockedEither(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pesan tidak dapat dikirim karena salah satu akun memblokir akun lainnya")); return; }
        if (!canDirectMessage(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pengaturan privasi penerima tidak mengizinkan pesan langsung dari akunmu")); return; }
        if (text.length()==0 || text.length()>2000) { sendJson(out, 400, "Bad Request", errorJson("Pesan harus 1–2000 karakter")); return; }
        String clientId=d.optString("clientId","").trim();
        if(clientId.length()>80)clientId=clientId.substring(0,80);
        JSONObject msg=null; boolean duplicateMessage=false;
        synchronized(DATA_LOCK) {
            // A retry after a dropped connection must not create a duplicate message.
            if(clientId.length()>0){
                for(int i=messages.length()-1;i>=0;i--){
                    JSONObject old=messages.optJSONObject(i);
                    if(old!=null&&clientId.equals(old.optString("clientId"))&&samePhone(sender,old.optString("sender"))&&samePhone(recipient,old.optString("recipient"))){msg=old;duplicateMessage=true;break;}
                }
            }
            if(msg==null){
                msg=newMessage(sender, recipient, text, "text", "");
                if(clientId.length()>0)msg.put("clientId",clientId);
                if(text.startsWith("\u2063RKM1X\u2063"))msg.put("viewOnce",true);
                appendLimited(messages, msg, MAX_MESSAGES); saveArray(messagesFile,messages);
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message",msg);r.put("duplicate",duplicateMessage);sendJson(out,200,"OK",r.toString());
    }

    private void postImage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String sender=me.optString("phone"), recipient=resolveAccountKey(d.optString("recipient", "")), encoded=d.optString("data", "");
        if (!validPhone(recipient) || samePhone(sender,recipient) || findUser(recipient)==null) { sendJson(out,400,"Bad Request",errorJson("Pilih teman yang sudah terdaftar")); return; }
        if (isBlockedEither(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pesan tidak dapat dikirim karena salah satu akun memblokir akun lainnya")); return; }
        if (!canDirectMessage(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pengaturan privasi penerima tidak mengizinkan pesan langsung dari akunmu")); return; }
        if (encoded.length()==0 || encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)) { sendJson(out,413,"Payload Too Large",errorJson("Foto terlalu besar")); return; }
        byte[] image;
        try { image=Base64.decode(encoded,Base64.DEFAULT); } catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Data gambar tidak valid"));return;}
        if(image.length==0||image.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Foto maksimal sekitar 180 KB setelah dikompres"));return;}
        String id=UUID.randomUUID().toString().replace("-","");File file=new File(imagesDir,id+".jpg");
        FileOutputStream fos=null;try{fos=new FileOutputStream(file);fos.write(image);fos.flush();fos.getFD().sync();}catch(Exception e){if(file.exists())file.delete();sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan foto"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
        JSONObject msg=newMessage(sender,recipient,"","image",id);if(d.optBoolean("once",false))msg.put("viewOnce",true);
        synchronized(DATA_LOCK){appendLimited(messages,msg,MAX_MESSAGES);saveArray(messagesFile,messages);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message",msg);sendJson(out,200,"OK",r.toString());
    }

    private void postVoice(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", "")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String sender=me.optString("phone"), recipient=resolveAccountKey(d.optString("recipient", "")), encoded=d.optString("data", "");
        String mime=d.optString("mimeType", "audio/webm");
        if(!mime.equals("audio/webm")&&!mime.equals("audio/ogg")&&!mime.equals("audio/mp4")&&!mime.equals("audio/aac")){mime="audio/webm";}
        if(!validPhone(recipient)||samePhone(sender,recipient)||findUser(recipient)==null){sendJson(out,400,"Bad Request",errorJson("Pilih teman yang sudah terdaftar"));return;}
        if(isBlockedEither(sender,recipient)){sendJson(out,403,"Forbidden",errorJson("Pesan tidak dapat dikirim karena salah satu akun memblokir akun lainnya"));return;}
        if(!canDirectMessage(sender,recipient)){sendJson(out,403,"Forbidden",errorJson("Pengaturan privasi penerima tidak mengizinkan pesan langsung dari akunmu"));return;}
        if(encoded.length()==0||encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)){sendJson(out,413,"Payload Too Large",errorJson("Pesan suara terlalu besar"));return;}
        byte[] audio;try{audio=Base64.decode(encoded,Base64.DEFAULT);}catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Data pesan suara tidak valid"));return;}
        if(audio.length==0||audio.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Pesan suara maksimal sekitar 180 KB; rekam lebih singkat"));return;}
        String id=UUID.randomUUID().toString().replace("-","");File file=new File(imagesDir,id+".voice");FileOutputStream fos=null;
        try{fos=new FileOutputStream(file);fos.write(audio);fos.flush();fos.getFD().sync();}catch(Exception e){if(file.exists())file.delete();sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan pesan suara"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
        JSONObject msg=newMessage(sender,recipient,"","voice","");msg.put("voiceId",id);msg.put("mimeType",mime);if(d.optBoolean("once",false))msg.put("viewOnce",true);
        synchronized(DATA_LOCK){appendLimited(messages,msg,MAX_MESSAGES);saveArray(messagesFile,messages);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message",msg);sendJson(out,200,"OK",r.toString());
    }

    private void getVoice(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=queryParam(query,"id");if(id.length()!=32||!id.matches("[a-fA-F0-9]+")){sendJson(out,400,"Bad Request",errorJson("ID pesan suara tidak valid"));return;}
        File voiceFile=new File(imagesDir,id+".voice");if(!voiceFile.exists()||voiceFile.length()>MAX_IMAGE_BYTES){sendJson(out,404,"Not Found",errorJson("Pesan suara tidak ditemukan"));return;}
        boolean authorized=false,onceVoice=false,onceDenied=false;String mime="audio/webm";
        synchronized(DATA_LOCK){for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&id.equals(m.optString("voiceId"))&&(samePhone(me.optString("phone"),m.optString("sender"))||samePhone(me.optString("phone"),m.optString("recipient")))&&!isBlockedEither(m.optString("sender"),m.optString("recipient"))){if(m.optBoolean("viewOnce",false)&&samePhone(me.optString("phone"),m.optString("recipient"))){onceVoice=true;if(m.optLong("viewOnceOpenedAt",0)>0)onceDenied=true;else{m.put("viewOnceOpenedAt",System.currentTimeMillis());m.put("viewOnceOpenedBy",me.optString("phone"));saveArray(messagesFile,messages);}}authorized=!onceDenied;mime=m.optString("mimeType","audio/webm");break;}}}
        if(onceDenied){sendJson(out,410,"Gone",errorJson("Pesan suara sekali lihat sudah dibuka"));return;}if(!authorized){sendJson(out,404,"Not Found",errorJson("Pesan suara tidak ditemukan"));return;}
        ByteArrayOutputStream buf=new ByteArrayOutputStream();FileInputStream in=null;try{in=new FileInputStream(voiceFile);byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);}finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        if(!mime.equals("audio/webm")&&!mime.equals("audio/ogg")&&!mime.equals("audio/mp4")&&!mime.equals("audio/aac"))mime="audio/webm";
        if(onceVoice)sendResponse(out,200,"OK",mime,buf.toByteArray());else sendResponseCached(out,200,"OK",mime,buf.toByteArray(),86400);
    }

    private void editMessage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = d.optString("id", ""), text = d.optString("text", "").trim();
        if (id.length() == 0 || text.length() == 0 || text.length() > 2000) { sendJson(out, 400, "Bad Request", errorJson("Pesan edit tidak valid")); return; }
        synchronized (DATA_LOCK) {
            for (int i=0; i<messages.length(); i++) {
                JSONObject m=messages.optJSONObject(i);
                if (m!=null && id.equals(m.optString("id"))) {
                    if (!samePhone(me.optString("phone"), m.optString("sender"))) { sendJson(out,403,"Forbidden",errorJson("Hanya pengirim yang dapat mengedit pesan ini")); return; }
                    if (m.optBoolean("viewOnce",false)) { sendJson(out,403,"Forbidden",errorJson("Pesan sekali lihat tidak dapat diedit")); return; }
                    if (!"text".equals(m.optString("type"))) { sendJson(out,400,"Bad Request",errorJson("Hanya pesan teks yang dapat diedit")); return; }
                    m.put("text",text); m.put("edited",true); m.put("editedAt",timeNow()); saveArray(messagesFile,messages);
                    JSONObject r=new JSONObject();r.put("ok",true);r.put("message",m);sendJson(out,200,"OK",r.toString());return;
                }
            }
        }
        sendJson(out,404,"Not Found",errorJson("Pesan tidak ditemukan"));
    }

    private void deleteMessage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id=d.optString("id", ""); if(id.length()==0){sendJson(out,400,"Bad Request",errorJson("ID pesan kosong"));return;}
        JSONObject removed = null;
        synchronized(DATA_LOCK) {
            JSONArray next=new JSONArray(); boolean found=false;
            for(int i=0;i<messages.length();i++) {
                JSONObject m=messages.optJSONObject(i);
                if(m!=null && id.equals(m.optString("id"))) {
                    found=true;
                    if(!samePhone(me.optString("phone"),m.optString("sender"))) { sendJson(out,403,"Forbidden",errorJson("Hanya pengirim yang dapat menghapus pesan untuk semua")); return; }
                    removed = m;
                    continue;
                }
                if(m!=null) next.put(m);
            }
            if(!found){sendJson(out,404,"Not Found",errorJson("Pesan tidak ditemukan"));return;}
            messages=next;saveArray(messagesFile,messages);
        }
        cleanupDeletedMessageMedia(removed);
        JSONObject r=new JSONObject();r.put("ok",true);sendJson(out,200,"OK",r.toString());
    }

    private void cleanupDeletedMessageMedia(JSONObject removed) {
        if (removed == null) return;
        String imageId = removed.optString("imageId", "");
        String voiceId = removed.optString("voiceId", "");
        String mediaId = removed.optString("mediaId", "");
        synchronized (DATA_LOCK) {
            if (imageId.matches("[a-fA-F0-9]{32}") && !isMessageImageReferenced(imageId)) {
                File f = new File(imagesDir, imageId + ".jpg"); if (f.exists()) f.delete();
            }
            if (voiceId.matches("[a-fA-F0-9]{32}") && !isMessageVoiceReferenced(voiceId)) {
                File f = new File(imagesDir, voiceId + ".voice"); if (f.exists()) f.delete();
            }
            if (mediaId.matches("[a-fA-F0-9]{32}") && !isMessageMediaReferenced(mediaId)) {
                File f = new File(mediaDir, mediaId + ".bin"); if (f.exists()) f.delete();
            }
        }
    }

    private boolean isMessageImageReferenced(String id) {
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&id.equals(m.optString("imageId")))return true;}
        for(int i=0;i<statuses.length();i++){JSONObject x=statuses.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialPosts.length();i++){JSONObject x=socialPosts.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialHighlights.length();i++){JSONObject x=socialHighlights.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialStoryCollections.length();i++){JSONObject x=socialStoryCollections.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialStickerUploads.length();i++){JSONObject x=socialStickerUploads.optJSONObject(i);if(x!=null&&(id.equals(x.optString("imageId"))||id.equals(x.optString("mediaId"))))return true;}
        for(int i=0;i<socialStickerFavorites.length();i++){JSONObject x=socialStickerFavorites.optJSONObject(i);if(x!=null&&(id.equals(x.optString("imageId"))||id.equals(x.optString("mediaId"))))return true;}
        return false;
    }
    private boolean isMessageVoiceReferenced(String id) {
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&id.equals(m.optString("voiceId")))return true;}
        return false;
    }
    private boolean isMessageMediaReferenced(String id) {
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&id.equals(m.optString("mediaId")))return true;}
        for(int i=0;i<socialPosts.length();i++){JSONObject x=socialPosts.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<statuses.length();i++){JSONObject x=statuses.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialMusic.length();i++){JSONObject x=socialMusic.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialHighlights.length();i++){JSONObject x=socialHighlights.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        for(int i=0;i<socialStoryCollections.length();i++){JSONObject x=socialStoryCollections.optJSONObject(i);if(x!=null&&id.equals(x.optString("mediaId")))return true;}
        return false;
    }

    private void hideMessage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = d.optString("id", ""), phone = me.optString("phone");
        if (id.length() == 0) { sendJson(out, 400, "Bad Request", errorJson("ID pesan kosong")); return; }
        synchronized (DATA_LOCK) {
            for (int i=0; i<messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null || !id.equals(m.optString("id"))) continue;
                if (!phone.equals(m.optString("sender")) && !phone.equals(m.optString("recipient"))) {
                    sendJson(out, 404, "Not Found", errorJson("Pesan tidak ditemukan")); return;
                }
                JSONArray hidden = m.optJSONArray("hiddenFor");
                if (hidden == null) hidden = new JSONArray();
                boolean exists = false;
                for (int j=0; j<hidden.length(); j++) if (phone.equals(hidden.optString(j))) exists = true;
                if (!exists) hidden.put(phone);
                m.put("hiddenFor", hidden);
                saveArray(messagesFile, messages);
                JSONObject result = new JSONObject(); result.put("ok", true); result.put("hidden", true);
                sendJson(out, 200, "OK", result.toString()); return;
            }
        }
        sendJson(out, 404, "Not Found", errorJson("Pesan tidak ditemukan"));
    }

    private void updateProfilePhoto(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", ""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String encoded=d.optString("data", ""); if(encoded.length()==0 || encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)){sendJson(out,413,"Payload Too Large",errorJson("Foto profil terlalu besar"));return;}
        byte[] image;try{image=Base64.decode(encoded,Base64.DEFAULT);}catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Foto profil tidak valid"));return;}
        if(image.length==0||image.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Foto profil maksimal 180 KB"));return;}
        File file=new File(profilesDir,me.optString("phone")+".jpg");FileOutputStream fos=null;
        try{fos=new FileOutputStream(file);fos.write(image);fos.flush();fos.getFD().sync();}catch(Exception e){sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan foto profil"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
        JSONObject r=new JSONObject();r.put("ok",true);sendJson(out,200,"OK",r.toString());
    }

    private void getAvatar(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=resolveAccountKey(queryParam(query,"phone"));if(!validPhone(phone)||findUser(phone)==null||(!samePhone(me.optString("phone"),phone)&&isBlockedEither(me.optString("phone"),phone))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}
        File f=new File(profilesDir,phone+".jpg");if(!f.exists()||f.length()>MAX_IMAGE_BYTES){sendJson(out,404,"Not Found",errorJson("Foto profil belum dipasang"));return;}
        ByteArrayOutputStream buf=new ByteArrayOutputStream();FileInputStream in=null;
        try{in=new FileInputStream(f);byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);}finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        sendResponseCached(out,200,"OK","image/jpeg",buf.toByteArray(),60);
    }

    private File socialAvatarFile(String phone) { return new File(profilesDir, phone + "_social.jpg"); }

    private void getSocialAvatar(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=resolveAccountKey(queryParam(query,"phone"));JSONObject target=findUser(phone);
        if(!validPhone(phone)||target==null||(!samePhone(me.optString("phone"),phone)&&isBlockedEither(me.optString("phone"),phone))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}
        File f="separate".equals(target.optString("socialAvatarMode","same"))?socialAvatarFile(phone):new File(profilesDir,phone+".jpg");
        if(!f.exists()||f.length()>MAX_IMAGE_BYTES){sendJson(out,404,"Not Found",errorJson("Foto profil sosial belum dipasang"));return;}
        ByteArrayOutputStream buf=new ByteArrayOutputStream();FileInputStream in=null;try{in=new FileInputStream(f);byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);}finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        sendResponseCached(out,200,"OK","image/jpeg",buf.toByteArray(),60);
    }

    private void updateSocialAvatarMode(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String mode="separate".equals(d.optString("mode"))?"separate":"same";String phone=me.optString("phone");
        synchronized(DATA_LOCK){JSONObject user=findUser(phone);if(user==null){sendJson(out,404,"Not Found",errorJson("Akun tidak ditemukan"));return;}user.put("socialAvatarMode",mode);saveArray(usersFile,users);}
        if("same".equals(mode)){File old=socialAvatarFile(phone);if(old.exists())old.delete();}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("mode",mode);sendJson(out,200,"OK",r.toString());
    }

    private void updateSocialProfilePhoto(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String encoded=d.optString("data","");if(encoded.length()==0||encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)){sendJson(out,413,"Payload Too Large",errorJson("Foto profil sosial terlalu besar"));return;}
        byte[] image;try{image=Base64.decode(encoded,Base64.DEFAULT);}catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Foto profil sosial tidak valid"));return;}
        if(image.length==0||image.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Foto profil sosial maksimal 180 KB"));return;}
        String phone=me.optString("phone");File file=socialAvatarFile(phone);FileOutputStream fos=null;
        try{fos=new FileOutputStream(file);fos.write(image);fos.flush();fos.getFD().sync();}catch(Exception e){sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan foto profil sosial"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
        synchronized(DATA_LOCK){JSONObject user=findUser(phone);if(user!=null){user.put("socialAvatarMode","separate");saveArray(usersFile,users);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("mode","separate");sendJson(out,200,"OK",r.toString());
    }

    private JSONObject newMessage(String sender, String recipient, String text, String type, String imageId) throws JSONException {
        JSONObject s=findUser(sender), r=findUser(recipient); JSONObject msg=new JSONObject();
        msg.put("id",UUID.randomUUID().toString());msg.put("sender",sender);msg.put("recipient",recipient);
        msg.put("senderName",s==null?sender:s.optString("name",sender));msg.put("recipientName",r==null?recipient:r.optString("name",recipient));
        msg.put("text",text);msg.put("type",type);msg.put("imageId",imageId);msg.put("time",timeNow());return msg;
    }

    private void getMessages(OutputStream out, String query) throws IOException, JSONException {
        String token=queryParam(query,"token");JSONObject me=userForToken(token); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        touchSession(token);
        String phone=me.optString("phone"), peer=resolveAccountKey(queryParam(query,"peer")); JSONArray copy=new JSONArray();boolean blocked=peer.length()>0&&isBlockedEither(phone,peer);long now=System.currentTimeMillis();boolean changed=false;
        synchronized(DATA_LOCK){for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m==null)continue;String s=m.optString("sender"),r=m.optString("recipient");boolean allowed=(samePhone(s,phone)||samePhone(r,phone));if(!allowed||blocked)continue;JSONArray hidden=m.optJSONArray("hiddenFor");boolean hiddenHere=false;if(hidden!=null)for(int h=0;h<hidden.length();h++)if(phone.equals(hidden.optString(h))){hiddenHere=true;break;}if(hiddenHere)continue;if(peer.length()>0&&!((samePhone(s,phone)&&samePhone(r,peer))||(samePhone(s,peer)&&samePhone(r,phone))))continue;
                if(samePhone(r,phone)&&!m.has("deliveredAt")){m.put("deliveredAt",now);changed=true;}
                JSONObject safe=new JSONObject(m.toString());safe.remove("viewOnceSessionKey");boolean isOnce=m.optBoolean("viewOnce",false);boolean onceOpened=isOnce&&m.optLong("viewOnceOpenedAt",0)>0;safe.put("viewOnceOpened",onceOpened);if(isOnce&&samePhone(r,phone)){safe.put("text","");safe.put("viewOnceOpenedByMe",onceOpened&&samePhone(m.optString("viewOnceOpenedBy"),phone));}copy.put(safe);
            }if(changed)saveArray(messagesFile,messages);}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("messages",copy);result.put("count",copy.length());sendJson(out,200,"OK",result.toString());
    }

    private void openViewOnceMessage(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("id",""),phone=me.optString("phone"),openedText=null;
        if(id.length()==0||id.length()>80){sendJson(out,400,"Bad Request",errorJson("ID pesan tidak valid"));return;}
        synchronized(DATA_LOCK){
            for(int i=0;i<messages.length();i++){
                JSONObject m=messages.optJSONObject(i);
                if(m==null||!id.equals(m.optString("id")))continue;
                if(!samePhone(phone,m.optString("recipient"))||!m.optBoolean("viewOnce",false)||!"text".equals(m.optString("type"))){sendJson(out,403,"Forbidden",errorJson("Pesan ini bukan pesan teks sekali lihat untuk akunmu"));return;}
                if(isBlockedEither(m.optString("sender"),m.optString("recipient"))){sendJson(out,404,"Not Found",errorJson("Pesan tidak tersedia"));return;}
                if(m.optLong("viewOnceOpenedAt",0)>0){sendJson(out,410,"Gone",errorJson("Pesan sekali lihat sudah dibuka"));return;}
                String raw=m.optString("text","");
                String oncePrefix="\u2063RKM1X\u2063";
                if(!raw.startsWith(oncePrefix)){sendJson(out,400,"Bad Request",errorJson("Isi pesan sekali lihat tidak valid"));return;}
                openedText=raw.substring(oncePrefix.length());
                m.put("viewOnceOpenedAt",System.currentTimeMillis());
                m.put("viewOnceOpenedBy",phone);
                saveArray(messagesFile,messages);
                break;
            }
        }
        if(openedText==null){sendJson(out,404,"Not Found",errorJson("Pesan tidak ditemukan"));return;}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("opened",true);r.put("text",openedText);sendJson(out,200,"OK",r.toString());
    }

    private void markMessagesRead(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=me.optString("phone"),peer=normalizePhone(d.optString("peer",""));if(!validPhone(peer)||isBlockedEither(phone,peer)){sendJson(out,200,"OK","{\"ok\":true,\"updated\":0}");return;}
        int updated=0;long now=System.currentTimeMillis();synchronized(DATA_LOCK){for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&samePhone(m.optString("recipient"),phone)&&samePhone(m.optString("sender"),peer)&&!m.has("readAt")){m.put("deliveredAt",m.optLong("deliveredAt",now));m.put("readAt",now);updated++;}}if(updated>0)saveArray(messagesFile,messages);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("updated",updated);sendJson(out,200,"OK",r.toString());
    }

    private void getCallHistory(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"); JSONArray items=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=socialCallHistory.length()-1;i>=0;i--){
                JSONObject row=socialCallHistory.optJSONObject(i);
                if(row!=null&&owner.equals(row.optString("owner"))){
                    JSONObject safe=new JSONObject(row.toString()); safe.remove("owner"); items.put(safe);
                    if(items.length()>=300)break;
                }
            }
        }
        JSONObject result=new JSONObject();result.put("ok",true);result.put("items",items);sendJson(out,200,"OK",result.toString());
    }

    private void recordCallHistory(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"), peer=normalizePhone(d.optString("peer","")), callId=d.optString("callId","").trim();
        if(!validPhone(peer)||samePhone(owner,peer)||findUser(peer)==null||callId.length()<4||callId.length()>100){sendJson(out,400,"Bad Request",errorJson("Data riwayat panggilan tidak valid"));return;}
        String direction=d.optString("direction","out"), result=d.optString("result","selesai"), mediaType=d.optString("mediaType","audio");
        if(!"in".equals(direction)&&!"out".equals(direction))direction="out";
        if(!"audio".equals(mediaType)&&!"video".equals(mediaType))mediaType="audio";
        String[] allowedResults={"memanggil","menghubungkan","terhubung","selesai","gagal","sibuk","terputus","tidak dijawab","ditolak","tidak terhubung"};
        boolean validResult=false;for(String value:allowedResults)if(value.equals(result))validResult=true;if(!validResult)result="selesai";
        long now=System.currentTimeMillis(), started=d.optLong("startedAt",now), connected=d.optLong("connectedAt",0), ended=d.optLong("endedAt",now);
        if(started<now-90L*24*60*60*1000)started=now-90L*24*60*60*1000;if(started>now+60000L)started=now;
        if(connected<started||connected>now+60000L)connected=0;
        if(ended<started||ended>now+60000L)ended=now;
        long duration=connected>0?Math.max(0,Math.min(24L*60*60,(ended-connected)/1000L)):0;
        JSONObject row=new JSONObject();row.put("owner",owner);row.put("peer",peer);row.put("callId",callId);row.put("direction",direction);row.put("result",result);row.put("mediaType",mediaType);row.put("startedAt",started);row.put("connectedAt",connected);row.put("endedAt",ended);row.put("durationSeconds",duration);row.put("time",started);row.put("updatedAt",now);
        synchronized(DATA_LOCK){
            JSONArray next=new JSONArray();boolean updated=false;
            for(int i=0;i<socialCallHistory.length();i++){
                JSONObject old=socialCallHistory.optJSONObject(i);if(old==null)continue;
                if(owner.equals(old.optString("owner"))&&callId.equals(old.optString("callId"))){if(!updated){next.put(row);updated=true;}}
                else next.put(old);
            }
            if(!updated)next.put(row);
            JSONArray limited=new JSONArray();int kept=0;
            for(int i=next.length()-1;i>=0;i--){JSONObject item=next.optJSONObject(i);if(item==null)continue;if(owner.equals(item.optString("owner"))&&++kept>500)continue;limited.put(item);}
            socialCallHistory=limited;saveArray(socialCallHistoryFile,socialCallHistory);
        }
        JSONObject response=new JSONObject();response.put("ok",true);response.put("saved",true);response.put("durationSeconds",duration);sendJson(out,200,"OK",response.toString());
    }

    private void clearCallHistory(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone");
        synchronized(DATA_LOCK){JSONArray next=new JSONArray();for(int i=0;i<socialCallHistory.length();i++){JSONObject item=socialCallHistory.optJSONObject(i);if(item!=null&&!owner.equals(item.optString("owner")))next.put(item);}socialCallHistory=next;saveArray(socialCallHistoryFile,socialCallHistory);}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("cleared",true);sendJson(out,200,"OK",result.toString());
    }

    private void postCallSignal(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", "")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String from=me.optString("phone"), to=normalizePhone(d.optString("to", "")), type=d.optString("type", ""), callId=d.optString("callId", "");
        if(!validPhone(to)||samePhone(from,to)||findUser(to)==null){sendJson(out,400,"Bad Request",errorJson("Nomor teman tidak valid"));return;}
        if(isBlockedEither(from,to)||!canDirectMessage(from,to)){sendJson(out,403,"Forbidden",errorJson("Pengaturan privasi penerima tidak mengizinkan panggilan dari akunmu"));return;}
        if(!(type.equals("offer")||type.equals("answer")||type.equals("ice")||type.equals("end")||type.equals("reject")||type.equals("busy"))){sendJson(out,400,"Bad Request",errorJson("Jenis sinyal tidak valid"));return;}
        if(callId.length()==0||callId.length()>80){sendJson(out,400,"Bad Request",errorJson("ID panggilan tidak valid"));return;}
        JSONObject payload=d.optJSONObject("payload"); if(payload==null)payload=new JSONObject();
        JSONObject sig=new JSONObject(); sig.put("id",nextSignalId());sig.put("from",from);sig.put("fromName",me.optString("name",from));sig.put("to",to);sig.put("type",type);sig.put("callId",callId);sig.put("payload",payload);sig.put("time",System.currentTimeMillis());
        synchronized(DATA_LOCK){appendLimited(callSignals,sig,MAX_CALL_SIGNALS);saveArray(callsFile,callSignals);}
        JSONObject r=new JSONObject();r.put("ok",true);sendJson(out,200,"OK",r.toString());
    }

    private void getCallSignals(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=me.optString("phone");long since=0;try{since=Long.parseLong(queryParam(query,"since"));}catch(Exception ignored){}
        JSONArray resultList=new JSONArray();long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){for(int i=0;i<callSignals.length();i++){JSONObject s=callSignals.optJSONObject(i);if(s==null)continue;long id=s.optLong("id",0);if(samePhone(s.optString("to"),phone)&&id>since&&now-s.optLong("time",now)<120000)resultList.put(s);}}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("signals",resultList);sendJson(out,200,"OK",result.toString());
    }

    // Streaming binary endpoint: POST /api/media/upload?recipient=...&purpose=message|status
    // Binary bytes are written incrementally to disk to keep tablet memory use bounded.
    private void uploadMediaStream(OutputStream out, InputStream input, int length, String query, String contentType, String headerToken) throws IOException, JSONException {
        String token = !TextUtils.isEmpty(headerToken) ? headerToken : queryParam(query, "token");
        JSONObject me = userForToken(token);
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        if (length <= 0 || length > MAX_MEDIA_BYTES) { sendJson(out, 413, "Payload Too Large", errorJson("Ukuran media harus 1 byte sampai 1 GB per file")); return; }
        String mime = contentType == null ? "application/octet-stream" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.US);
        if (mime.length() == 0 || mime.length() > 120 || mime.contains("\\") || mime.contains("\r") || mime.contains("\n")) mime = "application/octet-stream";
        String purpose = queryParam(query, "purpose"); if (purpose.length() == 0) purpose = "message";
        if (!"message".equals(purpose) && !"status".equals(purpose) && !"post".equals(purpose) && !"music".equals(purpose) && !"comment-sticker".equals(purpose)) { sendJson(out, 400, "Bad Request", errorJson("Tujuan media tidak valid")); return; }
        String statusText = queryParam(query, "text"); if (("status".equals(purpose) && statusText.length() > 240) || ("post".equals(purpose) && statusText.length() > 2000)) { sendJson(out, 400, "Bad Request", errorJson("Caption terlalu panjang")); return; }
        boolean isImage = mime.startsWith("image/"); boolean isVideo = mime.startsWith("video/"); boolean isAudio = mime.startsWith("audio/");
        if ("comment-sticker".equals(purpose) && (!isImage || length > 2 * 1024 * 1024)) { sendJson(out, 415, "Unsupported Media Type", errorJson("Stiker komentar harus berupa gambar maksimal 2 MB")); return; }
        if ("music".equals(purpose) && length > 30 * 1024 * 1024) { sendJson(out, 413, "Payload Too Large", errorJson("Audio maksimal 30 MB per file")); return; }
        if (("status".equals(purpose) || "post".equals(purpose)) && !isImage && !isVideo) { sendJson(out, 415, "Unsupported Media Type", errorJson("Status dan postingan hanya mendukung foto atau video")); return; }
        if ("music".equals(purpose) && !isAudio) { sendJson(out, 415, "Unsupported Media Type", errorJson("Katalog musik hanya menerima file audio MP3, AAC, M4A, OGG, WAV, atau WebM audio")); return; }
        String sender = me.optString("phone"), recipient = normalizePhone(queryParam(query, "recipient"));
        if ("music".equals(purpose) && (queryParam(query, "title").trim().length() > 100 || queryParam(query, "artist").trim().length() > 80)) { sendJson(out, 400, "Bad Request", errorJson("Judul musik maksimal 100 karakter dan artis 80 karakter")); return; }
        if ("message".equals(purpose) && (!validPhone(recipient) || samePhone(sender, recipient) || findUser(recipient) == null)) { sendJson(out, 400, "Bad Request", errorJson("Pilih teman yang sudah terdaftar")); return; }
        if ("message".equals(purpose) && isBlockedEither(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pesan tidak dapat dikirim karena salah satu akun memblokir akun lainnya")); return; }
        if ("message".equals(purpose) && !canDirectMessage(sender,recipient)) { sendJson(out,403,"Forbidden",errorJson("Pengaturan privasi penerima tidak mengizinkan pesan langsung dari akunmu")); return; }
        if (mediaBytesUsed() + length > MAX_MEDIA_STORAGE_BYTES) { sendJson(out, 507, "Insufficient Storage", errorJson("Penyimpanan media server hampir penuh (batas 16 GB). Hapus media lama atau kosongkan ruang tablet.")); return; }
        String id = UUID.randomUUID().toString().replace("-", ""); File file = new File(mediaDir, id + ".bin");
        FileOutputStream fos = null; int remaining = length;
        try {
            fos = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            while (remaining > 0) {
                int n = input.read(buf, 0, Math.min(buf.length, remaining));
                if (n < 0) throw new IOException("Unggahan media terputus sebelum selesai");
                if (n == 0) continue;
                fos.write(buf, 0, n); remaining -= n;
            }
            fos.flush(); fos.getFD().sync();
        } catch (Exception e) {
            if (file.exists()) file.delete();
            sendJson(out, 400, "Bad Request", errorJson("Unggahan tidak lengkap atau gagal disimpan")); return;
        } finally { if (fos != null) try { fos.close(); } catch (Exception ignored) { } }
        if ("music".equals(purpose) && !hasValidAudioSignature(file, mime)) {
            if (file.exists()) file.delete();
            sendJson(out, 415, "Unsupported Media Type", errorJson("Format file audio tidak cocok dengan MIME type. Pilih MP3, AAC/M4A, OGG, WAV, atau audio WebM yang valid."));
            return;
        }
        if (("status".equals(purpose) || "post".equals(purpose) || "comment-sticker".equals(purpose)) && !hasValidSocialMediaSignature(file, mime)) {
            if (file.exists()) file.delete();
            sendJson(out, 415, "Unsupported Media Type", errorJson("Format media tidak cocok dengan file. Gunakan JPEG, PNG, GIF, WebP, HEIC/AVIF yang didukung, MP4/MOV, atau WebM."));
            return;
        }
        String filename = queryParam(query, "filename");
        filename = cleanFileName(filename);
        if (filename.length() == 0) filename = "media" + safeExtension(mime);
        long now = System.currentTimeMillis();
        JSONObject result = new JSONObject();
        if ("comment-sticker".equals(purpose)) {
            JSONObject sticker = new JSONObject();
            sticker.put("mediaId", id); sticker.put("owner", sender); sticker.put("mimeType", mime);
            sticker.put("fileName", filename); sticker.put("fileSize", length); sticker.put("createdAt", now);
            synchronized (DATA_LOCK) { socialStickerUploads = appendLimitedCopy(socialStickerUploads, sticker, 10000); saveArray(socialStickerUploadsFile, socialStickerUploads); }
            result.put("sticker", new JSONObject().put("mediaId", id).put("mimeType", mime).put("fileName", filename));
        } else if ("music".equals(purpose)) {
            JSONObject track = new JSONObject();
            track.put("id", UUID.randomUUID().toString().replace("-", ""));
            track.put("owner", sender); track.put("ownerName", me.optString("name", sender));
            String title = queryParam(query, "title").trim(); if (title.length() == 0) title = filename.replaceFirst("(?i)\\.[a-z0-9]{1,8}$", "");
            track.put("title", title.length() > 100 ? title.substring(0, 100) : title);
            String artist = queryParam(query, "artist").trim(); track.put("artist", artist.length() > 80 ? artist.substring(0, 80) : artist);
            int durationSeconds=0; try { durationSeconds=Integer.parseInt(queryParam(query,"duration")); } catch(Exception ignored) { durationSeconds=0; }
            if(durationSeconds<0||durationSeconds>7200)durationSeconds=0;
            track.put("mediaId", id); track.put("mimeType", mime); track.put("fileName", filename); track.put("fileSize", length); track.put("createdAt", now); track.put("uses", 0); track.put("durationSeconds",durationSeconds);
            synchronized (DATA_LOCK) { socialMusic = appendLimitedCopy(socialMusic, track, 2000); saveArray(socialMusicFile, socialMusic); }
            result.put("music", publicMusic(track, sender));
        } else if ("status".equals(purpose)) {
            JSONObject st = new JSONObject();
            st.put("id", UUID.randomUUID().toString()); st.put("phone", sender); st.put("name", me.optString("name"));
            st.put("text", statusText); st.put("time", now); st.put("mediaId", id); st.put("mediaType", isVideo ? "video" : "image");
            st.put("mimeType", mime); st.put("fileName", filename); st.put("fileSize", length);
            String statusAudience=queryParam(query,"audience"),statusDestination=queryParam(query,"destination");
            if(!("public".equals(statusAudience)||"followers".equals(statusAudience)||"contacts".equals(statusAudience)||"private".equals(statusAudience)))statusAudience="contacts";
            if(!("social".equals(statusDestination)||"chat".equals(statusDestination)||"both".equals(statusDestination)))statusDestination="social";
            st.put("audience",statusAudience);st.put("distribution",statusDestination);
            { String metaParam = queryParam(query, "meta"); if (metaParam.length() > 0 && metaParam.length() <= 1500) st.put("meta", metaParam); }
            synchronized (DATA_LOCK) { appendLimited(statuses, st, MAX_STATUSES); saveArray(statusesFile, statuses); }
            JSONObject publicStatus=new JSONObject(st.toString());
            if(!"chat".equals(statusDestination)){publicStatus.put("phone",socialId(me));publicStatus.put("isMine",true);}
            result.put("status", publicStatus);
        } else if ("post".equals(purpose)) {
            String audience = queryParam(query, "audience");
            JSONObject post = newSocialPost(me, statusText, audience, id, isVideo ? "video" : "image", mime, filename, length);
            post.put("allowDownload", !"false".equalsIgnoreCase(queryParam(query, "allowDownload")));
            String audioTrackId = queryParam(query, "audioId");
            if (audioTrackId.length() > 0) { synchronized (DATA_LOCK) { JSONObject track = findMusicTrack(audioTrackId); if (track == null || !canReadMusic(sender, track)) { if (file.exists()) file.delete(); sendJson(out, 404, "Not Found", errorJson("Audio pilihan tidak ditemukan atau tidak dapat diakses")); return; } post.put("audioId", audioTrackId); post.put("audioTitle", track.optString("title")); post.put("audioArtist", track.optString("artist")); track.put("uses", track.optInt("uses", 0) + 1); saveArray(socialMusicFile, socialMusic); } }
            { String metaParam = queryParam(query, "meta"); if (metaParam.length() > 0 && metaParam.length() <= 1500) post.put("meta", metaParam); }
            synchronized (DATA_LOCK) { socialPosts = appendLimitedCopy(socialPosts, post, 500); saveArray(socialPostsFile, socialPosts); }
            result.put("post", publicPost(post, sender));
        } else {
            String type = isVideo ? "video" : (isImage ? "image" : "file");
            JSONObject msg = newMessage(sender, recipient, "", type, "");
            msg.put("mediaId", id); msg.put("mimeType", mime); msg.put("fileName", filename); msg.put("fileSize", length); if("1".equals(queryParam(query,"once"))||"true".equalsIgnoreCase(queryParam(query,"once")))msg.put("viewOnce",true);
            synchronized (DATA_LOCK) { appendLimited(messages, msg, MAX_MESSAGES); saveArray(messagesFile, messages); }
            result.put("message", msg);
        }
        result.put("ok", true); result.put("mediaId", id); result.put("mimeType", mime); result.put("fileName", filename); result.put("fileSize", length);
        sendJson(out, 201, "Created", result.toString());
    }

    private boolean hasValidAudioSignature(File file, String mime) {
        if (file == null || mime == null) return false;
        // MediaRecorder may append codec parameters, e.g. audio/webm;codecs=opus.
        int mimeParams = mime.indexOf(';');
        if (mimeParams >= 0) mime = mime.substring(0, mimeParams).trim();
        FileInputStream input = null;
        try {
            byte[] h = new byte[16]; int total = 0; input = new FileInputStream(file);
            while (total < h.length) { int n = input.read(h, total, h.length-total); if (n < 0) break; total += n; }
            if (mime.equals("audio/mpeg") || mime.equals("audio/mp3") || mime.equals("audio/x-mpeg")) return total >= 2 && ((h[0]=='I' && h[1]=='D') || ((h[0]&255)==0xFF && ((h[1]&0xE0)==0xE0)));
            if (mime.equals("audio/aac") || mime.equals("audio/aacp") || mime.equals("audio/x-aac")) return total >= 2 && (h[0]&255)==0xFF && ((h[1]&0xF6)==0xF0);
            if (mime.equals("audio/mp4") || mime.equals("audio/m4a") || mime.equals("audio/x-m4a")) return total >= 12 && h[4]=='f' && h[5]=='t' && h[6]=='y' && h[7]=='p';
            if (mime.equals("audio/ogg") || mime.equals("audio/opus")) return total >= 4 && h[0]=='O' && h[1]=='g' && h[2]=='g' && h[3]=='S';
            if (mime.equals("audio/wav") || mime.equals("audio/wave") || mime.equals("audio/x-wav")) return total >= 12 && h[0]=='R' && h[1]=='I' && h[2]=='F' && h[3]=='F' && h[8]=='W' && h[9]=='A' && h[10]=='V' && h[11]=='E';
            if (mime.equals("audio/webm") || mime.equals("audio/x-matroska")) return total >= 4 && (h[0]&255)==0x1A && (h[1]&255)==0x45 && (h[2]&255)==0xDF && (h[3]&255)==0xA3;
            return false;
        } catch (Exception ignored) { return false; }
        finally { if (input != null) try { input.close(); } catch (Exception ignored) { } }
    }

    private boolean hasValidSocialMediaSignature(File file, String mime) {
        if (file == null || mime == null) return false;
        FileInputStream input = null;
        try {
            byte[] h = new byte[16]; int total = 0;
            input = new FileInputStream(file);
            while (total < h.length) { int n = input.read(h, total, h.length - total); if (n < 0) break; total += n; }
            if (mime.equals("image/jpeg")) return total >= 3 && (h[0]&255)==0xFF && (h[1]&255)==0xD8 && (h[2]&255)==0xFF;
            if (mime.equals("image/png")) return total >= 8 && (h[0]&255)==0x89 && h[1]=='P' && h[2]=='N' && h[3]=='G' && (h[4]&255)==0x0D && (h[5]&255)==0x0A && (h[6]&255)==0x1A && (h[7]&255)==0x0A;
            if (mime.equals("image/gif")) return total >= 6 && h[0]=='G' && h[1]=='I' && h[2]=='F' && h[3]=='8' && (h[4]=='7' || h[4]=='9') && h[5]=='a';
            if (mime.equals("image/webp")) return total >= 12 && h[0]=='R' && h[1]=='I' && h[2]=='F' && h[3]=='F' && h[8]=='W' && h[9]=='E' && h[10]=='B' && h[11]=='P';
            if (mime.startsWith("image/avif") || mime.startsWith("image/heic") || mime.startsWith("image/heif")) {
                if (total < 12 || h[4]!='f' || h[5]!='t' || h[6]!='y' || h[7]!='p') return false;
                String brand = new String(h, 8, 4, LATIN1);
                return "avif".equals(brand) || "avis".equals(brand) || "heic".equals(brand) || "heix".equals(brand) || "hevc".equals(brand) || "hevx".equals(brand) || "mif1".equals(brand);
            }
            if (mime.startsWith("video/quicktime") || mime.startsWith("video/mp4") || mime.startsWith("video/3gpp") || mime.startsWith("video/x-m4v"))
                return total >= 12 && h[4]=='f' && h[5]=='t' && h[6]=='y' && h[7]=='p';
            if (mime.startsWith("video/webm")) return total >= 4 && (h[0]&255)==0x1A && (h[1]&255)==0x45 && (h[2]&255)==0xDF && (h[3]&255)==0xA3;
            return false;
        } catch (Exception ignored) { return false; }
        finally { if (input != null) try { input.close(); } catch (Exception ignored) { } }
    }

    private long mediaBytesUsed() {
        long total = 0; File[] files = mediaDir == null ? null : mediaDir.listFiles();
        if (files != null) for (File file : files) if (file != null && file.isFile()) total += file.length();
        return total;
    }

    private String cleanFileName(String name) {
        if (name == null) return "";
        name = name.replace('\\', '_').replace('/', '_').replace('\r', '_').replace('\n', '_').replace('"', '_').trim();
        if (name.length() > 100) name = name.substring(name.length() - 100);
        if (".".equals(name) || "..".equals(name)) return "";
        return name;
    }

    private String safeExtension(String mime) {
        if (mime == null) return ".bin";
        if ("video/mp4".equals(mime)) return ".mp4";
        if ("video/webm".equals(mime)) return ".webm";
        if ("video/quicktime".equals(mime)) return ".mov";
        if ("video/3gpp".equals(mime)) return ".3gp";
        if ("image/jpeg".equals(mime)) return ".jpg";
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        if ("application/pdf".equals(mime)) return ".pdf";
        if ("text/plain".equals(mime)) return ".txt";
        if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime)) return ".mp3";
        if ("audio/aac".equals(mime) || "audio/aacp".equals(mime) || "audio/x-aac".equals(mime)) return ".aac";
        if ("audio/mp4".equals(mime) || "audio/m4a".equals(mime) || "audio/x-m4a".equals(mime)) return ".m4a";
        if ("audio/ogg".equals(mime) || "audio/opus".equals(mime)) return ".ogg";
        if ("audio/wav".equals(mime) || "audio/wave".equals(mime) || "audio/x-wav".equals(mime)) return ".wav";
        if ("audio/webm".equals(mime)) return ".webm";
        return ".bin";
    }

    private boolean claimViewOnceMedia(String phone,String mediaId,String field,boolean consume)throws JSONException{
        synchronized(DATA_LOCK){for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m==null||!mediaId.equals(m.optString(field))||!m.optBoolean("viewOnce",false))continue;if(!samePhone(phone,m.optString("recipient")))return true;if(m.optLong("viewOnceOpenedAt",0)>0)return false;if(consume){m.put("viewOnceOpenedAt",System.currentTimeMillis());m.put("viewOnceOpenedBy",phone);saveArray(messagesFile,messages);}return true;}}return true;
    }

    private void getMedia(OutputStream out, String query, String rangeHeader) throws IOException, JSONException {
        JSONObject me = userForToken(queryParam(query, "token"));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = queryParam(query, "id");
        if (id.length() != 32 || !id.matches("[a-fA-F0-9]+")) { sendJson(out, 400, "Bad Request", errorJson("ID media tidak valid")); return; }
        File mediaFile=new File(mediaDir,id+".bin");if(!mediaFile.exists()){sendJson(out,404,"Not Found",errorJson("File media tidak tersedia"));return;}
        boolean authorized = false, inline = false, viewOnceMedia = false, onceDenied = false, invalidMediaFile = false, downloadDenied = false; String mime = "application/octet-stream", filename = "download.bin"; long expectedSize = 0;
        String phone = me.optString("phone"), onceSessionKey=queryParam(query,"onceKey");
        boolean invalidOnceSession = false;
        synchronized (DATA_LOCK) {
            for (int i=0;i<messages.length();i++) {
                JSONObject m=messages.optJSONObject(i); if(m==null) continue;
                if(id.equals(m.optString("mediaId")) || id.equals(m.optString("videoId"))) {
                    if((samePhone(phone,m.optString("sender")) || samePhone(phone,m.optString("recipient"))) && !isBlockedEither(m.optString("sender"),m.optString("recipient"))) {
                        mime=m.optString("mimeType","application/octet-stream"); filename=cleanFileName(m.optString("fileName","download"+safeExtension(mime))); expectedSize=m.optLong("fileSize",0); inline=mime.startsWith("video/")||mime.startsWith("image/")||mime.startsWith("audio/");
                        // Validate storage before consuming the one-time view.
                        if(expectedSize>0 && mediaFile.length()!=expectedSize){invalidMediaFile=true;break;}
                        if(m.optBoolean("viewOnce",false)&&samePhone(phone,m.optString("recipient"))){
                            viewOnceMedia=true;
                            if(!onceSessionKey.matches("[a-fA-F0-9]{32}")){invalidOnceSession=true;break;}
                            long openedAt=m.optLong("viewOnceOpenedAt",0);
                            if(openedAt>0){
                                boolean sameSession=phone.equals(m.optString("viewOnceOpenedBy"))&&onceSessionKey.equals(m.optString("viewOnceSessionKey"))&&System.currentTimeMillis()-openedAt<10L*60L*1000L;
                                if(!sameSession)onceDenied=true;
                            }else{
                                m.put("viewOnceOpenedAt",System.currentTimeMillis());m.put("viewOnceOpenedBy",phone);m.put("viewOnceSessionKey",onceSessionKey);saveArray(messagesFile,messages);
                            }
                        }
                        authorized=!onceDenied&&!invalidOnceSession; break;
                    }
                }
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<statuses.length();i++) {
                JSONObject st=statuses.optJSONObject(i); if(st==null) continue;
                if(id.equals(st.optString("mediaId")) && canReadStatus(phone,st)) {
                    authorized=true; mime=st.optString("mimeType", "image/jpeg"); filename=cleanFileName(st.optString("fileName","status"+safeExtension(mime))); expectedSize=st.optLong("fileSize",0); inline=true; break;
                }
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialPosts.length();i++) {
                JSONObject post=socialPosts.optJSONObject(i); if(post==null || !id.equals(post.optString("mediaId"))) continue;
                if(canReadPost(phone, post)) {
                    if ("1".equals(queryParam(query,"download")) && !post.optBoolean("allowDownload",true)) { downloadDenied=true; break; }
                    authorized=true; mime=post.optString("mimeType","application/octet-stream"); filename=cleanFileName(post.optString("fileName","post"+safeExtension(mime))); expectedSize=post.optLong("fileSize",0); inline=mime.startsWith("image/")||mime.startsWith("video/"); break;
                }
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialPosts.length();i++) {
                JSONObject post=socialPosts.optJSONObject(i); if(post==null || !canReadPost(phone,post)) continue;
                JSONArray comments=post.optJSONArray("comments"); if(comments==null) continue;
                for(int j=0;j<comments.length();j++){JSONObject c=comments.optJSONObject(j);if(c!=null&&id.equals(c.optString("mediaId"))){JSONObject meta=findStickerUpload(id);if(meta!=null){authorized=true;mime=meta.optString("mimeType","image/jpeg");filename=cleanFileName(meta.optString("fileName","sticker"+safeExtension(mime)));expectedSize=meta.optLong("fileSize",0);inline=true;}break;}}
                if(authorized)break;
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialStickerFavorites.length();i++) {
                JSONObject fav=socialStickerFavorites.optJSONObject(i); if(fav==null || !phone.equals(fav.optString("owner")) || !id.equals(fav.optString("mediaId"))) continue;
                JSONObject meta=findStickerUpload(id); if(meta!=null){authorized=true;mime=meta.optString("mimeType","image/jpeg");filename=cleanFileName(meta.optString("fileName","sticker"+safeExtension(mime)));expectedSize=meta.optLong("fileSize",0);inline=true;break;}
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialStickerUploads.length();i++) {
                JSONObject sticker=socialStickerUploads.optJSONObject(i); if(sticker==null || !phone.equals(sticker.optString("owner")) || !id.equals(sticker.optString("mediaId"))) continue;
                authorized=true; mime=sticker.optString("mimeType","image/jpeg"); filename=cleanFileName(sticker.optString("fileName","sticker"+safeExtension(mime))); expectedSize=sticker.optLong("fileSize",0); inline=true; break;
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialMusic.length();i++) {
                JSONObject track=socialMusic.optJSONObject(i); if(track==null || !id.equals(track.optString("mediaId"))) continue;
                if(canReadMusic(phone,track)) { authorized=true; mime=track.optString("mimeType","audio/mpeg"); filename=cleanFileName(track.optString("fileName","music"+safeExtension(mime))); expectedSize=track.optLong("fileSize",0); inline=true; break; }
            }
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialActivityHistory.length();i++){JSONObject history=socialActivityHistory.optJSONObject(i);if(history!=null&&phone.equals(history.optString("owner"))&&"story".equals(history.optString("type"))&&id.equals(history.optString("mediaId"))){authorized=true;mime=history.optString("mimeType",history.optString("mediaType","image").equals("video")?"video/mp4":"image/jpeg");filename=cleanFileName(history.optString("fileName","story-history"+safeExtension(mime)));expectedSize=history.optLong("fileSize",0);inline=mime.startsWith("image/")||mime.startsWith("video/");break;}}
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialStoryCollections.length();i++){JSONObject saved=socialStoryCollections.optJSONObject(i);if(saved!=null&&phone.equals(saved.optString("owner"))&&id.equals(saved.optString("mediaId"))){authorized=true;mime=saved.optString("mimeType","image/jpeg");filename="story-saved"+safeExtension(mime);expectedSize=saved.optLong("fileSize",0);inline=mime.startsWith("image/")||mime.startsWith("video/");break;}}
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialStoryHistory.length();i++){JSONObject saved=socialStoryHistory.optJSONObject(i);if(saved!=null&&phone.equals(saved.optString("phone"))&&id.equals(saved.optString("mediaId"))){authorized=true;mime=saved.optString("mimeType","image/jpeg");filename="story-history"+safeExtension(mime);expectedSize=saved.optLong("fileSize",0);inline=mime.startsWith("image/")||mime.startsWith("video/");break;}}
            if(!authorized && !invalidMediaFile && !invalidOnceSession && !onceDenied) for(int i=0;i<socialHighlights.length();i++) {
                JSONObject h=socialHighlights.optJSONObject(i); if(h==null || !id.equals(h.optString("mediaId"))) continue;
                JSONObject owner=findUser(h.optString("owner"));
                String highlightVisibility=h.optString("visibility","public");boolean highlightAllowed=phone.equals(h.optString("owner"))||("public".equals(highlightVisibility))||("followers".equals(highlightVisibility)&&isFollowing(phone,h.optString("owner")))||("friends".equals(highlightVisibility)&&isSocialFriend(phone,h.optString("owner")));if(owner!=null&&!isBlockedEither(phone,h.optString("owner"))&&highlightAllowed&&(phone.equals(h.optString("owner"))||!"private".equals(socialPrivacy(owner).optString("account","public"))||isFollowing(phone,h.optString("owner")))) { authorized=true; mime=h.optString("mimeType","image/jpeg"); filename="highlight"+safeExtension(mime); inline=mime.startsWith("image/")||mime.startsWith("video/"); break; }
            }
        }
        if(downloadDenied){sendJson(out,403,"Forbidden",errorJson("Pemilik postingan menonaktifkan unduhan video"));return;}if(invalidMediaFile){sendJson(out,404,"Not Found",errorJson("File media tidak lengkap atau tidak cocok dengan metadata"));return;}if(invalidOnceSession){sendJson(out,400,"Bad Request",errorJson("Sesi pembukaan sekali lihat tidak valid"));return;}if(onceDenied){sendJson(out,410,"Gone",errorJson("Media sekali lihat sudah dibuka"));return;}if(!authorized){sendJson(out,404,"Not Found",errorJson("Media tidak ditemukan atau kamu tidak punya akses"));return;}
        File f=mediaFile; if(expectedSize>0 && f.length()!=expectedSize){sendJson(out,404,"Not Found",errorJson("File media tidak tersedia"));return;}
        if(mime.length()==0 || mime.contains("\r") || mime.contains("\n")) mime="application/octet-stream";
        long start=0, end=f.length()-1; boolean partial=false;
        if(rangeHeader!=null && rangeHeader.startsWith("bytes=") && f.length()>0) {
            try {
                String spec=rangeHeader.substring(6).trim(); int dash=spec.indexOf('-');
                if(dash<0) throw new Exception();
                String a=spec.substring(0,dash).trim(), b=spec.substring(dash+1).trim();
                if(a.length()==0) { long suffix=Long.parseLong(b); if(suffix<=0) throw new Exception(); start=Math.max(0,f.length()-suffix); }
                else start=Long.parseLong(a);
                if(b.length()>0) end=Math.min(f.length()-1,Long.parseLong(b));
                if(start<0 || start>=f.length() || end<start) throw new Exception();
                partial=true;
            } catch(Exception badRange) { String h="HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+f.length()+"\r\nContent-Length: 0\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n";out.write(h.getBytes(LATIN1));out.flush();return; }
        }
        long count=end-start+1; String disposition=inline?"inline":"attachment";
        StringBuilder h=new StringBuilder(); h.append("HTTP/1.1 ").append(partial?"206 Partial Content":"200 OK").append("\r\n");
        h.append("Content-Type: ").append(mime).append("\r\nContent-Length: ").append(count).append("\r\nAccept-Ranges: bytes\r\n");
        if(partial) h.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(f.length()).append("\r\n");
        h.append("Content-Disposition: ").append(disposition).append("; filename=\"").append(filename.replace("\"","_")).append("\"\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n"); if(viewOnceMedia)h.append("Cache-Control: no-store, no-cache, must-revalidate\r\nPragma: no-cache\r\n");else h.append("Cache-Control: private, max-age=300\r\n");h.append("Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type, X-RKM-Token, Range\r\nConnection: close\r\n\r\n");
        out.write(h.toString().getBytes(LATIN1));
        FileInputStream fin=null; try { fin=new FileInputStream(f); long skip=start; while(skip>0){long n=fin.skip(skip);if(n<=0){if(fin.read()<0)break;n=1;}skip-=n;}byte[] buf=new byte[8192];long remain=count;while(remain>0){int n=fin.read(buf,0,(int)Math.min(buf.length,remain));if(n<0)break;out.write(buf,0,n);remain-=n;}out.flush(); } finally { if(fin!=null)try{fin.close();}catch(Exception ignored){} }
    }

    private JSONObject findLive(String id) { if(id==null)return null; for(int i=0;i<liveSessions.length();i++){JSONObject s=liveSessions.optJSONObject(i);if(s!=null&&id.equals(s.optString("id")))return s;}return null; }
    private boolean isLiveParticipant(JSONObject live,String phone,long now) {
        if(live==null||phone==null)return false;JSONArray a=live.optJSONArray("participants");if(a==null)return false;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&phone.equals(p.optString("phone"))&&now-p.optLong("lastSeen",0)<45000)return true;}return false;
    }
    private boolean isLiveViewer(JSONObject live,String phone,long now) {
        if(live==null||phone==null)return false;JSONArray a=live.optJSONArray("viewers");if(a==null)return false;
        for(int i=0;i<a.length();i++){JSONObject v=a.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone"))&&now-v.optLong("lastSeen",0)<45000)return true;}return false;
    }
    private int liveParticipantCount(JSONObject live,long now) {
        JSONArray a=live==null?null:live.optJSONArray("participants");int n=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&now-p.optLong("lastSeen",0)<45000)n++;}return n;
    }
    private int liveViewerCount(JSONObject live, long now) { JSONArray a=live.optJSONArray("viewers");int n=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.optJSONObject(i);if(v!=null&&now-v.optLong("lastSeen",0)<45000)n++;}return n; }
    private JSONArray publicLiveParticipants(JSONObject live,long now) throws JSONException {
        JSONArray out=new JSONArray(),a=live.optJSONArray("participants");if(a==null)return out;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null||now-p.optLong("lastSeen",0)>=45000)continue;JSONObject u=findUser(p.optString("phone"));if(u==null)continue;JSONObject item=new JSONObject();item.put("id",socialId(u));item.put("name",socialDisplayName(u));item.put("role",p.optString("role","guest"));item.put("cameraOn",p.optBoolean("cameraOn",true));item.put("micOn",p.optBoolean("micOn",true));out.put(item);}return out;
    }
    private JSONObject publicLive(JSONObject live, long now, String viewer) throws JSONException {
        JSONObject r=new JSONObject();r.put("id",live.optString("id"));JSONObject hostUser=findUser(live.optString("host"));r.put("host",hostUser==null?"":socialId(hostUser));r.put("isMine",viewer!=null&&viewer.equals(live.optString("host")));r.put("hostName",socialDisplayName(hostUser));r.put("title",live.optString("title"));r.put("description",live.optString("description",""));r.put("audience",live.optString("audience","public"));r.put("commentsEnabled",live.optBoolean("commentsEnabled",true));r.put("startedAt",live.optLong("startedAt"));r.put("active",live.optBoolean("active"));r.put("viewerCount",liveViewerCount(live,now));
        JSONArray participants=publicLiveParticipants(live,now);r.put("participants",participants);r.put("participantCount",participants.length());if(viewer!=null){JSONObject u=findUser(viewer);r.put("selfId",u==null?"":socialId(u));}
        JSONArray likes=live.optJSONArray("likes");int count=0;boolean liked=false;if(likes!=null)for(int i=0;i<likes.length();i++){JSONObject l=likes.optJSONObject(i);if(l!=null){count++;if(viewer!=null&&viewer.equals(l.optString("phone")))liked=true;}}r.put("likesCount",count);r.put("liked",liked);r.put("isFollowing",viewer!=null&&isFollowing(viewer,live.optString("host")));return r;
    }

    private void startLive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String title=d.optString("title","Live OHLI").trim(),description=d.optString("description","").trim(),audience=d.optString("audience","public");if(title.length()==0)title="Live OHLI";if(title.length()>80||description.length()>240){sendJson(out,400,"Bad Request",errorJson("Judul maksimal 80 karakter dan deskripsi 240 karakter"));return;}if(!"public".equals(audience)&&!"followers".equals(audience)&&!"private".equals(audience))audience="public";
        String phone=me.optString("phone");long now=System.currentTimeMillis();JSONObject live=new JSONObject();
        synchronized(DATA_LOCK){for(int i=0;i<liveSessions.length();i++){JSONObject old=liveSessions.optJSONObject(i);if(old!=null&&phone.equals(old.optString("host"))&&old.optBoolean("active")){old.put("active",false);old.put("endedAt",now);}}
            live.put("id",UUID.randomUUID().toString().replace("-",""));live.put("host",phone);live.put("hostName",me.optString("name"));live.put("title",title);live.put("description",description);live.put("audience",audience);live.put("commentsEnabled",d.optBoolean("commentsEnabled",true));live.put("likes",new JSONArray());live.put("startedAt",now);live.put("lastSeen",now);live.put("active",true);live.put("viewers",new JSONArray());
            JSONArray participants=new JSONArray();JSONObject hostParticipant=new JSONObject();hostParticipant.put("phone",phone);hostParticipant.put("name",me.optString("name"));hostParticipant.put("role","host");hostParticipant.put("lastSeen",now);hostParticipant.put("cameraOn",true);hostParticipant.put("micOn",true);participants.put(hostParticipant);live.put("participants",participants);live.put("joinRequests",new JSONArray());live.put("invites",new JSONArray());live.put("maxParticipants",4);
            appendLimited(liveSessions,live,MAX_LIVE_SESSIONS);saveArray(liveFile,liveSessions);
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("live",publicLive(live,now,me.optString("phone")));sendJson(out,201,"Created",r.toString());
    }

    private boolean canReadLive(String viewer,JSONObject live) {
        if(live==null)return false;String host=live.optString("host"),audience=live.optString("audience","public");
        if(viewer!=null&&viewer.equals(host))return true;if(isBlockedEither(viewer,host))return false;
        if("public".equals(audience))return true;
        if("followers".equals(audience)&&isFollowing(viewer,host))return true;
        JSONArray invites=live.optJSONArray("invites");
        if(invites!=null)for(int i=0;i<invites.length();i++){JSONObject inv=invites.optJSONObject(i);if(inv!=null&&viewer.equals(inv.optString("to"))&&"accepted".equals(inv.optString("status")))return true;}
        return false;
    }

    private void toggleLiveLike(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=me.optString("phone"),id=d.optString("liveId","");JSONObject live;boolean liked=false;
        synchronized(DATA_LOCK){live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("Live tidak ditemukan"));return;}if(!canReadLive(phone,live)){sendJson(out,403,"Forbidden",errorJson("Live ini tidak dapat diakses"));return;}JSONArray old=live.optJSONArray("likes");if(old==null)old=new JSONArray();JSONArray next=new JSONArray();boolean found=false;for(int i=0;i<old.length();i++){JSONObject x=old.optJSONObject(i);if(x==null)continue;if(phone.equals(x.optString("phone"))){found=true;continue;}next.put(x);}if(!found){JSONObject x=new JSONObject();x.put("phone",phone);x.put("time",System.currentTimeMillis());next.put(x);liked=true;}live.put("likes",next);saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("liked",liked);r.put("live",publicLive(live,System.currentTimeMillis(),phone));sendJson(out,200,"OK",r.toString());
    }

    private void getLiveSessions(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        long now=System.currentTimeMillis();JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=0;i<liveSessions.length();i++){JSONObject live=liveSessions.optJSONObject(i);if(live==null||!live.optBoolean("active"))continue;if(now-live.optLong("lastSeen",0)>90000)continue;if(!canReadLive(me.optString("phone"),live))continue;list.put(publicLive(live,now,me.optString("phone")));}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("lives",list);sendJson(out,200,"OK",r.toString());
    }

    private void getLiveParticipants(OutputStream out,String query) throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=queryParam(query,"liveId"),phone=me.optString("phone");synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")||!canReadLive(phone,live)){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif atau tidak dapat diakses"));return;}JSONObject r=new JSONObject();JSONArray participants=publicLiveParticipants(live,System.currentTimeMillis());r.put("ok",true);r.put("participants",participants);r.put("participantCount",participants.length());r.put("viewerCount",liveViewerCount(live,System.currentTimeMillis()));r.put("selfId",socialId(me));boolean active=isLiveParticipant(live,phone,System.currentTimeMillis());r.put("isParticipant",active);String requestStatus="";JSONArray requests=live.optJSONArray("joinRequests");if(requests!=null)for(int i=requests.length()-1;i>=0;i--){JSONObject q=requests.optJSONObject(i);if(q!=null&&phone.equals(q.optString("phone"))){requestStatus=q.optString("status");break;}}r.put("myRequestStatus",requestStatus);sendJson(out,200,"OK",r.toString());}
    }

    private void getLiveInvites(OutputStream out,String query) throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=me.optString("phone");JSONArray results=new JSONArray();long now=System.currentTimeMillis();synchronized(DATA_LOCK){for(int i=liveSessions.length()-1;i>=0;i--){JSONObject live=liveSessions.optJSONObject(i);if(live==null||!live.optBoolean("active")||now-live.optLong("lastSeen",0)>90000)continue;JSONArray invites=live.optJSONArray("invites");if(invites==null)continue;for(int j=0;j<invites.length();j++){JSONObject inv=invites.optJSONObject(j);if(inv==null||!phone.equals(inv.optString("to"))||!"pending".equals(inv.optString("status")))continue;JSONObject item=new JSONObject();item.put("id",inv.optString("id"));item.put("live",publicLive(live,now,phone));item.put("hostName",socialDisplayName(findUser(live.optString("host"))));item.put("title",live.optString("title"));item.put("time",inv.optLong("time"));results.put(item);}}}JSONObject r=new JSONObject();r.put("ok",true);r.put("invites",results);sendJson(out,200,"OK",r.toString());
    }

    private void getLiveRequests(OutputStream out,String query) throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=queryParam(query,"liveId"),phone=me.optString("phone");synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")||!canReadLive(phone,live)){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif atau tidak dapat diakses"));return;}JSONArray outRequests=new JSONArray(),requests=live.optJSONArray("joinRequests");boolean isHost=phone.equals(live.optString("host"));String myStatus="";if(requests!=null)for(int i=0;i<requests.length();i++){JSONObject q=requests.optJSONObject(i);if(q==null)continue;if(phone.equals(q.optString("phone")))myStatus=q.optString("status");if(isHost&&"pending".equals(q.optString("status"))){JSONObject u=findUser(q.optString("phone"));JSONObject item=new JSONObject();item.put("id",q.optString("id"));item.put("userId",u==null?"":socialId(u));item.put("name",socialDisplayName(u));item.put("source",q.optString("source","viewer"));item.put("time",q.optLong("time"));outRequests.put(item);}}JSONObject r=new JSONObject();r.put("ok",true);r.put("isHost",isHost);r.put("requests",outRequests);r.put("myRequestStatus",myStatus);r.put("participants",publicLiveParticipants(live,System.currentTimeMillis()));r.put("participantCount",liveParticipantCount(live,System.currentTimeMillis()));sendJson(out,200,"OK",r.toString());}
    }

    private void inviteLive(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),target=resolveAccountKey(d.optString("target","")),phone=me.optString("phone");
        if(!validPhone(target)||samePhone(phone,target)||findUser(target)==null){sendJson(out,400,"Bad Request",errorJson("Pilih teman OHLI yang valid"));return;}
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif"));return;}if(!phone.equals(live.optString("host"))){sendJson(out,403,"Forbidden",errorJson("Hanya host yang dapat mengundang teman"));return;}if(isBlockedEither(phone,target)){sendJson(out,403,"Forbidden",errorJson("Undangan tidak dapat dikirim karena salah satu akun memblokir akun lainnya"));return;}JSONArray invites=live.optJSONArray("invites");if(invites==null){invites=new JSONArray();live.put("invites",invites);}for(int i=0;i<invites.length();i++){JSONObject old=invites.optJSONObject(i);if(old!=null&&target.equals(old.optString("to"))&&"pending".equals(old.optString("status"))){sendJson(out,409,"Conflict",errorJson("Undangan untuk teman ini masih menunggu jawaban"));return;}}if(invites.length()>=30){sendJson(out,429,"Too Many Requests",errorJson("Batas undangan LIVE tercapai"));return;}JSONObject inv=new JSONObject();inv.put("id",UUID.randomUUID().toString().replace("-",""));inv.put("to",target);inv.put("from",phone);inv.put("status","pending");inv.put("time",System.currentTimeMillis());invites.put(inv);saveArray(liveFile,liveSessions);try{addSocialNotification(target,phone,"live_invite",id,"Mengundangmu bergabung ke LIVE");}catch(Exception ignored){}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("invited",true);sendJson(out,201,"Created",r.toString());
    }

    private void respondLiveInvite(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),inviteId=d.optString("inviteId",""),phone=me.optString("phone");boolean accept=d.optBoolean("accept",false);
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE sudah berakhir"));return;}JSONArray invites=live.optJSONArray("invites");JSONObject target=null;if(invites!=null)for(int i=0;i<invites.length();i++){JSONObject inv=invites.optJSONObject(i);if(inv!=null&&inviteId.equals(inv.optString("id"))&&phone.equals(inv.optString("to"))&&"pending".equals(inv.optString("status"))){target=inv;break;}}if(target==null){sendJson(out,404,"Not Found",errorJson("Undangan tidak ditemukan atau sudah dijawab"));return;}target.put("status",accept?"accepted":"rejected");target.put("respondedAt",System.currentTimeMillis());if(accept){JSONArray viewers=live.optJSONArray("viewers");if(viewers==null){viewers=new JSONArray();live.put("viewers",viewers);}boolean has=false;for(int i=0;i<viewers.length();i++){JSONObject v=viewers.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone")))has=true;}if(!has){JSONObject v=new JSONObject();v.put("phone",phone);v.put("name",me.optString("name"));v.put("lastSeen",System.currentTimeMillis());viewers.put(v);}JSONArray reqs=live.optJSONArray("joinRequests");if(reqs==null){reqs=new JSONArray();live.put("joinRequests",reqs);}boolean pending=false;for(int i=0;i<reqs.length();i++){JSONObject q=reqs.optJSONObject(i);if(q!=null&&phone.equals(q.optString("phone"))&&"pending".equals(q.optString("status")))pending=true;}if(!pending){JSONObject q=new JSONObject();q.put("id",UUID.randomUUID().toString().replace("-",""));q.put("phone",phone);q.put("source","invite");q.put("status","pending");q.put("time",System.currentTimeMillis());reqs.put(q);}}saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("accepted",accept);r.put("awaitingHost",accept);sendJson(out,200,"OK",r.toString());
    }

    private void requestLiveJoin(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),phone=me.optString("phone");long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")||!canReadLive(phone,live)){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif atau tidak dapat diakses"));return;}if(isLiveParticipant(live,phone,now)){JSONObject r=new JSONObject();r.put("ok",true);r.put("alreadyParticipant",true);sendJson(out,200,"OK",r.toString());return;}if(liveParticipantCount(live,now)>=4){sendJson(out,409,"Conflict",errorJson("Panggung sudah penuh (maksimal 4 orang termasuk host)"));return;}JSONArray reqs=live.optJSONArray("joinRequests");if(reqs==null){reqs=new JSONArray();live.put("joinRequests",reqs);}for(int i=reqs.length()-1;i>=0;i--){JSONObject old=reqs.optJSONObject(i);if(old==null||!phone.equals(old.optString("phone")))continue;if("pending".equals(old.optString("status"))){sendJson(out,409,"Conflict",errorJson("Permintaanmu masih menunggu jawaban host"));return;}if("rejected".equals(old.optString("status"))){long responded=old.optLong("respondedAt",old.optLong("time",0));long remaining=30000L-(now-responded);if(remaining>0){sendJson(out,429,"Too Many Requests",errorJson("Tunggu "+Math.max(1,(remaining+999)/1000)+" detik sebelum meminta bergabung lagi"));return;}}break;}if(reqs.length()>=100){sendJson(out,429,"Too Many Requests",errorJson("Terlalu banyak permintaan bergabung di LIVE ini"));return;}JSONObject q=new JSONObject();q.put("id",UUID.randomUUID().toString().replace("-",""));q.put("phone",phone);q.put("source","viewer");q.put("status","pending");q.put("time",now);reqs.put(q);saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("pending",true);sendJson(out,201,"Created",r.toString());
    }

    private void respondLiveJoinRequest(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),requestId=d.optString("requestId",""),phone=me.optString("phone");boolean accept=d.optBoolean("accept",false);long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif"));return;}if(!phone.equals(live.optString("host"))){sendJson(out,403,"Forbidden",errorJson("Hanya host yang dapat menyetujui permintaan"));return;}JSONArray reqs=live.optJSONArray("joinRequests");JSONObject target=null;if(reqs!=null)for(int i=0;i<reqs.length();i++){JSONObject q=reqs.optJSONObject(i);if(q!=null&&requestId.equals(q.optString("id"))&&"pending".equals(q.optString("status"))){target=q;break;}}if(target==null){sendJson(out,404,"Not Found",errorJson("Permintaan tidak ditemukan atau sudah dijawab"));return;}String guest=target.optString("phone");if(accept){if(liveParticipantCount(live,now)>=4){sendJson(out,409,"Conflict",errorJson("Panggung sudah penuh (maksimal 4 orang termasuk host)"));return;}if(isBlockedEither(phone,guest)){sendJson(out,403,"Forbidden",errorJson("Akun ini diblokir"));return;}JSONArray participants=live.optJSONArray("participants");if(participants==null){participants=new JSONArray();live.put("participants",participants);}if(!isLiveParticipant(live,guest,now)){JSONObject participant=new JSONObject();JSONObject u=findUser(guest);participant.put("phone",guest);participant.put("name",u==null?"Pengguna OHLI":u.optString("name"));participant.put("role","guest");participant.put("lastSeen",now);participant.put("cameraOn",false);participant.put("micOn",false);participant.put("ready",false);participants.put(participant);}JSONArray viewers=live.optJSONArray("viewers"),remaining=new JSONArray();if(viewers!=null)for(int i=0;i<viewers.length();i++){JSONObject v=viewers.optJSONObject(i);if(v!=null&&!guest.equals(v.optString("phone")))remaining.put(v);}live.put("viewers",remaining);target.put("status","accepted");}else target.put("status","rejected");target.put("respondedAt",now);saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("accepted",accept);r.put("live",getPublicLiveSafe(id,phone));sendJson(out,200,"OK",r.toString());
    }

    private JSONObject getPublicLiveSafe(String id,String viewer) throws JSONException {JSONObject live=findLive(id);return live==null?new JSONObject():publicLive(live,System.currentTimeMillis(),viewer);}

    private void participantReadyLive(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),phone=me.optString("phone");long now=System.currentTimeMillis();boolean found=false;
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif"));return;}JSONArray a=live.optJSONArray("participants");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&phone.equals(x.optString("phone"))&&!phone.equals(live.optString("host"))){x.put("cameraOn",true);x.put("micOn",true);x.put("ready",true);x.put("lastSeen",now);found=true;break;}}if(found)saveArray(liveFile,liveSessions);}
        if(!found){sendJson(out,403,"Forbidden",errorJson("Persetujuan host untuk bergabung belum tersedia"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("ready",true);sendJson(out,200,"OK",r.toString());
    }

    private void participantStateLive(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),phone=me.optString("phone");long now=System.currentTimeMillis();boolean found=false;boolean camera=d.optBoolean("cameraOn",true),mic=d.optBoolean("micOn",true);
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif"));return;}JSONArray a=live.optJSONArray("participants");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&phone.equals(x.optString("phone"))){x.put("cameraOn",camera);x.put("micOn",mic);x.put("lastSeen",now);found=true;break;}}if(found)saveArray(liveFile,liveSessions);}
        if(!found){sendJson(out,403,"Forbidden",errorJson("Hanya peserta yang disetujui yang dapat mengubah status kamera/mikrofon"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("cameraOn",camera);r.put("micOn",mic);sendJson(out,200,"OK",r.toString());
    }

    private void participantLeaveLive(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),phone=me.optString("phone");long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("LIVE tidak aktif"));return;}if(phone.equals(live.optString("host"))){sendJson(out,403,"Forbidden",errorJson("Host tidak dapat keluar dari panggung; host dapat mengakhiri LIVE"));return;}JSONArray old=live.optJSONArray("participants"),next=new JSONArray();if(old!=null)for(int i=0;i<old.length();i++){JSONObject x=old.optJSONObject(i);if(x!=null&&!phone.equals(x.optString("phone")))next.put(x);}live.put("participants",next);JSONArray viewers=live.optJSONArray("viewers");if(viewers==null){viewers=new JSONArray();live.put("viewers",viewers);}boolean found=false;for(int i=0;i<viewers.length();i++){JSONObject v=viewers.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone"))){v.put("lastSeen",now);found=true;}}if(!found){JSONObject v=new JSONObject();v.put("phone",phone);v.put("name",me.optString("name"));v.put("lastSeen",now);viewers.put(v);}saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("leftStage",true);sendJson(out,200,"OK",r.toString());
    }

    private void stopLive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId","");long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null){sendJson(out,404,"Not Found",errorJson("Sesi Live tidak ditemukan"));return;}if(!me.optString("phone").equals(live.optString("host"))){sendJson(out,403,"Forbidden",errorJson("Hanya pembuat Live yang dapat mengakhirinya"));return;}live.put("active",false);live.put("endedAt",now);saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("ended",true);sendJson(out,200,"OK",r.toString());
    }

    private void joinLive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId","");long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")||now-live.optLong("lastSeen",0)>90000){sendJson(out,404,"Not Found",errorJson("Live sudah berakhir atau tidak ditemukan"));return;}String phone=me.optString("phone");if(!canReadLive(phone,live)){sendJson(out,403,"Forbidden",errorJson("Privasi siaran tidak mengizinkan akunmu menonton"));return;}if(phone.equals(live.optString("host"))){JSONObject r=new JSONObject();r.put("ok",true);r.put("live",publicLive(live,now,me.optString("phone")));sendJson(out,200,"OK",r.toString());return;}JSONArray viewers=live.optJSONArray("viewers");if(viewers==null){viewers=new JSONArray();live.put("viewers",viewers);}boolean found=false;for(int i=0;i<viewers.length();i++){JSONObject v=viewers.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone"))){v.put("lastSeen",now);v.put("name",me.optString("name"));found=true;break;}}if(!found){if(liveViewerCount(live,now)>=6){sendJson(out,429,"Too Many Requests",errorJson("Live ini mencapai batas uji 6 penonton langsung. Gunakan server relay untuk skala lebih besar."));return;}JSONObject v=new JSONObject();v.put("phone",phone);v.put("name",me.optString("name"));v.put("lastSeen",now);viewers.put(v);}saveArray(liveFile,liveSessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("joined",true);sendJson(out,200,"OK",r.toString());
    }

    private void leaveLive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId","");String phone=me.optString("phone");
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live!=null){if(phone.equals(live.optString("host"))){sendJson(out,403,"Forbidden",errorJson("Host harus mengakhiri siaran"));return;}JSONArray old=live.optJSONArray("viewers"), next=new JSONArray();if(old!=null)for(int i=0;i<old.length();i++){JSONObject v=old.optJSONObject(i);if(v!=null&&!phone.equals(v.optString("phone")))next.put(v);}live.put("viewers",next);JSONArray oldParticipants=live.optJSONArray("participants"),nextParticipants=new JSONArray();if(oldParticipants!=null)for(int i=0;i<oldParticipants.length();i++){JSONObject v=oldParticipants.optJSONObject(i);if(v!=null&&!phone.equals(v.optString("phone")))nextParticipants.put(v);}live.put("participants",nextParticipants);saveArray(liveFile,liveSessions);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("left",true);sendJson(out,200,"OK",r.toString());
    }

    private void heartbeatLive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId","");String phone=me.optString("phone");long now=System.currentTimeMillis();boolean found=false;
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live!=null&&live.optBoolean("active")){if(phone.equals(live.optString("host"))){live.put("lastSeen",now);JSONArray hostParticipants=live.optJSONArray("participants");boolean hostFound=false;if(hostParticipants==null){hostParticipants=new JSONArray();live.put("participants",hostParticipants);}for(int h=0;h<hostParticipants.length();h++){JSONObject hostPart=hostParticipants.optJSONObject(h);if(hostPart!=null&&phone.equals(hostPart.optString("phone"))){hostPart.put("lastSeen",now);hostFound=true;break;}}if(!hostFound){JSONObject hostUser=findUser(phone),hostPart=new JSONObject();hostPart.put("phone",phone);hostPart.put("name",hostUser==null?"Host":hostUser.optString("name"));hostPart.put("role","host");hostPart.put("lastSeen",now);hostPart.put("cameraOn",true);hostPart.put("micOn",true);hostParticipants.put(hostPart);}found=true;}else{JSONArray a=live.optJSONArray("participants");if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone"))){v.put("lastSeen",now);found=true;break;}}if(!found){a=live.optJSONArray("viewers");if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.optJSONObject(i);if(v!=null&&phone.equals(v.optString("phone"))){v.put("lastSeen",now);found=true;break;}}}}if(found)saveArray(liveFile,liveSessions);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("active",found);sendJson(out,200,"OK",r.toString());
    }

    private void postLiveSignal(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String liveId=d.optString("liveId",""),to=resolveAccountKey(d.optString("to","")),type=d.optString("type","");Object payload=d.opt("payload");
        if(!validPhone(to)||!("offer".equals(type)||"answer".equals(type)||"ice".equals(type)||"end".equals(type))){sendJson(out,400,"Bad Request",errorJson("Sinyal Live tidak valid"));return;}
        if(payload==null||JSONObject.NULL.equals(payload)||payload.toString().length()>90000){sendJson(out,413,"Payload Too Large",errorJson("Data sinyal terlalu besar"));return;}
        synchronized(DATA_LOCK){JSONObject live=findLive(liveId);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("Live tidak aktif"));return;}String from=me.optString("phone"),host=live.optString("host");long now=System.currentTimeMillis();boolean fromParticipant=isLiveParticipant(live,from,now),toParticipant=isLiveParticipant(live,to,now);boolean fromViewer=isLiveViewer(live,from,now),toViewer=isLiveViewer(live,to,now);boolean meshPair=fromParticipant&&toParticipant;boolean participantViewerPair=(fromParticipant&&toViewer)||(toParticipant&&fromViewer);if(!meshPair&&!participantViewerPair){sendJson(out,403,"Forbidden",errorJson("Sinyal hanya boleh dikirim antar peserta yang disetujui atau antara host dan penonton"));return;}
            JSONObject sig=new JSONObject();sig.put("id",++nextLiveSignalId);sig.put("liveId",liveId);sig.put("from",from);sig.put("to",to);sig.put("type",type);sig.put("payload",payload);sig.put("time",now);appendLimited(liveSignals,sig,MAX_LIVE_SIGNALS);saveArray(liveSignalsFile,liveSignals);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("queued",true);sendJson(out,200,"OK",r.toString());
    }

    private void getLiveSignals(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String liveId=queryParam(query,"liveId");long since=0;try{since=Long.parseLong(queryParam(query,"since"));}catch(Exception ignored){}long now=System.currentTimeMillis();JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){for(int i=0;i<liveSignals.length();i++){JSONObject sig=liveSignals.optJSONObject(i);if(sig!=null&&me.optString("phone").equals(sig.optString("to"))&&liveId.equals(sig.optString("liveId"))&&sig.optLong("id",0)>since&&now-sig.optLong("time",now)<300000){JSONObject safe=new JSONObject(sig.toString());JSONObject fromUser=findUser(sig.optString("from")),toUser=findUser(sig.optString("to"));safe.put("from",fromUser==null?"":socialId(fromUser));safe.put("to",toUser==null?"":socialId(toUser));list.put(safe);}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("signals",list);sendJson(out,200,"OK",r.toString());
    }

    private void postLiveComment(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("liveId",""),text=d.optString("text","").trim();if(text.length()==0||text.length()>280){sendJson(out,400,"Bad Request",errorJson("Komentar Live harus 1–280 karakter"));return;}JSONObject comment=new JSONObject();comment.put("id",UUID.randomUUID().toString());comment.put("liveId",id);comment.put("phone",me.optString("phone"));comment.put("name",me.optString("name"));comment.put("text",text);comment.put("time",System.currentTimeMillis());
        synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")){sendJson(out,404,"Not Found",errorJson("Live tidak aktif"));return;}if(!canReadLive(me.optString("phone"),live)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke siaran ini"));return;}if(!live.optBoolean("commentsEnabled",true)){sendJson(out,403,"Forbidden",errorJson("Komentar LIVE dinonaktifkan oleh pembuat siaran"));return;}appendLimited(liveComments,comment,MAX_LIVE_COMMENTS);saveArray(liveCommentsFile,liveComments);}
        JSONObject safeComment=new JSONObject(comment.toString());safeComment.put("phone",socialId(me));safeComment.put("isMine",true);JSONObject r=new JSONObject();r.put("ok",true);r.put("comment",safeComment);sendJson(out,201,"Created",r.toString());
    }

    private void getLiveComments(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=queryParam(query,"liveId");long since=0;try{since=Long.parseLong(queryParam(query,"since"));}catch(Exception ignored){}JSONArray list=new JSONArray();synchronized(DATA_LOCK){JSONObject live=findLive(id);if(live==null||!live.optBoolean("active")||!canReadLive(me.optString("phone"),live)){sendJson(out,403,"Forbidden",errorJson("Komentar LIVE tidak dapat diakses"));return;}if(!live.optBoolean("commentsEnabled",true)){JSONObject empty=new JSONObject();empty.put("ok",true);empty.put("comments",list);sendJson(out,200,"OK",empty.toString());return;}for(int i=0;i<liveComments.length();i++){JSONObject c=liveComments.optJSONObject(i);if(c!=null&&id.equals(c.optString("liveId"))&&c.optLong("time",0)>since){JSONObject safe=new JSONObject(c.toString());JSONObject author=findUser(c.optString("phone"));safe.put("phone",author==null?"":socialId(author));safe.put("name",socialDisplayName(author));safe.put("isMine",samePhone(me.optString("phone"),c.optString("phone")));list.put(safe);}}}JSONObject r=new JSONObject();r.put("ok",true);r.put("comments",list);sendJson(out,200,"OK",r.toString());
    }

    // ---------------- OHLI Social API ----------------
    private JSONArray appendLimitedCopy(JSONArray old, JSONObject item, int max) throws JSONException {
        JSONArray next = new JSONArray(); int from = Math.max(0, old.length() - max + 1);
        for (int i = from; i < old.length(); i++) next.put(old.get(i)); next.put(item); return next;
    }

    private boolean isFollowing(String follower, String target) {
        for (int i=0;i<socialFollows.length();i++) { JSONObject f=socialFollows.optJSONObject(i); if(f!=null&&follower.equals(f.optString("follower"))&&target.equals(f.optString("following"))&&(!f.has("status")||"accepted".equals(f.optString("status"))))return true; }
        return false;
    }

    private JSONObject socialPrivacy(JSONObject user) {
        JSONObject p=user==null?null:user.optJSONObject("socialPrivacy");
        if(p==null)p=new JSONObject();
        try {
            if(!p.has("account"))p.put("account","public");
            if(!p.has("followers"))p.put("followers","everyone");
            if(!p.has("following"))p.put("following","everyone");
            if(!p.has("likes"))p.put("likes","everyone");
            if(!p.has("saved"))p.put("saved","private");
            if(!p.has("directMessages"))p.put("directMessages","everyone");
            if(!p.has("activity"))p.put("activity","everyone");
        } catch(JSONException ignored){}
        return p;
    }

    private JSONObject findFriendRecord(String a,String b) {
        if(a==null||b==null)return null;
        for(int i=socialFriends.length()-1;i>=0;i--){JSONObject f=socialFriends.optJSONObject(i);if(f!=null&&((a.equals(f.optString("a"))&&b.equals(f.optString("b")))||(a.equals(f.optString("b"))&&b.equals(f.optString("a")))))return f;}
        return null;
    }

    private boolean hasPendingFriendRequest(String requester,String recipient) {
        JSONObject f=findFriendRecord(requester,recipient);
        return f!=null&&"pending".equals(f.optString("status"))&&requester.equals(f.optString("requester"))&&recipient.equals(f.optString("recipient"));
    }

    private boolean isSocialFriend(String viewer,String owner) {
        if(viewer==null||owner==null||viewer.length()==0||owner.length()==0||viewer.equals(owner))return false;
        JSONObject record=findFriendRecord(viewer,owner);
        if(record!=null)return "accepted".equals(record.optString("status"));
        // Keep existing legacy relationships working for accounts created before v3.6.4.
        return (isFollowing(viewer,owner)&&isFollowing(owner,viewer))||hasSavedContact(owner,viewer);
    }

    private JSONObject publicFriendUser(String account,String viewer,long requestedAt)throws JSONException {
        JSONObject u=findUser(account);if(u==null)return null;
        JSONObject row=userPublicSocial(u,viewer);addSocialPresence(row,u,viewer);
        if(requestedAt>0)row.put("requestedAt",requestedAt);
        return row;
    }

    private void getSocialFriends(OutputStream out,String query)throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone");JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=0;i<users.length();i++){
                JSONObject candidate=users.optJSONObject(i);if(candidate==null)continue;String other=candidate.optString("phone");
                if(owner.equals(other)||isBlockedEither(owner,other)||!isSocialFriend(owner,other))continue;
                JSONObject row=publicFriendUser(other,owner,0);if(row!=null){JSONObject record=findFriendRecord(owner,other);row.put("friendSince",record==null?0:record.optLong("acceptedAt",record.optLong("time",0)));list.put(row);}
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("friends",list);r.put("count",list.length());sendJson(out,200,"OK",r.toString());
    }

    private void getSocialFriendRequests(OutputStream out,String query)throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone");JSONArray incoming=new JSONArray(),sent=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=socialFriends.length()-1;i>=0;i--){JSONObject f=socialFriends.optJSONObject(i);if(f==null||!"pending".equals(f.optString("status")))continue;
                String requester=f.optString("requester"),recipient=f.optString("recipient");
                if(owner.equals(recipient)&&!isBlockedEither(owner,requester)){JSONObject row=publicFriendUser(requester,owner,f.optLong("time"));if(row!=null)incoming.put(row);}
                else if(owner.equals(requester)&&!isBlockedEither(owner,recipient)){JSONObject row=publicFriendUser(recipient,owner,f.optLong("time"));if(row!=null)sent.put(row);}
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("incoming",incoming);r.put("sent",sent);r.put("incomingCount",incoming.length());r.put("sentCount",sent.length());sendJson(out,200,"OK",r.toString());
    }

    private void updateSocialFriendship(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),target=resolveAccountKey(d.optString("target",d.optString("user",""))),action=d.optString("action","request");
        if(!validPhone(target)||owner.equals(target)||findUser(target)==null){sendJson(out,400,"Bad Request",errorJson("Akun tujuan teman tidak valid"));return;}
        if(isBlockedEither(owner,target)){sendJson(out,403,"Forbidden",errorJson("Permintaan teman tidak tersedia untuk akun yang diblokir"));return;}
        if(!"request".equals(action)&&!"accept".equals(action)&&!"reject".equals(action)&&!"cancel".equals(action)&&!"remove".equals(action)){sendJson(out,400,"Bad Request",errorJson("Aksi pertemanan tidak dikenal"));return;}
        boolean isFriend=false,requested=false,received=false,changed=false,autoAccepted=false;long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){
            JSONObject current=findFriendRecord(owner,target);
            if("request".equals(action)){
                if(current!=null&&"accepted".equals(current.optString("status")))isFriend=true;
                else if(current==null&&isSocialFriend(owner,target))isFriend=true;
                else if(current!=null&&"pending".equals(current.optString("status"))){
                    if(owner.equals(current.optString("requester"))){requested=true;}
                    else if(target.equals(current.optString("requester"))&&owner.equals(current.optString("recipient"))){current.put("status","accepted");current.put("acceptedAt",now);current.put("updatedAt",now);saveArray(socialFriendsFile,socialFriends);isFriend=true;changed=true;autoAccepted=true;addSocialNotification(target,owner,"friend_accepted","","menerima permintaan pertemananmu");addSocialNotification(owner,target,"friend_mutual","","kalian sekarang berteman");}
                } else {
                    int recent=0;for(int i=0;i<socialFriends.length();i++){JSONObject f=socialFriends.optJSONObject(i);if(f!=null&&owner.equals(f.optString("requester"))&&f.optLong("time",0)>0&&now-f.optLong("time",0)<86400000L)recent++;}
                    if(recent>=30){sendJson(out,429,"Too Many Requests",errorJson("Terlalu banyak permintaan teman. Coba lagi nanti."));return;}
                    JSONObject f=new JSONObject();f.put("a",owner.compareTo(target)<0?owner:target);f.put("b",owner.compareTo(target)<0?target:owner);f.put("requester",owner);f.put("recipient",target);f.put("status","pending");f.put("time",now);f.put("updatedAt",now);
                    if(current!=null){for(int i=socialFriends.length()-1;i>=0;i--){JSONObject old=socialFriends.optJSONObject(i);if(old==current){socialFriends.put(i,f);break;}}}else socialFriends.put(f);
                    saveArray(socialFriendsFile,socialFriends);addSocialNotification(target,owner,"friend_request","","mengirim permintaan pertemanan");requested=true;changed=true;
                }
            } else if("accept".equals(action)||"reject".equals(action)){
                if(current==null||!"pending".equals(current.optString("status"))||!target.equals(current.optString("requester"))||!owner.equals(current.optString("recipient"))){sendJson(out,404,"Not Found",errorJson("Permintaan pertemanan tidak ditemukan"));return;}
                if("accept".equals(action)){current.put("status","accepted");current.put("acceptedAt",now);isFriend=true;addSocialNotification(target,owner,"friend_accepted","","menerima permintaan pertemananmu");}
                else{current.put("status","rejected");current.put("resolvedAt",now);addSocialNotification(target,owner,"friend_rejected","","menolak permintaan pertemanan");}
                current.put("updatedAt",now);saveArray(socialFriendsFile,socialFriends);changed=true;
            } else if("cancel".equals(action)){
                if(current==null||!"pending".equals(current.optString("status"))||!owner.equals(current.optString("requester"))||!target.equals(current.optString("recipient"))){sendJson(out,404,"Not Found",errorJson("Permintaan terkirim tidak ditemukan"));return;}
                current.put("status","cancelled");current.put("updatedAt",now);current.put("resolvedAt",now);saveArray(socialFriendsFile,socialFriends);changed=true;
            } else if("remove".equals(action)){
                if(current!=null&&"accepted".equals(current.optString("status"))){
                    current.put("status","removed");current.put("updatedAt",now);current.put("resolvedAt",now);saveArray(socialFriendsFile,socialFriends);changed=true;
                } else if(current==null&&isSocialFriend(owner,target)) {
                    // Legacy mutual-follow/contact friendships get a tombstone so removal is effective.
                    JSONObject tombstone=new JSONObject();tombstone.put("a",owner.compareTo(target)<0?owner:target);tombstone.put("b",owner.compareTo(target)<0?target:owner);tombstone.put("requester",owner);tombstone.put("recipient",target);tombstone.put("status","removed");tombstone.put("time",now);tombstone.put("updatedAt",now);tombstone.put("resolvedAt",now);socialFriends.put(tombstone);saveArray(socialFriendsFile,socialFriends);changed=true;
                } else {sendJson(out,404,"Not Found",errorJson("Pertemanan tidak ditemukan"));return;}
            }
            isFriend=isFriend||isSocialFriend(owner,target);requested=hasPendingFriendRequest(owner,target);received=hasPendingFriendRequest(target,owner);
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("action",action);r.put("isFriend",isFriend);r.put("requested",requested);r.put("received",received);r.put("changed",changed);r.put("mutualAccepted",autoAccepted);sendJson(out,200,"OK",r.toString());
    }

    private boolean canDirectMessage(String sender,String recipient) {
        if(sender==null||recipient==null||sender.length()==0||recipient.length()==0||isBlockedEither(sender,recipient))return false;
        JSONObject target=findUser(recipient);if(target==null)return false;
        String policy=socialPrivacy(target).optString("directMessages","everyone");
        if("everyone".equals(policy))return true;
        if("friends".equals(policy))return isSocialFriend(sender,recipient);
        return sender.equals(recipient);
    }

    private boolean canSeeSocialPresence(String viewer,String owner,JSONObject target) {
        if(viewer!=null&&viewer.equals(owner))return true;
        String policy=socialPrivacy(target).optString("activity","everyone");
        if("everyone".equals(policy))return true;
        if("friends".equals(policy))return isSocialFriend(viewer,owner);
        return false;
    }

    private void addSocialPresence(JSONObject out,JSONObject target,String viewer)throws JSONException {
        String owner=target.optString("phone");
        boolean online=canSeeSocialPresence(viewer,owner,target)&&target.optLong("socialLastActive",0)>=System.currentTimeMillis()-90000L;
        out.put("online",online);
    }

    private void heartbeatSocialPresence(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        long now=System.currentTimeMillis();
        synchronized(DATA_LOCK){JSONObject user=findUser(me.optString("phone"));if(user!=null&&now-user.optLong("socialLastActive",0)>=20000L){user.put("socialLastActive",now);saveArray(usersFile,users);}}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("updated",now);sendJson(out,200,"OK",result.toString());
    }

    private boolean visibilityAllowed(String visibility,String viewer,String owner) {
        if(viewer!=null&&viewer.equals(owner))return true;
        if("everyone".equals(visibility))return true;
        if("followers".equals(visibility))return isFollowing(viewer,owner);
        return false;
    }

    private boolean profileVisibilityAllowed(JSONObject ownerUser,String field,String viewer,String owner) {
        if(viewer!=null&&viewer.equals(owner))return true;
        JSONObject privacy=socialPrivacy(ownerUser);
        if("private".equals(privacy.optString("account","public"))&&!isFollowing(viewer,owner))return false;
        return visibilityAllowed(privacy.optString(field,"private"),viewer,owner);
    }

    private boolean canReadStatus(String viewer,JSONObject status) {
        if(status==null)return false;
        String owner=status.optString("phone");
        if(viewer!=null&&viewer.equals(owner))return true;
        if(isBlockedEither(viewer,owner))return false;
        String audience=status.optString("audience","contacts");
        if("public".equals(audience))return true;
        if("followers".equals(audience))return isFollowing(viewer,owner);
        if("private".equals(audience))return false;
        JSONObject user=findUser(owner);
        if(user!=null&&"private".equals(socialPrivacy(user).optString("account","public"))&&!isFollowing(viewer,owner))return false;
        return hasSavedContact(viewer,owner);
    }

    private boolean canReadPost(String viewer, JSONObject post) {
        String author=post.optString("author"), audience=post.optString("audience","public");
        if(!viewer.equals(author)&&isBlockedEither(viewer,author))return false;
        if(post.optBoolean("archived", false) && !viewer.equals(author)) return false;
        if(viewer.equals(author)) return true;
        JSONObject owner=findUser(author);
        if(owner!=null&&"private".equals(socialPrivacy(owner).optString("account","public"))&&!isFollowing(viewer,author))return false;
        if("public".equals(audience)) return true;
        if("followers".equals(audience)) return isFollowing(viewer, author);
        return false;
    }

    private JSONObject newSocialPost(JSONObject author, String text, String audience, String mediaId, String mediaType, String mime, String fileName, long fileSize) throws JSONException {
        String phone=author.optString("phone");
        if(!"followers".equals(audience) && !"private".equals(audience)) audience="public";
        JSONObject p=new JSONObject(); p.put("id",UUID.randomUUID().toString().replace("-","")); p.put("author",phone); p.put("authorName",author.optString("name",phone)); p.put("authorUsername",author.optString("username",makeSocialUsername(author.optString("name"),phone)));
        p.put("text",text==null?"":text.trim()); p.put("audience",audience); p.put("createdAt",System.currentTimeMillis()); p.put("likes",new JSONArray()); p.put("comments",new JSONArray()); p.put("allowDownload",true);
        if(mediaId!=null&&mediaId.length()>0){p.put("mediaId",mediaId);p.put("mediaType",mediaType==null?"image":mediaType);p.put("mimeType",mime==null?"application/octet-stream":mime);p.put("fileName",fileName==null?"media":fileName);p.put("fileSize",fileSize);}
        return p;
    }

    private JSONObject publicPost(JSONObject raw, String viewer) throws JSONException {
        JSONObject p=new JSONObject(raw.toString());
        String rawAuthor=raw.optString("author"); JSONObject authorUser=findUser(rawAuthor);
        if(authorUser!=null){p.put("authorUsername",authorUser.optString("username",makeSocialUsername(authorUser.optString("name"),authorUser.optString("phone"))));p.put("authorName",socialDisplayName(authorUser));}else{p.put("authorName","Pengguna OHLI");}
        // Social APIs never return a phone number, including to the post owner.
        p.put("author",authorUser==null?"":socialId(authorUser));
        p.put("isMine",samePhone(viewer,rawAuthor));
        String audioId=raw.optString("audioId",""); if(audioId.length()>0){JSONObject audioTrack=findMusicTrack(audioId);if(audioTrack!=null)p.put("audioMediaId",audioTrack.optString("mediaId",""));} JSONArray likes=raw.optJSONArray("likes"), comments=raw.optJSONArray("comments");
        int likeCount=0; boolean liked=false;
        if(likes!=null)for(int i=0;i<likes.length();i++){JSONObject l=likes.optJSONObject(i);if(l!=null){likeCount++;if(viewer.equals(l.optString("phone")))liked=true;}}
        p.put("likesCount",likeCount);p.put("liked",liked);p.put("commentsCount",comments==null?0:comments.length());p.put("commentsDisabled",raw.optBoolean("commentsDisabled",false));p.put("canModerateComments",viewer.equals(raw.optString("author")));String postAuthor=raw.optString("author");p.put("isFollowing",viewer.equals(postAuthor)||isFollowing(viewer,postAuthor));p.put("followRequested",!viewer.equals(postAuthor)&&hasPendingFollow(viewer,postAuthor));
        boolean saved=false;for(int i=0;i<socialBookmarks.length();i++){JSONObject b=socialBookmarks.optJSONObject(i);if(b!=null&&viewer.equals(b.optString("phone"))&&raw.optString("id").equals(b.optString("postId"))){saved=true;break;}}p.put("saved",saved);JSONArray rps=raw.optJSONArray("reposts");int rpc=0;boolean rpd=false;if(rps!=null)for(int i=0;i<rps.length();i++){JSONObject rr=rps.optJSONObject(i);if(rr!=null){rpc++;if(viewer.equals(rr.optString("phone")))rpd=true;}}p.put("repostsCount",rpc);p.put("reposted",rpd);p.put("sharesCount",raw.optInt("shares",0));
        JSONArray safeComments=new JSONArray(); if(comments!=null){int start=Math.max(0,comments.length()-80);for(int i=start;i<comments.length();i++){JSONObject c=comments.optJSONObject(i);if(c!=null){JSONObject sc=new JSONObject(c.toString());String cp=c.optString("phone");JSONObject cu=findUser(cp);sc.put("phone",cu==null?"":socialId(cu));sc.put("name",socialDisplayName(cu));sc.put("isMine",samePhone(viewer,cp));JSONArray cl=c.optJSONArray("likes");int clCount=0;boolean clMine=false;JSONArray safeCl=new JSONArray();if(cl!=null)for(int z=0;z<cl.length();z++){JSONObject x=cl.optJSONObject(z);if(x!=null){clCount++;if(viewer.equals(x.optString("phone")))clMine=true;JSONObject sx=new JSONObject();JSONObject xu=findUser(x.optString("phone"));sx.put("phone",xu==null?"":socialId(xu));sx.put("isMine",samePhone(viewer,x.optString("phone")));safeCl.put(sx);}}sc.put("likes",safeCl);sc.put("likesCount",clCount);sc.put("liked",clMine);safeComments.put(sc);}}}p.put("comments",safeComments);
        JSONArray safeLikes=new JSONArray();if(likes!=null)for(int i=0;i<likes.length();i++){JSONObject l=likes.optJSONObject(i);if(l!=null){JSONObject sl=new JSONObject(l.toString());String lp=l.optString("phone");JSONObject lu=findUser(lp);sl.put("phone",lu==null?"":socialId(lu));sl.put("isMine",samePhone(viewer,lp));safeLikes.put(sl);}}p.put("likes",safeLikes);
        JSONArray safeReposts=new JSONArray();JSONArray rawReposts=raw.optJSONArray("reposts");if(rawReposts!=null)for(int i=0;i<rawReposts.length();i++){JSONObject rp=rawReposts.optJSONObject(i);if(rp!=null){JSONObject sr=new JSONObject(rp.toString());String rphone=rp.optString("phone");JSONObject ru=findUser(rphone);sr.put("phone",ru==null?"":socialId(ru));sr.put("isMine",samePhone(viewer,rphone));safeReposts.put(sr);}}if(rawReposts!=null)p.put("reposts",safeReposts);
        return p;
    }

    private double socialPostRank(JSONObject p,long now){double likes=(p.optJSONArray("likes")==null?0:p.optJSONArray("likes").length());double comments=(p.optJSONArray("comments")==null?0:p.optJSONArray("comments").length());double reposts=(p.optJSONArray("reposts")==null?0:p.optJSONArray("reposts").length());double shares=p.optInt("shares",0);double hours=Math.max(0,(now-p.optLong("createdAt",now))/3600000.0);double engagement=likes*3.0+comments*5.0+reposts*4.0+shares*2.0;return (engagement+0.25)/(1.0+hours/18.0);}
    private void addInterestTokens(Map<String,Double> weights,String text,double weight){if(text==null||text.length()==0)return;Matcher m=Pattern.compile("#[\\p{L}0-9_]{2,}|[\\p{L}0-9_]{3,}").matcher(text.toLowerCase(Locale.US));while(m.find()){String t=m.group().replaceFirst("^#","");if(t.length()<3||t.matches("(yang|dan|atau|untuk|dari|dengan|ini|itu|aku|kamu|the|and|for|with|this|that)"))continue;Double old=weights.get(t);weights.put(t,(old==null?0:old)+weight);}}
    private double personalizedPostRank(JSONObject p,String viewer,long now,Map<String,Double> interests,Set<String> rejected,Set<String> likedIds,Set<String> savedIds){
        double base=socialPostRank(p,now);String postId=p.optString("id"),author=p.optString("author");
        if(rejected.contains(postId))return -1000.0;
        double topic=0;Matcher m=Pattern.compile("#[\\p{L}0-9_]{2,}|[\\p{L}0-9_]{3,}").matcher(p.optString("text","").toLowerCase(Locale.US));while(m.find()){Double w=interests.get(m.group().replaceFirst("^#",""));if(w!=null)topic+=Math.min(3.0,w);}
        double relation=isFollowing(viewer,author)?1.4:0.0;if(viewer.equals(author))relation-=2.0;
        double engaged=(likedIds.contains(postId)?2.2:0.0)+(savedIds.contains(postId)?2.5:0.0);
        return Math.log1p(Math.max(0,base))*1.35 + Math.min(8.0,topic)*1.15 + relation + engaged;
    }
    private Map<String,Double> buildSocialInterests(String viewer,long now,Set<String> rejected,Map<String,JSONObject> postById){
        Map<String,Double> weights=new HashMap<String,Double>();int scanned=0;
        for(int i=socialActivityHistory.length()-1;i>=0&&scanned<250;i--){JSONObject a=socialActivityHistory.optJSONObject(i);if(a==null||!viewer.equals(a.optString("owner")))continue;long age=Math.max(0,now-a.optLong("time",now));if(age>45L*24*60*60*1000)continue;scanned++;double decay=Math.exp(-(double)age/(18.0*24*60*60*1000));String type=a.optString("type"),item=a.optString("itemId");
            if("not_interested".equals(type)){if(item.length()>0)rejected.add(item);continue;}
            if("search".equals(type)){addInterestTokens(weights,a.optString("query"),2.2*decay);continue;}
            JSONObject viewed=postById.get(item);if(viewed!=null){double w="video_complete".equals(type)?3.0:("video_skip".equals(type)?-1.5:("video".equals(type)?1.1:0.55));addInterestTokens(weights,viewed.optString("text",""),w*decay);}
        }
        int likedScanned=0;for(int i=socialPosts.length()-1;i>=0&&likedScanned<180;i--){JSONObject p=socialPosts.optJSONObject(i);if(p==null)continue;JSONArray likes=p.optJSONArray("likes");if(likes==null)continue;for(int li=0;li<likes.length();li++){JSONObject like=likes.optJSONObject(li);if(like!=null&&viewer.equals(like.optString("phone"))){addInterestTokens(weights,p.optString("text",""),0.8);likedScanned++;break;}}}
        return weights;
    }
    private void getSocialFeed(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String viewer=me.optString("phone"),mode=queryParam(query,"mode");long before=0;try{before=Long.parseLong(queryParam(query,"before"));}catch(Exception ignored){}
        int limit=20;try{limit=Integer.parseInt(queryParam(query,"limit"));}catch(Exception ignored){}limit=Math.max(1,Math.min(50,limit));
        JSONArray result=new JSONArray();long next=0;int count=0;boolean more=false;
        synchronized(DATA_LOCK){
            if("public".equals(mode)||"foryou".equals(mode)){
                ArrayList<JSONObject> ranked=new ArrayList<JSONObject>();final Map<String,JSONObject> postById=new HashMap<String,JSONObject>();for(int i=0;i<socialPosts.length();i++){JSONObject raw=socialPosts.optJSONObject(i);if(raw==null)continue;String candidateId=raw.optString("id");if(candidateId.length()>0)postById.put(candidateId,raw);if(raw.optBoolean("archived",false)||!canReadPost(viewer,raw))continue;ranked.add(raw);}
                final long now=System.currentTimeMillis();final Set<String> rejected=new HashSet<String>();final Map<String,Double> interests=buildSocialInterests(viewer,now,rejected,postById);final Set<String> likedIds=new HashSet<String>(),savedIds=new HashSet<String>();for(JSONObject candidate:ranked){JSONArray ls=candidate.optJSONArray("likes");if(ls!=null)for(int li=0;li<ls.length();li++){JSONObject like=ls.optJSONObject(li);if(like!=null&&viewer.equals(like.optString("phone"))){likedIds.add(candidate.optString("id"));break;}}}for(int bi=0;bi<socialBookmarks.length();bi++){JSONObject bm=socialBookmarks.optJSONObject(bi);if(bm!=null&&viewer.equals(bm.optString("phone")))savedIds.add(bm.optString("postId"));}final Map<String,Double> rankScores=new HashMap<String,Double>();for(JSONObject candidate:ranked)rankScores.put(candidate.optString("id"),personalizedPostRank(candidate,viewer,now,interests,rejected,likedIds,savedIds));Collections.sort(ranked,new Comparator<JSONObject>(){public int compare(JSONObject a,JSONObject b){int c=Double.compare(rankScores.get(b.optString("id")),rankScores.get(a.optString("id")));if(c!=0)return c;return Long.compare(b.optLong("createdAt",0),a.optLong("createdAt",0));}});
                int offset=(int)Math.max(0,Math.min((long)ranked.size(),before));int end=Math.min(ranked.size(),offset+limit);for(int i=offset;i<end;i++){JSONObject raw=ranked.get(i);result.put(publicPost(raw,viewer));count++;}more=end<ranked.size();next=more?end:0;
            }else{
                for(int i=socialPosts.length()-1;i>=0;i--){JSONObject raw=socialPosts.optJSONObject(i);if(raw==null||raw.optBoolean("archived",false))continue;long created=raw.optLong("createdAt",0);if(before>0&&created>=before)continue;if(!canReadPost(viewer,raw))continue;String author=raw.optString("author");if("following".equals(mode)&&!viewer.equals(author)&&!isFollowing(viewer,author))continue;if("videos".equals(mode)&&!"video".equals(raw.optString("mediaType")))continue;result.put(publicPost(raw,viewer));next=created;count++;if(count>=limit){more=true;break;}}
            }
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",result);r.put("nextBefore",next);r.put("hasMore",more);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialTrending(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone");Map<String,JSONObject> counts=new HashMap<String,JSONObject>();long min=System.currentTimeMillis()-14L*24L*60L*60L*1000L;Pattern pattern=Pattern.compile("#[\\p{L}0-9_]{2,28}");
        synchronized(DATA_LOCK){for(int i=0;i<socialPosts.length();i++){JSONObject p=socialPosts.optJSONObject(i);if(p==null||p.optLong("createdAt",0)<min||!canReadPost(viewer,p))continue;String text=p.optString("text","").toLowerCase(Locale.US);Matcher m=pattern.matcher(text);while(m.find()){String tag=m.group();JSONObject item=counts.get(tag);if(item==null){item=new JSONObject();item.put("tag",tag);item.put("uses",0);item.put("score",0);counts.put(tag,item);}item.put("uses",item.optInt("uses")+1);item.put("score",item.optDouble("score")+socialPostRank(p,System.currentTimeMillis()));}}}
        ArrayList<JSONObject> list=new ArrayList<JSONObject>(counts.values());Collections.sort(list,new Comparator<JSONObject>(){public int compare(JSONObject a,JSONObject b){return Double.compare(b.optDouble("score"),a.optDouble("score"));}});JSONArray topics=new JSONArray();for(int i=0;i<list.size()&&i<8;i++){JSONObject x=list.get(i);JSONObject t=new JSONObject();t.put("tag",x.optString("tag"));t.put("uses",x.optInt("uses"));topics.put(t);}JSONObject r=new JSONObject();r.put("ok",true);r.put("topics",topics);sendJson(out,200,"OK",r.toString());
    }

    private void createSocialPost(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String text=d.optString("text","").trim(),audience=d.optString("audience","public");
        if(text.length()==0||text.length()>2000){sendJson(out,400,"Bad Request",errorJson("Postingan teks harus berisi 1–2000 karakter"));return;}
        if(!"public".equals(audience)&&!"followers".equals(audience)&&!"private".equals(audience))audience="public";
        JSONObject post=newSocialPost(me,text,audience,"","","","",0);
        String audioTrackId=d.optString("audioId","");
        synchronized(DATA_LOCK){if(audioTrackId.length()>0){JSONObject track=findMusicTrack(audioTrackId);if(track==null||!canReadMusic(me.optString("phone"),track)){sendJson(out,404,"Not Found",errorJson("Audio pilihan tidak ditemukan atau tidak dapat diakses"));return;}post.put("audioId",audioTrackId);post.put("audioTitle",track.optString("title"));post.put("audioArtist",track.optString("artist"));track.put("uses",track.optInt("uses",0)+1);saveArray(socialMusicFile,socialMusic);}socialPosts=appendLimitedCopy(socialPosts,post,500);saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("post",publicPost(post,me.optString("phone")));sendJson(out,201,"Created",r.toString());
    }

    private void editSocialPost(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token","")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""), text=d.optString("text","").trim(), audience=d.optString("audience","public"), phone=me.optString("phone");
        if(text.length()>2000){sendJson(out,400,"Bad Request",errorJson("Caption maksimal 2000 karakter"));return;}
        if(!"public".equals(audience)&&!"followers".equals(audience)&&!"private".equals(audience))audience="public";
        JSONObject post; synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!phone.equals(post.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik postingan yang boleh mengeditnya"));return;}if(text.length()==0&&!post.has("mediaId")){sendJson(out,400,"Bad Request",errorJson("Postingan teks tidak boleh kosong"));return;}post.put("text",text);post.put("audience",audience);post.put("editedAt",System.currentTimeMillis());saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void toggleSocialArchive(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("postId",""),action=d.optString("action","archive"),phone=me.optString("phone");JSONObject post;
        synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!phone.equals(post.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik postingan yang boleh mengarsipkan"));return;}boolean archived=!("restore".equals(action));post.put("archived",archived);if(archived)post.put("archivedAt",System.currentTimeMillis());else post.remove("archivedAt");saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("archived",post.optBoolean("archived",false));r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void getSocialArchive(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=me.optString("phone");JSONArray items=new JSONArray();synchronized(DATA_LOCK){for(int i=socialPosts.length()-1;i>=0;i--){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&phone.equals(p.optString("author"))&&p.optBoolean("archived",false))items.put(publicPost(p,phone));}}JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",items);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialSaved(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),owner=resolveAccountKey(queryParam(query,"phone"));if(owner.length()==0)owner=viewer;JSONObject user=findUser(owner);if(user==null||(!viewer.equals(owner)&&isBlockedEither(viewer,owner))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}if(!profileVisibilityAllowed(user,"saved",viewer,owner)){sendJson(out,403,"Forbidden",errorJson("Koleksi tersimpan dibatasi oleh privasi akun"));return;}JSONArray items=new JSONArray();synchronized(DATA_LOCK){for(int i=socialBookmarks.length()-1;i>=0;i--){JSONObject b=socialBookmarks.optJSONObject(i);if(b==null||!owner.equals(b.optString("phone")))continue;JSONObject post=findSocialPost(b.optString("postId"));if(post!=null&&!post.optBoolean("archived",false)&&canReadPost(viewer,post))items.put(publicPost(post,viewer));}}JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",items);sendJson(out,200,"OK",r.toString());
    }

    private JSONObject findSocialPost(String id){for(int i=0;i<socialPosts.length();i++){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p;}return null;}

    private void toggleSocialLike(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""), phone=me.optString("phone");boolean liked=false;JSONObject post;
        synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}
            JSONArray likes=post.optJSONArray("likes");if(likes==null){likes=new JSONArray();post.put("likes",likes);}JSONArray next=new JSONArray();boolean had=false;for(int i=0;i<likes.length();i++){JSONObject l=likes.optJSONObject(i);if(l==null)continue;if(phone.equals(l.optString("phone"))){had=true;continue;}next.put(l);}if(!had){JSONObject l=new JSONObject();l.put("phone",phone);l.put("time",System.currentTimeMillis());next.put(l);liked=true;}post.put("likes",next);saveArray(socialPostsFile,socialPosts);
            if(liked&&!phone.equals(post.optString("author")))addSocialNotification(post.optString("author"),phone,"like",id,"menyukai postinganmu");
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("liked",liked);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void addSocialNotification(String recipient,String actor,String type,String objectId,String message) throws JSONException {
        if(recipient==null||recipient.length()==0||recipient.equals(actor))return;JSONObject n=new JSONObject();n.put("id",UUID.randomUUID().toString().replace("-",""));n.put("recipient",recipient);n.put("actor",actor);JSONObject a=findUser(actor);n.put("actorName",a==null?actor:a.optString("name",actor));n.put("type",type);n.put("objectId",objectId==null?"":objectId);n.put("text",message==null?"":message);n.put("time",System.currentTimeMillis());n.put("read",false);socialNotifications=appendLimitedCopy(socialNotifications,n,10000);saveArray(socialNotificationsFile,socialNotifications);
    }

    private void addSocialComment(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""),text=d.optString("text","").trim(),parent=d.optString("parentId",""),stickerId=d.optString("mediaId","");if((text.length()==0&&stickerId.length()==0)||text.length()>500){sendJson(out,400,"Bad Request",errorJson("Komentar harus berisi teks atau stiker"));return;}
        JSONObject c=new JSONObject(),post;String phone=me.optString("phone");c.put("id",UUID.randomUUID().toString().replace("-",""));c.put("phone",phone);c.put("name",me.optString("name"));c.put("text",text);c.put("time",System.currentTimeMillis());c.put("likes",new JSONArray());if(parent.length()>0)c.put("parentId",parent);if(stickerId.length()>0)c.put("mediaId",stickerId);
        synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}if(post.optBoolean("commentsDisabled",false)){sendJson(out,403,"Forbidden",errorJson("Komentar dinonaktifkan oleh pemilik postingan"));return;}JSONArray comments=post.optJSONArray("comments");if(comments==null){comments=new JSONArray();post.put("comments",comments);}if(comments.length()>=300){sendJson(out,429,"Too Many Requests",errorJson("Batas komentar pada postingan ini sudah tercapai"));return;}if(parent.length()>0){boolean parentFound=false;for(int i=0;i<comments.length();i++){JSONObject parentComment=comments.optJSONObject(i);if(parentComment!=null&&parent.equals(parentComment.optString("id"))){parentFound=true;c.put("replyToName",parentComment.optString("name","Pengguna OHLI"));break;}}if(!parentFound){sendJson(out,404,"Not Found",errorJson("Komentar yang dibalas tidak ditemukan"));return;}}if(stickerId.length()>0){JSONObject sticker=findStickerUpload(stickerId);boolean stickerAllowed=sticker!=null&&phone.equals(sticker.optString("owner"));if(!stickerAllowed){for(int si=0;si<socialStickerFavorites.length();si++){JSONObject fav=socialStickerFavorites.optJSONObject(si);if(fav!=null&&phone.equals(fav.optString("owner"))&&stickerId.equals(fav.optString("mediaId"))){stickerAllowed=true;break;}}}if(!stickerAllowed){sendJson(out,403,"Forbidden",errorJson("Stiker tidak valid atau belum disimpan ke koleksimu"));return;}}comments.put(c);saveArray(socialPostsFile,socialPosts);addSocialNotification(post.optString("author"),phone,"comment",id,"mengomentari postinganmu");}
        JSONObject safeComment=new JSONObject(c.toString());safeComment.put("phone",socialId(me));safeComment.put("isMine",true);JSONObject r=new JSONObject();r.put("ok",true);r.put("comment",safeComment);r.put("post",publicPost(post,phone));sendJson(out,201,"Created",r.toString());
    }

    private JSONObject findStickerUpload(String mediaId) {
        for (int i=0;i<socialStickerUploads.length();i++) { JSONObject x=socialStickerUploads.optJSONObject(i); if(x!=null&&mediaId.equals(x.optString("mediaId")))return x; }
        return null;
    }

    private void toggleSocialCommentLike(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String postId=d.optString("postId",""),commentId=d.optString("commentId",""),phone=me.optString("phone");JSONObject post=null,comment=null;boolean liked=false;
        synchronized(DATA_LOCK){post=findSocialPost(postId);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Postingan tidak dapat diakses"));return;}JSONArray comments=post.optJSONArray("comments");if(comments!=null)for(int i=0;i<comments.length();i++){JSONObject c=comments.optJSONObject(i);if(c!=null&&commentId.equals(c.optString("id"))){comment=c;break;}}if(comment==null){sendJson(out,404,"Not Found",errorJson("Komentar tidak ditemukan"));return;}JSONArray old=comment.optJSONArray("likes");if(old==null)old=new JSONArray();JSONArray next=new JSONArray();boolean had=false;for(int i=0;i<old.length();i++){JSONObject l=old.optJSONObject(i);if(l==null)continue;if(phone.equals(l.optString("phone"))){had=true;continue;}next.put(l);}if(!had){JSONObject l=new JSONObject();l.put("phone",phone);l.put("time",System.currentTimeMillis());next.put(l);liked=true;}comment.put("likes",next);saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("liked",liked);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void updateSocialDownloadSettings(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""),phone=me.optString("phone");boolean allow=d.optBoolean("allowDownload",true);JSONObject post;
        synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!phone.equals(post.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik video yang dapat mengatur izin unduhan"));return;}post.put("allowDownload",allow);post.put("downloadSettingsUpdatedAt",System.currentTimeMillis());saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("allowDownload",allow);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void getStickerFavorites(OutputStream out,String query) throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray items=new JSONArray();
        synchronized(DATA_LOCK){for(int i=socialStickerFavorites.length()-1;i>=0;i--){JSONObject fav=socialStickerFavorites.optJSONObject(i);if(fav==null||!owner.equals(fav.optString("owner")))continue;JSONObject meta=findStickerUpload(fav.optString("mediaId"));if(meta==null)continue;JSONObject row=new JSONObject();row.put("mediaId",meta.optString("mediaId"));row.put("mimeType",meta.optString("mimeType"));row.put("fileName",meta.optString("fileName"));row.put("savedAt",fav.optLong("savedAt"));items.put(row);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("stickers",items);sendJson(out,200,"OK",r.toString());
    }

    private void getStickerLibrary(OutputStream out,String query) throws IOException,JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray mine=new JSONArray(),favorites=new JSONArray();
        synchronized(DATA_LOCK){
            for(int i=socialStickerUploads.length()-1;i>=0;i--){JSONObject meta=socialStickerUploads.optJSONObject(i);if(meta==null||!owner.equals(meta.optString("owner")))continue;JSONObject row=new JSONObject();row.put("mediaId",meta.optString("mediaId"));row.put("mimeType",meta.optString("mimeType","image/jpeg"));row.put("fileName",meta.optString("fileName","sticker"));row.put("fileSize",meta.optLong("fileSize",0));row.put("createdAt",meta.optLong("createdAt",0));row.put("isMine",true);row.put("saved",isStickerFavorite(owner,meta.optString("mediaId")));mine.put(row);}
            for(int i=socialStickerFavorites.length()-1;i>=0;i--){JSONObject fav=socialStickerFavorites.optJSONObject(i);if(fav==null||!owner.equals(fav.optString("owner")))continue;JSONObject meta=findStickerUpload(fav.optString("mediaId"));if(meta==null)continue;JSONObject row=new JSONObject();row.put("mediaId",meta.optString("mediaId"));row.put("mimeType",meta.optString("mimeType","image/jpeg"));row.put("fileName",meta.optString("fileName","sticker"));row.put("fileSize",meta.optLong("fileSize",0));row.put("savedAt",fav.optLong("savedAt",0));row.put("isMine",owner.equals(meta.optString("owner")));row.put("saved",true);favorites.put(row);}
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("mine",mine);r.put("favorites",favorites);r.put("count",mine.length());sendJson(out,200,"OK",r.toString());
    }

    private boolean isStickerFavorite(String owner,String mediaId){for(int i=0;i<socialStickerFavorites.length();i++){JSONObject fav=socialStickerFavorites.optJSONObject(i);if(fav!=null&&owner.equals(fav.optString("owner"))&&mediaId.equals(fav.optString("mediaId")))return true;}return false;}

    private void deleteOwnSticker(OutputStream out,JSONObject d)throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone"),mediaId=d.optString("mediaId","");if(!mediaId.matches("[a-fA-F0-9]{32}")){sendJson(out,400,"Bad Request",errorJson("ID stiker tidak valid"));return;}
        synchronized(DATA_LOCK){JSONObject meta=findStickerUpload(mediaId);if(meta==null){sendJson(out,404,"Not Found",errorJson("Stiker tidak ditemukan"));return;}if(!owner.equals(meta.optString("owner"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik stiker yang dapat menghapusnya"));return;}
            for(int i=0;i<socialPosts.length();i++){JSONObject post=socialPosts.optJSONObject(i);if(post==null)continue;JSONArray comments=post.optJSONArray("comments");if(comments==null)continue;for(int j=0;j<comments.length();j++){JSONObject c=comments.optJSONObject(j);if(c!=null&&mediaId.equals(c.optString("mediaId"))){sendJson(out,409,"Conflict",errorJson("Stiker sudah digunakan dalam komentar dan tidak dapat dihapus agar komentar tidak rusak"));return;}}}
            File f=new File(mediaDir,mediaId+".bin");if(f.exists()&&!f.delete()){sendJson(out,507,"Insufficient Storage",errorJson("File stiker tidak dapat dihapus dari penyimpanan"));return;}
            JSONArray nextUploads=new JSONArray();for(int i=0;i<socialStickerUploads.length();i++){JSONObject x=socialStickerUploads.optJSONObject(i);if(x!=null&&!mediaId.equals(x.optString("mediaId")))nextUploads.put(x);}socialStickerUploads=nextUploads;saveArray(socialStickerUploadsFile,socialStickerUploads);
            JSONArray nextFavs=new JSONArray();for(int i=0;i<socialStickerFavorites.length();i++){JSONObject x=socialStickerFavorites.optJSONObject(i);if(x!=null&&!mediaId.equals(x.optString("mediaId")))nextFavs.put(x);}socialStickerFavorites=nextFavs;saveArray(socialStickerFavoritesFile,socialStickerFavorites);
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);sendJson(out,200,"OK",r.toString());
    }

    private void toggleStickerFavorite(OutputStream out,JSONObject d) throws IOException,JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone"),mediaId=d.optString("mediaId",""),action=d.optString("action","save");boolean saved=false;
        synchronized(DATA_LOCK){JSONObject meta=findStickerUpload(mediaId);if(meta==null){sendJson(out,404,"Not Found",errorJson("Stiker tidak ditemukan"));return;}boolean found=false,visible=owner.equals(meta.optString("owner"));for(int i=0;i<socialStickerFavorites.length();i++){JSONObject f=socialStickerFavorites.optJSONObject(i);if(f!=null&&owner.equals(f.optString("owner"))&&mediaId.equals(f.optString("mediaId"))){found=true;break;}}if(!visible){for(int i=0;i<socialPosts.length()&&!visible;i++){JSONObject post=socialPosts.optJSONObject(i);if(post==null||!canReadPost(owner,post))continue;JSONArray comments=post.optJSONArray("comments");if(comments==null)continue;for(int j=0;j<comments.length();j++){JSONObject c=comments.optJSONObject(j);if(c!=null&&mediaId.equals(c.optString("mediaId"))){visible=true;break;}}}}if(!visible&&!found){sendJson(out,403,"Forbidden",errorJson("Stiker tidak dapat disimpan karena sumbernya tidak dapat diakses"));return;}JSONArray next=new JSONArray();for(int i=0;i<socialStickerFavorites.length();i++){JSONObject f=socialStickerFavorites.optJSONObject(i);if(f==null)continue;if(owner.equals(f.optString("owner"))&&mediaId.equals(f.optString("mediaId"))&&"remove".equals(action))continue;next.put(f);}if(!found&&!("remove".equals(action))){JSONObject f=new JSONObject();f.put("owner",owner);f.put("mediaId",mediaId);f.put("savedAt",System.currentTimeMillis());next.put(f);saved=true;}else saved=!"remove".equals(action);socialStickerFavorites=next;saveArray(socialStickerFavoritesFile,socialStickerFavorites);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("saved",saved);sendJson(out,200,"OK",r.toString());
    }

    private void editSocialComment(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token","")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String postId=d.optString("postId",""), commentId=d.optString("commentId",""), text=d.optString("text","").trim(), phone=me.optString("phone");
        if(text.length()==0||text.length()>500){sendJson(out,400,"Bad Request",errorJson("Komentar harus berisi 1–500 karakter"));return;}
        JSONObject post=null,comment=null;
        synchronized(DATA_LOCK){post=findSocialPost(postId);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}JSONArray comments=post.optJSONArray("comments");if(comments!=null)for(int i=0;i<comments.length();i++){JSONObject c=comments.optJSONObject(i);if(c!=null&&commentId.equals(c.optString("id"))){comment=c;break;}}
            if(comment==null){sendJson(out,404,"Not Found",errorJson("Komentar tidak ditemukan"));return;}
            if(!phone.equals(comment.optString("phone"))){sendJson(out,403,"Forbidden",errorJson("Kamu hanya dapat mengedit komentar milikmu sendiri"));return;}
            comment.put("text",text);comment.put("editedAt",System.currentTimeMillis());saveArray(socialPostsFile,socialPosts);
        }
        JSONObject safeComment=new JSONObject(comment.toString());safeComment.put("phone",socialId(me));safeComment.put("isMine",true);JSONObject r=new JSONObject();r.put("ok",true);r.put("comment",safeComment);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void deleteSocialComment(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token","")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String postId=d.optString("postId",""), commentId=d.optString("commentId",""), phone=me.optString("phone"); JSONObject post=null, deleted=null;
        synchronized(DATA_LOCK){post=findSocialPost(postId);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}JSONArray old=post.optJSONArray("comments");if(old==null){sendJson(out,404,"Not Found",errorJson("Komentar tidak ditemukan"));return;}JSONArray next=new JSONArray();for(int i=0;i<old.length();i++){JSONObject c=old.optJSONObject(i);if(c!=null&&commentId.equals(c.optString("id"))){deleted=c;continue;}if(c!=null)next.put(c);}if(deleted==null){sendJson(out,404,"Not Found",errorJson("Komentar tidak ditemukan"));return;}if(!phone.equals(deleted.optString("phone"))&&!phone.equals(post.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya penulis komentar atau pemilik postingan yang boleh menghapus komentar ini"));return;}post.put("comments",next);saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void updateSocialCommentSettings(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token","")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""),phone=me.optString("phone");boolean disabled=d.optBoolean("disabled",false);JSONObject post;
        synchronized(DATA_LOCK){post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!phone.equals(post.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya kreator postingan yang dapat mengatur komentar"));return;}post.put("commentsDisabled",disabled);post.put("commentsSettingsUpdatedAt",System.currentTimeMillis());saveArray(socialPostsFile,socialPosts);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("commentsDisabled",disabled);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void deleteSocialPost(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("postId","");String phone=me.optString("phone");
        String mediaToDelete="";synchronized(DATA_LOCK){JSONArray next=new JSONArray();boolean found=false;for(int i=0;i<socialPosts.length();i++){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&id.equals(p.optString("id"))){if(!phone.equals(p.optString("author"))){sendJson(out,403,"Forbidden",errorJson("Hanya pembuat postingan yang boleh menghapusnya"));return;}found=true;mediaToDelete=p.optString("mediaId","");continue;}if(p!=null)next.put(p);}if(!found){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}socialPosts=next;saveArray(socialPostsFile,socialPosts);JSONArray b=new JSONArray();for(int i=0;i<socialBookmarks.length();i++){JSONObject x=socialBookmarks.optJSONObject(i);if(x!=null&&!id.equals(x.optString("postId")))b.put(x);}socialBookmarks=b;saveArray(socialBookmarksFile,socialBookmarks);}if(mediaToDelete.matches("[a-fA-F0-9]{32}")){boolean referenced=false;synchronized(DATA_LOCK){for(int i=0;i<socialPosts.length();i++){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&mediaToDelete.equals(p.optString("mediaId")))referenced=true;}for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&mediaToDelete.equals(m.optString("mediaId")))referenced=true;}for(int i=0;i<statuses.length();i++){JSONObject st=statuses.optJSONObject(i);if(st!=null&&mediaToDelete.equals(st.optString("mediaId")))referenced=true;}}if(!referenced){File mediaFile=new File(mediaDir,mediaToDelete+".bin");if(mediaFile.exists())mediaFile.delete();}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);sendJson(out,200,"OK",r.toString());
    }

    private void toggleSocialBookmark(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("postId",""),phone=me.optString("phone");boolean saved=false;
        synchronized(DATA_LOCK){JSONObject post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses"));return;}JSONArray next=new JSONArray();boolean exists=false;for(int i=0;i<socialBookmarks.length();i++){JSONObject b=socialBookmarks.optJSONObject(i);if(b!=null&&phone.equals(b.optString("phone"))&&id.equals(b.optString("postId"))){exists=true;continue;}if(b!=null)next.put(b);}if(!exists){JSONObject b=new JSONObject();b.put("phone",phone);b.put("postId",id);b.put("time",System.currentTimeMillis());next.put(b);saved=true;if(!phone.equals(post.optString("author")))addSocialNotification(post.optString("author"),phone,"save",id,"menyimpan postinganmu");}socialBookmarks=next;saveArray(socialBookmarksFile,socialBookmarks);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("saved",saved);sendJson(out,200,"OK",r.toString());
    }


    private void getCover(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=resolveAccountKey(queryParam(query,"phone"));if(!validPhone(phone)||findUser(phone)==null||(!samePhone(me.optString("phone"),phone)&&isBlockedEither(me.optString("phone"),phone))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}
        File f=new File(profilesDir,phone+"_cover.jpg");if(!f.exists()||f.length()>MAX_IMAGE_BYTES){sendJson(out,404,"Not Found",errorJson("Latar profil belum dipasang"));return;}
        ByteArrayOutputStream buf=new ByteArrayOutputStream();FileInputStream in=null;
        try{in=new FileInputStream(f);byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);}finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        sendResponseCached(out,200,"OK","image/jpeg",buf.toByteArray(),5);
    }

    private void updateCoverPhoto(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", ""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String encoded=d.optString("data", ""); if(encoded.length()==0 || encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)){sendJson(out,413,"Payload Too Large",errorJson("Latar profil terlalu besar"));return;}
        byte[] image;try{image=Base64.decode(encoded,Base64.DEFAULT);}catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Latar profil tidak valid"));return;}
        if(image.length==0||image.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Latar profil maksimal 180 KB"));return;}
        File file=new File(profilesDir,me.optString("phone")+"_cover.jpg");FileOutputStream fos=null;
        try{fos=new FileOutputStream(file);fos.write(image);fos.flush();fos.getFD().sync();}catch(Exception e){sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan latar profil"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
        JSONObject r=new JSONObject();r.put("ok",true);sendJson(out,200,"OK",r.toString());
    }

    private void removeProfileImage(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", ""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String kind=d.optString("kind","avatar");File file=new File(profilesDir,me.optString("phone")+("cover".equals(kind)?"_cover.jpg":".jpg"));
        if(file.exists())file.delete();
        JSONObject r=new JSONObject();r.put("ok",true);sendJson(out,200,"OK",r.toString());
    }

    private void toggleSocialRepost(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""), phone=me.optString("phone");boolean reposted=false;JSONObject post;
        synchronized(DATA_LOCK){
            post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}
            if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}
            JSONArray rp=post.optJSONArray("reposts");if(rp==null){rp=new JSONArray();}
            JSONArray next=new JSONArray();boolean had=false;
            for(int i=0;i<rp.length();i++){JSONObject x=rp.optJSONObject(i);if(x==null)continue;if(phone.equals(x.optString("phone"))){had=true;continue;}next.put(x);}
            if(!had){JSONObject y=new JSONObject();y.put("phone",phone);y.put("time",System.currentTimeMillis());next.put(y);reposted=true;}
            post.put("reposts",next);saveArray(socialPostsFile,socialPosts);
            if(reposted&&!phone.equals(post.optString("author")))addSocialNotification(post.optString("author"),phone,"repost",id,"memposting ulang postinganmu");
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("reposted",reposted);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void countSocialShare(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("postId",""), phone=me.optString("phone");JSONObject post;
        synchronized(DATA_LOCK){
            post=findSocialPost(id);if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan tidak ditemukan"));return;}
            if(!canReadPost(phone,post)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki akses ke postingan ini"));return;}
            post.put("shares",post.optInt("shares",0)+1);saveArray(socialPostsFile,socialPosts);
            if(!phone.equals(post.optString("author")))addSocialNotification(post.optString("author"),phone,"share",id,"membagikan postinganmu");
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("post",publicPost(post,phone));sendJson(out,200,"OK",r.toString());
    }

    private void toggleSocialFollow(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String from=me.optString("phone"),target=resolveAccountKey(d.optString("target",""));if(!validPhone(target)||from.equals(target)||findUser(target)==null){sendJson(out,400,"Bad Request",errorJson("Akun tujuan tidak valid"));return;}if(isBlockedEither(from,target)){sendJson(out,403,"Forbidden",errorJson("Tidak dapat mengikuti akun yang diblokir"));return;}
        String action=d.optString("action","follow");boolean following=false,requested=false;JSONObject targetUser=findUser(target);boolean privateAccount="private".equals(socialPrivacy(targetUser).optString("account","public"));
        synchronized(DATA_LOCK){JSONArray next=new JSONArray();JSONObject existing=null;for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f!=null&&from.equals(f.optString("follower"))&&target.equals(f.optString("following"))){existing=f;continue;}if(f!=null)next.put(f);}
            if("unfollow".equals(action)){socialFollows=next;}
            else if(existing!=null){next.put(existing);socialFollows=next;following=!"pending".equals(existing.optString("status","accepted"));requested=!following;}
            else {JSONObject f=new JSONObject();f.put("follower",from);f.put("following",target);f.put("time",System.currentTimeMillis());if(privateAccount){f.put("status","pending");requested=true;addSocialNotification(target,from,"follow_request","","meminta mengikuti akunmu");}else{f.put("status","accepted");following=true;addSocialNotification(target,from,"follow","","mulai mengikutimu");}socialFollows=next;socialFollows.put(f);}
            saveArray(socialFollowsFile,socialFollows);
        }
        JSONObject r=new JSONObject();r.put("ok",true);r.put("following",following);r.put("requested",requested);sendJson(out,200,"OK",r.toString());
    }

    private void updateSocialPrivacy(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        JSONObject u=findUser(me.optString("phone")),p=socialPrivacy(u);String[] keys={"account","followers","following","likes","saved","directMessages","activity"};
        for(String key:keys){if(!d.has(key))continue;String value=d.optString(key,"");if("account".equals(key)){if(!"private".equals(value))value="public";}else if("directMessages".equals(key)||"activity".equals(key)){if(!"everyone".equals(value)&&!"friends".equals(value)&&!"private".equals(value)){sendJson(out,400,"Bad Request",errorJson("Pilihan privasi tidak valid: "+key));return;}}else if(!"everyone".equals(value)&&!"followers".equals(value)&&!"private".equals(value)){sendJson(out,400,"Bad Request",errorJson("Pilihan privasi tidak valid: "+key));return;}p.put(key,value);}
        synchronized(DATA_LOCK){u.put("socialPrivacy",p);saveArray(usersFile,users);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("privacy",p);sendJson(out,200,"OK",r.toString());
    }

    private boolean isBlockedEither(String a,String b){if(a==null||b==null||a.length()==0||b.length()==0)return false;for(int i=0;i<socialBlocks.length();i++){JSONObject x=socialBlocks.optJSONObject(i);if(x!=null&&((a.equals(x.optString("blocker"))&&b.equals(x.optString("blocked")))||(b.equals(x.optString("blocker"))&&a.equals(x.optString("blocked")))))return true;}return false;}
    private void getBlockedUsers(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=0;i<socialBlocks.length();i++){JSONObject b=socialBlocks.optJSONObject(i);if(b!=null&&owner.equals(b.optString("blocker"))){JSONObject u=findUser(b.optString("blocked"));if(u!=null){JSONObject row=userPublicSocial(u,owner);row.put("blockedAt",b.optLong("time"));list.put(row);}}}}JSONObject r=new JSONObject();r.put("ok",true);r.put("users",list);sendJson(out,200,"OK",r.toString());
    }
    private void toggleBlock(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone"),target=resolveAccountKey(d.optString("phone","")),action=d.optString("action","block");if(!validPhone(target)||owner.equals(target)||findUser(target)==null){sendJson(out,400,"Bad Request",errorJson("Akun tujuan tidak valid"));return;}boolean blocked=false;synchronized(DATA_LOCK){JSONArray next=new JSONArray();boolean already=false;for(int i=0;i<socialBlocks.length();i++){JSONObject b=socialBlocks.optJSONObject(i);if(b!=null&&owner.equals(b.optString("blocker"))&&target.equals(b.optString("blocked"))){already=true;if(!"unblock".equals(action)&&!blocked){next.put(b);blocked=true;}}else if(b!=null)next.put(b);}if(!"unblock".equals(action)&&!blocked){JSONObject b=new JSONObject();b.put("blocker",owner);b.put("blocked",target);b.put("time",System.currentTimeMillis());next.put(b);blocked=true;}socialBlocks=next;saveArray(socialBlocksFile,socialBlocks);}JSONObject r=new JSONObject();r.put("ok",true);r.put("blocked",blocked);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialFollowList(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String viewer=me.optString("phone"),target=resolveAccountKey(queryParam(query,"phone")),kind=queryParam(query,"type");if(target.length()==0)target=viewer;if(!"following".equals(kind))kind="followers";
        JSONObject owner=findUser(target);if(owner==null||(!viewer.equals(target)&&isBlockedEither(viewer,target))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}
        String vis=socialPrivacy(owner).optString("following".equals(kind)?"following":"followers","everyone");if(!profileVisibilityAllowed(owner,"following".equals(kind)?"following":"followers",viewer,target)){sendJson(out,403,"Forbidden",errorJson("Daftar ini dibatasi oleh pengaturan privasi akun"));return;}
        JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f==null|| (f.has("status")&&!"accepted".equals(f.optString("status"))))continue;String phone="following".equals(kind)?f.optString("following"):f.optString("follower"),other="following".equals(kind)?f.optString("follower"):f.optString("following");if(!target.equals(phone))continue;JSONObject u=findUser(other);if(u!=null&&!isBlockedEither(viewer,other)){JSONObject row=userPublicSocial(u,viewer);row.put("isFollowing",isFollowing(viewer,other));list.put(row);}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("type",kind);r.put("users",list);sendJson(out,200,"OK",r.toString());
    }

    private void getPendingFollowRequests(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f==null||!owner.equals(f.optString("following"))||!"pending".equals(f.optString("status")))continue;JSONObject u=findUser(f.optString("follower"));if(u!=null){JSONObject row=userPublicSocial(u,owner);row.put("requestedAt",f.optLong("time"));list.put(row);}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("requests",list);sendJson(out,200,"OK",r.toString());
    }

    private void respondFollowRequest(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone"),from=resolveAccountKey(d.optString("user","")),action=d.optString("action","approve");if(!"approve".equals(action)&&!"reject".equals(action)){sendJson(out,400,"Bad Request",errorJson("Aksi permintaan tidak valid"));return;}boolean found=false;
        synchronized(DATA_LOCK){JSONArray next=new JSONArray();for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f!=null&&owner.equals(f.optString("following"))&&from.equals(f.optString("follower"))&&"pending".equals(f.optString("status"))){found=true;if("approve".equals(action)){f.put("status","accepted");f.put("acceptedAt",System.currentTimeMillis());next.put(f);addSocialNotification(from,owner,"follow_accepted","","menerima permintaan mengikuti akunmu");}continue;}if(f!=null)next.put(f);}socialFollows=next;saveArray(socialFollowsFile,socialFollows);}
        if(!found){sendJson(out,404,"Not Found",errorJson("Permintaan mengikuti tidak ditemukan"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("approved","approve".equals(action));sendJson(out,200,"OK",r.toString());
    }

    private void recordProfileView(String viewer,String target)throws JSONException{
        if(viewer==null||target==null||viewer.equals(target))return;synchronized(DATA_LOCK){long now=System.currentTimeMillis(),day=now/(24L*60L*60L*1000L);for(int i=0;i<socialProfileViews.length();i++){JSONObject v=socialProfileViews.optJSONObject(i);if(v!=null&&target.equals(v.optString("owner"))&&viewer.equals(v.optString("viewer"))&&day==v.optLong("day")){v.put("lastSeen",now);saveArray(socialProfileViewsFile,socialProfileViews);return;}}JSONObject v=new JSONObject();v.put("owner",target);v.put("viewer",viewer);JSONObject u=findUser(viewer);v.put("viewerName",u==null?viewer:u.optString("name",viewer));v.put("day",day);v.put("views",1);v.put("firstSeen",now);v.put("lastSeen",now);socialProfileViews=appendLimitedCopy(socialProfileViews,v,50000);saveArray(socialProfileViewsFile,socialProfileViews);}
    }

    private void getSocialProfileViews(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray list=new JSONArray();int count=0;
        synchronized(DATA_LOCK){for(int i=socialProfileViews.length()-1;i>=0;i--){JSONObject v=socialProfileViews.optJSONObject(i);if(v==null||!owner.equals(v.optString("owner")))continue;String viewer=v.optString("viewer");JSONObject row=null;for(int k=0;k<list.length();k++){JSONObject existing=list.optJSONObject(k);if(existing!=null&&viewer.equals(existing.optString("phone"))){row=existing;break;}}if(row==null){row=new JSONObject();JSONObject viewerUser=findUser(viewer);row.put("phone",viewerUser==null?"":socialId(viewerUser));row.put("name",socialDisplayName(viewerUser));row.put("views",0);row.put("firstSeen",v.optLong("firstSeen"));row.put("lastSeen",v.optLong("lastSeen"));list.put(row);}row.put("views",row.optInt("views",0)+v.optInt("views",1));row.put("firstSeen",Math.min(row.optLong("firstSeen",v.optLong("firstSeen")),v.optLong("firstSeen")));row.put("lastSeen",Math.max(row.optLong("lastSeen",0),v.optLong("lastSeen")));count+=v.optInt("views",1);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("uniqueViewers",list.length());r.put("viewsCount",count);r.put("viewers",list);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialLiked(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),target=resolveAccountKey(queryParam(query,"phone"));if(target.length()==0)target=viewer;JSONObject owner=findUser(target);if(owner==null||(!viewer.equals(target)&&isBlockedEither(viewer,target))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}if(!profileVisibilityAllowed(owner,"likes",viewer,target)){sendJson(out,403,"Forbidden",errorJson("Daftar postingan disukai dibatasi oleh privasi akun"));return;}
        JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=socialPosts.length()-1;i>=0;i--){JSONObject p=socialPosts.optJSONObject(i);if(p==null||p.optBoolean("archived",false)||!canReadPost(viewer,p))continue;JSONArray likes=p.optJSONArray("likes");if(likes==null)continue;for(int j=0;j<likes.length();j++){JSONObject l=likes.optJSONObject(j);if(l!=null&&target.equals(l.optString("phone"))){list.put(publicPost(p,viewer));break;}}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",list);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialReposted(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),target=resolveAccountKey(queryParam(query,"phone"));if(target.length()==0)target=viewer;JSONObject owner=findUser(target);if(owner==null||(!viewer.equals(target)&&isBlockedEither(viewer,target))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}if(!viewer.equals(target)&&"private".equals(socialPrivacy(owner).optString("account","public"))&&!isFollowing(viewer,target)){sendJson(out,403,"Forbidden",errorJson("Postingan ulang akun privat hanya dapat dilihat pengikut"));return;}JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){for(int i=socialPosts.length()-1;i>=0;i--){JSONObject p=socialPosts.optJSONObject(i);if(p==null||p.optBoolean("archived",false)||!canReadPost(viewer,p))continue;JSONArray arr=p.optJSONArray("reposts");if(arr==null)continue;for(int j=0;j<arr.length();j++){JSONObject rp=arr.optJSONObject(j);if(rp!=null&&target.equals(rp.optString("phone"))){JSONObject row=publicPost(p,viewer);JSONObject repostUser=findUser(target);row.put("repostedBy",repostUser==null?"":socialId(repostUser));row.put("repostedByIsMine",samePhone(viewer,target));list.put(row);break;}}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",list);sendJson(out,200,"OK",r.toString());
    }

    private void changePassword(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String current=d.optString("currentPassword",""),next=d.optString("newPassword","");if(next.length()<6||next.length()>100){sendJson(out,400,"Bad Request",errorJson("Kata sandi baru minimal 6 karakter dan maksimal 100 karakter"));return;}
        synchronized(DATA_LOCK){JSONObject u=findUser(me.optString("phone"));String salt=u.optString("salt",""),stored=u.optString("passwordHash","");boolean valid=stored.startsWith("pbkdf2$")?constantTimeEquals(stored,hashPassword(salt,current)):constantTimeEquals(stored,legacyHashPassword(salt,current));if(!valid){sendJson(out,403,"Forbidden",errorJson("Kata sandi saat ini salah"));return;}String newSalt=UUID.randomUUID().toString();u.put("salt",newSalt);u.put("passwordHash",hashPassword(newSalt,next));saveArray(usersFile,users);JSONArray kept=new JSONArray();String token=d.optString("token","");for(int i=0;i<sessions.length();i++){JSONObject ss=sessions.optJSONObject(i);if(ss!=null&&u.optString("phone").equals(ss.optString("phone"))&&constantTimeEquals(token,ss.optString("token")))kept.put(ss);}sessions=kept;saveArray(sessionsFile,sessions);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message","Kata sandi berhasil diperbarui. Sesi perangkat lain telah dikeluarkan.");sendJson(out,200,"OK",r.toString());
    }

    private void getSocialProfile(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),target=resolveAccountKey(queryParam(query,"phone"));if(target.length()==0)target=viewer;JSONObject u=findUser(target);if(u==null||(!viewer.equals(target)&&isBlockedEither(viewer,target))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}
        JSONObject privacy=socialPrivacy(u);boolean self=viewer.equals(target),follows=isFollowing(viewer,target);if(!self)recordProfileView(viewer,target);
        JSONArray visible=new JSONArray();int followers=0,following=0,postCount=0; synchronized(DATA_LOCK){for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f==null||(f.has("status")&&!"accepted".equals(f.optString("status"))))continue;if(target.equals(f.optString("following")))followers++;if(target.equals(f.optString("follower")))following++;}boolean accountLocked=!self&&"private".equals(privacy.optString("account","public"))&&!follows;if(!accountLocked){for(int i=socialPosts.length()-1;i>=0;i--){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&target.equals(p.optString("author"))&&!p.optBoolean("archived",false)&&canReadPost(viewer,p)){postCount++;visible.put(publicPost(p,viewer));}}}}
        boolean canFollowers=profileVisibilityAllowed(u,"followers",viewer,target),canFollowing=profileVisibilityAllowed(u,"following",viewer,target),canLikes=profileVisibilityAllowed(u,"likes",viewer,target),canSaved=profileVisibilityAllowed(u,"saved",viewer,target);
        JSONObject outUser=userPublicSocial(u,viewer);outUser.put("isSelf",self);addSocialPresence(outUser,u,viewer);if(self)outUser.put("privacy",privacy);JSONObject r=new JSONObject();r.put("ok",true);r.put("user",outUser);r.put("canDirectMessage",self||canDirectMessage(viewer,target));r.put("postsCount",postCount);r.put("followersCount",followers);r.put("followingCount",following);r.put("isFollowing",follows);r.put("followRequested",hasPendingFollow(viewer,target));r.put("isFriend",isSocialFriend(viewer,target));r.put("friendRequestSent",hasPendingFriendRequest(viewer,target));r.put("friendRequestReceived",hasPendingFriendRequest(target,viewer));r.put("locked",!self&&"private".equals(privacy.optString("account","public"))&&!follows);r.put("canSeeFollowers",canFollowers);r.put("canSeeFollowing",canFollowing);r.put("canSeeLikes",canLikes);r.put("canSeeSaved",canSaved);r.put("profileViewsCount",self?profileViewCount(target):0);r.put("posts",visible);r.put("highlights",(!self&&"private".equals(privacy.optString("account","public"))&&!follows)?new JSONArray():getHighlightArray(target,viewer));sendJson(out,200,"OK",r.toString());
    }

    private boolean hasPendingFollow(String follower,String target){for(int i=0;i<socialFollows.length();i++){JSONObject f=socialFollows.optJSONObject(i);if(f!=null&&follower.equals(f.optString("follower"))&&target.equals(f.optString("following"))&&"pending".equals(f.optString("status")))return true;}return false;}
    private int profileViewCount(String owner){int n=0;for(int i=0;i<socialProfileViews.length();i++){JSONObject v=socialProfileViews.optJSONObject(i);if(v!=null&&owner.equals(v.optString("owner")))n+=v.optInt("views",1);}return n;}
    private JSONArray getHighlightArray(String owner,String viewer)throws JSONException{
        JSONArray groups=new JSONArray();Map<String,JSONObject> groupIndex=new HashMap<>();JSONObject ownerUser=findUser(owner);String publicOwner=ownerUser==null?"":socialId(ownerUser),ownerName=socialDisplayName(ownerUser);
        for(int i=socialHighlights.length()-1;i>=0;i--){
            JSONObject raw=socialHighlights.optJSONObject(i);if(raw==null||!owner.equals(raw.optString("owner")))continue;
            String visibility=raw.optString("visibility","public");
            if(!samePhone(viewer,owner)){if("private".equals(visibility))continue;if("friends".equals(visibility)&&!isSocialFriend(viewer,owner))continue;if("followers".equals(visibility)&&!isFollowing(viewer,owner))continue;}
            String collectionId=raw.optString("collectionId","");if(collectionId.length()==0)collectionId=raw.optString("id","");
            JSONObject group=groupIndex.get(collectionId);
            if(group==null){group=new JSONObject();group.put("id",collectionId);group.put("collectionId",collectionId);group.put("owner",publicOwner);group.put("ownerName",ownerName);group.put("name",raw.optString("name","Sorotan"));group.put("visibility",raw.optString("visibility","public"));group.put("coverStatusId",raw.optString("coverStatusId",raw.optString("statusId","")));group.put("isMine",samePhone(viewer,owner));group.put("createdAt",raw.optLong("createdAt",0));group.put("_itemsReverse",new JSONArray());groups.put(group);groupIndex.put(collectionId,group);}
            JSONObject item=new JSONObject();item.put("id",raw.optString("statusId",raw.optString("id")));item.put("statusId",raw.optString("statusId",raw.optString("id")));item.put("phone",publicOwner);item.put("name",ownerName);item.put("isMine",samePhone(viewer,owner));item.put("mediaId",raw.optString("mediaId",""));item.put("mediaType",raw.optString("mediaType",""));item.put("mimeType",raw.optString("mimeType",""));item.put("fileName",raw.optString("fileName",""));item.put("fileSize",raw.optLong("fileSize",0));item.put("text",raw.optString("text",""));item.put("time",raw.optLong("storyTime",raw.optLong("createdAt",0)));item.put("createdAt",raw.optLong("createdAt",0));
            group.optJSONArray("_itemsReverse").put(item);if(raw.optLong("createdAt",0)>group.optLong("updatedAt",0))group.put("updatedAt",raw.optLong("createdAt",0));
        }
        JSONArray out=new JSONArray();for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);if(group==null)continue;JSONArray reverse=group.optJSONArray("_itemsReverse"),items=new JSONArray();
            if(reverse!=null)for(int j=reverse.length()-1;j>=0;j--){JSONObject item=reverse.optJSONObject(j);if(item!=null)items.put(item);}
            group.remove("_itemsReverse");group.put("items",items);group.put("itemCount",items.length());
            if(items.length()>0){String coverId=group.optString("coverStatusId","");JSONObject cover=items.optJSONObject(0);for(int ci=0;ci<items.length();ci++){JSONObject candidate=items.optJSONObject(ci);if(candidate!=null&&(coverId.equals(candidate.optString("id"))||coverId.equals(candidate.optString("statusId")))){cover=candidate;break;}}if(cover!=null){group.put("coverStatusId",cover.optString("id"));group.put("mediaId",cover.optString("mediaId",""));group.put("mediaType",cover.optString("mediaType",""));group.put("mimeType",cover.optString("mimeType",""));group.put("fileName",cover.optString("fileName",""));group.put("fileSize",cover.optLong("fileSize",0));group.put("text",cover.optString("text",""));group.put("storyTime",cover.optLong("time",0));}}
            out.put(group);
        }
        return out;
    }

    private void getSocialSearch(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),q=queryParam(query,"q").trim().toLowerCase(Locale.US);if(q.length()>80)q=q.substring(0,80);JSONArray matchedUsers=new JSONArray(),matchedPosts=new JSONArray();
        synchronized(DATA_LOCK){if(q.length()>0){for(int i=0;i<users.length()&&matchedUsers.length()<30;i++){JSONObject u=users.optJSONObject(i);if(u==null||viewer.equals(u.optString("phone"))||isBlockedEither(viewer,u.optString("phone")))continue;String name=u.optString("name","").toLowerCase(Locale.US),about=u.optString("about","").toLowerCase(Locale.US),username=u.optString("username",makeSocialUsername(u.optString("name"),u.optString("phone"))).toLowerCase(Locale.US);if(socialDisplayName(u).toLowerCase(Locale.US).contains(q)||username.contains(q)||about.contains(q)){JSONObject row=userPublicSocial(u,viewer);addSocialPresence(row,u,viewer);row.put("isFollowing",isFollowing(viewer,u.optString("phone")));row.put("followRequested",hasPendingFollow(viewer,u.optString("phone")));row.put("isFriend",isSocialFriend(viewer,u.optString("phone")));row.put("friendRequestSent",hasPendingFriendRequest(viewer,u.optString("phone")));row.put("friendRequestReceived",hasPendingFriendRequest(u.optString("phone"),viewer));matchedUsers.put(row);}}for(int i=socialPosts.length()-1;i>=0&&matchedPosts.length()<30;i--){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&!p.optBoolean("archived",false)&&canReadPost(viewer,p)&&(p.optString("text").toLowerCase(Locale.US).contains(q)||p.optString("authorName").toLowerCase(Locale.US).contains(q)))matchedPosts.put(publicPost(p,viewer));}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("query",q);r.put("users",matchedUsers);r.put("posts",matchedPosts);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialNotifications(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}JSONArray outList=new JSONArray();String phone=me.optString("phone");synchronized(DATA_LOCK){for(int i=socialNotifications.length()-1;i>=0;i--){JSONObject n=socialNotifications.optJSONObject(i);if(n!=null&&phone.equals(n.optString("recipient"))){JSONObject safe=new JSONObject(n.toString());JSONObject actor=findUser(n.optString("actor"));safe.put("actor",actor==null?"":socialId(actor));safe.put("actorName",socialDisplayName(actor));safe.remove("recipient");safe.remove("actorPhone");outList.put(safe);}}}JSONObject r=new JSONObject();r.put("ok",true);r.put("notifications",outList);sendJson(out,200,"OK",r.toString());
    }

    private void markSocialNotificationsRead(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=me.optString("phone"),wanted=d.optString("id","");int changed=0;synchronized(DATA_LOCK){for(int i=0;i<socialNotifications.length();i++){JSONObject n=socialNotifications.optJSONObject(i);if(n!=null&&phone.equals(n.optString("recipient"))&&(wanted.length()==0||wanted.equals(n.optString("id")))){if(!n.optBoolean("read")){n.put("read",true);changed++;}}}saveArray(socialNotificationsFile,socialNotifications);}JSONObject r=new JSONObject();r.put("ok",true);r.put("updated",changed);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialCommunities(OutputStream out, String query, String kind) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=me.optString("phone");JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=socialCommunities.length()-1;i>=0;i--){JSONObject c=socialCommunities.optJSONObject(i);if(c==null||!kind.equals(c.optString("kind")))continue;boolean member=containsString(c.optJSONArray("members"),phone),follower=containsString(c.optJSONArray("followers"),phone);if("public".equals(c.optString("privacy","public"))||phone.equals(c.optString("owner"))||member||follower)list.put(publicCommunity(c,phone));}}JSONObject r=new JSONObject();r.put("ok",true);r.put("items",list);sendJson(out,200,"OK",r.toString());
    }

    private boolean containsString(JSONArray a, String value){if(a==null)return false;for(int i=0;i<a.length();i++)if(value.equals(a.optString(i)))return true;return false;}
    private JSONObject publicCommunity(JSONObject c,String viewer)throws JSONException{JSONObject r=new JSONObject();r.put("id",c.optString("id"));r.put("kind",c.optString("kind"));r.put("name",c.optString("name"));r.put("description",c.optString("description"));JSONObject ownerUser=findUser(c.optString("owner"));r.put("owner",ownerUser==null?"":socialId(ownerUser));r.put("isOwner",viewer.equals(c.optString("owner")));r.put("ownerName",socialDisplayName(ownerUser));r.put("privacy",c.optString("privacy","public"));r.put("memberCount", "channel".equals(c.optString("kind")) ? (c.optJSONArray("followers")==null?0:c.optJSONArray("followers").length()) : (c.optJSONArray("members")==null?0:c.optJSONArray("members").length()));r.put("following","channel".equals(c.optString("kind"))?(viewer.equals(c.optString("owner"))||containsString(c.optJSONArray("followers"),viewer)):containsString(c.optJSONArray("followers"),viewer));r.put("joined",containsString(c.optJSONArray("members"),viewer));r.put("createdAt",c.optLong("createdAt"));return r;}

    private void createSocialCommunity(OutputStream out, JSONObject d, String kind) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),name=d.optString("name","").trim(),description=d.optString("description","").trim(),privacy=d.optString("privacy","public");
        if(name.length()<2||name.length()>70||description.length()>500){sendJson(out,400,"Bad Request",errorJson("Nama harus 2–70 karakter dan deskripsi maksimal 500 karakter"));return;}
        if(!"private".equals(privacy))privacy="public";
        JSONArray members=new JSONArray();members.put(owner);int added=0;
        if("group".equals(kind)){
            JSONArray requested=d.optJSONArray("memberPhones");
            if(requested!=null){
                if(requested.length()>100){sendJson(out,400,"Bad Request",errorJson("Maksimal 100 teman dapat dipilih sekaligus"));return;}
                synchronized(DATA_LOCK){
                    for(int i=0;i<requested.length();i++){
                        String target=resolveAccountKey(requested.optString(i,""));
                        if(!validPhone(target)||samePhone(owner,target)||containsString(members,target)||findUser(target)==null||!canInviteToGroup(owner,target)||isBlockedEither(owner,target))continue;
                        members.put(target);added++;
                    }
                }
            }
        }
        JSONObject c=new JSONObject();c.put("id",UUID.randomUUID().toString().replace("-",""));c.put("kind",kind);c.put("name",name);c.put("description",description);c.put("owner",owner);c.put("ownerName",me.optString("name"));c.put("privacy",privacy);c.put("members",members);c.put("followers",new JSONArray());c.put("createdAt",System.currentTimeMillis());
        synchronized(DATA_LOCK){socialCommunities=appendLimitedCopy(socialCommunities,c,2000);saveArray(socialCommunitiesFile,socialCommunities);if("group".equals(kind)){for(int i=1;i<members.length();i++)addSocialNotification(members.optString(i),owner,"community",c.optString("id"),"menambahkanmu ke grup "+name);}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("item",publicCommunity(c,owner));r.put("addedMembers",added);sendJson(out,201,"Created",r.toString());
    }

    private void getGroupMembers(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String groupId=queryParam(query,"groupId"),viewer=me.optString("phone");
        JSONObject group=findCommunity(groupId,"group");
        if(group==null){sendJson(out,404,"Not Found",errorJson("Grup tidak ditemukan"));return;}
        if(!canAccessGroup(group,viewer)){sendJson(out,403,"Forbidden",errorJson("Kamu belum bergabung dengan grup ini"));return;}
        JSONArray outMembers=new JSONArray(),raw=group.optJSONArray("members");
        if(raw!=null){for(int i=0;i<raw.length();i++){String phone=raw.optString(i);JSONObject u=findUser(phone);if(u==null)continue;JSONObject row=new JSONObject();row.put("id",socialId(u));row.put("name",socialDisplayName(u));row.put("username",u.optString("username",makeSocialUsername(u.optString("name"),phone)));row.put("isMe",samePhone(viewer,phone));row.put("isOwner",samePhone(group.optString("owner"),phone));outMembers.put(row);}}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("members",outMembers);result.put("group",publicCommunity(group,viewer));sendJson(out,200,"OK",result.toString());
    }

    private void manageGroupMembers(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),groupId=d.optString("groupId",""),action=d.optString("action","add");
        JSONObject group=findCommunity(groupId,"group");
        if(group==null){sendJson(out,404,"Not Found",errorJson("Grup tidak ditemukan"));return;}
        if(!owner.equals(group.optString("owner"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik grup yang dapat mengelola anggota"));return;}
        int added=0,removed=0,skipped=0;
        synchronized(DATA_LOCK){
            JSONArray old=group.optJSONArray("members");if(old==null)old=new JSONArray();JSONArray next=new JSONArray();
            if("remove".equals(action)){
                String target=resolveAccountKey(d.optString("target",""));
                if(target.length()==0||samePhone(target,owner)){sendJson(out,400,"Bad Request",errorJson("Anggota yang dipilih tidak valid"));return;}
                for(int i=0;i<old.length();i++){String p=old.optString(i);if(samePhone(p,target)){removed++;continue;}next.put(p);}
                if(removed==0){sendJson(out,404,"Not Found",errorJson("Anggota tidak ditemukan di grup"));return;}
            }else if("add".equals(action)){
                JSONArray requested=d.optJSONArray("phones");
                if(requested==null||requested.length()==0){sendJson(out,400,"Bad Request",errorJson("Pilih minimal satu teman"));return;}
                if(requested.length()>100){sendJson(out,400,"Bad Request",errorJson("Maksimal 100 teman dapat dipilih sekaligus"));return;}
                for(int i=0;i<old.length();i++)next.put(old.optString(i));
                for(int i=0;i<requested.length();i++){
                    String target=resolveAccountKey(requested.optString(i,""));
                    if(!validPhone(target)||samePhone(target,owner)||containsString(next,target)||findUser(target)==null||!canInviteToGroup(owner,target)||isBlockedEither(owner,target)){skipped++;continue;}
                    if(next.length()>=251){skipped+=requested.length()-i;break;}
                    next.put(target);added++;addSocialNotification(target,owner,"community",groupId,"menambahkanmu ke grup "+group.optString("name"));
                }
            }else{sendJson(out,400,"Bad Request",errorJson("Aksi anggota grup tidak dikenal"));return;}
            group.put("members",next);saveArray(socialCommunitiesFile,socialCommunities);
        }
        JSONObject result=new JSONObject();result.put("ok",true);result.put("added",added);result.put("removed",removed);result.put("skipped",skipped);result.put("group",publicCommunity(group,owner));sendJson(out,200,"OK",result.toString());
    }

    private void joinSocialCommunity(OutputStream out, JSONObject d, String path) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("id",""),phone=me.optString("phone"),action=d.optString("action","join");String kind=path.startsWith("/api/channels")?"channel":"group";JSONObject c=null;boolean joined=false;
        synchronized(DATA_LOCK){for(int i=0;i<socialCommunities.length();i++){JSONObject test=socialCommunities.optJSONObject(i);if(test!=null&&id.equals(test.optString("id"))&&kind.equals(test.optString("kind"))){c=test;break;}}if(c==null){sendJson(out,404,"Not Found",errorJson("Grup atau channel tidak ditemukan"));return;}if("private".equals(c.optString("privacy"))&&!phone.equals(c.optString("owner"))&&!containsString(c.optJSONArray("members"),phone)&&!("channel".equals(kind)&&containsString(c.optJSONArray("followers"),phone))){sendJson(out,403,"Forbidden",errorJson("Grup ini bersifat privat; diperlukan undangan pemilik"));return;}String arrayName="channel".equals(kind)?"followers":"members";JSONArray old=c.optJSONArray(arrayName);if(old==null)old=new JSONArray();JSONArray next=new JSONArray();boolean exists=false;for(int i=0;i<old.length();i++){String v=old.optString(i);if(phone.equals(v)){exists=true;if("leave".equals(action)||"unfollow".equals(action))continue;}next.put(v);}if(!exists&&!"leave".equals(action)&&!"unfollow".equals(action))next.put(phone);c.put(arrayName,next);joined=!"leave".equals(action)&&!"unfollow".equals(action);saveArray(socialCommunitiesFile,socialCommunities);if(!phone.equals(c.optString("owner"))&&joined)addSocialNotification(c.optString("owner"),phone,"community",id,("channel".equals(kind)?"mengikuti channel ":"bergabung ke grup ")+c.optString("name"));}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("joined",joined);r.put("item",publicCommunity(c,phone));sendJson(out,200,"OK",r.toString());
    }

    private void deleteSocialCommunity(OutputStream out, JSONObject d, String kind) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = d.optString("id", "").trim(), owner = me.optString("phone");
        if (id.length() == 0) { sendJson(out, 400, "Bad Request", errorJson("ID grup/Channel tidak valid")); return; }
        synchronized (DATA_LOCK) {
            JSONObject target = null;
            for (int i = 0; i < socialCommunities.length(); i++) {
                JSONObject item = socialCommunities.optJSONObject(i);
                if (item != null && id.equals(item.optString("id")) && kind.equals(item.optString("kind"))) { target = item; break; }
            }
            if (target == null) { sendJson(out, 404, "Not Found", errorJson("Grup/Channel tidak ditemukan")); return; }
            if (!owner.equals(target.optString("owner"))) { sendJson(out, 403, "Forbidden", errorJson("Hanya pemilik yang dapat menghapus grup/Channel")); return; }
            JSONArray nextCommunities = new JSONArray();
            for (int i = 0; i < socialCommunities.length(); i++) {
                JSONObject item = socialCommunities.optJSONObject(i);
                if (item != null && !(id.equals(item.optString("id")) && kind.equals(item.optString("kind")))) nextCommunities.put(item);
            }
            socialCommunities = nextCommunities;
            saveArray(socialCommunitiesFile, socialCommunities);
            if ("group".equals(kind)) {
                JSONArray nextMessages = new JSONArray();
                for (int i = 0; i < socialGroupMessages.length(); i++) {
                    JSONObject item = socialGroupMessages.optJSONObject(i);
                    if (item != null && !id.equals(item.optString("groupId"))) nextMessages.put(item);
                }
                socialGroupMessages = nextMessages;
                saveArray(socialGroupMessagesFile, socialGroupMessages);
            } else {
                JSONArray nextPosts = new JSONArray();
                for (int i = 0; i < socialChannelPosts.length(); i++) {
                    JSONObject item = socialChannelPosts.optJSONObject(i);
                    if (item != null && !id.equals(item.optString("channelId"))) nextPosts.put(item);
                }
                socialChannelPosts = nextPosts;
                saveArray(socialChannelPostsFile, socialChannelPosts);
            }
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("deleted", true); result.put("kind", kind);
        sendJson(out, 200, "OK", result.toString());
    }

    private void recordStatusView(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = d.optString("statusId", ""), viewer = me.optString("phone");
        JSONObject story = null; String owner = ""; boolean activeStory = false, visibleFromHighlight = false;
        long now = System.currentTimeMillis();
        synchronized (DATA_LOCK) {
            for (int i = 0; i < statuses.length(); i++) {
                JSONObject candidate = statuses.optJSONObject(i);
                if (candidate != null && id.equals(candidate.optString("id")) && candidate.optLong("time", 0) >= now - 86400000L) {
                    story = candidate; owner = candidate.optString("phone"); activeStory = true; break;
                }
            }
            if (story == null) {
                for (int i = 0; i < socialStoryHistory.length(); i++) {
                    JSONObject candidate = socialStoryHistory.optJSONObject(i);
                    if (candidate != null && id.equals(candidate.optString("id"))) { story = candidate; owner = candidate.optString("phone"); break; }
                }
                // Expired Stories may collect replay views only while present in a Highlight
                // that this viewer is allowed to see.
                if (story != null && !viewer.equals(owner) && !isBlockedEither(viewer, owner)) {
                    JSONObject ownerAccount = findUser(owner);
                    boolean accountAllowed = ownerAccount == null || !"private".equals(socialPrivacy(ownerAccount).optString("account", "public")) || isFollowing(viewer, owner);
                    for (int i = 0; accountAllowed && i < socialHighlights.length(); i++) {
                        JSONObject h = socialHighlights.optJSONObject(i);
                        if (h == null || !id.equals(h.optString("statusId")) || !owner.equals(h.optString("owner"))) continue;
                        String visibility = h.optString("visibility", "public");
                        if ("public".equals(visibility) || ("followers".equals(visibility) && isFollowing(viewer, owner))
                                || ("friends".equals(visibility) && isSocialFriend(viewer, owner))) { visibleFromHighlight = true; break; }
                    }
                }
            }
            if (story == null) { sendJson(out, 404, "Not Found", errorJson("Story tidak ditemukan atau sudah dihapus")); return; }
            if (activeStory && !canReadStatus(viewer, story)) { sendJson(out, 403, "Forbidden", errorJson("Kamu tidak memiliki izin melihat Story ini")); return; }
            if (!activeStory && !viewer.equals(owner) && !visibleFromHighlight) { sendJson(out, 403, "Forbidden", errorJson("Story lama hanya dapat dihitung saat dilihat melalui Sorotan yang diizinkan")); return; }
            if (!viewer.equals(owner)) {
                JSONObject found = null;
                for (int i = 0; i < statusViews.length(); i++) {
                    JSONObject v = statusViews.optJSONObject(i);
                    if (v != null && id.equals(v.optString("statusId")) && viewer.equals(v.optString("viewer"))) { found = v; break; }
                }
                if (found == null) {
                    found = new JSONObject(); found.put("statusId", id); found.put("owner", owner); found.put("viewer", viewer);
                    found.put("viewerName", me.optString("name")); found.put("firstSeen", now); found.put("lastSeen", now); found.put("views", 1);
                    statusViews = appendLimitedCopy(statusViews, found, 20000);
                } else { found.put("lastSeen", now); found.put("views", found.optInt("views", 1) + 1); }
                saveArray(statusViewsFile, statusViews);
            }
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("recorded", true); sendJson(out, 200, "OK", result.toString());
    }

    private void getStatusViews(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=queryParam(query,"statusId"),owner=me.optString("phone");JSONObject status=null;
        synchronized(DATA_LOCK){for(int i=0;i<statuses.length();i++){JSONObject st=statuses.optJSONObject(i);if(st!=null&&id.equals(st.optString("id"))&&owner.equals(st.optString("phone"))){status=st;break;}}
            if(status==null){for(int i=0;i<socialStoryHistory.length();i++){JSONObject st=socialStoryHistory.optJSONObject(i);if(st!=null&&id.equals(st.optString("id"))&&owner.equals(st.optString("phone"))){status=st;break;}}}
            if(status==null){sendJson(out,403,"Forbidden",errorJson("Daftar penonton hanya dapat dilihat pemilik Story"));return;}}
        JSONArray viewers=new JSONArray(),likes=new JSONArray();long totalViews=0;
        synchronized(DATA_LOCK){for(int i=0;i<statusViews.length();i++){JSONObject v=statusViews.optJSONObject(i);if(v!=null&&id.equals(v.optString("statusId"))){JSONObject safe=new JSONObject();String viewer=v.optString("viewer");JSONObject vu=findUser(viewer);safe.put("name",socialDisplayName(vu));safe.put("phone",vu==null?"":socialId(vu));safe.put("firstSeen",v.optLong("firstSeen"));safe.put("lastSeen",v.optLong("lastSeen"));safe.put("views",v.optInt("views",1));viewers.put(safe);totalViews+=v.optInt("views",1);}}
            JSONArray rawLikes=status.optJSONArray("likes");if(rawLikes!=null){for(int i=0;i<rawLikes.length();i++){JSONObject like=rawLikes.optJSONObject(i);if(like==null)continue;JSONObject safe=new JSONObject();JSONObject lu=findUser(like.optString("phone"));safe.put("name",socialDisplayName(lu));safe.put("phone",lu==null?"":socialId(lu));safe.put("time",like.optLong("time"));likes.put(safe);}}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("uniqueViewers",viewers.length());r.put("totalViews",totalViews);r.put("viewers",viewers);r.put("likes",likes);sendJson(out,200,"OK",r.toString());
    }

    private void toggleStatusLike(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("statusId",""),viewer=me.optString("phone");JSONObject st=null;boolean liked=false;
        synchronized(DATA_LOCK){for(int i=0;i<statuses.length();i++){JSONObject x=statuses.optJSONObject(i);if(x!=null&&id.equals(x.optString("id"))&&x.optLong("time",0)>=System.currentTimeMillis()-86400000L){st=x;break;}}if(st==null){sendJson(out,404,"Not Found",errorJson("Story tidak ditemukan atau sudah berakhir"));return;}if(!canReadStatus(viewer,st)){sendJson(out,403,"Forbidden",errorJson("Kamu tidak memiliki izin melihat Story ini"));return;}JSONArray old=st.optJSONArray("likes");if(old==null)old=new JSONArray();JSONArray next=new JSONArray();boolean had=false;for(int i=0;i<old.length();i++){JSONObject x=old.optJSONObject(i);if(x==null)continue;if(viewer.equals(x.optString("phone"))){had=true;continue;}next.put(x);}if(!had){JSONObject x=new JSONObject();x.put("phone",viewer);x.put("name",me.optString("name"));x.put("time",System.currentTimeMillis());next.put(x);liked=true;}st.put("likes",next);saveArray(statusesFile,statuses);if(liked&&!viewer.equals(st.optString("phone")))addSocialNotification(st.optString("phone"),viewer,"story_like",id,"menyukai Story kamu");}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("liked",liked);r.put("likesCount",st.optJSONArray("likes")==null?0:st.optJSONArray("likes").length());sendJson(out,200,"OK",r.toString());
    }

    private void createSocialHighlight(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),statusId=d.optString("statusId",""),requestedCollection=d.optString("highlightId","").trim(),name=d.optString("name","Sorotan").trim();
        JSONObject status=null;String collectionId=requestedCollection;String visibility=d.optString("visibility","public");
        synchronized(DATA_LOCK){
            for(int i=0;i<statuses.length();i++){JSONObject candidate=statuses.optJSONObject(i);if(candidate!=null&&statusId.equals(candidate.optString("id"))&&owner.equals(candidate.optString("phone"))){status=candidate;break;}}
            if(status==null){for(int i=0;i<socialStoryHistory.length();i++){JSONObject candidate=socialStoryHistory.optJSONObject(i);if(candidate!=null&&statusId.equals(candidate.optString("id"))&&owner.equals(candidate.optString("phone"))){status=candidate;break;}}}
            if(status==null){sendJson(out,404,"Not Found",errorJson("Story milikmu tidak ditemukan di aktif maupun riwayat"));return;}
            // Satu Story boleh disimpan di beberapa koleksi Sorotan. Cegah duplikat hanya
            // bila Story yang sama ditambahkan dua kali ke koleksi tujuan yang sama.
            if(requestedCollection.length()>0){
                for(int i=0;i<socialHighlights.length();i++){
                    JSONObject old=socialHighlights.optJSONObject(i);
                    if(old==null||!owner.equals(old.optString("owner"))||!statusId.equals(old.optString("statusId")))continue;
                    String oldCollection=old.optString("collectionId","");if(oldCollection.length()==0)oldCollection=old.optString("id","");
                    if(requestedCollection.equals(oldCollection)||requestedCollection.equals(old.optString("id"))){sendJson(out,409,"Conflict",errorJson("Story ini sudah ada di Sorotan yang dipilih"));return;}
                }
            }
            JSONObject selected=null;
            if(requestedCollection.length()>0){
                for(int i=0;i<socialHighlights.length();i++){JSONObject candidate=socialHighlights.optJSONObject(i);if(candidate==null||!owner.equals(candidate.optString("owner")))continue;String cid=candidate.optString("collectionId","");if(cid.length()==0)cid=candidate.optString("id","");if(requestedCollection.equals(cid)||requestedCollection.equals(candidate.optString("id"))){selected=candidate;collectionId=cid;break;}}
                if(selected==null){sendJson(out,404,"Not Found",errorJson("Sorotan yang dipilih tidak ditemukan"));return;}
                name=selected.optString("name","Sorotan");
                visibility=selected.optString("visibility","public");
            }else{
                if(name.length()<1||name.length()>40){sendJson(out,400,"Bad Request",errorJson("Nama Sorotan harus 1–40 karakter"));return;}
                if(!("public".equals(visibility)||"followers".equals(visibility)||"friends".equals(visibility)||"private".equals(visibility))){visibility="public";}
                collectionId=UUID.randomUUID().toString().replace("-","");
            }
            String coverStatusId=selected==null?statusId:selected.optString("coverStatusId",selected.optString("statusId",statusId));
            JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString().replace("-",""));item.put("collectionId",collectionId);item.put("owner",owner);item.put("name",name);item.put("visibility",visibility);item.put("coverStatusId",coverStatusId);item.put("statusId",statusId);item.put("text",status.optString("text"));item.put("mediaId",status.optString("mediaId",""));item.put("mediaType",status.optString("mediaType",""));item.put("mimeType",status.optString("mimeType",""));item.put("fileName",status.optString("fileName",""));item.put("fileSize",status.optLong("fileSize",0));item.put("createdAt",System.currentTimeMillis());item.put("storyTime",status.optLong("time"));
            socialHighlights=appendLimitedCopy(socialHighlights,item,4000);saveArray(socialHighlightsFile,socialHighlights);
            JSONArray all=getHighlightArray(owner,owner);JSONObject resultGroup=null;for(int i=0;i<all.length();i++){JSONObject g=all.optJSONObject(i);if(g!=null&&collectionId.equals(g.optString("id"))){resultGroup=g;break;}}
            JSONObject result=new JSONObject();result.put("ok",true);result.put("created",requestedCollection.length()==0);result.put("highlight",resultGroup==null?new JSONObject():resultGroup);sendJson(out,201,"Created",result.toString());
        }
    }

    private JSONObject findMusicTrack(String id){for(int i=0;i<socialMusic.length();i++){JSONObject track=socialMusic.optJSONObject(i);if(track!=null&&id.equals(track.optString("id")))return track;}return null;}
    private boolean canReadMusic(String viewer,JSONObject track){if(track==null)return false;String owner=track.optString("owner");if(viewer.equals(owner))return true;if(isBlockedEither(viewer,owner))return false;JSONObject user=findUser(owner);if(user==null)return false;return !"private".equals(socialPrivacy(user).optString("account","public"))||isFollowing(viewer,owner);}
    private boolean isMusicSaved(String phone,String musicId){for(int i=0;i<socialMusicFavorites.length();i++){JSONObject f=socialMusicFavorites.optJSONObject(i);if(f!=null&&phone.equals(f.optString("phone"))&&musicId.equals(f.optString("musicId")))return true;}return false;}
    private JSONObject publicMusic(JSONObject raw,String viewer)throws JSONException{JSONObject r=new JSONObject();r.put("id",raw.optString("id"));r.put("title",raw.optString("title","Audio OHLI"));JSONObject ownerUser=findUser(raw.optString("owner"));String artist=raw.optString("artist","");if(artist.length()==0)artist=socialDisplayName(ownerUser);if(ownerUser!=null){String artistDigits=normalizePhone(artist);if(validPhone(artistDigits)&&artistDigits.equals(ownerUser.optString("phone")))artist=socialDisplayName(ownerUser);}r.put("artist",artist);r.put("owner",ownerUser==null?"":socialId(ownerUser));r.put("ownerName",socialDisplayName(ownerUser));r.put("mediaId",raw.optString("mediaId"));r.put("mimeType",raw.optString("mimeType","audio/mpeg"));r.put("fileName",raw.optString("fileName","audio"));r.put("fileSize",raw.optLong("fileSize",0));r.put("createdAt",raw.optLong("createdAt",0));r.put("durationSeconds",raw.optInt("durationSeconds",0));r.put("uses",raw.optInt("uses",0));r.put("saved",isMusicSaved(viewer,raw.optString("id")));r.put("isMine",viewer.equals(raw.optString("owner")));return r;}

    private void getMusicCatalog(OutputStream out,String query,boolean favoritesOnly)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),q=queryParam(query,"q").trim().toLowerCase(Locale.US);JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){for(int i=socialMusic.length()-1;i>=0;i--){JSONObject t=socialMusic.optJSONObject(i);if(t==null||!canReadMusic(viewer,t))continue;if(q.length()>0&&!(t.optString("title").toLowerCase(Locale.US).contains(q)||t.optString("artist").toLowerCase(Locale.US).contains(q)||t.optString("ownerName").toLowerCase(Locale.US).contains(q)))continue;boolean saved=isMusicSaved(viewer,t.optString("id"));if(favoritesOnly&&!saved)continue;list.put(publicMusic(t,viewer));}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("music",list);r.put("count",list.length());sendJson(out,200,"OK",r.toString());
    }

    private void getMyMusic(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");JSONArray list=new JSONArray();
        synchronized(DATA_LOCK){for(int i=socialMusic.length()-1;i>=0;i--){JSONObject t=socialMusic.optJSONObject(i);if(t!=null&&owner.equals(t.optString("owner")))list.put(publicMusic(t,owner));}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("music",list);r.put("count",list.length());sendJson(out,200,"OK",r.toString());
    }

    private void getMusicDetail(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),id=queryParam(query,"id");JSONObject track;JSONArray videos=new JSONArray();
        synchronized(DATA_LOCK){track=findMusicTrack(id);if(track==null||!canReadMusic(viewer,track)){sendJson(out,404,"Not Found",errorJson("Musik tidak ditemukan atau tidak dapat diakses"));return;}for(int i=socialPosts.length()-1;i>=0;i--){JSONObject post=socialPosts.optJSONObject(i);if(post!=null&&id.equals(post.optString("audioId"))&&!post.optBoolean("archived",false)&&canReadPost(viewer,post))videos.put(publicPost(post,viewer));}}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("music",publicMusic(track,viewer));r.put("videos",videos);sendJson(out,200,"OK",r.toString());
    }

    private void toggleMusicFavorite(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String phone=me.optString("phone"),id=d.optString("musicId","");String action=d.optString("action","toggle");boolean saved=false;
        synchronized(DATA_LOCK){JSONObject track=findMusicTrack(id);if(track==null||!canReadMusic(phone,track)){sendJson(out,404,"Not Found",errorJson("Musik tidak ditemukan atau tidak dapat diakses"));return;}JSONArray next=new JSONArray();boolean had=false;for(int i=0;i<socialMusicFavorites.length();i++){JSONObject f=socialMusicFavorites.optJSONObject(i);if(f==null)continue;if(phone.equals(f.optString("phone"))&&id.equals(f.optString("musicId"))){had=true;if("remove".equals(action)||"toggle".equals(action))continue;}next.put(f);}if(!had&&!("remove".equals(action))){JSONObject fav=new JSONObject();fav.put("phone",phone);fav.put("musicId",id);fav.put("time",System.currentTimeMillis());next.put(fav);saved=true;}else if(had&&"save".equals(action))saved=true;socialMusicFavorites=next;saveArray(socialMusicFavoritesFile,socialMusicFavorites);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("saved",saved);r.put("musicId",id);sendJson(out,200,"OK",r.toString());
    }

    private void deleteMusicTrack(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("musicId",""),phone=me.optString("phone"),mediaId="";synchronized(DATA_LOCK){JSONArray next=new JSONArray();JSONObject removed=null;for(int i=0;i<socialMusic.length();i++){JSONObject t=socialMusic.optJSONObject(i);if(t!=null&&id.equals(t.optString("id"))){removed=t;continue;}if(t!=null)next.put(t);}if(removed==null){sendJson(out,404,"Not Found",errorJson("Musik tidak ditemukan"));return;}if(!phone.equals(removed.optString("owner"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik musik yang dapat menghapusnya"));return;}for(int i=0;i<socialPosts.length();i++){JSONObject p=socialPosts.optJSONObject(i);if(p!=null&&id.equals(p.optString("audioId"))){sendJson(out,409,"Conflict",errorJson("Musik sudah dipakai pada postingan. Hapus atau ganti audio postingan itu terlebih dahulu."));return;}}mediaId=removed.optString("mediaId");socialMusic=next;saveArray(socialMusicFile,socialMusic);JSONArray favs=new JSONArray();for(int i=0;i<socialMusicFavorites.length();i++){JSONObject f=socialMusicFavorites.optJSONObject(i);if(f!=null&&!id.equals(f.optString("musicId")))favs.put(f);}socialMusicFavorites=favs;saveArray(socialMusicFavoritesFile,socialMusicFavorites);}
        if(mediaId.matches("[a-fA-F0-9]{32}")){File file=new File(mediaDir,mediaId+".bin");if(file.exists())file.delete();}JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);sendJson(out,200,"OK",r.toString());
    }

    private void getSocialHighlights(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String viewer=me.optString("phone"),owner=resolveAccountKey(queryParam(query,"phone"));if(owner.length()==0)owner=viewer;JSONObject u=findUser(owner);if(u==null||(!viewer.equals(owner)&&isBlockedEither(viewer,owner))){sendJson(out,404,"Not Found",errorJson("Profil tidak ditemukan"));return;}if(!viewer.equals(owner)&&"private".equals(socialPrivacy(u).optString("account","public"))&&!isFollowing(viewer,owner)){sendJson(out,403,"Forbidden",errorJson("Sorotan akun ini hanya dapat dilihat pengikut"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("highlights",(!viewer.equals(owner)&&"private".equals(socialPrivacy(u).optString("account","public"))&&!isFollowing(viewer,owner))?new JSONArray():getHighlightArray(owner,viewer));sendJson(out,200,"OK",r.toString());
    }

    private void deleteSocialHighlight(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("id",""),owner=me.optString("phone");JSONArray next=new JSONArray();boolean found=false;
        synchronized(DATA_LOCK){for(int i=0;i<socialHighlights.length();i++){JSONObject h=socialHighlights.optJSONObject(i);if(h==null)continue;String collectionId=h.optString("collectionId","");if(collectionId.length()==0)collectionId=h.optString("id","");if(owner.equals(h.optString("owner"))&&(id.equals(collectionId)||id.equals(h.optString("id")))){found=true;continue;}next.put(h);}if(found){socialHighlights=next;saveArray(socialHighlightsFile,socialHighlights);}}
        if(!found){sendJson(out,404,"Not Found",errorJson("Sorotan tidak ditemukan"));return;}JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);r.put("collectionId",id);sendJson(out,200,"OK",r.toString());
    }

    private void editSocialHighlight(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", ""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),id=d.optString("id",""),name=d.optString("name","").trim(),visibility=d.optString("visibility",""),coverStatusId=d.optString("coverStatusId","").trim();
        if(id.length()==0||(!name.isEmpty()&&(name.length()<1||name.length()>40))){sendJson(out,400,"Bad Request",errorJson("Nama Sorotan tidak valid"));return;}
        if(!visibility.isEmpty()&&!("public".equals(visibility)||"followers".equals(visibility)||"friends".equals(visibility)||"private".equals(visibility))){sendJson(out,400,"Bad Request",errorJson("Privasi Sorotan tidak valid"));return;}
        int changed=0;synchronized(DATA_LOCK){boolean validCover=coverStatusId.isEmpty();if(!validCover){for(int i=0;i<socialHighlights.length();i++){JSONObject h=socialHighlights.optJSONObject(i);if(h==null||!owner.equals(h.optString("owner")))continue;String cid=h.optString("collectionId","");if(cid.length()==0)cid=h.optString("id","");if((id.equals(cid)||id.equals(h.optString("id")))&&coverStatusId.equals(h.optString("statusId"))){validCover=true;break;}}}if(!validCover){sendJson(out,400,"Bad Request",errorJson("Sampul harus dipilih dari Story di Sorotan ini"));return;}for(int i=0;i<socialHighlights.length();i++){JSONObject h=socialHighlights.optJSONObject(i);if(h==null||!owner.equals(h.optString("owner")))continue;String cid=h.optString("collectionId","");if(cid.length()==0)cid=h.optString("id","");if(id.equals(cid)||id.equals(h.optString("id"))){if(!name.isEmpty())h.put("name",name);if(!visibility.isEmpty())h.put("visibility",visibility);if(!coverStatusId.isEmpty())h.put("coverStatusId",coverStatusId);h.put("updatedAt",System.currentTimeMillis());changed++;}}if(changed>0)saveArray(socialHighlightsFile,socialHighlights);}
        if(changed==0){sendJson(out,404,"Not Found",errorJson("Sorotan tidak ditemukan"));return;}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("updated",changed);r.put("highlights",getHighlightArray(owner,owner));sendJson(out,200,"OK",r.toString());
    }

    private void deleteSocialHighlightItem(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", ""));
        if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String owner=me.optString("phone"),collectionId=d.optString("highlightId",""),statusId=d.optString("statusId","");
        JSONArray next=new JSONArray();boolean found=false;
        synchronized(DATA_LOCK){for(int i=0;i<socialHighlights.length();i++){JSONObject h=socialHighlights.optJSONObject(i);if(h==null)continue;String cid=h.optString("collectionId","");if(cid.length()==0)cid=h.optString("id","");if(owner.equals(h.optString("owner"))&&collectionId.equals(cid)&&statusId.equals(h.optString("statusId"))){found=true;continue;}next.put(h);}if(found){socialHighlights=next;saveArray(socialHighlightsFile,socialHighlights);}}
        if(!found){sendJson(out,404,"Not Found",errorJson("Story tidak ditemukan di Sorotan ini"));return;}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("deleted",true);sendJson(out,200,"OK",r.toString());
    }

    private JSONObject findCommunity(String id,String kind){for(int i=0;i<socialCommunities.length();i++){JSONObject c=socialCommunities.optJSONObject(i);if(c!=null&&id.equals(c.optString("id"))&&kind.equals(c.optString("kind")))return c;}return null;}
    private boolean canAccessGroup(JSONObject c,String phone){return c!=null&&"group".equals(c.optString("kind"))&&(containsString(c.optJSONArray("members"),phone)||phone.equals(c.optString("owner")));}
    private JSONObject publicGroupMessage(JSONObject raw,String viewer)throws JSONException{
        JSONObject safe=new JSONObject(raw.toString());
        String sender=raw.optString("sender");JSONObject senderUser=findUser(sender);
        safe.put("sender",senderUser==null?"":socialId(senderUser));safe.put("name",socialDisplayName(senderUser));safe.put("isMine",samePhone(viewer,sender));
        JSONArray options=raw.optJSONArray("options");
        if(options!=null){JSONArray safeOptions=new JSONArray();for(int i=0;i<options.length();i++){JSONObject option=options.optJSONObject(i);if(option==null)continue;JSONObject so=new JSONObject(option.toString());JSONArray votes=option.optJSONArray("votes"),safeVotes=new JSONArray();boolean voted=false;if(votes!=null)for(int j=0;j<votes.length();j++){String voter=votes.optString(j);if(samePhone(viewer,voter))voted=true;JSONObject voterUser=findUser(voter);safeVotes.put(voterUser==null?"":socialId(voterUser));}so.put("votes",safeVotes);so.put("votedByMe",voted);safeOptions.put(so);}safe.put("options",safeOptions);}
        return safe;
    }
    private void getGroupMessages(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String groupId=queryParam(query,"groupId"),phone=me.optString("phone");JSONObject group=findCommunity(groupId,"group");if(group==null){sendJson(out,404,"Not Found",errorJson("Grup tidak ditemukan"));return;}if(!canAccessGroup(group,phone)){sendJson(out,403,"Forbidden",errorJson("Kamu belum bergabung dengan grup ini"));return;}JSONArray list=new JSONArray();synchronized(DATA_LOCK){JSONArray reverse=new JSONArray();for(int i=socialGroupMessages.length()-1;i>=0&&reverse.length()<100;i--){JSONObject m=socialGroupMessages.optJSONObject(i);if(m!=null&&groupId.equals(m.optString("groupId")))reverse.put(m);}for(int i=reverse.length()-1;i>=0;i--){JSONObject raw=reverse.optJSONObject(i);if(raw!=null)list.put(publicGroupMessage(raw,phone));}}JSONObject r=new JSONObject();r.put("ok",true);r.put("messages",list);r.put("group",publicCommunity(group,phone));sendJson(out,200,"OK",r.toString());
    }
    private void postGroupMessage(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=d.optString("groupId",""),kind=d.optString("kind","text"),text=d.optString("text","").trim(),phone=me.optString("phone");JSONObject group=findCommunity(id,"group");
        if(group==null){sendJson(out,404,"Not Found",errorJson("Grup tidak ditemukan"));return;}if(!canAccessGroup(group,phone)){sendJson(out,403,"Forbidden",errorJson("Kamu belum bergabung dengan grup ini"));return;}
        JSONObject msg=new JSONObject();msg.put("id",UUID.randomUUID().toString().replace("-",""));msg.put("groupId",id);msg.put("sender",phone);msg.put("name",me.optString("name"));msg.put("time",System.currentTimeMillis());
        if("poll".equals(kind)){
            String question=d.optString("question",text).trim();JSONArray raw=d.optJSONArray("options");if(question.length()<2||question.length()>240||raw==null||raw.length()<2||raw.length()>6){sendJson(out,400,"Bad Request",errorJson("Polling membutuhkan pertanyaan dan 2–6 pilihan"));return;}
            JSONArray options=new JSONArray();for(int i=0;i<raw.length();i++){String option=raw.optString(i,"").trim();if(option.length()<1||option.length()>80){sendJson(out,400,"Bad Request",errorJson("Setiap pilihan harus 1–80 karakter"));return;}JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString().replace("-",""));item.put("text",option);item.put("votes",new JSONArray());options.put(item);}
            msg.put("kind","poll");msg.put("text",question);msg.put("options",options);
        }else{if(text.length()<1||text.length()>2000){sendJson(out,400,"Bad Request",errorJson("Pesan grup harus 1–2000 karakter"));return;}msg.put("kind","text");msg.put("text",text);}
        synchronized(DATA_LOCK){socialGroupMessages=appendLimitedCopy(socialGroupMessages,msg,5000);saveArray(socialGroupMessagesFile,socialGroupMessages);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message",publicGroupMessage(msg,phone));sendJson(out,201,"Created",r.toString());
    }

    private void voteGroupPoll(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String groupId=d.optString("groupId",""),messageId=d.optString("messageId",""),optionId=d.optString("optionId",""),phone=me.optString("phone");JSONObject group=findCommunity(groupId,"group");
        if(group==null||!canAccessGroup(group,phone)){sendJson(out,403,"Forbidden",errorJson("Akses grup tidak tersedia"));return;}
        JSONObject target=null; synchronized(DATA_LOCK){for(int i=0;i<socialGroupMessages.length();i++){JSONObject m=socialGroupMessages.optJSONObject(i);if(m!=null&&messageId.equals(m.optString("id"))&&groupId.equals(m.optString("groupId"))&&"poll".equals(m.optString("kind"))){target=m;break;}}
            if(target==null){sendJson(out,404,"Not Found",errorJson("Polling tidak ditemukan"));return;}
            JSONArray options=target.optJSONArray("options");if(options==null){sendJson(out,400,"Bad Request",errorJson("Pilihan polling tidak valid"));return;}
            boolean validOption=false;for(int i=0;i<options.length();i++){JSONObject option=options.optJSONObject(i);if(option!=null&&optionId.equals(option.optString("id"))){validOption=true;break;}}
            if(!validOption){sendJson(out,404,"Not Found",errorJson("Pilihan polling tidak ditemukan"));return;}
            // Validate the option before mutating anything: a malformed vote must not clear an earlier vote.
            for(int i=0;i<options.length();i++){JSONObject option=options.optJSONObject(i);if(option==null)continue;JSONArray votes=option.optJSONArray("votes");if(votes==null)votes=new JSONArray();JSONArray next=new JSONArray();for(int j=0;j<votes.length();j++){String v=votes.optString(j);if(!phone.equals(v))next.put(v);}if(optionId.equals(option.optString("id")))next.put(phone);option.put("votes",next);}
            saveArray(socialGroupMessagesFile,socialGroupMessages);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("message",publicGroupMessage(target,phone));sendJson(out,200,"OK",r.toString());
    }

    private JSONObject publicChannelPost(JSONObject raw,String viewer)throws JSONException{
        JSONObject p=new JSONObject(raw.toString());JSONArray old=raw.optJSONArray("reactions"),counts=new JSONArray();Map<String,Integer> totals=new HashMap<String,Integer>();String mine="";
        if(old!=null)for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item==null)continue;String emoji=item.optString("emoji");if(emoji.length()==0)continue;totals.put(emoji,(totals.containsKey(emoji)?totals.get(emoji):0)+1);if(viewer.equals(item.optString("phone")))mine=emoji;}
        for(String emoji:new String[]{"👍","❤️","🔥","👏"}){JSONObject count=new JSONObject();count.put("emoji",emoji);count.put("count",totals.containsKey(emoji)?totals.get(emoji):0);count.put("mine",emoji.equals(mine));counts.put(count);}p.put("reactionSummary",counts);
        JSONObject author=findUser(raw.optString("author"));p.put("author",author==null?"":socialId(author));p.put("authorName",socialDisplayName(author));p.put("isMine",samePhone(viewer,raw.optString("author")));
        JSONArray safeReactions=new JSONArray();if(old!=null)for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item!=null){JSONObject sr=new JSONObject(item.toString());String reactionPhone=item.optString("phone");JSONObject ru=findUser(reactionPhone);sr.put("phone",ru==null?"":socialId(ru));sr.put("isMine",samePhone(viewer,reactionPhone));safeReactions.put(sr);}}if(old!=null)p.put("reactions",safeReactions);return p;
    }

    private void getChannelPosts(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=queryParam(query,"channelId"),phone=me.optString("phone");JSONObject c=findCommunity(id,"channel");if(c==null){sendJson(out,404,"Not Found",errorJson("Channel tidak ditemukan"));return;}boolean access="public".equals(c.optString("privacy","public"))||phone.equals(c.optString("owner"))||containsString(c.optJSONArray("followers"),phone);if(!access){sendJson(out,403,"Forbidden",errorJson("Ikuti channel privat untuk membaca postingannya"));return;}JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=socialChannelPosts.length()-1;i>=0;i--){JSONObject p=socialChannelPosts.optJSONObject(i);if(p!=null&&id.equals(p.optString("channelId")))list.put(publicChannelPost(p,phone));}}JSONObject r=new JSONObject();r.put("ok",true);r.put("posts",list);r.put("channel",publicCommunity(c,phone));sendJson(out,200,"OK",r.toString());
    }

    private void toggleChannelReaction(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String channelId=d.optString("channelId",""),postId=d.optString("postId",""),emoji=d.optString("emoji",""),phone=me.optString("phone");
        if(!"👍".equals(emoji)&&!"❤️".equals(emoji)&&!"🔥".equals(emoji)&&!"👏".equals(emoji)){sendJson(out,400,"Bad Request",errorJson("Reaksi tidak didukung"));return;}JSONObject channel=findCommunity(channelId,"channel");if(channel==null){sendJson(out,404,"Not Found",errorJson("Channel tidak ditemukan"));return;}if(!"public".equals(channel.optString("privacy","public"))&&!phone.equals(channel.optString("owner"))&&!containsString(channel.optJSONArray("followers"),phone)){sendJson(out,403,"Forbidden",errorJson("Ikuti channel untuk memberi reaksi"));return;}
        JSONObject post=null;synchronized(DATA_LOCK){for(int i=0;i<socialChannelPosts.length();i++){JSONObject item=socialChannelPosts.optJSONObject(i);if(item!=null&&postId.equals(item.optString("id"))&&channelId.equals(item.optString("channelId"))){post=item;break;}}if(post==null){sendJson(out,404,"Not Found",errorJson("Postingan Channel tidak ditemukan"));return;}JSONArray old=post.optJSONArray("reactions");if(old==null)old=new JSONArray();JSONArray next=new JSONArray();boolean same=false;for(int i=0;i<old.length();i++){JSONObject r=old.optJSONObject(i);if(r==null)continue;if(phone.equals(r.optString("phone"))){if(emoji.equals(r.optString("emoji")))same=true;continue;}next.put(r);}if(!same){JSONObject r=new JSONObject();r.put("phone",phone);r.put("emoji",emoji);r.put("time",System.currentTimeMillis());next.put(r);}post.put("reactions",next);saveArray(socialChannelPostsFile,socialChannelPosts);}
        JSONObject result=new JSONObject();result.put("ok",true);result.put("post",publicChannelPost(post,phone));sendJson(out,200,"OK",result.toString());
    }
    private void createChannelPost(OutputStream out,JSONObject d)throws IOException,JSONException{
        JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String id=d.optString("channelId",""),text=d.optString("text","").trim(),phone=me.optString("phone");JSONObject c=findCommunity(id,"channel");if(c==null){sendJson(out,404,"Not Found",errorJson("Channel tidak ditemukan"));return;}if(!phone.equals(c.optString("owner"))){sendJson(out,403,"Forbidden",errorJson("Hanya pemilik channel yang dapat membuat postingan"));return;}if(text.length()<1||text.length()>2000){sendJson(out,400,"Bad Request",errorJson("Postingan Channel harus 1–2000 karakter"));return;}JSONObject p=new JSONObject();p.put("id",UUID.randomUUID().toString().replace("-",""));p.put("channelId",id);p.put("author",phone);p.put("authorName",me.optString("name"));p.put("authorUsername",me.optString("username",makeSocialUsername(me.optString("name"),phone)));p.put("text",text);p.put("createdAt",System.currentTimeMillis());synchronized(DATA_LOCK){socialChannelPosts=appendLimitedCopy(socialChannelPosts,p,5000);saveArray(socialChannelPostsFile,socialChannelPosts);JSONArray followers=c.optJSONArray("followers");if(followers!=null)for(int i=0;i<followers.length();i++)addSocialNotification(followers.optString(i),phone,"channel_post",id,"menerbitkan postingan baru di "+c.optString("name"));}JSONObject r=new JSONObject();r.put("ok",true);r.put("post",publicChannelPost(p,phone));sendJson(out,201,"Created",r.toString());
    }

    private void getContactQr(OutputStream out,String query)throws IOException,JSONException{
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}JSONObject payload=new JSONObject();payload.put("type","ohli-contact");payload.put("version",1);payload.put("phone",me.optString("phone"));payload.put("name",me.optString("name"));String qrText=payload.toString();String baseUrl=queryParam(query,"baseUrl").trim();if(baseUrl.matches("(?i)^https?://[a-z0-9._:-]+(?::[0-9]{1,5})?$")&&baseUrl.length()<240){try{qrText=baseUrl+"/?ohli_profile="+URLEncoder.encode(me.optString("phone"),"UTF-8")+"&ohli_name="+URLEncoder.encode(me.optString("name"),"UTF-8");}catch(Exception ignored){}}try{java.util.Map<EncodeHintType,Object> hints=new java.util.EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);hints.put(EncodeHintType.MARGIN,2);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");BitMatrix matrix=new MultiFormatWriter().encode(qrText,BarcodeFormat.QR_CODE,420,420,hints);Bitmap bitmap=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888);for(int y=0;y<420;y++)for(int x=0;x<420;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();sendResponseCached(out,200,"OK","image/png",bytes.toByteArray(),60);}catch(Exception e){sendJson(out,500,"Internal Server Error",errorJson("QR kontak gagal dibuat"));}
    }

    private JSONObject publicStoryHistory(JSONObject raw, String owner) throws JSONException {
        JSONObject safe = new JSONObject(raw.toString());
        safe.put("id", raw.optString("id"));
        safe.put("statusId", raw.optString("id"));
        safe.put("phone", socialId(findUser(owner)));
        safe.put("name", socialDisplayName(findUser(owner)));
        safe.put("isMine", true);
        safe.put("historyAt", raw.optLong("historyAt", raw.optLong("time", 0)));
        safe.put("viewsCount", countStoryViews(raw.optString("id")));
        safe.remove("owner");
        return safe;
    }

    private long countStoryViews(String statusId) {
        long count = 0;
        for (int i = 0; i < statusViews.length(); i++) {
            JSONObject view = statusViews.optJSONObject(i);
            if (view != null && statusId.equals(view.optString("statusId"))) count += Math.max(1, view.optInt("views", 1));
        }
        return count;
    }

    private void getStoryHistory(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me = userForToken(queryParam(query, "token"));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"); JSONArray list = new JSONArray();
        synchronized (DATA_LOCK) {
            for (int i = socialStoryHistory.length() - 1; i >= 0; i--) {
                JSONObject story = socialStoryHistory.optJSONObject(i);
                if (story == null || !owner.equals(story.optString("phone"))) continue;
                list.put(publicStoryHistory(story, owner));
                if (list.length() >= 500) break;
            }
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("stories", list); result.put("count", list.length());
        sendJson(out, 200, "OK", result.toString());
    }

    private void deleteStoryHistoryItem(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"), id = d.optString("statusId", ""); boolean found = false;
        synchronized (DATA_LOCK) {
            JSONArray next = new JSONArray();
            for (int i = 0; i < socialStoryHistory.length(); i++) {
                JSONObject item = socialStoryHistory.optJSONObject(i);
                if (item != null && owner.equals(item.optString("phone")) && id.equals(item.optString("id"))) { found = true; continue; }
                if (item != null) next.put(item);
            }
            if (found) { socialStoryHistory = next; saveArray(socialStoryHistoryFile, socialStoryHistory); }
        }
        if (!found) { sendJson(out, 404, "Not Found", errorJson("Story tidak ditemukan di riwayat")); return; }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("deleted", true); sendJson(out, 200, "OK", result.toString());
    }

    private void repostStoryHistoryItem(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"), id = d.optString("statusId", ""); JSONObject source = null, repost = null;
        synchronized (DATA_LOCK) {
            for (int i = socialStoryHistory.length() - 1; i >= 0; i--) {
                JSONObject item = socialStoryHistory.optJSONObject(i);
                if (item != null && owner.equals(item.optString("phone")) && id.equals(item.optString("id"))) { source = item; break; }
            }
            if (source == null) { sendJson(out, 404, "Not Found", errorJson("Story tidak ditemukan di riwayat")); return; }
            repost = new JSONObject(source.toString());
            repost.put("id", UUID.randomUUID().toString()); repost.put("time", System.currentTimeMillis());
            repost.put("phone", owner); repost.put("name", me.optString("name"));
            repost.put("audience", d.optString("audience", source.optString("audience", "contacts")));
            repost.put("distribution", d.optString("destination", source.optString("distribution", "both")));
            repost.put("likes", new JSONArray()); repost.remove("historyAt");
            appendLimited(statuses, repost, MAX_STATUSES); saveArray(statusesFile, statuses);
            JSONObject historyCopy = new JSONObject(repost.toString()); historyCopy.put("historyAt", repost.optLong("time"));
            socialStoryHistory = appendLimitedCopy(socialStoryHistory, historyCopy, 5000); saveArray(socialStoryHistoryFile, socialStoryHistory);
        }
        JSONObject safe = new JSONObject(repost.toString()); safe.put("isMine", true);
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("status", safe); sendJson(out, 201, "Created", result.toString());
    }

    private void postStatus(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me=userForToken(d.optString("token", "")); if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String text=d.optString("text", "").trim(), encoded=d.optString("data", "");
        if(text.length()>240 || (text.length()==0 && encoded.length()==0)){sendJson(out,400,"Bad Request",errorJson("Isi teks status atau pilih foto; teks maksimal 240 karakter"));return;}
        String audience=d.optString("audience","contacts"),destination=d.optString("destination","both");
        if(!("public".equals(audience)||"followers".equals(audience)||"contacts".equals(audience)||"private".equals(audience)))audience="contacts";
        if(!("social".equals(destination)||"chat".equals(destination)||"both".equals(destination)))destination="both";
        JSONObject st=new JSONObject(); st.put("id",UUID.randomUUID().toString()); st.put("phone",me.optString("phone")); st.put("name",me.optString("name")); st.put("text",text); st.put("time",System.currentTimeMillis());st.put("audience",audience);st.put("distribution",destination);
        if(encoded.length()>0){
            if(encoded.length()>((MAX_IMAGE_BYTES*4/3)+1000)){sendJson(out,413,"Payload Too Large",errorJson("Foto status terlalu besar"));return;}
            byte[] image; try{image=Base64.decode(encoded,Base64.DEFAULT);}catch(Exception e){sendJson(out,400,"Bad Request",errorJson("Data foto status tidak valid"));return;}
            if(image.length==0||image.length>MAX_IMAGE_BYTES){sendJson(out,413,"Payload Too Large",errorJson("Foto status maksimal sekitar 180 KB"));return;}
            String mediaId=UUID.randomUUID().toString().replace("-",""); File file=new File(imagesDir,mediaId+".jpg"); FileOutputStream fos=null;
            try{fos=new FileOutputStream(file);fos.write(image);fos.flush();fos.getFD().sync();}catch(Exception e){if(file.exists())file.delete();sendJson(out,500,"Internal Server Error",errorJson("Gagal menyimpan foto status"));return;}finally{if(fos!=null)try{fos.close();}catch(Exception ignored){}}
            st.put("mediaId",mediaId); st.put("mediaType","image");
        }
        synchronized(DATA_LOCK){appendLimited(statuses,st,MAX_STATUSES);saveArray(statusesFile,statuses);JSONObject historyCopy=new JSONObject(st.toString());historyCopy.put("historyAt",st.optLong("time"));socialStoryHistory=appendLimitedCopy(socialStoryHistory,historyCopy,5000);saveArray(socialStoryHistoryFile,socialStoryHistory);}
        JSONObject publicStatus=new JSONObject(st.toString());if(!"chat".equals(destination)){publicStatus.put("phone",socialId(me));publicStatus.put("isMine",true);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("status",publicStatus);sendJson(out,200,"OK",r.toString());
    }

    private void recordSocialActivityHistory(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"), type = d.optString("type", "activity"), id = d.optString("id", "");
        String label = d.optString("label", "Aktivitas OHLI").trim(), query = d.optString("query", "").trim(), clientId = d.optString("clientId", "").trim();
        String[] allowed = {"search", "profile", "post", "photo", "video", "story", "video_complete", "video_skip", "not_interested"};
        boolean valid = false; for (String value : allowed) if (value.equals(type)) valid = true;
        if (!valid) type = "activity";
        if (id.length() > 180) id = id.substring(0, 180);
        if (label.length() > 120) label = label.substring(0, 120);
        if (query.length() > 80) query = query.substring(0, 80);
        if (clientId.length() > 80) clientId = clientId.substring(0, 80);
        if (label.length() == 0) label = "Aktivitas OHLI";

        // Snapshot only media that this user was allowed to view at the time the Story was opened.
        JSONObject storyMedia = null;
        if ("story".equals(type) && id.length() > 0) {
            synchronized (DATA_LOCK) {
                for (int i = statuses.length() - 1; i >= 0; i--) {
                    JSONObject story = statuses.optJSONObject(i);
                    if (story == null || !id.equals(story.optString("id")) || !canReadStatus(owner, story)) continue;
                    String mediaId = story.optString("mediaId", "");
                    if (mediaId.length() > 0) {
                        storyMedia = new JSONObject();
                        storyMedia.put("mediaId", mediaId);
                        storyMedia.put("mediaType", story.optString("mediaType", "image"));
                        storyMedia.put("mimeType", story.optString("mimeType", ""));
                        storyMedia.put("fileName", story.optString("fileName", ""));
                        storyMedia.put("fileSize", story.optLong("fileSize", 0));
                    }
                    break;
                }
            }
        }

        long now = System.currentTimeMillis();
        synchronized (DATA_LOCK) {
            JSONObject duplicate = null;
            for (int i = socialActivityHistory.length() - 1; i >= 0; i--) {
                JSONObject x = socialActivityHistory.optJSONObject(i);
                if (x == null || !owner.equals(x.optString("owner"))) continue;
                if (clientId.length() > 0 && clientId.equals(x.optString("clientId"))) { duplicate = x; break; }
                if (type.equals(x.optString("type")) && id.equals(x.optString("itemId")) && query.equals(x.optString("query")) && now - x.optLong("time", 0) < 60000L) { duplicate = x; break; }
            }
            if (duplicate != null) {
                duplicate.put("label", label); duplicate.put("time", now);
                if (clientId.length() > 0) duplicate.put("clientId", clientId);
                if (storyMedia != null) {
                    duplicate.put("mediaId", storyMedia.optString("mediaId"));
                    duplicate.put("mediaType", storyMedia.optString("mediaType", "image"));
                    duplicate.put("mimeType", storyMedia.optString("mimeType", ""));
                    duplicate.put("fileName", storyMedia.optString("fileName", ""));
                    duplicate.put("fileSize", storyMedia.optLong("fileSize", 0));
                }
            } else {
                JSONObject x = new JSONObject(); x.put("id", UUID.randomUUID().toString().replace("-", ""));
                x.put("owner", owner); x.put("type", type); x.put("itemId", id); x.put("label", label); x.put("query", query);
                x.put("clientId", clientId); x.put("time", now);
                if (storyMedia != null) {
                    x.put("mediaId", storyMedia.optString("mediaId"));
                    x.put("mediaType", storyMedia.optString("mediaType", "image"));
                    x.put("mimeType", storyMedia.optString("mimeType", ""));
                    x.put("fileName", storyMedia.optString("fileName", ""));
                    x.put("fileSize", storyMedia.optLong("fileSize", 0));
                }
                socialActivityHistory = appendLimitedCopy(socialActivityHistory, x, 50000);
            }
            saveArray(socialActivityHistoryFile, socialActivityHistory);
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("saved", true); result.put("deduplicated", true);
        sendJson(out, 200, "OK", result.toString());
    }

    private void getSocialActivityHistory(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me = userForToken(queryParam(query, "token"));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        JSONArray list = new JSONArray(); String owner = me.optString("phone");
        synchronized (DATA_LOCK) {
            for (int i = socialActivityHistory.length() - 1; i >= 0; i--) {
                JSONObject x = socialActivityHistory.optJSONObject(i);
                if (x == null || !owner.equals(x.optString("owner"))) continue;
                JSONObject safe = new JSONObject(); String itemId = x.optString("itemId"), type = x.optString("type");
                safe.put("id", x.optString("id")); safe.put("type", type); safe.put("itemId", itemId);
                safe.put("label", x.optString("label")); safe.put("query", x.optString("query")); safe.put("time", x.optLong("time"));
                JSONObject post = findSocialPost(itemId);
                if (post != null && canReadPost(owner, post) && post.optString("mediaId").length() > 0) {
                    JSONObject thumb = new JSONObject(); thumb.put("id", post.optString("id")); thumb.put("mediaId", post.optString("mediaId"));
                    thumb.put("mediaType", post.optString("mediaType", "image")); thumb.put("text", post.optString("text", ""));
                    thumb.put("authorName", socialDisplayName(findUser(post.optString("author")))); thumb.put("createdAt", post.optLong("createdAt", 0));
                    safe.put("mediaId", post.optString("mediaId")); safe.put("mediaType", post.optString("mediaType", "image")); safe.put("post", thumb);
                } else if ("story".equals(type) && owner.equals(x.optString("owner")) && x.optString("mediaId").length() > 0) {
                    safe.put("mediaId", x.optString("mediaId")); safe.put("mediaType", x.optString("mediaType", "image"));
                    safe.put("mimeType", x.optString("mimeType", "")); safe.put("storyAvailable", true);
                } else if ("story".equals(type)) {
                    for (int si = statuses.length() - 1; si >= 0; si--) {
                        JSONObject story = statuses.optJSONObject(si);
                        if (story != null && itemId.equals(story.optString("id")) && canReadStatus(owner, story) && story.optString("mediaId").length() > 0) {
                            safe.put("mediaId", story.optString("mediaId")); safe.put("mediaType", story.optString("mediaType", "image"));
                            safe.put("mimeType", story.optString("mimeType", "")); safe.put("storyAvailable", true); break;
                        }
                    }
                }
                list.put(safe); if (list.length() >= 500) break;
            }
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("activities", list);
        sendJson(out, 200, "OK", result.toString());
    }

    private void clearSocialActivityHistory(OutputStream out,JSONObject d)throws IOException,JSONException{JSONObject me=userForToken(d.optString("token",""));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone");synchronized(DATA_LOCK){JSONArray next=new JSONArray();for(int i=0;i<socialActivityHistory.length();i++){JSONObject x=socialActivityHistory.optJSONObject(i);if(x!=null&&!owner.equals(x.optString("owner")))next.put(x);}socialActivityHistory=next;saveArray(socialActivityHistoryFile,socialActivityHistory);}JSONObject r=new JSONObject();r.put("ok",true);r.put("cleared",true);sendJson(out,200,"OK",r.toString());}
    private void deleteStatus(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String id = d.optString("statusId", "").trim(), owner = me.optString("phone");
        if (id.length() == 0) { sendJson(out, 400, "Bad Request", errorJson("ID Story tidak valid")); return; }
        synchronized (DATA_LOCK) {
            boolean found = false;
            JSONArray nextStatuses = new JSONArray();
            for (int i = 0; i < statuses.length(); i++) {
                JSONObject item = statuses.optJSONObject(i);
                if (item != null && id.equals(item.optString("id"))) {
                    if (!owner.equals(item.optString("phone"))) { sendJson(out, 403, "Forbidden", errorJson("Hanya pemilik Story yang dapat menghapusnya")); return; }
                    found = true;
                    continue;
                }
                if (item != null) nextStatuses.put(item);
            }
            if (!found) { sendJson(out, 404, "Not Found", errorJson("Story tidak ditemukan")); return; }
            statuses = nextStatuses;
            saveArray(statusesFile, statuses);
            // Keep view history for the persistent Story History.
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("deleted", true);
        sendJson(out, 200, "OK", result.toString());
    }

    private void updateStoryCollection(OutputStream out, JSONObject d) throws IOException, JSONException {
        JSONObject me = userForToken(d.optString("token", ""));
        if (me == null) { sendJson(out, 401, "Unauthorized", errorJson("Sesi tidak valid")); return; }
        String owner = me.optString("phone"), statusId = d.optString("statusId", ""), action = d.optString("action", "pin");
        if (statusId.length() == 0 || !("pin".equals(action) || "unpin".equals(action) || "archive".equals(action) || "unarchive".equals(action) || "remove".equals(action))) {
            sendJson(out, 400, "Bad Request", errorJson("Aksi koleksi Story tidak valid")); return;
        }
        synchronized (DATA_LOCK) {
            JSONObject saved = null;
            for (int i = 0; i < socialStoryCollections.length(); i++) {
                JSONObject item = socialStoryCollections.optJSONObject(i);
                if (item != null && owner.equals(item.optString("owner")) && statusId.equals(item.optString("statusId"))) { saved = item; break; }
            }

            // Existing saved stories remain manageable even after the 24-hour source expires.
            if ("remove".equals(action) || "unpin".equals(action) || "unarchive".equals(action)) {
                if (saved == null) { sendJson(out, 404, "Not Found", errorJson("Story belum ada di koleksimu")); return; }
                if ("remove".equals(action)) {
                    JSONArray next = new JSONArray();
                    for (int i = 0; i < socialStoryCollections.length(); i++) {
                        JSONObject item = socialStoryCollections.optJSONObject(i);
                        if (item != null && !(owner.equals(item.optString("owner")) && statusId.equals(item.optString("statusId")))) next.put(item);
                    }
                    socialStoryCollections = next;
                } else if ("unpin".equals(action)) saved.put("pinned", false);
                else saved.put("archived", false);
                if (saved != null) {
                    saved.put("updatedAt", System.currentTimeMillis());
                    if (!saved.optBoolean("pinned", false) && !saved.optBoolean("archived", false)) {
                        JSONArray next = new JSONArray();
                        for (int i = 0; i < socialStoryCollections.length(); i++) {
                            JSONObject item = socialStoryCollections.optJSONObject(i);
                            if (item != null && !(owner.equals(item.optString("owner")) && statusId.equals(item.optString("statusId")))) next.put(item);
                        }
                        socialStoryCollections = next;
                    }
                }
                saveArray(socialStoryCollectionsFile, socialStoryCollections);
            } else {
                JSONObject status = null;
                for (int i = 0; i < statuses.length(); i++) {
                    JSONObject candidate = statuses.optJSONObject(i);
                    if (candidate != null && statusId.equals(candidate.optString("id"))) { status = candidate; break; }
                }
                if (saved == null) {
                    if (status == null) { sendJson(out, 404, "Not Found", errorJson("Story sudah kedaluwarsa dan belum pernah disimpan")); return; }
                    if (owner.equals(status.optString("phone")) || !canReadStatus(owner, status)) {
                        sendJson(out, 403, "Forbidden", errorJson("Koleksi hanya untuk Story akun lain yang boleh kamu lihat")); return;
                    }
                    saved = new JSONObject();
                    saved.put("id", UUID.randomUUID().toString().replace("-", ""));
                    saved.put("owner", owner);
                    saved.put("storyOwner", status.optString("phone"));
                    saved.put("statusId", statusId);
                    saved.put("text", status.optString("text"));
                    saved.put("mediaId", status.optString("mediaId", ""));
                    saved.put("mediaType", status.optString("mediaType", ""));
                    saved.put("mimeType", status.optString("mimeType", ""));
                    saved.put("fileName", status.optString("fileName", ""));
                    saved.put("fileSize", status.optLong("fileSize", 0));
                    saved.put("storyTime", status.optLong("time"));
                    saved.put("ownerName", status.optString("name", "Pengguna OHLI"));
                    saved.put("pinned", false);
                    saved.put("archived", false);
                    saved.put("savedAt", System.currentTimeMillis());
                    socialStoryCollections = appendLimitedCopy(socialStoryCollections, saved, 20000);
                } else if (status != null && (!canReadStatus(owner, status) || owner.equals(status.optString("phone")))) {
                    sendJson(out, 403, "Forbidden", errorJson("Akses Story tidak tersedia")); return;
                }
                if ("pin".equals(action)) saved.put("pinned", true);
                else saved.put("archived", true);
                saved.put("updatedAt", System.currentTimeMillis());
                saveArray(socialStoryCollectionsFile, socialStoryCollections);
            }
        }
        JSONObject result = new JSONObject(); result.put("ok", true); result.put("action", action); sendJson(out, 200, "OK", result.toString());
    }

    private void getStoryCollections(OutputStream out,String query)throws IOException,JSONException{JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}String owner=me.optString("phone"),mode=queryParam(query,"mode");JSONArray list=new JSONArray();synchronized(DATA_LOCK){for(int i=socialStoryCollections.length()-1;i>=0;i--){JSONObject x=socialStoryCollections.optJSONObject(i);if(x==null||!owner.equals(x.optString("owner")))continue;if("pinned".equals(mode)&&!x.optBoolean("pinned"))continue;if("archived".equals(mode)&&!x.optBoolean("archived"))continue;JSONObject safe=new JSONObject(x.toString());JSONObject source=findUser(x.optString("storyOwner"));safe.put("storyOwnerKey",source==null?"":socialId(source));safe.put("ownerName",socialDisplayName(source));safe.remove("owner");safe.remove("storyOwner");list.put(safe);}}JSONObject r=new JSONObject();r.put("ok",true);r.put("items",list);sendJson(out,200,"OK",r.toString());}

    private void getStatuses(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String phone=me.optString("phone"),surface=queryParam(query,"surface"); JSONArray list=new JSONArray();long min=System.currentTimeMillis()-24L*60L*60L*1000L;
        synchronized(DATA_LOCK){for(int i=0;i<statuses.length();i++){JSONObject st=statuses.optJSONObject(i);if(st==null||st.optLong("time",0)<min||!canReadStatus(phone,st))continue;String distribution=st.optString("distribution","both");if("social".equals(surface)&&!("social".equals(distribution)||"both".equals(distribution)))continue;if("chat".equals(surface)&&!("chat".equals(distribution)||"both".equals(distribution)))continue;list.put(st);}}
        JSONArray enriched=new JSONArray();for(int i=0;i<list.length();i++){JSONObject raw=list.optJSONObject(i);if(raw==null)continue;JSONObject safe=new JSONObject(raw.toString());JSONObject statusOwner=findUser(raw.optString("phone"));if(statusOwner!=null){safe.put("username",statusOwner.optString("username",makeSocialUsername(statusOwner.optString("name"),statusOwner.optString("phone"))));if("social".equals(surface))safe.put("name",socialDisplayName(statusOwner));}if("social".equals(surface)){safe.put("name",socialDisplayName(statusOwner));safe.put("phone",statusOwner==null?"":socialId(statusOwner));safe.put("isMine",samePhone(phone,raw.optString("phone")));}JSONArray likes=raw.optJSONArray("likes");int count=0;boolean liked=false,viewedByMe=phone.equals(raw.optString("phone"));long seenAt=0;if(likes!=null)for(int j=0;j<likes.length();j++){JSONObject like=likes.optJSONObject(j);if(like!=null){count++;if(phone.equals(like.optString("phone")))liked=true;}}if("social".equals(surface)&&likes!=null){JSONArray safeLikes=new JSONArray();for(int j=0;j<likes.length();j++){JSONObject like=likes.optJSONObject(j);if(like!=null){JSONObject sl=new JSONObject(like.toString());String likePhone=like.optString("phone");JSONObject lu=findUser(likePhone);sl.put("phone",lu==null?"":socialId(lu));sl.put("name",socialDisplayName(lu));sl.put("isMine",samePhone(phone,likePhone));safeLikes.put(sl);}}safe.put("likes",safeLikes);}if(!viewedByMe){synchronized(DATA_LOCK){for(int j=0;j<statusViews.length();j++){JSONObject seen=statusViews.optJSONObject(j);if(seen!=null&&raw.optString("id").equals(seen.optString("statusId"))&&phone.equals(seen.optString("viewer"))){viewedByMe=true;seenAt=seen.optLong("lastSeen",seen.optLong("firstSeen",0));break;}}}}safe.put("likesCount",count);safe.put("liked",liked);safe.put("viewedByMe",viewedByMe);if(seenAt>0)safe.put("seenAt",seenAt);enriched.put(safe);}
        JSONObject r=new JSONObject();r.put("ok",true);r.put("statuses",enriched);sendJson(out,200,"OK",r.toString());
    }

    private void getImage(OutputStream out, String query) throws IOException, JSONException {
        JSONObject me=userForToken(queryParam(query,"token"));if(me==null){sendJson(out,401,"Unauthorized",errorJson("Sesi tidak valid"));return;}
        String id=queryParam(query,"id");if(id.length()<16||id.length()>64||!id.matches("[a-fA-F0-9]+")){sendJson(out,400,"Bad Request",errorJson("ID foto tidak valid"));return;}
        File imageFile=new File(imagesDir,id+".jpg");if(!imageFile.exists()||imageFile.length()>MAX_IMAGE_BYTES){sendJson(out,404,"Not Found",errorJson("Foto tidak ditemukan"));return;}
        boolean authorized=false,onceDenied=false,onceImage=false;String phone=me.optString("phone");
        synchronized(DATA_LOCK){for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&id.equals(m.optString("imageId"))&&(samePhone(phone,m.optString("sender"))||samePhone(phone,m.optString("recipient")))&&!isBlockedEither(m.optString("sender"),m.optString("recipient"))){if(m.optBoolean("viewOnce",false)&&samePhone(phone,m.optString("recipient"))){onceImage=true;if(m.optLong("viewOnceOpenedAt",0)>0)onceDenied=true;else{m.put("viewOnceOpenedAt",System.currentTimeMillis());m.put("viewOnceOpenedBy",phone);saveArray(messagesFile,messages);}}authorized=!onceDenied;break;}}if(!authorized){for(int i=0;i<statuses.length();i++){JSONObject st=statuses.optJSONObject(i);if(st!=null&&id.equals(st.optString("mediaId"))&&canReadStatus(phone,st)){authorized=true;break;}}}if(!authorized){for(int i=0;i<socialHighlights.length();i++){JSONObject h=socialHighlights.optJSONObject(i);if(h==null||!id.equals(h.optString("mediaId")))continue;JSONObject owner=findUser(h.optString("owner"));String highlightVisibility=h.optString("visibility","public");boolean highlightAllowed=phone.equals(h.optString("owner"))||("public".equals(highlightVisibility))||("followers".equals(highlightVisibility)&&isFollowing(phone,h.optString("owner")))||("friends".equals(highlightVisibility)&&isSocialFriend(phone,h.optString("owner")));if(owner!=null&&!isBlockedEither(phone,h.optString("owner"))&&highlightAllowed&&(phone.equals(h.optString("owner"))||!"private".equals(socialPrivacy(owner).optString("account","public"))||isFollowing(phone,h.optString("owner")))){authorized=true;break;}}}}
        if(onceDenied){sendJson(out,410,"Gone",errorJson("Foto sekali lihat sudah dibuka"));return;}if(!authorized){synchronized(DATA_LOCK){for(int i=0;i<socialStoryCollections.length();i++){JSONObject saved=socialStoryCollections.optJSONObject(i);if(saved!=null&&phone.equals(saved.optString("owner"))&&id.equals(saved.optString("mediaId"))){authorized=true;break;}}}}if(!authorized){synchronized(DATA_LOCK){for(int i=0;i<socialStoryHistory.length();i++){JSONObject saved=socialStoryHistory.optJSONObject(i);if(saved!=null&&phone.equals(saved.optString("phone"))&&id.equals(saved.optString("mediaId"))){authorized=true;break;}}}}if(!authorized){synchronized(DATA_LOCK){for(int i=0;i<socialActivityHistory.length();i++){JSONObject saved=socialActivityHistory.optJSONObject(i);if(saved!=null&&phone.equals(saved.optString("owner"))&&"story".equals(saved.optString("type"))&&id.equals(saved.optString("mediaId"))){authorized=true;break;}}}}if(!authorized){sendJson(out,404,"Not Found",errorJson("Foto tidak ditemukan"));return;}
        FileInputStream in=null;ByteArrayOutputStream buf=new ByteArrayOutputStream();try{in=new FileInputStream(imageFile);byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);}finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        if(onceImage)sendResponse(out,200,"OK","image/jpeg",buf.toByteArray());else sendResponseCached(out,200,"OK","image/jpeg",buf.toByteArray(),86400);
    }

    private void loadAll(){ synchronized(DATA_LOCK){messages=loadArray(messagesFile,MAX_MESSAGES);users=loadArray(usersFile,1000);sessions=loadArray(sessionsFile,2000);rebuildUserIndex();rebuildSessionIndex();callSignals=loadArray(callsFile,MAX_CALL_SIGNALS);statuses=loadArray(statusesFile,MAX_STATUSES);savedContacts=loadArray(contactsFile,5000);liveSessions=loadArray(liveFile,MAX_LIVE_SESSIONS);liveSignals=loadArray(liveSignalsFile,MAX_LIVE_SIGNALS);liveComments=loadArray(liveCommentsFile,MAX_LIVE_COMMENTS);socialPosts=loadArray(socialPostsFile,500);socialFollows=loadArray(socialFollowsFile,20000);socialFriends=loadArray(socialFriendsFile,20000);socialBookmarks=loadArray(socialBookmarksFile,20000);socialNotifications=loadArray(socialNotificationsFile,10000);socialCommunities=loadArray(socialCommunitiesFile,2000);statusViews=loadArray(statusViewsFile,20000);socialProfileViews=loadArray(socialProfileViewsFile,50000);socialHighlights=loadArray(socialHighlightsFile,2000);socialGroupMessages=loadArray(socialGroupMessagesFile,5000);socialChannelPosts=loadArray(socialChannelPostsFile,5000);socialBlocks=loadArray(socialBlocksFile,20000);socialMusic=loadArray(socialMusicFile,2000);socialMusicFavorites=loadArray(socialMusicFavoritesFile,20000);socialActivityHistory=loadArray(socialActivityHistoryFile,50000);socialStoryCollections=loadArray(socialStoryCollectionsFile,20000);socialStoryHistory=loadArray(socialStoryHistoryFile,5000);socialCallHistory=loadArray(socialCallHistoryFile,20000);serverInbox=loadArray(serverInboxFile,2000);if(socialStoryHistory.length()==0&&statuses.length()>0){for(int hi=0;hi<statuses.length();hi++){JSONObject oldStory=statuses.optJSONObject(hi);if(oldStory!=null){try{JSONObject archived=new JSONObject(oldStory.toString());archived.put("historyAt",oldStory.optLong("time",System.currentTimeMillis()));socialStoryHistory.put(archived);}catch(Exception ignored){}}}saveArray(socialStoryHistoryFile,socialStoryHistory);}socialStickerUploads=loadArray(socialStickerUploadsFile,10000);socialStickerFavorites=loadArray(socialStickerFavoritesFile,20000);authChallenges=loadArray(authChallengesFile,MAX_AUTH_CHALLENGES);ensureSocialIds();for(int i=0;i<callSignals.length();i++){JSONObject s=callSignals.optJSONObject(i);if(s!=null)nextSignalId=Math.max(nextSignalId,s.optLong("id",0));}for(int i=0;i<liveSignals.length();i++){JSONObject s=liveSignals.optJSONObject(i);if(s!=null)nextLiveSignalId=Math.max(nextLiveSignalId,s.optLong("id",0));}} }
    private JSONArray loadArray(File file,int limit){
        if(file==null)return new JSONArray();
        File temp=new File(getFilesDir(),file.getName()+".tmp"),backup=new File(getFilesDir(),file.getName()+".bak");
        JSONArray mainData=null,tempData=null,backupData=null;
        try{if(file.exists()&&file.length()>0)mainData=new JSONArray(readFile(file));}catch(Exception ignored){}
        try{if(temp.exists()&&temp.length()>0)tempData=new JSONArray(readFile(temp));}catch(Exception ignored){}
        try{if(backup.exists()&&backup.length()>0)backupData=new JSONArray(readFile(backup));}catch(Exception ignored){}
        File chosen=null;JSONArray chosenData=null;
        if(mainData!=null){chosen=file;chosenData=mainData;if(tempData!=null&&temp.lastModified()>file.lastModified()){chosen=temp;chosenData=tempData;}}
        else if(tempData!=null){chosen=temp;chosenData=tempData;}
        else if(backupData!=null){chosen=backup;chosenData=backupData;}
        if(chosenData==null)return new JSONArray();
        if(!file.equals(chosen)){File restore=new File(getFilesDir(),file.getName()+".restore");FileOutputStream out=null;try{out=new FileOutputStream(restore);out.write(chosenData.toString().getBytes(UTF8));out.flush();out.getFD().sync();out.close();out=null;if(file.exists())file.delete();if(restore.renameTo(file))lastError="Data dipulihkan dari cadangan transaksi";}catch(Exception ignored){if(out!=null)try{out.close();}catch(Exception ignored2){}}}
        JSONArray result=new JSONArray();int from=Math.max(0,chosenData.length()-limit);try{for(int i=from;i<chosenData.length();i++)result.put(chosenData.get(i));}catch(Exception ignored){}return result;
    }
    private String readFile(File f)throws IOException{FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();return new String(out.toByteArray(),UTF8);}
    private void saveArray(File f,JSONArray a){
        if (f == usersFile) rebuildUserIndex();
        if (f == sessionsFile) rebuildSessionIndex();
        File temp=new File(getFilesDir(),f.getName()+".tmp");
        File backup=new File(getFilesDir(),f.getName()+".bak");
        FileOutputStream stream=null;
        try {
            stream=new FileOutputStream(temp);
            stream.write(a.toString().getBytes(UTF8));
            stream.flush();
            stream.getFD().sync();
            stream.close(); stream=null;

            // Same-directory rename is substantially cheaper than copying every old JSON
            // database on each like, read marker, heartbeat, or message write.
            boolean previousMoved=false;
            if(f.exists() && f.length()>0){
                if(backup.exists() && !backup.delete()) throw new IOException("backup cleanup failed");
                previousMoved=f.renameTo(backup);
                if(!previousMoved){
                    copyFileForBackup(f,backup);
                }
            }
            if(!temp.renameTo(f)){
                if(previousMoved && backup.exists() && !f.exists()) backup.renameTo(f);
                throw new IOException("replace failed");
            }
            lastError="";
        } catch(Exception e) {
            lastError="Gagal menyimpan data";
            try { if(stream!=null)stream.close(); } catch(Exception ignored) { }
            // The fully fsynced .tmp or previous .bak remains available to loadArray recovery.
        }
    }
    private void copyFileForBackup(File source, File target) throws IOException {
        FileInputStream in=null; FileOutputStream out=null;
        try {
            in=new FileInputStream(source); out=new FileOutputStream(target);
            byte[] buffer=new byte[8192]; int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
            out.flush(); out.getFD().sync();
        } finally {
            if(in!=null)try{in.close();}catch(Exception ignored){}
            if(out!=null)try{out.close();}catch(Exception ignored){}
        }
    }
    private void appendLimited(JSONArray a,JSONObject item,int max)throws JSONException{JSONArray n=new JSONArray();int from=Math.max(0,a.length()-max+1);for(int i=from;i<a.length();i++)n.put(a.get(i));n.put(item);if(a==messages)messages=n;else if(a==callSignals)callSignals=n;else if(a==statuses)statuses=n;else if(a==sessions)sessions=n;else if(a==users)users=n;else if(a==liveSignals)liveSignals=n;else if(a==liveComments)liveComments=n;else if(a==liveSessions)liveSessions=n;}
    private void rebuildUserIndex() {
        synchronized (DATA_LOCK) {
            HashMap<String, JSONObject> next = new HashMap<String, JSONObject>();
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.optJSONObject(i);
                if (user != null) {
                    String phone = user.optString("phone", "");
                    if (phone.length() > 0) next.put(phone, user);
                }
            }
            userIndex = Collections.unmodifiableMap(next);
        }
    }
    private void rebuildSessionIndex() {
        synchronized (DATA_LOCK) {
            HashMap<String, JSONObject> next = new HashMap<String, JSONObject>();
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session != null) {
                    String token = session.optString("token", "");
                    if (token.length() > 0) next.put(token, session);
                }
            }
            sessionIndex = Collections.unmodifiableMap(next);
        }
    }
    private JSONObject findUser(String phone) {
        if (phone == null || phone.length() == 0) return null;
        JSONObject indexed = userIndex.get(phone);
        if (indexed != null) return indexed;
        // Safe fallback keeps older or externally restored data visible until the next index rebuild.
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);
            if (user != null && samePhone(phone, user.optString("phone"))) return user;
        }
        return null;
    }
    private void touchSession(String token){if(token==null||token.length()==0)return;long now=System.currentTimeMillis();synchronized(DATA_LOCK){boolean changed=false;for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null&&constantTimeEquals(token,s.optString("token",""))){if(now-s.optLong("lastActive",0)>30000){try{s.put("lastActive",now);saveArray(sessionsFile,sessions);}catch(Exception ignored){}}break;}}}}
    private boolean isUserAccessAllowed(JSONObject user){if(user==null)return false;String status=user.optString("accessStatus","active");if("disabled".equals(status)||"deleted".equals(status))return false;if("suspended".equals(status)&&user.optLong("suspendedUntil",0L)>System.currentTimeMillis())return false;return true;}
    private JSONObject userForToken(String token){
        if(token==null||token.length()==0)return null;
        synchronized(DATA_LOCK){
            JSONObject session = sessionIndex.get(token);
            if (session == null || !constantTimeEquals(token, session.optString("token", ""))) {
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject candidate = sessions.optJSONObject(i);
                    if (candidate != null && constantTimeEquals(token, candidate.optString("token", ""))) { session = candidate; break; }
                }
            }
            if (session != null && constantTimeEquals(token, session.optString("token", ""))) {
                JSONObject user=findUser(session.optString("phone"));
                if(!isUserAccessAllowed(user))return null;
                long now=System.currentTimeMillis(), last=session.optLong("lastActive",session.optLong("time",now));
                if(now-last>=15000L){
                    try{session.put("lastActive",now);}catch(JSONException ignored){}
                    if(now-lastSessionPresenceSaveAt>=60000L){
                        saveArray(sessionsFile,sessions);
                        lastSessionPresenceSaveAt=now;
                    }
                }
                return user;
            }
        }
        return null;
    }
    private String createSession(String phone)throws JSONException{String token=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");JSONObject s=new JSONObject();s.put("phone",phone);s.put("token",token);s.put("time",System.currentTimeMillis());s.put("lastActive",System.currentTimeMillis());s.put("otpVerified",true);appendLimited(sessions,s,2000);saveArray(sessionsFile,sessions);return token;}
    private String cleanSocialUsername(String value){return value==null?"":value.trim().toLowerCase(Locale.US).replaceAll("^@+","");}
    private String makeSocialUsername(String name,String phone){String base=name==null?"":name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+",".").replaceAll("^[.]+|[.]+$","");if(base.length()==0)base="pengguna";if(base.length()>16)base=base.substring(0,16).replaceAll("[.]+$","");String suffix=Long.toString(Math.abs((long)(phone==null?"":phone).hashCode()),36);if(suffix.length()>4)suffix=suffix.substring(suffix.length()-4);return base+"."+suffix;}
    private JSONObject userPublic(JSONObject u)throws JSONException{JSONObject r=new JSONObject();r.put("phone",u.optString("phone"));r.put("name",u.optString("name"));r.put("username",u.optString("username",makeSocialUsername(u.optString("name"),u.optString("phone"))));r.put("about",u.optString("about",""));r.put("socialAvatarMode",u.optString("socialAvatarMode","same"));r.put("privateAccount",socialPrivacy(u).optString("account","public").equals("private"));return r;}
    private void ensureSocialIds(){boolean changed=false;for(int i=0;i<users.length();i++){JSONObject u=users.optJSONObject(i);if(u!=null&&u.optString("socialId","").length()==0){try{u.put("socialId","oh_"+UUID.randomUUID().toString().replace("-","").substring(0,20));changed=true;}catch(JSONException ignored){}}}if(changed)saveArray(usersFile,users);}
    private String socialId(JSONObject u){if(u==null)return "";String id=u.optString("socialId","");if(id.length()==0){try{id="oh_"+UUID.randomUUID().toString().replace("-","").substring(0,20);u.put("socialId",id);saveArray(usersFile,users);}catch(JSONException ignored){return "";}}return id;}
    private String resolveAccountKey(String key){if(key==null||key.length()==0)return "";String n=normalizePhone(key);if(validPhone(n)&&findUser(n)!=null)return n;for(int i=0;i<users.length();i++){JSONObject u=users.optJSONObject(i);if(u==null)continue;if(key.equals(u.optString("socialId")))return u.optString("phone");String username=u.optString("username",makeSocialUsername(u.optString("name"),u.optString("phone")));if(key.equalsIgnoreCase(username)||key.equalsIgnoreCase("@"+username))return u.optString("phone");}return "";}
    private String socialDisplayName(JSONObject u){if(u==null)return "Pengguna OHLI";String name=u.optString("name","").trim(),phone=u.optString("phone","");String clean=normalizePhone(name);if(name.length()==0||(validPhone(clean)&&clean.equals(phone))){String username=cleanSocialUsername(u.optString("username",makeSocialUsername(name,phone)));return username.length()>0?"@"+username:"Pengguna OHLI";}return name;}
    private JSONObject userPublicSocial(JSONObject u,String viewer)throws JSONException{JSONObject r=userPublic(u);r.put("name",socialDisplayName(u));String id=socialId(u);r.put("phone",id);r.put("socialId",id);r.put("publicId",id);r.put("numberHidden",true);r.remove("phoneNumber");r.remove("mobile");r.remove("number");return r;}
    private String normalizePhone(String p){if(p==null)return "";return p.replaceAll("[^0-9]", "");}
    private boolean validPhone(String p){return p!=null&&p.matches("[0-9]{8,15}");}
    private String cleanName(String n){if(n==null)return "";n=n.trim();if(n.length()>40)n=n.substring(0,40);return n;}
    private String hashPassword(String salt,String password){try{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt.getBytes(UTF8),30000,256);byte[] b=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();spec.clearPassword();StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&0xff));return "pbkdf2$30000$"+s.toString();}catch(Exception e){return "";}}
    private String legacyHashPassword(String salt,String password){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=md.digest((salt+":"+password).getBytes(UTF8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&0xff));return s.toString();}catch(Exception e){return "";}}
    private boolean constantTimeEquals(String a,String b){if(a==null||b==null)return false;int diff=a.length()^b.length();int n=Math.max(a.length(),b.length());for(int i=0;i<n;i++){char x=i<a.length()?a.charAt(i):0,y=i<b.length()?b.charAt(i):0;diff|=x^y;}return diff==0;}
    private boolean samePhone(String a,String b){return a!=null&&b!=null&&a.equals(b);}
    private long nextSignalId(){nextSignalId=Math.max(nextSignalId+1,System.currentTimeMillis());return nextSignalId;}
    private String timeNow(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
    private String queryParam(String query,String wanted){if(query==null||query.length()==0)return "";String[] pairs=query.split("&");for(String pair:pairs){int e=pair.indexOf('=');String k=e>=0?pair.substring(0,e):pair,v=e>=0?pair.substring(e+1):"";try{k=URLDecoder.decode(k,"UTF-8");v=URLDecoder.decode(v,"UTF-8");}catch(Exception ignored){}if(wanted.equals(k))return v;}return "";}
    private JSONObject errorJson(String msg){JSONObject o=new JSONObject();try{o.put("ok",false);o.put("error",msg);}catch(JSONException ignored){}return o;}
    private int getMessageCount(){synchronized(DATA_LOCK){return messages.length();}}
    private void persistAdaptiveModelIfDue(boolean force) {
        if (!force && !adaptiveEngine.shouldPersistModel()) return;
        try {
            getSharedPreferences("ohli_ai_performance", MODE_PRIVATE).edit()
                    .putString("model_state", adaptiveEngine.exportModelState()).apply();
        } catch (Exception ignored) { }
    }
    public static String adaptiveStatus() {
        ServerService service = INSTANCE;
        return service == null ? "OHLI AI Performance: menunggu server" : service.adaptiveEngine.statusLine(service.workers);
    }
    private byte[] readHeaders(InputStream input)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int state=0;while(b.size()<MAX_HEADER_BYTES){int c=input.read();if(c<0)return null;b.write(c);if(state==0&&c=='\r')state=1;else if(state==1&&c=='\n')state=2;else if(state==2&&c=='\r')state=3;else if(state==3&&c=='\n')return b.toByteArray();else if(c=='\r')state=1;else state=0;}throw new IOException("Header terlalu besar");}
    private byte[] readBody(InputStream input,int length)throws IOException{byte[] r=new byte[length];int total=0;while(total<length){int n=input.read(r,total,length-total);if(n<0)return null;total+=n;}return r;}
    private byte[] readAssetCached(String name)throws IOException{synchronized(assetCache){byte[] cached=assetCache.get(name);if(cached!=null)return cached;byte[] loaded=readAsset(name);assetCache.put(name,loaded);return loaded;}}
    private byte[] readAsset(String name)throws IOException{InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();return out.toByteArray();}
    private boolean acceptsGzip(String value){
        if(value==null||value.trim().isEmpty())return false;
        for(String part:value.split(",")){
            String[] item=part.trim().split(";",2);
            if(!"gzip".equalsIgnoreCase(item[0].trim()))continue;
            if(item.length<2)return true;
            Matcher q=Pattern.compile("(?i)q\\s*=\\s*([0-9.]+)").matcher(item[1]);
            if(!q.find())return true;
            try{return Double.parseDouble(q.group(1))>0.0;}catch(Exception ignored){return false;}
        }
        return false;
    }
    private byte[] readAssetGzipCached(String name)throws IOException{
        synchronized(gzipAssetCache){
            byte[] cached=gzipAssetCache.get(name);if(cached!=null)return cached;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            GZIPOutputStream gzip=new GZIPOutputStream(bytes);
            gzip.write(readAssetCached(name));gzip.finish();gzip.close();
            byte[] packed=bytes.toByteArray();gzipAssetCache.put(name,packed);return packed;
        }
    }
    private void sendAssetResponse(OutputStream out,int code,String reason,String type,String asset,int maxAge,boolean gzip)throws IOException{
        byte[] body=gzip?readAssetGzipCached(asset):readAssetCached(asset);
        String cache=maxAge>0?"private, max-age="+maxAge:"no-store";
        StringBuilder header=new StringBuilder("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
                .append("Content-Type: ").append(type).append("\r\nContent-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\nCache-Control: ").append(cache).append("\r\nVary: Accept-Encoding\r\n");
        if(gzip)header.append("Content-Encoding: gzip\r\n");
        header.append("X-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\n")
                .append("Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                .append("Access-Control-Allow-Headers: Content-Type, X-RKM-Token, Range\r\n\r\n");
        out.write(header.toString().getBytes(LATIN1));out.write(body);out.flush();
    }
    private void sendJson(OutputStream out,int code,String reason,String json)throws IOException{sendResponse(out,code,reason,"application/json; charset=utf-8",json.getBytes(UTF8));}
    // Build fix: accept JSONObject responses and serialize them to JSON text.
    private void sendJson(OutputStream out,int code,String reason,JSONObject json)throws IOException{sendJson(out,code,reason,json==null?"{}":json.toString());}
    private void sendResponseCached(OutputStream out,int code,String reason,String type,byte[] body,int maxAge)throws IOException{String header="HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: private, max-age="+Math.max(0,maxAge)+"\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type, X-RKM-Token, Range\r\n\r\n";out.write(header.getBytes(LATIN1));out.write(body);out.flush();}
    private void sendResponse(OutputStream out,int code,String reason,String type,byte[] body)throws IOException{
        byte[] payload=body; boolean compressed=false;
        Boolean accepts=gzipPreference.get();
        if(accepts!=null&&accepts.booleanValue()&&type!=null&&type.toLowerCase(Locale.US).startsWith("application/json")
                &&body!=null&&body.length>=2048&&adaptiveEngine.shouldCompressJson(body.length)){
            ByteArrayOutputStream packed=new ByteArrayOutputStream();
            GZIPOutputStream gzip=null;
            try{gzip=new GZIPOutputStream(packed);gzip.write(body);gzip.finish();gzip.close();gzip=null;
                if(packed.size()+64<body.length){payload=packed.toByteArray();compressed=true;}
            }catch(Exception ignored){payload=body;compressed=false;}
            finally{if(gzip!=null)try{gzip.close();}catch(Exception ignored){}}
        }
        StringBuilder header=new StringBuilder("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
                .append("Content-Type: ").append(type).append("\r\nContent-Length: ").append(payload.length).append("\r\n")
                .append("Connection: close\r\nCache-Control: no-store\r\n");
        if(compressed)header.append("Content-Encoding: gzip\r\nVary: Accept-Encoding\r\n");
        header.append("X-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\n")
                .append("Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                .append("Access-Control-Allow-Headers: Content-Type, X-RKM-Token, Range\r\n\r\n");
        out.write(header.toString().getBytes(LATIN1));out.write(payload);out.flush();
    }
    private void ensureOperationalMonitor() {
        if (monitorStarted) return;
        monitorStarted = true;
        operationalHandler.removeCallbacks(operationalMonitor);
        operationalHandler.postDelayed(operationalMonitor, 12000L);
    }

    private boolean isWifiConnected() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return false;
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifi != null && wifi.isConnected();
        } catch (Exception ignored) { return false; }
    }

    private boolean isAnyNetworkConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
            return active != null && active.isConnected();
        } catch (Exception ignored) { return false; }
    }

    private void checkOperationalState() {
        if (stopRequested || !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) return;
        boolean wifiConnected = isWifiConnected();
        Boolean previousWifi = lastWifiConnected;
        if (previousWifi == null) {
            lastWifiConnected = Boolean.valueOf(wifiConnected);
        } else if (previousWifi.booleanValue() != wifiConnected) {
            lastWifiConnected = Boolean.valueOf(wifiConnected);
            if (!wifiConnected && previousWifi.booleanValue()) {
                boolean anyNetwork = isAnyNetworkConnected();
                String message = anyNetwork
                        ? "Wi-Fi lokal terputus. Server masih dapat berjalan, tetapi perangkat lain di Wi-Fi tidak dapat memakai alamat lokal. Tunnel publik mungkin masih berjalan melalui jaringan data."
                        : "Wi-Fi dan koneksi jaringan tidak tersedia. Link publik tidak dapat dijangkau sampai jaringan tersambung kembali.";
                postOperationalNotification(NOTIFICATION_WIFI_ALERT, "Koneksi Wi-Fi OHLI terputus", message);
            } else {
                cancelOperationalNotification(NOTIFICATION_WIFI_ALERT);
                postOperationalNotification(NOTIFICATION_WIFI_ALERT, "Koneksi Wi-Fi pulih", "Wi-Fi tersambung kembali. Server lokal dapat digunakan lagi; periksa status tunnel publik bila diperlukan.");
            }
        }
        refreshWifiLock();

        ServerSocket socket = serverSocket;
        boolean healthy = isRunning && socket != null && !socket.isClosed()
                && acceptThread != null && acceptThread.isAlive();
        long now = System.currentTimeMillis();
        if (healthy) {
            if (serverDownAlertShown) {
                serverDownAlertShown = false;
                cancelOperationalNotification(NOTIFICATION_SERVER_ALERT);
                postOperationalNotification(NOTIFICATION_SERVER_ALERT, "Server OHLI pulih", "Listener lokal sudah aktif kembali pada port " + PORT + ".");
            }
            return;
        }
        if (!dataReady || now - serverStartRequestedAt < 20000L) return;
        if (!serverDownAlertShown) {
            serverDownAlertShown = true;
            postOperationalNotification(NOTIFICATION_SERVER_ALERT, "Server OHLI berhenti sementara", "Pemantau mendeteksi listener lokal tidak aktif. OHLI akan mencoba memulihkannya otomatis. Periksa baterai, ruang penyimpanan, dan status perangkat bila masalah berulang.");
        }
        if ((acceptThread == null || !acceptThread.isAlive()) && now - lastRecoveryAttemptAt >= 12000L) {
            lastRecoveryAttemptAt = now;
            lastError = TextUtils.isEmpty(lastError) ? "Pemulihan otomatis server sedang dicoba." : lastError;
            startServer();
        }
    }

    private void createOperationalNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL, "Peringatan Server OHLI", NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("Peringatan jika server atau koneksi jaringan OHLI bermasalah");
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception ignored) { }
        }
    }

    private void postOperationalNotification(int id, String title, String message) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pending = PendingIntent.getActivity(this, id, open, flags);
            Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, ALERT_CHANNEL) : new Notification.Builder(this);
            builder.setSmallIcon(R.drawable.ohli_notification_icon)
                    .setContentTitle(title).setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setContentIntent(pending).setAutoCancel(true)
                    .setWhen(System.currentTimeMillis());
            if (android.os.Build.VERSION.SDK_INT < 26) builder.setPriority(Notification.PRIORITY_HIGH);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(id, builder.build());
        } catch (Exception ignored) { }
    }

    private void cancelOperationalNotification(int id) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(id);
        } catch (Exception ignored) { }
    }

    private void closeServerSocket(){ServerSocket s=serverSocket;serverSocket=null;if(s!=null)try{s.close();}catch(Exception ignored){}}
    private synchronized void refreshWifiLock(){
        try {
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            boolean enabled=wm!=null&&wm.isWifiEnabled()&&!stopRequested&&getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_ENABLED,false);
            if(enabled){
                if(wifiLock==null||!wifiLock.isHeld()){
                    if(wifiLock!=null){try{wifiLock.release();}catch(Exception ignored){}}
                    wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL,"RKServer:Wifi");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            }else if(wifiLock!=null){if(wifiLock.isHeld())wifiLock.release();wifiLock=null;}
        }catch(Exception ignored){}
    }
    private synchronized void releaseLocks(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}wakeLock=null;wifiLock=null;}
    private void closeQuietly(Socket s){if(s!=null)try{s.close();}catch(Exception ignored){}}
    @Override public void onDestroy(){
        persistAdaptiveModelIfDue(true);
        boolean shouldRestart=getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
        operationalHandler.removeCallbacks(operationalMonitor); monitorStarted=false;
        if (shouldRestart) postOperationalNotification(NOTIFICATION_SERVER_ALERT, "Layanan OHLI sedang dipulihkan", "Android menghentikan proses server. OHLI menjadwalkan upaya mulai ulang jika sistem mengizinkan.");
        stopRequested=true;isRunning=false;closeServerSocket();releaseLocks();
        try{if(wifiStateReceiver!=null)unregisterReceiver(wifiStateReceiver);}catch(Exception ignored){} wifiStateReceiver=null;
        ThreadPoolExecutor p=workers;if(p!=null)p.shutdownNow();if(acceptThread!=null)acceptThread.interrupt();stopForeground(true);
        if(shouldRestart) scheduleRestartIfEnabled();
        if (INSTANCE == this) INSTANCE = null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
