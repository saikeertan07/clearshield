package com.clearshield.adblocker.engine.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, bounded ByteBuffer pool to eliminate GC allocation spikes during high-throughput
 * network packet capture and synthesis on the Android TUN interface.
 */
class PacketBufferPool private constructor(
    val bufferCapacity: Int = DEFAULT_BUFFER_SIZE,
    val maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE
) {
    companion object {
        const val DEFAULT_BUFFER_SIZE = 2048 // Sufficient for standard MTU (1500) + IP/UDP headers
        const val DEFAULT_MAX_POOL_SIZE = 64

        val defaultInstance: PacketBufferPool by lazy {
            PacketBufferPool(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_POOL_SIZE)
        }
    }

    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    private val currentPoolSize = AtomicInteger(0)

    private val _acquiredCount = AtomicLong(0)
    private val _recycledCount = AtomicLong(0)
    private val _exhaustionCount = AtomicLong(0)

    val acquiredCount: Long get() = _acquiredCount.get()
    val recycledCount: Long get() = _recycledCount.get()
    val exhaustionCount: Long get() = _exhaustionCount.get()
    val pooledCount: Int get() = currentPoolSize.get()

    /**
     * Acquires a cleared, Big-Endian ByteBuffer from the pool or allocates a new one if pool is empty.
     */
    fun acquire(minimumCapacity: Int = bufferCapacity): ByteBuffer {
        _acquiredCount.incrementAndGet()

        if (minimumCapacity <= bufferCapacity) {
            val pooled = pool.poll()
            if (pooled != null) {
                currentPoolSize.decrementAndGet()
                pooled.clear()
                pooled.order(ByteOrder.BIG_ENDIAN)
                return pooled
            }
        }

        _exhaustionCount.incrementAndGet()
        val capacityToAlloc = maxOf(minimumCapacity, bufferCapacity)
        return ByteBuffer.allocate(capacityToAlloc).order(ByteOrder.BIG_ENDIAN)
    }

    /**
     * Recycles a ByteBuffer back to the pool if pool bounds have not been exceeded.
     */
    fun recycle(buffer: ByteBuffer) {
        if (buffer.capacity() < bufferCapacity) return

        if (currentPoolSize.get() < maxPoolSize) {
            buffer.clear()
            pool.offer(buffer)
            currentPoolSize.incrementAndGet()
            _recycledCount.incrementAndGet()
        }
    }

    /**
     * Resets pool and clears all counters.
     */
    fun clear() {
        pool.clear()
        currentPoolSize.set(0)
        _acquiredCount.set(0)
        _recycledCount.set(0)
        _exhaustionCount.set(0)
    }
}
