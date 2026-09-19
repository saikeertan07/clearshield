package com.clearshield.adblocker.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.R
import com.clearshield.adblocker.data.db.entities.QueryLogItem
import com.clearshield.adblocker.engine.dns.DnsPacket
import com.clearshield.adblocker.engine.dns.IpPacket
import com.clearshield.adblocker.engine.dns.UpstreamDnsResolver
import com.clearshield.adblocker.engine.filter.FilterDecision
import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.engine.filter.ThreatCategory
import com.clearshield.adblocker.ui.MainActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ClearShieldVpnService : VpnService() {

    companion object {
        private const val TAG = "ClearShieldVpnService"
        const val ACTION_START = "com.clearshield.adblocker.START"
        const val ACTION_STOP = "com.clearshield.adblocker.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_PRIVATE_DNS_ID = 1002

        // Local Virtual DNS endpoint (RFC 6890 private addresses)
        const val VPN_IPV4_ADDRESS = "10.233.1.2"
        const val VPN_IPV4_DNS = "10.233.1.1"
        const val VPN_IPV6_ADDRESS = "fd00:1::2"
        const val VPN_IPV6_DNS = "fd00:1::1"

        @Volatile
        var activeInstance: ClearShieldVpnService? = null
            private set

        fun invalidateDnsCache() {
            activeInstance?.upstreamResolver?.responseCache?.invalidateAll()
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var upstreamResolver: UpstreamDnsResolver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Bounded concurrency semaphore: prevents unbounded coroutine spawning during packet spikes
    private val upstreamDispatchSemaphore = java.util.concurrent.Semaphore(16)

    private val lifecycleLock = Any()
    @Volatile private var isRunning = false
    @Volatile private var isStarting = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        synchronized(lifecycleLock) {
            if (isRunning || isStarting) {
                Log.d(TAG, "VPN is already running or starting. Ignoring duplicate start.")
                return
            }
            isStarting = true
        }

        VpnStateHolder.setStatus(VpnStatus.STARTING)

        val app = applicationContext as ClearShieldApp
        val upstreamIp = app.settingsRepository.upstreamDns.value
        val useDoh = app.settingsRepository.useDoh.value
        val allowInsecureFallback = app.settingsRepository.allowInsecureFallback.value
        val dohUrl = app.settingsRepository.getDohUrlForIp(upstreamIp)

        // Secondary upstream resolver fallback
        val secondaryIp = when (upstreamIp) {
            "1.1.1.1" -> "1.0.0.1"
            "8.8.8.8" -> "8.8.4.4"
            "9.9.9.9" -> "149.112.112.112"
            else -> ""
        }

        upstreamResolver = UpstreamDnsResolver(
            primaryServer = upstreamIp,
            secondaryServer = secondaryIp,
            useDoh = useDoh,
            dohUrl = dohUrl,
            allowInsecureFallback = allowInsecureFallback,
            socketProtector = { socket -> protect(socket) }
        )
        VpnStateHolder.setActiveUpstream(upstreamIp)

        // Launch foreground notification safely
        val notification = createNotification()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34 /* Build.VERSION_CODES.UPSIDE_DOWN_CAKE */) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Specialized startForeground failed, falling back to standard notification", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Throwable) {
                Log.e(TAG, "startForeground failed completely", e2)
            }
        }

        serviceScope.launch {
            try {
                val disallowedPackages = app.appFilterRepository.getDisallowedPackages()
                val builder = Builder().apply {
                    setSession("ClearShield Local DNS Filter")
                    setMtu(1500)
                    setBlocking(true)

                    // Virtual IPv4 DNS (RFC 6890 / RFC 1918)
                    addAddress(VPN_IPV4_ADDRESS, 32)
                    addDnsServer(VPN_IPV4_DNS)
                    addRoute(VPN_IPV4_DNS, 32)

                    // Virtual IPv6 DNS (RFC 4193 Unique Local Address in /64 subnet, RFC 4291)
                    var ipv6Configured = false
                    try {
                        addAddress(VPN_IPV6_ADDRESS, 64)
                        addDnsServer(VPN_IPV6_DNS)
                        addRoute(VPN_IPV6_DNS, 128)
                        ipv6Configured = true
                        Log.i(TAG, "Dual-stack IPv6 DNS route configured successfully ($VPN_IPV6_DNS/128 on $VPN_IPV6_ADDRESS/64)")
                    } catch (e: Exception) {
                        Log.w(TAG, "IPv6 route configuration unavailable on this kernel/network: ${e.message}", e)
                    }
                    VpnStateHolder.setIpv6Supported(ipv6Configured)

                    // Exclude self package to prevent loopback
                    try {
                        addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot disallow self package: ${e.message}")
                    }

                    // Exclude user-selected apps
                    for (pkg in disallowedPackages) {
                        try {
                            addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.w(TAG, "Cannot disallow package $pkg: ${e.message}")
                        }
                    }
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    Log.e(TAG, "Failed to establish VPN interface: returned null")
                    synchronized(lifecycleLock) {
                        isStarting = false
                    }
                    VpnStateHolder.setStatus(VpnStatus.ERROR, "VPN establishment returned null interface")
                    stopSelf()
                    return@launch
                }

                synchronized(lifecycleLock) {
                    vpnInterface = pfd
                    isRunning = true
                    isStarting = false
                }

                VpnStateHolder.setVpnEstablished(true)
                VpnStateHolder.setStatus(VpnStatus.PROTECTED)
                checkAndNotifyPrivateDns()
                Log.i(TAG, "ClearShield VPN successfully established.")

                workerJob = launch {
                    runPacketProcessingLoop(pfd)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                synchronized(lifecycleLock) {
                    isStarting = false
                }
                VpnStateHolder.setStatus(VpnStatus.ERROR, e.message)
                stopVpn()
            }
        }
    }

    private fun runPacketProcessingLoop(pfd: ParcelFileDescriptor) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val outStreamLock = Any()
        val packetBuffer = ByteArray(32768)

        val app = applicationContext as ClearShieldApp
        var loopTerminatedUnexpectedly = false

        try {
            while (isRunning) {
                val bytesRead = inStream.read(packetBuffer)
                if (bytesRead <= 0) break

                val ipPacket = IpPacket.parse(packetBuffer, bytesRead) ?: continue

                // Handle TCP port 53 traffic: reject cleanly with immediate TCP RST to avoid 30s connection hangs
                val tcp = ipPacket.tcpHeader
                if (tcp != null && tcp.dstPort == 53) {
                    val rstPacket = IpPacket.buildTcpRstPacket(ipPacket)
                    if (rstPacket != null) {
                        synchronized(outStreamLock) {
                            try {
                                outStream.write(rstPacket)
                            } catch (_: Exception) {}
                        }
                    }
                    continue
                }

                val udp = ipPacket.udpHeader ?: continue

                // Strictly process DNS packets (destination port 53)
                if (udp.dstPort != 53) continue

                val dnsPacket = DnsPacket.parse(udp.payload) ?: continue
                val question = dnsPacket.questions.firstOrNull() ?: continue
                val domain = question.qName

                // Dynamically evaluate current blocking mode
                val asNxDomain = (app.settingsRepository.blockingMode.value == "NXDOMAIN")

                val decision = FilterEngine.instance.evaluate(domain)
                when (decision) {
                    is FilterDecision.Blocked -> {
                        // Immediate Local Synthesis (Sub-millisecond)
                        val blockedDnsPayload = DnsPacket.buildBlockedResponse(
                            query = dnsPacket,
                            asNxDomain = asNxDomain
                        )
                        val responseIpPacket = IpPacket.buildResponsePacket(ipPacket, blockedDnsPayload)
                        synchronized(outStreamLock) {
                            outStream.write(responseIpPacket)
                        }

                        val isTracker = decision.category == ThreatCategory.TRACKER
                        val item = QueryLogItem(
                            domain = domain,
                            queryType = DnsPacket.getTypeName(question.qType),
                            isBlocked = true,
                            category = if (isTracker) "TRACKER" else "AD",
                            reason = decision.reason
                        )
                        VpnStateHolder.recordQuery(item)
                        app.statsRepository.recordQuery(isBlocked = true, isTracker = isTracker)
                    }

                    is FilterDecision.Allowed -> {
                        // Asynchronous bounded dispatch: caps concurrent coroutines to 16 to prevent thread/socket exhaustion
                        if (upstreamDispatchSemaphore.tryAcquire()) {
                            serviceScope.launch {
                                try {
                                    val resolver = upstreamResolver ?: return@launch
                                    val upstreamResponse = resolver.resolve(udp.payload)
                                    if (upstreamResponse != null) {
                                        val responseIpPacket = IpPacket.buildResponsePacket(ipPacket, upstreamResponse)
                                        synchronized(outStreamLock) {
                                            try {
                                                outStream.write(responseIpPacket)
                                            } catch (_: Exception) {}
                                        }

                                        val item = QueryLogItem(
                                            domain = domain,
                                            queryType = DnsPacket.getTypeName(question.qType),
                                            isBlocked = false,
                                            category = "CLEAN",
                                            reason = decision.reason
                                        )
                                        VpnStateHolder.recordQuery(item)
                                        app.statsRepository.recordQuery(isBlocked = false, isTracker = false)
                                    }
                                } finally {
                                    upstreamDispatchSemaphore.release()
                                }
                            }
                        } else {
                            Log.w(TAG, "Upstream DNS concurrent dispatch capacity (16) saturated; dropping burst packet")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (isRunning) {
                Log.e(TAG, "I/O exception in packet processing loop", e)
                loopTerminatedUnexpectedly = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in packet processing loop", e)
            loopTerminatedUnexpectedly = true
        } finally {
            try {
                inStream.close()
                outStream.close()
            } catch (_: Exception) {}

            if (loopTerminatedUnexpectedly && isRunning) {
                // Never leave UI saying "PROTECTED" if worker died
                VpnStateHolder.setStatus(VpnStatus.ERROR, "Packet processing engine interrupted")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        synchronized(lifecycleLock) {
            if (!isRunning && !isStarting && VpnStateHolder.vpnStatus.value == VpnStatus.OFF) return
            isRunning = false
            isStarting = false
        }

        VpnStateHolder.setVpnEstablished(false)
        VpnStateHolder.setStatus(VpnStatus.OFF)

        // Flush pending stats to DB before exiting
        serviceScope.launch {
            try {
                val app = applicationContext as? ClearShieldApp
                app?.statsRepository?.flushPendingStats()
            } catch (_: Exception) {}
        }

        upstreamResolver?.close()
        upstreamResolver = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        workerJob?.cancel()
        workerJob = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_PRIVATE_DNS_ID)
        } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "ClearShield VPN stopped cleanly.")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system or user")
        VpnStateHolder.setStatus(VpnStatus.PERMISSION_REQUIRED, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If user swiped app from Recents, ensure stats are saved
        serviceScope.launch {
            try {
                val app = applicationContext as? ClearShieldApp
                app?.statsRepository?.flushPendingStats()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        stopVpn()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_PRIVATE_DNS_ID)
        } catch (_: Exception) {}
        try {
            unregisterNetworkCallback()
        } catch (e: Exception) {
            Log.w(TAG, "Error in unregisterNetworkCallback: ${e.message}")
        }
        try {
            val app = applicationContext as? ClearShieldApp
            serviceScope.launch {
                app?.statsRepository?.flushPendingStats()
            }
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ClearShieldVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClearShieldApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_active_title))
            .setContentText(getString(R.string.vpn_active_desc))
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentIntent(pendingOpen)
            .addAction(0, "Disconnect", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun checkAndNotifyPrivateDns() {
        try {
            val isPrivateDns = com.clearshield.adblocker.engine.dns.PrivateDnsDetector.isPrivateDnsActive(this)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (isPrivateDns && isRunning) {
                val settingsIntent = com.clearshield.adblocker.engine.dns.PrivateDnsDetector.createSettingsIntent()
                val pendingSettings = PendingIntent.getActivity(
                    this, 2, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(this, ClearShieldApp.NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Private DNS Bypassing ClearShield")
                    .setContentText("Android Private DNS is active and bypassing local filtering. Tap to open network settings and disable it.")
                    .setSmallIcon(R.drawable.ic_shield_notification)
                    .setContentIntent(pendingSettings)
                    .addAction(0, "Open Settings", pendingSettings)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                nm?.notify(NOTIFICATION_PRIVATE_DNS_ID, notification)
            } else {
                nm?.cancel(NOTIFICATION_PRIVATE_DNS_ID)
            }
        } catch (_: Exception) {}
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                upstreamResolver?.onNetworkChanged()
                if (isRunning) {
                    VpnStateHolder.setStatus(VpnStatus.PROTECTED)
                    checkAndNotifyPrivateDns()
                }
            }
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                upstreamResolver?.onNetworkChanged()
                if (isRunning) {
                    VpnStateHolder.setStatus(VpnStatus.NETWORK_UNAVAILABLE, "No active internet connection")
                }
            }
        }
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback: ${e.message}")
            }
        }
    }
}
