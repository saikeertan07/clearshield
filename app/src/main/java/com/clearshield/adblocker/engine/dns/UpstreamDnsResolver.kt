package com.clearshield.adblocker.engine.dns

import android.util.Log
import com.clearshield.adblocker.service.VpnStateHolder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UpstreamDnsResolver(
    private var primaryServer: String = "1.1.1.1",
    private var secondaryServer: String = "1.0.0.1",
    private var upstreamPort: Int = 53,
    private var useDoh: Boolean = true,
    private var dohUrl: String = "https://cloudflare-dns.com/dns-query",
    private var allowInsecureFallback: Boolean = false,
    private val socketProtector: ((DatagramSocket) -> Boolean)? = null
) : Closeable {

    companion object {
        private const val TAG = "UpstreamDnsResolver"
        private const val TIMEOUT_MS = 2500
        private const val BUFFER_SIZE = 4096
        private const val MAX_CONCURRENT_REQUESTS = 16
    }

    private val isClosed = AtomicBoolean(false)
    private var cachedSocket: DatagramSocket? = null
    private val socketLock = Any()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Bounded concurrency semaphore: caps simultaneous upstream queries to 16
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_REQUESTS, true)

    // Bounded RFC 1035 / RFC 2308 LRU response cache with negative caching
    val responseCache = DnsResponseCache(maxEntries = 512, enableNegativeCaching = true)

    // Map of in-flight upstream requests by DnsCacheKey to avoid duplicate upstream queries (Request Coalescing)
    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<DnsCacheKey, java.util.concurrent.CompletableFuture<ByteArray?>>()

    private val consecutiveFailures = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastResolvedTimestamp: Long = 0L
        private set

    @Volatile
    var lastMeasuredLatencyMs: Long = -1L
        private set

    fun setUpstream(
        primary: String,
        secondary: String = "",
        port: Int = 53,
        useDoh: Boolean = true,
        dohUrl: String = "",
        allowInsecureFallback: Boolean = false
    ) {
        synchronized(socketLock) {
            this.primaryServer = primary
            this.secondaryServer = secondary
            this.upstreamPort = port
            this.useDoh = useDoh
            this.dohUrl = dohUrl
            this.allowInsecureFallback = allowInsecureFallback
            consecutiveFailures.set(0)
            VpnStateHolder.setDnsDegraded(false)
            closeSocketLocked()
            responseCache.invalidateAll()
            inFlightRequests.clear()
        }
    }

    fun getUpstream(): String = primaryServer

    /**
     * Handles network transitions (Wi-Fi <-> Mobile data).
     * Closes stale sockets, invalidates cache, and ensures next query uses fresh protected socket.
     */
    fun onNetworkChanged() {
        synchronized(socketLock) {
            Log.i(TAG, "Network change detected: resetting upstream socket and cache")
            consecutiveFailures.set(0)
            VpnStateHolder.setDnsDegraded(false)
            closeSocketLocked()
            responseCache.invalidateAll()
        }
    }

    private fun getOrCreateSocketLocked(): DatagramSocket? {
        if (isClosed.get()) return null

        cachedSocket?.let { sock ->
            if (!sock.isClosed) return sock
        }

        return try {
            val sock = DatagramSocket()
            sock.soTimeout = TIMEOUT_MS
            val protected = socketProtector?.invoke(sock) ?: true
            if (!protected) {
                Log.w(TAG, "Socket protector returned false for upstream DNS socket")
            }
            cachedSocket = sock
            sock
        } catch (e: Exception) {
            lastError = "Socket create error: ${e.message}"
            Log.e(TAG, "Failed to create upstream DNS socket", e)
            null
        }
    }

    private fun closeSocketLocked() {
        try {
            cachedSocket?.close()
        } catch (_: Exception) {}
        cachedSocket = null
    }

    /**
     * Forwards raw DNS query to upstream resolver with caching, request coalescing, and strict security fallback policy.
     */
    fun resolve(queryData: ByteArray): ByteArray? {
        if (isClosed.get() || queryData.size < 12) return null

        val parsed = DnsPacket.parse(queryData)
        val question = parsed?.questions?.firstOrNull()
        val queryId = parsed?.header?.id ?: (((queryData[0].toInt() and 0xFF) shl 8) or (queryData[1].toInt() and 0xFF))

        // 1. Check in-memory LRU response cache
        var cacheKey: DnsCacheKey? = null
        if (question != null) {
            cacheKey = DnsCacheKey.create(
                rawQName = question.qName,
                qType = question.qType,
                qClass = question.qClass,
                upstreamServer = if (useDoh) dohUrl else "$primaryServer:$upstreamPort"
            )
            val cachedResponse = responseCache.get(cacheKey, queryId)
            if (cachedResponse != null) {
                return cachedResponse
            }
        }

        // 2. Request Deduplication (Coalescing):
        // If an identical DNS query for this exact cache key is already resolving upstream,
        // await its result rather than generating duplicate network queries.
        if (cacheKey != null) {
            var isLeader = false
            val myFuture = java.util.concurrent.CompletableFuture<ByteArray?>()
            val existingFuture = inFlightRequests.putIfAbsent(cacheKey, myFuture)
            val targetFuture = if (existingFuture != null) {
                existingFuture
            } else {
                isLeader = true
                myFuture
            }

            if (!isLeader) {
                return try {
                    val sharedResponse = targetFuture.get(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    if (sharedResponse != null && sharedResponse.size >= 12) {
                        val rewritten = sharedResponse.copyOf()
                        rewritten[0] = ((queryId shr 8) and 0xFF).toByte()
                        rewritten[1] = (queryId and 0xFF).toByte()
                        rewritten
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            try {
                val resolved = executeUpstreamResolution(queryData, queryId, cacheKey)
                targetFuture.complete(resolved)
                return resolved
            } catch (e: Throwable) {
                targetFuture.complete(null)
                throw e
            } finally {
                inFlightRequests.remove(cacheKey)
            }
        } else {
            return executeUpstreamResolution(queryData, queryId, null)
        }
    }

    private fun executeUpstreamResolution(
        queryData: ByteArray,
        queryId: Int,
        cacheKey: DnsCacheKey?
    ): ByteArray? {
        val fails = consecutiveFailures.get()
        if (fails >= 5) {
            val baseBackoffMs = Math.min(1000L, 50L * (1L shl Math.min(fails - 5, 4)))
            val jitterMs = (Math.random() * (baseBackoffMs * 0.3)).toLong()
            val totalBackoffMs = baseBackoffMs + jitterMs
            try {
                Thread.sleep(totalBackoffMs)
            } catch (_: InterruptedException) {
                return null
            }
        }

        val acquired = try {
            concurrencySemaphore.tryAcquire(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            return null
        }

        if (!acquired) {
            val err = "Upstream query concurrency limit ($MAX_CONCURRENT_REQUESTS) exceeded"
            lastError = err
            Log.w(TAG, err)
            return null
        }

        try {
            val startTime = System.currentTimeMillis()
            var response: ByteArray? = null

            // 1. Primary: DNS-over-HTTPS (DoH) encrypted transport
            if (useDoh && dohUrl.isNotBlank()) {
                response = sendAndReceiveDoh(dohUrl, queryData, queryId)
            }

            // 2. Strict Security Check:
            // If DoH was requested and failed, NEVER silently downgrade to plaintext UDP unless
            // the user explicitly permitted insecure fallback with clear warning.
            if (response == null) {
                if (useDoh && !allowInsecureFallback) {
                    val msg = "DoH resolution failed and insecure fallback is disabled (strict mode)"
                    lastError = msg
                    Log.w(TAG, msg)
                } else {
                    // If DoH is disabled OR user explicitly permitted insecure fallback:
                    response = sendAndReceive(primaryServer, queryData, queryId)
                    if (response == null && secondaryServer.isNotBlank() && secondaryServer != primaryServer) {
                        response = sendAndReceive(secondaryServer, queryData, queryId)
                    }
                }
            }

            if (response != null) {
                consecutiveFailures.set(0)
                VpnStateHolder.setDnsDegraded(false)
                lastResolvedTimestamp = System.currentTimeMillis()
                lastMeasuredLatencyMs = lastResolvedTimestamp - startTime
                lastError = null

                if (cacheKey != null) {
                    responseCache.put(cacheKey, response)
                }
                return response
            }

            val currentFails = consecutiveFailures.incrementAndGet()
            if (currentFails >= 3) {
                VpnStateHolder.setDnsDegraded(true, lastError ?: "Upstream DNS failures ($currentFails consecutive)")
            }

            return null
        } finally {
            concurrencySemaphore.release()
        }
    }

    private fun sendAndReceiveDoh(url: String, queryData: ByteArray, expectedId: Int): ByteArray? {
        return try {
            val mediaType = "application/dns-message".toMediaType()
            val requestBody = queryData.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/dns-message")
                .header("Accept", "application/dns-message")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    lastError = "DoH HTTP ${response.code} from $url"
                    return null
                }
                val contentType = response.header("Content-Type")?.lowercase()
                if (contentType != null && !contentType.startsWith("application/dns-message") && !contentType.startsWith("application/octet-stream")) {
                    lastError = "Invalid DoH Content-Type: $contentType from $url"
                    Log.w(TAG, "Discarded DoH response with non-DNS media type: $contentType")
                    return null
                }
                val body = response.body ?: return null
                if (body.contentLength() > 65535L) {
                    lastError = "DoH response exceeds 64KB ceiling (${body.contentLength()} bytes)"
                    return null
                }
                val bodyBytes = body.bytes()
                if (bodyBytes.size < 12) {
                    lastError = "DoH response too short (${bodyBytes.size} bytes)"
                    return null
                }
                if (bodyBytes.size > 65535) {
                    lastError = "DoH response exceeds 64KB ceiling (${bodyBytes.size} bytes)"
                    return null
                }
                // Ensure response transaction ID matches the original client query ID
                bodyBytes[0] = ((expectedId shr 8) and 0xFF).toByte()
                bodyBytes[1] = (expectedId and 0xFF).toByte()
                bodyBytes
            }
        } catch (e: Exception) {
            lastError = "DoH error ($url): ${e.message}"
            Log.w(TAG, "DoH resolution error for $url: ${e.message}")
            null
        }
    }

    private fun sendAndReceive(serverIp: String, queryData: ByteArray, expectedId: Int): ByteArray? {
        synchronized(socketLock) {
            val socket = getOrCreateSocketLocked() ?: return null
            try {
                val targetAddr = InetAddress.getByName(serverIp)
                val sendPacket = DatagramPacket(queryData, queryData.size, targetAddr, upstreamPort)
                socket.send(sendPacket)

                val receiveBuf = ByteArray(BUFFER_SIZE)
                val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
                socket.receive(receivePacket)

                val len = receivePacket.length
                if (len < 12) {
                    lastError = "Response too short ($len bytes) from $serverIp"
                    return null
                }

                // Verify transaction ID matches
                val respId = ((receiveBuf[0].toInt() and 0xFF) shl 8) or (receiveBuf[1].toInt() and 0xFF)
                if (respId != expectedId) {
                    lastError = "Mismatched transaction ID from $serverIp"
                    return null
                }

                val result = ByteArray(len)
                System.arraycopy(receivePacket.data, 0, result, 0, len)
                return result
            } catch (e: SocketTimeoutException) {
                lastError = "Timeout from $serverIp"
                return null
            } catch (e: Exception) {
                lastError = "Error communicating with $serverIp: ${e.message}"
                Log.w(TAG, "Upstream DNS error ($serverIp): ${e.message}")
                closeSocketLocked() // Recreate socket on network failure
                return null
            }
        }
    }

    /**
     * Performs a real reachability check by querying a known benign root domain.
     * Returns round-trip latency in milliseconds, or -1 if unreachable.
     */
    fun pingUpstream(testDomain: String = "one.one.one.one"): Long {
        return try {
            val queryBytes = buildSimpleQuery(testDomain) ?: return -1L
            val start = System.currentTimeMillis()
            val resp = resolve(queryBytes)
            if (resp != null) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun buildSimpleQuery(domain: String): ByteArray? {
        return try {
            val labels = domain.split('.')
            val totalLen = 12 + domain.length + 2 + 4
            val buf = java.nio.ByteBuffer.allocate(totalLen).order(java.nio.ByteOrder.BIG_ENDIAN)
            buf.putShort(0x4321.toShort()) // Transaction ID
            buf.putShort(0x0100.toShort()) // RD=1
            buf.putShort(1.toShort())      // QDCOUNT=1
            buf.putShort(0.toShort())      // ANCOUNT=0
            buf.putShort(0.toShort())      // NSCOUNT=0
            buf.putShort(0.toShort())      // ARCOUNT=0
            for (l in labels) {
                buf.put(l.length.toByte())
                buf.put(l.toByteArray(Charsets.US_ASCII))
            }
            buf.put(0.toByte())
            buf.putShort(1.toShort()) // Type A
            buf.putShort(1.toShort()) // Class IN
            buf.array()
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        isClosed.set(true)
        synchronized(socketLock) {
            closeSocketLocked()
            responseCache.invalidateAll()
        }
    }
}
