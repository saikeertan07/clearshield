package com.clearshield.adblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.clearshield.adblocker.ClearShieldApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        try {
            Log.i(TAG, "Broadcast received: ${intent.action}")
            // Strictly auto-start on device boot completed, never from background package replacement
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                val app = context.applicationContext as? ClearShieldApp ?: return
                val autoStart = app.settingsRepository.autoStartBoot.value

                // Verify VPN permission is already granted by user previously
                val vpnIntent = VpnService.prepare(context)
                if (autoStart && vpnIntent == null) {
                    Log.i(TAG, "Auto-starting ClearShield VPN service on boot")
                    val serviceIntent = Intent(context, ClearShieldVpnService::class.java).apply {
                        action = ClearShieldVpnService.ACTION_START
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to startForegroundService on boot", e)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error in BootReceiver.onReceive", t)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
