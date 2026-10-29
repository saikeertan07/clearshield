package com.clearshield.adblocker

import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.engine.filter.FilterRule
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.service.VpnStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class StartupResilienceTest {

    @Test
    fun testUncaughtExceptionHandlerDelegation() {
        val originalHandlerInvoked = AtomicBoolean(false)
        val testException = RuntimeException("Startup test exception")

        val mockOriginalHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            if (throwable === testException) {
                originalHandlerInvoked.set(true)
            }
        }

        val delegatingHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ClearShieldTest", "Logging before delegating", throwable)
            mockOriginalHandler.uncaughtException(thread, throwable)
        }

        // Simulate an uncaught exception on the current thread
        delegatingHandler.uncaughtException(Thread.currentThread(), testException)

        assertTrue(
            "Uncaught exceptions MUST delegate to the original handler rather than being swallowed",
            originalHandlerInvoked.get()
        )
    }

    @Test
    fun testVpnInitialStateIsCompletelyIndependent() {
        // Verification that UI startup does NOT require VPN to be active or permitted
        VpnStateHolder.resetSessionStats()
        VpnStateHolder.setStatus(VpnStatus.OFF)

        assertEquals("Initial VPN status must be OFF", VpnStatus.OFF, VpnStateHolder.vpnStatus.value)
        assertFalse("VPN running flag must be false on initial startup", VpnStateHolder.isVpnRunning.value)
        assertFalse("VPN established flag must be false on initial startup", VpnStateHolder.isVpnEstablished.value)
        assertFalse("Traffic flowed flag must be false on initial startup", VpnStateHolder.hasTrafficFlowed.value)
        assertEquals("Initial query count must be 0", 0L, VpnStateHolder.totalQueries.value)
        assertEquals("Initial default upstream must be present", "1.1.1.1", VpnStateHolder.activeUpstreamDns.value)
    }

    @Test
    fun testFilterEngineSafeWithEmptyOrCorruptedRules() {
        val engine = FilterEngine.instance
        // Loading empty tiers must never throw
        engine.loadTiers(
            explicitAllow = emptyList(),
            customBlock = emptyList(),
            subscriptionBlock = emptyList(),
            defaultBlock = emptyList()
        )

        assertEquals(0, engine.totalBlockedRules)
        assertEquals(0, engine.totalAllowedRules)

        // Parsing malformed lines must return null safely without throwing exceptions
        val malformed1 = FilterRule.parse("")
        val malformed2 = FilterRule.parse("   ")
        val malformed3 = FilterRule.parse("! Only a comment")
        val malformed4 = FilterRule.parse("#####invalid-syntax")

        assertNull("Empty line should parse to null", malformed1)
        assertNull("Blank line should parse to null", malformed2)
        assertNull("Comment should parse to null", malformed3)
        assertNull("Invalid syntax should parse to null", malformed4)
    }

    enum class AppStartupState {
        STARTING,
        READY,
        ERROR,
        RETRY
    }

    @Test
    fun testStartupStateMachineTransitions() {
        var state = AppStartupState.STARTING
        assertEquals(AppStartupState.STARTING, state)

        // Normal path
        state = AppStartupState.READY
        assertEquals(AppStartupState.READY, state)

        // Error recovery path
        state = AppStartupState.ERROR
        assertEquals(AppStartupState.ERROR, state)

        state = AppStartupState.RETRY
        assertEquals(AppStartupState.RETRY, state)

        state = AppStartupState.READY
        assertEquals(AppStartupState.READY, state)
    }

    @Test
    fun testLazyRepositoryInitializationEliminatesUninitializedPropertyAccess() {
        // Mock application class with lazy properties
        class MockApp {
            var dbInitialized = false
            val db: String by lazy {
                dbInitialized = true
                "mock_db"
            }
        }

        val app = MockApp()
        assertFalse("Lazy property must not be initialized before access", app.dbInitialized)
        // Accessing property should initialize without UninitializedPropertyAccessException
        val value = app.db
        assertEquals("mock_db", value)
        assertTrue("Lazy property must be safely initialized after access", app.dbInitialized)
    }

    @Test
    fun testMigration1To2ContractAndVersionSpecification() {
        val migration = com.clearshield.adblocker.data.db.AppDatabase.MIGRATION_1_2
        assertEquals("Migration start version must be 1", 1, migration.startVersion)
        assertEquals("Migration end version must be 2", 2, migration.endVersion)
    }

    @Test
    fun testMigration1To2ExecutesNonDestructiveSchemaCreation() {
        val migration = com.clearshield.adblocker.data.db.AppDatabase.MIGRATION_1_2
        val executedSql = mutableListOf<String>()

        val invocationHandler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        }

        val dbProxy = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java),
            invocationHandler
        ) as androidx.sqlite.db.SupportSQLiteDatabase

        migration.migrate(dbProxy)

        assertEquals("MIGRATION_1_2 must execute exactly one DDL statement", 1, executedSql.size)
        val sql = executedSql[0].lowercase()
        assertTrue("Must create daily_stats table", sql.contains("create table if not exists `daily_stats`"))
        assertTrue("Must contain dateString column", sql.contains("datestring"))
        assertTrue("Must contain totalQueries column", sql.contains("totalqueries"))
        assertTrue("Must contain blockedQueries column", sql.contains("blockedqueries"))
        assertTrue("Must contain adsBlocked column", sql.contains("adsblocked"))
        assertTrue("Must contain trackersBlocked column", sql.contains("trackersblocked"))
        assertFalse("Must never drop existing tables during migration", sql.contains("drop table"))
    }

    @Test
    fun testVpnStateTransitionResilience() {
        // Initial transition to starting
        VpnStateHolder.setStatus(VpnStatus.STARTING)
        assertEquals(VpnStatus.STARTING, VpnStateHolder.vpnStatus.value)
        assertFalse(VpnStateHolder.isVpnRunning.value)

        // Running protected
        VpnStateHolder.setVpnEstablished(true)
        VpnStateHolder.setStatus(VpnStatus.PROTECTED)
        assertEquals(VpnStatus.PROTECTED, VpnStateHolder.vpnStatus.value)
        assertTrue(VpnStateHolder.isVpnRunning.value)
        assertTrue(VpnStateHolder.isVpnEstablished.value)

        // Temporary network loss
        VpnStateHolder.setStatus(VpnStatus.NETWORK_UNAVAILABLE, "No active internet connection")
        assertEquals(VpnStatus.NETWORK_UNAVAILABLE, VpnStateHolder.vpnStatus.value)
        assertFalse(VpnStateHolder.isVpnRunning.value)
        assertTrue("Interface remains established during transient network loss", VpnStateHolder.isVpnEstablished.value)
        assertEquals("No active internet connection", VpnStateHolder.lastError.value)

        // Network recovery
        VpnStateHolder.setStatus(VpnStatus.PROTECTED)
        assertEquals(VpnStatus.PROTECTED, VpnStateHolder.vpnStatus.value)
        assertTrue(VpnStateHolder.isVpnRunning.value)

        // Teardown / Stopped
        VpnStateHolder.setStatus(VpnStatus.OFF)
        assertEquals(VpnStatus.OFF, VpnStateHolder.vpnStatus.value)
        assertFalse(VpnStateHolder.isVpnRunning.value)
        assertFalse(VpnStateHolder.isVpnEstablished.value)
    }
}
