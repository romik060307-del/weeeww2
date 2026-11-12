package com.rk.server;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** OHLI Social Android client. The online site remains the source of truth; the packaged
 *  HTML/CSS/JS shell is served on the same HTTPS origin when the device is offline. */
public class MainActivity extends Activity {
    public static final String SITE_URL = "https://ohlisocial.netlify.app/";
    public static final String SITE_HOST = "ohlisocial.netlify.app";
    public static final String PREFS = "ohli_client_session";
    private static final int REQUEST_FILE = 4101;
    private static final int REQUEST_WEB_PERMISSIONS = 4102;
    private static final int REQUEST_GEO_PERMISSION = 4103;

    private final AtomicBoolean localMode = new AtomicBoolean(false);
    private WebView webView;
    private View splash;
    private TextView splashStatus;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private String[] pendingWebAndroidPermissions = new String[0];
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private BroadcastReceiver connectivityReceiver;
    private boolean fallbackAttempted;
    private final android.os.Handler handler = new android.os.Handler();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.rgb(24, 24, 20));
            getWindow().setNavigationBarColor(Color.rgb(24, 24, 20));
        }
        createNotificationChannels();
        buildClientView();
        localMode.set(!hasValidatedInternet());
        registerConnectivityReceiver();
        webView.loadUrl(SITE_URL);
        handleLaunchIntent(getIntent());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4104);
        }
    }

    private void buildClientView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 245, 236));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(247, 245, 236));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        webView.addJavascriptInterface(new OhliBridge(this), "OHLI_ANDROID");
        webView.setWebViewClient(new ClientWebViewClient());
        webView.setWebChromeClient(new ClientChromeClient());
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(30), dp(30), dp(30), dp(30));
        loading.setBackgroundColor(Color.rgb(247, 245, 236));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.rk_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loading.addView(logo, new LinearLayout.LayoutParams(dp(116), dp(116)));
        TextView name = new TextView(this);
        name.setText("OHLI SOCIAL");
        name.setTextColor(Color.rgb(25, 25, 21));
        name.setTextSize(22);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 21) name.setLetterSpacing(0.08f);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = dp(18);
        loading.addView(name, nameParams);
        splashStatus = new TextView(this);
        splashStatus.setText("Menyiapkan aplikasi…");
        splashStatus.setTextColor(Color.rgb(119, 117, 105));
        splashStatus.setTextSize(13);
        splashStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.topMargin = dp(8);
        loading.addView(splashStatus, statusParams);
        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        progressParams.topMargin = dp(18);
        loading.addView(progress, progressParams);
        splash = loading;
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private final class ClientWebViewClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return interceptLocal(request.getUrl());
        }
        @SuppressWarnings("deprecation")
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            try { return interceptLocal(Uri.parse(url)); } catch (Exception ignored) { return null; }
        }
        private WebResourceResponse interceptLocal(Uri uri) {
            if (!localMode.get() || uri == null || !SITE_HOST.equalsIgnoreCase(uri.getHost())) return null;
            String path = uri.getPath();
            if (path == null || path.length() == 0 || "/".equals(path) || "/index.html".equals(path)) {
                return assetResponse("index.html", "text/html", "UTF-8");
            }
            if ("/assets/ohli-social.css".equals(path)) return assetResponse("ohli-social.css", "text/css", "UTF-8");
            if ("/assets/ohli-social.js".equals(path)) return assetResponse("ohli-social.js", "application/javascript", "UTF-8");
            if ("/favicon.ico".equals(path)) return new WebResourceResponse("image/x-icon", "UTF-8", new ByteArrayInputStream(new byte[0]));
            return null; // API calls and other network resources must continue to use the real site.
        }
        private WebResourceResponse assetResponse(String name, String mime, String encoding) {
            try { return new WebResourceResponse(mime, encoding, getAssets().open(name)); }
            catch (IOException e) { return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("Aset OHLI tidak ditemukan".getBytes())); }
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return routeUrl(request.getUrl());
        }
        @SuppressWarnings("deprecation")
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try { return routeUrl(Uri.parse(url)); } catch (Exception e) { return false; }
        }
        private boolean routeUrl(Uri uri) {
            if (uri == null) return false;
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            if (("https".equals(scheme) || "http".equals(scheme)) && SITE_HOST.equalsIgnoreCase(uri.getHost())) return false;
            if ("about".equals(scheme) || "javascript".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme)) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (splash != null && splash.getVisibility() != View.GONE && splashStatus != null) splashStatus.setText(localMode.get() ? "Membuka OHLI Social offline…" : "Menghubungkan ke OHLI Social…");
        }
        @Override public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            fallbackAttempted = false;
            if (splash != null) {
                handler.postDelayed(new Runnable() { @Override public void run() { if (splash != null) splash.setVisibility(View.GONE); } }, 350L);
            }
            handleLaunchIntent(getIntent());
            if (hasValidatedInternet()) {
                view.evaluateJavascript("window.dispatchEvent(new Event('online')); if(window.ohliClientOnlineSync){window.ohliClientOnlineSync();}", null);
            }
        }
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= 23 && request != null && request.isForMainFrame()) enableLocalFallback();
        }
        @SuppressWarnings("deprecation")
        @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (Build.VERSION.SDK_INT < 23 && failingUrl != null && failingUrl.startsWith(SITE_URL)) enableLocalFallback();
        }
        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);
            if (request != null && request.isForMainFrame() && response != null && response.getStatusCode() >= 400) enableLocalFallback();
        }
    }

    private void enableLocalFallback() {
        if (fallbackAttempted) return;
        fallbackAttempted = true;
        localMode.set(true);
        runOnUiThread(new Runnable() { @Override public void run() {
            if (splash != null) { splash.setVisibility(View.VISIBLE); if (splashStatus != null) splashStatus.setText("Membuka salinan offline OHLI Social…"); }
            webView.loadUrl(SITE_URL);
        }});
    }

    private final class ClientChromeClient extends WebChromeClient {
        @Override public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (request == null) return;
                String originHost = request.getOrigin() == null ? "" : request.getOrigin().getHost();
                if (!SITE_HOST.equalsIgnoreCase(originHost)) { request.deny(); return; }
                ArrayList<String> needed = new ArrayList<>();
                String[] resources = request.getResources();
                boolean wantsCamera = false, wantsMic = false;
                for (String resource : resources) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) { wantsCamera = true; if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA); }
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) { wantsMic = true; if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO); }
                }
                if (needed.isEmpty()) { request.grant(resources); return; }
                pendingWebPermission = request;
                pendingWebAndroidPermissions = needed.toArray(new String[0]);
                if (Build.VERSION.SDK_INT >= 23) requestPermissions(pendingWebAndroidPermissions, REQUEST_WEB_PERMISSIONS); else request.grant(resources);
            }});
        }
        @Override public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
            runOnUiThread(new Runnable() { @Override public void run() {
                Uri originUri = Uri.parse(origin == null ? "" : origin);
                if (!SITE_HOST.equalsIgnoreCase(originUri.getHost())) { callback.invoke(origin, false, false); return; }
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) { callback.invoke(origin, true, false); return; }
                pendingGeoCallback = callback; pendingGeoOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_GEO_PERMISSION);
            }});
        }
        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent intent;
            try { intent = params.createIntent(); }
            catch (Exception e) { intent = new Intent(Intent.ACTION_GET_CONTENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*"); intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); }
            try { startActivityForResult(intent, REQUEST_FILE); return true; }
            catch (ActivityNotFoundException e) { fileCallback = null; Toast.makeText(MainActivity.this, "Pemilih file tidak tersedia", Toast.LENGTH_SHORT).show(); return false; }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEB_PERMISSIONS && pendingWebPermission != null) {
            boolean ok = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingWebPermission.grant(pendingWebPermission.getResources()); else pendingWebPermission.deny();
            pendingWebPermission = null; pendingWebAndroidPermissions = new String[0];
        } else if (requestCode == REQUEST_GEO_PERMISSION && pendingGeoCallback != null) {
            boolean ok = false; for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) ok = true;
            pendingGeoCallback.invoke(pendingGeoOrigin, ok, false); pendingGeoCallback = null; pendingGeoOrigin = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount(); results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) results = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(results); fileCallback = null;
        }
    }

    private boolean hasValidatedInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            NetworkInfo info = cm.getActiveNetworkInfo(); return info != null && info.isConnected();
        } catch (Exception e) { return false; }
    }

    private void registerConnectivityReceiver() {
        connectivityReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) {
            boolean online = hasValidatedInternet();
            if (!online) {
                if (!localMode.get()) enableLocalFallback();
            } else if (webView != null) {
                webView.post(new Runnable() { @Override public void run() { webView.evaluateJavascript("window.dispatchEvent(new Event('online')); if(window.ohliClientOnlineSync){window.ohliClientOnlineSync();}", null); }});
            }
        }};
        try {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(connectivityReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(connectivityReceiver, filter);
        } catch (Exception ignored) { }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        createChannel(manager, "ohli_messages", "Pesan OHLI", "Notifikasi pesan dan balasan chat", R.raw.ohli_message, NotificationManager.IMPORTANCE_HIGH);
        createChannel(manager, "ohli_calls", "Panggilan OHLI", "Notifikasi panggilan suara dan video", R.raw.ohli_call, NotificationManager.IMPORTANCE_HIGH);
        createChannel(manager, "ohli_activity", "Aktivitas OHLI", "Notifikasi aktivitas sosial", R.raw.ohli_activity, NotificationManager.IMPORTANCE_DEFAULT);
    }
    private void createChannel(NotificationManager manager, String id, String name, String description, int soundId, int importance) {
        Uri sound = Uri.parse("android.resource://" + getPackageName() + "/" + soundId);
        android.media.AudioAttributes audio = new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description); channel.setSound(sound, audio); channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || webView == null) return;
        final String peer = intent.getStringExtra("ohli_peer");
        if (peer == null || peer.length() == 0) return;
        intent.removeExtra("ohli_peer");
        webView.postDelayed(new Runnable() { @Override public void run() {
            String js = "(function(){try{if(window.ohliRuntime&&window.ohliRuntime.openChatWith){window.ohliRuntime.openChatWith(" + org.json.JSONObject.quote(peer) + ").catch(function(){});}}catch(e){}})();";
            webView.evaluateJavascript(js, null);
        }}, 700L);
    }

    public class OhliBridge {
        private final Context context;
        OhliBridge(Context c) { context = c.getApplicationContext(); }
        @JavascriptInterface public boolean isOnline() { return hasValidatedInternet(); }
        @JavascriptInterface public void saveSession(String phone, String token, String name) {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putString("phone", phone == null ? "" : phone).putString("token", token == null ? "" : token).putString("name", name == null ? "" : name).apply();
        }
        @JavascriptInterface public void clearSession() { getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply(); }
        @JavascriptInterface public void showMessageNotification(String peer, String name, String message) {
            showClientNotification(peer, name, message, false);
        }
        @JavascriptInterface public void showCallNotification(String peer, String name, boolean video) {
            showClientNotification(peer, name, video ? "Panggilan video masuk" : "Panggilan suara masuk", true);
        }
        @JavascriptInterface public String consumePendingReplies() {
            SharedPreferences p = getSharedPreferences(NotificationReplyReceiver.PREFS, MODE_PRIVATE);
            String value = p.getString(NotificationReplyReceiver.PENDING_REPLIES, "[]");
            p.edit().putString(NotificationReplyReceiver.PENDING_REPLIES, "[]").apply();
            return value == null ? "[]" : value;
        }
        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent i = new Intent();
                    if (Build.VERSION.SDK_INT >= 26) { i.setAction("android.settings.APP_NOTIFICATION_SETTINGS"); i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName()); }
                    else i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS").setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) { }
            }});
        }
    }

    private void showClientNotification(String peer, String name, String message, boolean call) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        String safePeer = peer == null ? "" : peer;
        int id = Math.abs((safePeer + (call ? "call" : "message")).hashCode());
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("ohli_peer", safePeer);
        PendingIntent openPi = PendingIntent.getActivity(this, id, open, pendingFlags(false));
        Notification.Builder builder = notificationBuilder(call ? "ohli_calls" : "ohli_messages");
        builder.setSmallIcon(R.drawable.ohli_notification_icon)
                .setContentTitle(call ? "Panggilan OHLI Social" : (name == null || name.length() == 0 ? "Pesan OHLI Social" : name))
                .setContentText(call ? message : (message == null ? "Pesan baru" : message))
                .setAutoCancel(true).setContentIntent(openPi).setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= 21) builder.setCategory(call ? Notification.CATEGORY_CALL : Notification.CATEGORY_MESSAGE);
        if (!call && Build.VERSION.SDK_INT >= 20) {
            Intent reply = new Intent(this, NotificationReplyReceiver.class).setAction(NotificationReplyReceiver.ACTION_REPLY).putExtra(NotificationReplyReceiver.EXTRA_PEER, safePeer).putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, id);
            PendingIntent replyPi = PendingIntent.getBroadcast(this, id + 1, reply, pendingFlags(true));
            RemoteInput remoteInput = new RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY).setLabel("Tulis balasan…").build();
            Notification.Action action = new Notification.Action.Builder(android.R.drawable.ic_menu_send, "Balas", replyPi).addRemoteInput(remoteInput).build();
            builder.addAction(action);
        }
        if (call) builder.addAction(android.R.drawable.ic_menu_view, "Buka OHLI", openPi);
        manager.notify(id, builder.build());
    }

    private Notification.Builder notificationBuilder(String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, channel);
        return new Notification.Builder(this);
    }
    private int pendingFlags(boolean mutable) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= mutable && Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleLaunchIntent(intent); }

    @Override public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(function(){try{var m=document.getElementById('modalBackdrop');if(m&&m.classList.contains('open')&&window.closeModal){window.closeModal();return 'handled';}var a=document.getElementById('app');if(a&&a.classList.contains('page-open')){var b=document.getElementById('backButton');if(b)b.click();else a.classList.remove('page-open');return 'handled';}var s=document.getElementById('ohliSocialShell');if(s&&s.dataset&&s.dataset.page&&s.dataset.page!=='home'){var h=s.querySelector('.ohli-nav-link[data-page=home]');if(h){h.click();return 'handled';}}return 'root';}catch(e){return 'root';}})()", value -> {
            if ("\"handled\"".equals(value)) return;
            if (webView != null && webView.canGoBack()) webView.goBack(); else moveTaskToBack(true);
        });
    }

    @Override protected void onDestroy() {
        if (connectivityReceiver != null) { try { unregisterReceiver(connectivityReceiver); } catch (Exception ignored) { } connectivityReceiver = null; }
        if (webView != null) { webView.removeJavascriptInterface("OHLI_ANDROID"); webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
