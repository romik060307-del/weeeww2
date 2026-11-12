package com.rk.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the server after reboot only when the user previously left it enabled. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!context.getSharedPreferences("ohli_server_service", Context.MODE_PRIVATE).getBoolean("enabled_by_user", false)) return;
        Intent service = new Intent(context, ServerService.class);
        service.setAction(ServerService.ACTION_START);
        try { if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service); } catch (Exception ignored) { }
        boolean tunnelWasEnabled = context.getSharedPreferences(CloudflareTunnelService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(CloudflareTunnelService.PREF_ENABLED, false);
        if (tunnelWasEnabled) {
            Intent tunnel = new Intent(context, CloudflareTunnelService.class);
            tunnel.setAction(CloudflareTunnelService.ACTION_START_QUICK);
            try { if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(tunnel); else context.startService(tunnel); } catch (Exception ignored) { }
        }
    }
}
