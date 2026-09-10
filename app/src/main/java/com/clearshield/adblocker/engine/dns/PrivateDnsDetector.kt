package com.clearshield.adblocker.engine.dns

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings

object PrivateDnsDetector {

    /**
     * Detects whether Android's system Private DNS is enabled ("opportunistic" / "hostname"),
     * which causes Android to route queries via DoT (port 853) bypassing the local VPN (port 53).
     */
    fun isPrivateDnsActive(context: Context): Boolean {
        return try {
            val mode = Settings.Global.getString(
                context.contentResolver,
                "private_dns_mode"
            )
            if (mode == "opportunistic" || mode == "hostname") {
                return true
            }
            if (mode == "off") {
                return false
            }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return false
            val linkProperties = cm.getLinkProperties(activeNetwork) ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProperties.isPrivateDnsActive || !linkProperties.privateDnsServerName.isNullOrBlank()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent that deep-links to Android Network/Wireless/Private DNS settings.
     */
    fun createSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return intent
    }

    /**
     * Returns clear, non-accusatory guidance tailored to the user's Android device/version.
     * System Private DNS sends queries directly over port 853 (DNS-over-TLS), bypassing
     * local VPN filter loops on port 53.
     */
    fun getPrivateDnsInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") ->
                "Settings → Connections → More connection settings → Private DNS → Set to Off (ClearShield will encrypt your DNS queries via DNS-over-HTTPS instead)."
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                "Settings → Connection & sharing → Private DNS → Set to Off (ClearShield will protect and encrypt your DNS traffic)."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                "Settings → Network & internet → Private DNS → Set to Off (Allows ClearShield's local engine to filter ads and secure queries via DoH)."
            else ->
                "Settings → Network & internet → Advanced → Private DNS → Set to Off."
        }
    }
}
