package com.clearshield.adblocker

import com.clearshield.adblocker.engine.dns.PacketBufferPool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PacketBufferPoolTest {

    private lateinit var pool: PacketBufferPool

    @Before
    fun setUp() {
        pool = PacketBufferPool.defaultInstance
        pool.clear()
    }

    @Test
    fun testAcquireAndRecycleCycle() {
        assertEquals(0, pool.pooledCount)

        val buf1 = pool.acquire(1024)
        assertNotNull(buf1)
        assertTrue(buf1.capacity() >= 1024)
        assertEquals(1, pool.acquiredCount)

        pool.recycle(buf1)
        assertEquals(1, pool.pooledCount)
        assertEquals(1, pool.recycledCount)

        val buf2 = pool.acquire(1024)
        assertSame("Reused buffer should be identical to recycled buffer", buf1, buf2)
        assertEquals(0, pool.pooledCount)
        assertEquals(2, pool.acquiredCount)
    }

    @Test
    fun testOversizedBufferAllocationDoesNotCorruptPool() {
        val largeBuf = pool.acquire(minimumCapacity = 65536)
        assertTrue(largeBuf.capacity() >= 65536)

        // Recycling oversized buffer should still work safely
        pool.recycle(largeBuf)
        assertTrue(pool.pooledCount <= pool.maxPoolSize)
    }

    @Test
    fun testConcurrentAcquireAndRecycleUnderLoad() {
        val threadCount = 16
        val iterationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val buf = pool.acquire(512)
                        buf.put(0x45.toByte())
                        buf.put(0x00.toByte())
                        pool.recycle(buf)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("Concurrent executions must complete within timeout", completed)
        assertTrue("Pool size should remain within bounded ceiling", pool.pooledCount <= pool.maxPoolSize)
        assertEquals((threadCount * iterationsPerThread).toLong(), pool.acquiredCount)
    }
}
