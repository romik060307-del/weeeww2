package com.rk.server;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Handles Android's inline-reply action. Replies are sent immediately when possible;
 *  otherwise they are retained locally for the WebView outbox to retry on reconnect. */
public class NotificationReplyReceiver extends BroadcastReceiver {
    public static final String ACTION_REPLY = "com.rk.ohli.ACTION_REPLY";
    public static final String EXTRA_PEER = "ohli_reply_peer";
    public static final String EXTRA_NOTIFICATION_ID = "ohli_reply_notification_id";
    public static final String KEY_TEXT_REPLY = "ohli_reply_text";
    public static final String PREFS = "ohli_native_reply_queue";
    public static final String PENDING_REPLIES = "pending_replies";
    private static final Object QUEUE_LOCK = new Object();

    @Override public void onReceive(final Context context, final Intent intent) {
        if (intent == null || !ACTION_REPLY.equals(intent.getAction())) return;
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) return;
        final CharSequence replyValue = results.getCharSequence(KEY_TEXT_REPLY);
        final String reply = replyValue == null ? "" : replyValue.toString().trim();
        final String peer = intent.getStringExtra(EXTRA_PEER);
        final int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        final String clientId = "ohli_reply_" + UUID.randomUUID().toString().replace("-", "");
        if (reply.length() == 0 || peer == null || peer.length() < 8) return;
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SharedPreferences session = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
                    String token = session.getString("token", "");
                    if (token.length() == 0 || !sendReply(token, peer, reply, clientId)) {
                        queueReply(context, peer, reply, clientId);
                        updateNotification(context, notificationId, "Balasan disimpan di HP · akan dikirim saat online");
                    } else {
                        updateNotification(context, notificationId, "Balasan terkirim");
                    }
                } catch (Exception e) {
                    queueReply(context, peer, reply, clientId);
                    updateNotification(context, notificationId, "Balasan disimpan di HP · akan dikirim saat online");
                } finally {
                    pending.finish();
                }
            }
        }, "ohli-notification-reply").start();
    }

    private boolean sendReply(String token, String peer, String text, String clientId) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(MainActivity.SITE_URL + "api/messages");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(9000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-RKM-Token", token);
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("recipient", peer);
            body.put("text", text);
            body.put("clientId", clientId);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream out = connection.getOutputStream();
            out.write(bytes); out.flush(); out.close();
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                if (code >= 200 && code < 300) {
                    JSONObject data = new JSONObject(response.toString());
                    return data.optBoolean("ok", false);
                }
            }
        } catch (Exception ignored) {
        } finally { if (connection != null) connection.disconnect(); }
        return false;
    }

    private void queueReply(Context context, String peer, String text, String clientId) {
        synchronized (QUEUE_LOCK) {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray current;
            try { current = new JSONArray(p.getString(PENDING_REPLIES, "[]")); } catch (Exception e) { current = new JSONArray(); }
            try {
                JSONObject item = new JSONObject();
                item.put("recipient", peer); item.put("text", text);
                item.put("clientId", clientId);
                item.put("createdAt", System.currentTimeMillis()); current.put(item);
            } catch (Exception ignored) { }
            while (current.length() > 100) current.remove(0);
            p.edit().putString(PENDING_REPLIES, current.toString()).apply();
        }
    }

    private void updateNotification(Context context, int id, String message) {
        if (id == 0) return;
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(context, "ohli_messages");
        else builder = new Notification.Builder(context);
        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id + 7, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        builder.setSmallIcon(R.drawable.ohli_notification_icon).setContentTitle("OHLI Social").setContentText(message).setAutoCancel(true).setContentIntent(pi);
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(id, builder.build());
    }
}
