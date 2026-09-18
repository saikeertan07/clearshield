package com.clearshield.adblocker.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DnsProvider(
    val name: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dohUrl: String,
    val description: String
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clearshield_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_UPSTREAM_DNS = "upstream_dns"
        const val PREF_BLOCKING_MODE = "blocking_mode"
        const val PREF_AUTO_START_BOOT = "auto_start_boot"
        const val PREF_SHOW_NOTIFICATION = "show_notification"
        const val PREF_CUSTOM_DNS = "custom_dns_ip"
        const val PREF_USE_DOH = "use_doh"
        const val PREF_ALLOW_INSECURE_FALLBACK = "allow_insecure_fallback"

        val DNS_PROVIDERS = listOf(
            DnsProvider("Cloudflare", "1.1.1.1", "1.0.0.1", "https://cloudflare-dns.com/dns-query", "Fast, privacy-first public resolver"),
            DnsProvider("Google", "8.8.8.8", "8.8.4.4", "https://dns.google/dns-query", "Worldwide low latency Google DNS"),
            DnsProvider("Quad9", "9.9.9.9", "149.112.112.112", "https://dns.quad9.net/dns-query", "Security-focused malware blocking DNS"),
            DnsProvider("AdGuard DNS", "94.140.14.14", "94.140.15.15", "https://dns.adguard-dns.com/dns-query", "Upstream ad-filtering resolver"),
            DnsProvider("Custom", "1.1.1.1", "", "", "User-defined custom DNS IP")
        )
    }

    private val _upstreamDns = MutableStateFlow(prefs.getString(PREF_UPSTREAM_DNS, "1.1.1.1") ?: "1.1.1.1")
    val upstreamDns: StateFlow<String> = _upstreamDns

    private val _useDoh = MutableStateFlow(prefs.getBoolean(PREF_USE_DOH, true))
    val useDoh: StateFlow<Boolean> = _useDoh

    // Secure default: strictly forbid silent downgrade to plaintext unless user explicitly opts in with warning
    private val _allowInsecureFallback = MutableStateFlow(prefs.getBoolean(PREF_ALLOW_INSECURE_FALLBACK, false))
    val allowInsecureFallback: StateFlow<Boolean> = _allowInsecureFallback

    private val _blockingMode = MutableStateFlow(prefs.getString(PREF_BLOCKING_MODE, "0.0.0.0") ?: "0.0.0.0")
    val blockingMode: StateFlow<String> = _blockingMode

    private val _autoStartBoot = MutableStateFlow(prefs.getBoolean(PREF_AUTO_START_BOOT, false))
    val autoStartBoot: StateFlow<Boolean> = _autoStartBoot

    private val _customDnsIp = MutableStateFlow(prefs.getString(PREF_CUSTOM_DNS, "1.1.1.1") ?: "1.1.1.1")
    val customDnsIp: StateFlow<String> = _customDnsIp

    fun setUpstreamDns(ip: String) {
        prefs.edit().putString(PREF_UPSTREAM_DNS, ip).apply()
        _upstreamDns.value = ip
    }

    fun setBlockingMode(mode: String) {
        prefs.edit().putString(PREF_BLOCKING_MODE, mode).apply()
        _blockingMode.value = mode
    }

    fun setAutoStartBoot(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_START_BOOT, enabled).apply()
        _autoStartBoot.value = enabled
    }

    fun setUseDoh(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_USE_DOH, enabled).apply()
        _useDoh.value = enabled
    }

    fun setAllowInsecureFallback(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_ALLOW_INSECURE_FALLBACK, enabled).apply()
        _allowInsecureFallback.value = enabled
    }

    fun getDohUrlForIp(ip: String): String {
        return DNS_PROVIDERS.find { it.primaryIp == ip }?.dohUrl ?: ""
    }

    fun setCustomDnsIp(ip: String) {
        prefs.edit().putString(PREF_CUSTOM_DNS, ip).apply()
        _customDnsIp.value = ip
    }
}
