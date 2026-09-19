package com.clearshield.adblocker.service

import com.clearshield.adblocker.data.db.entities.QueryLogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedDeque

enum class VpnStatus {
    OFF,
    STARTING,
    PROTECTED,
    ERROR,
    PERMISSION_REQUIRED,
    NETWORK_UNAVAILABLE
}

object VpnStateHolder {
    private val _vpnStatus = MutableStateFlow(VpnStatus.OFF)
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus

    private val _isVpnRunning = MutableStateFlow(false)
    val isVpnRunning: StateFlow<Boolean> = _isVpnRunning

    private val _isVpnEstablished = MutableStateFlow(false)
    val isVpnEstablished: StateFlow<Boolean> = _isVpnEstablished

    private val _hasTrafficFlowed = MutableStateFlow(false)
    val hasTrafficFlowed: StateFlow<Boolean> = _hasTrafficFlowed

    private val _totalQueries = MutableStateFlow(0L)
    val totalQueries: StateFlow<Long> = _totalQueries

    private val _blockedQueries = MutableStateFlow(0L)
    val blockedQueries: StateFlow<Long> = _blockedQueries

    private val _adsBlocked = MutableStateFlow(0L)
    val adsBlocked: StateFlow<Long> = _adsBlocked

    private val _trackersBlocked = MutableStateFlow(0L)
    val trackersBlocked: StateFlow<Long> = _trackersBlocked

    private val _activeUpstreamDns = MutableStateFlow("1.1.1.1")
    val activeUpstreamDns: StateFlow<String> = _activeUpstreamDns

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _isDnsDegraded = MutableStateFlow(false)
    val isDnsDegraded: StateFlow<Boolean> = _isDnsDegraded

    private val _dnsDegradedReason = MutableStateFlow<String?>(null)
    val dnsDegradedReason: StateFlow<String?> = _dnsDegradedReason

    private val _isIpv6Supported = MutableStateFlow(false)
    val isIpv6Supported: StateFlow<Boolean> = _isIpv6Supported

    private val _lastDomain = MutableStateFlow<String?>("-")
    val lastDomain: StateFlow<String?> = _lastDomain

    private val _lastQueryType = MutableStateFlow<String?>("-")
    val lastQueryType: StateFlow<String?> = _lastQueryType

    private val _lastUpstreamLatencyMs = MutableStateFlow<Long?>(null)
    val lastUpstreamLatencyMs: StateFlow<Long?> = _lastUpstreamLatencyMs

    private val queryLogQueue = ConcurrentLinkedDeque<QueryLogItem>()
    private val _recentQueries = MutableStateFlow<List<QueryLogItem>>(emptyList())
    val recentQueries: StateFlow<List<QueryLogItem>> = _recentQueries

    private const val MAX_LOG_ENTRIES = 150

    fun setStatus(status: VpnStatus, errorMsg: String? = null) {
        _vpnStatus.value = status
        _isVpnRunning.value = (status == VpnStatus.PROTECTED)
        if (status == VpnStatus.OFF || status == VpnStatus.ERROR) {
            _isVpnEstablished.value = false
        }
        if (errorMsg != null) {
            _lastError.value = errorMsg
        }
    }

    fun setVpnEstablished(established: Boolean) {
        _isVpnEstablished.value = established
    }

    fun setTrafficFlowed(flowed: Boolean) {
        _hasTrafficFlowed.value = flowed
    }

    fun setUpstreamLatency(latencyMs: Long) {
        _lastUpstreamLatencyMs.value = latencyMs
    }

    fun setDnsDegraded(degraded: Boolean, reason: String? = null) {
        _isDnsDegraded.value = degraded
        _dnsDegradedReason.value = if (degraded) reason else null
    }

    fun setIpv6Supported(supported: Boolean) {
        _isIpv6Supported.value = supported
    }

    fun setVpnRunning(running: Boolean) {
        _isVpnRunning.value = running
        _vpnStatus.value = if (running) VpnStatus.PROTECTED else VpnStatus.OFF
        if (!running) {
            _isVpnEstablished.value = false
        }
    }

    fun setActiveUpstream(dns: String) {
        _activeUpstreamDns.value = dns
    }

    fun recordQuery(item: QueryLogItem) {
        _hasTrafficFlowed.value = true
        _totalQueries.value += 1
        _lastDomain.value = item.domain
        _lastQueryType.value = item.queryType

        if (item.isBlocked) {
            _blockedQueries.value += 1
            if (item.category.contains("TRACKER", ignoreCase = true)) {
                _trackersBlocked.value += 1
            } else {
                _adsBlocked.value += 1
            }
        }

        queryLogQueue.addFirst(item)
        while (queryLogQueue.size > MAX_LOG_ENTRIES) {
            queryLogQueue.removeLast()
        }
        _recentQueries.value = queryLogQueue.toList()
    }

    fun resetSessionStats() {
        _totalQueries.value = 0
        _blockedQueries.value = 0
        _adsBlocked.value = 0
        _trackersBlocked.value = 0
        _lastDomain.value = "-"
        _lastQueryType.value = "-"
        _lastError.value = null
        _isDnsDegraded.value = false
        _dnsDegradedReason.value = null
        _hasTrafficFlowed.value = false
        _lastUpstreamLatencyMs.value = null
        queryLogQueue.clear()
        _recentQueries.value = emptyList()
    }
}
