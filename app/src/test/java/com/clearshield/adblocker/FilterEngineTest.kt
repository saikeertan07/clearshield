package com.clearshield.adblocker

import com.clearshield.adblocker.engine.filter.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FilterEngineTest {

    private lateinit var engine: FilterEngine

    @Before
    fun setUp() {
        engine = FilterEngine()
        val rules = listOf(
            FilterRule.parse("||doubleclick.net^")!!,
            FilterRule.parse("||googleadservices.com^")!!,
            FilterRule.parse("0.0.0.0 tracking.adcolony.com")!!,
            FilterRule.parse("||analytics.tracker.io^")!!,
            FilterRule.parse("@@||trusted.doubleclick.net^")!!,
            FilterRule.parse("@@||safe.example.com^")!!
        )
        engine.loadRules(rules)
    }

    @Test
    fun testExactAndSubdomainBlocking() {
        // Root domain matches
        val r1 = engine.evaluate("doubleclick.net")
        assertTrue(r1 is FilterDecision.Blocked)

        // Subdomain matches
        val r2 = engine.evaluate("ad.doubleclick.net")
        assertTrue(r2 is FilterDecision.Blocked)

        val r3 = engine.evaluate("sub.deep.googleadservices.com")
        assertTrue(r3 is FilterDecision.Blocked)

        // Hosts rule matches
        val r4 = engine.evaluate("tracking.adcolony.com")
        assertTrue(r4 is FilterDecision.Blocked)
    }

    @Test
    fun testAllowlistPriorityOverBlocklist() {
        // doubleclick.net is blocked, BUT trusted.doubleclick.net is allowed
        val decision = engine.evaluate("trusted.doubleclick.net")
        assertTrue("Allowlist must take precedence", decision is FilterDecision.Allowed)

        // Other subdomains of doubleclick.net should still be blocked
        val blockedSub = engine.evaluate("untrusted.doubleclick.net")
        assertTrue(blockedSub is FilterDecision.Blocked)
    }

    @Test
    fun testCleanDomainAllowed() {
        val decision = engine.evaluate("wikipedia.org")
        assertTrue(decision is FilterDecision.Allowed)
        assertNull((decision as FilterDecision.Allowed).rule)
    }

    @Test
    fun testThreatCategoryDetection() {
        val rule = FilterRule.parse("||app-measurement.com^")
        assertNotNull(rule)
        assertEquals(ThreatCategory.TRACKER, rule!!.category)
    }

    @Test
    fun testCosmeticRulesIgnored() {
        // Cosmetic and element hiding rules must NOT be parsed as network domain blocks
        assertNull(FilterRule.parse("example.com##.ad-banner"))
        assertNull(FilterRule.parse("example.com#@#.ad-banner"))
        assertNull(FilterRule.parse("example.com#?#.sponsored"))
        assertNull(FilterRule.parse("example.com\$\$div[class*=\"ad\"]"))
        assertNull(FilterRule.parse("/ad-banner.js"))
    }

    @Test
    fun testDomainNormalizationAndTrailingDots() {
        // Upper case and trailing dot queries should still match normalized rules
        val decision1 = engine.evaluate("DOUBLECLICK.NET")
        assertTrue(decision1 is FilterDecision.Blocked)

        val decision2 = engine.evaluate("ad.doubleclick.net.")
        assertTrue(decision2 is FilterDecision.Blocked)
    }

    @Test
    fun testHostsCommentsParsing() {
        // Full comment lines
        assertNull(FilterRule.parse("# This is a full comment line"))
        assertNull(FilterRule.parse("! This is an ABP comment line"))

        // Rule with inline comment
        val ruleWithComment = FilterRule.parse("0.0.0.0 banner.adnetwork.com # analytics server")
        assertNotNull(ruleWithComment)
        assertEquals("banner.adnetwork.com", ruleWithComment!!.domain)
    }

    @Test
    fun testDomainTrieExactMatchVsSubdomainMatch() {
        val trie = DomainTrie()
        val exactRule = FilterRule(
            domain = "exact.com",
            type = RuleType.BLOCK,
            matchSubdomains = false,
            category = ThreatCategory.AD,
            rawRule = "|http://exact.com/"
        )
        val subRule = FilterRule(
            domain = "subdomain-inclusive.com",
            type = RuleType.BLOCK,
            matchSubdomains = true,
            category = ThreatCategory.AD,
            rawRule = "||subdomain-inclusive.com^"
        )
        trie.insert(exactRule)
        trie.insert(subRule)

        // Exact rule tests
        assertNotNull(trie.match("exact.com"))
        assertNull("sub.exact.com must NOT match exact rule", trie.match("sub.exact.com"))
        assertNull("exact.com.evil.com must NOT match exact rule", trie.match("exact.com.evil.com"))

        // Subdomain rule tests
        assertNotNull(trie.match("subdomain-inclusive.com"))
        assertNotNull("www.subdomain-inclusive.com must match subdomain rule", trie.match("www.subdomain-inclusive.com"))
        assertNotNull("a.b.subdomain-inclusive.com must match subdomain rule", trie.match("a.b.subdomain-inclusive.com"))
        assertNull("subdomain-inclusive.com.evil.com must NOT match", trie.match("subdomain-inclusive.com.evil.com"))
    }

    @Test
    fun testFiveTierEvaluationPrecedence() {
        val tieredEngine = FilterEngine()

        val allowRule = FilterRule("overridden.com", RuleType.ALLOW, true, ThreatCategory.GENERAL, "@@||overridden.com^")
        val customRule = FilterRule("overridden.com", RuleType.BLOCK, true, ThreatCategory.GENERAL, "||overridden.com^")
        val customOnlyRule = FilterRule("custom-blocked.com", RuleType.BLOCK, true, ThreatCategory.GENERAL, "||custom-blocked.com^")
        val subscriptionRule = FilterRule("sub-blocked.com", RuleType.BLOCK, true, ThreatCategory.AD, "||sub-blocked.com^")
        val defaultRule = FilterRule("default-blocked.com", RuleType.BLOCK, true, ThreatCategory.TRACKER, "||default-blocked.com^")

        tieredEngine.loadTiers(
            explicitAllow = listOf(allowRule),
            customBlock = listOf(customRule, customOnlyRule),
            subscriptionBlock = listOf(subscriptionRule),
            defaultBlock = listOf(defaultRule)
        )

        // Tier 1: Explicit Allowlist overrides Tier 2 Custom Blocklist
        val res1 = tieredEngine.evaluate("overridden.com")
        assertTrue("Allowlist must take precedence over custom block", res1 is FilterDecision.Allowed)

        // Tier 2: Custom Blocklist blocks custom domain
        val res2 = tieredEngine.evaluate("custom-blocked.com")
        assertTrue(res2 is FilterDecision.Blocked)
        assertEquals("custom-blocked.com", (res2 as FilterDecision.Blocked).rule.domain)

        // Tier 3: Subscription blocks sub domain
        val res3 = tieredEngine.evaluate("sub-blocked.com")
        assertTrue(res3 is FilterDecision.Blocked)

        // Tier 4: Default core blocks default domain
        val res4 = tieredEngine.evaluate("default-blocked.com")
        assertTrue(res4 is FilterDecision.Blocked)

        // Tier 5: Clean domain allowed
        val res5 = tieredEngine.evaluate("clean-example.org")
        assertTrue(res5 is FilterDecision.Allowed)
    }

    @Test
    fun testAllowlistOverridesSubscriptionBlocking() {
        val testEngine = FilterEngine()
        val allow = FilterRule("adservice.com", RuleType.ALLOW, true, ThreatCategory.GENERAL, "@@||adservice.com^")
        val subscription = FilterRule("adservice.com", RuleType.BLOCK, true, ThreatCategory.AD, "||adservice.com^")

        testEngine.loadTiers(
            explicitAllow = listOf(allow),
            customBlock = emptyList(),
            subscriptionBlock = listOf(subscription),
            defaultBlock = emptyList()
        )

        val decision = testEngine.evaluate("adservice.com")
        assertTrue("Allowlist must override subscription blocking", decision is FilterDecision.Allowed)
        val subDecision = testEngine.evaluate("sub.adservice.com")
        assertTrue("Allowlist must override subscription blocking for subdomains", subDecision is FilterDecision.Allowed)
    }

    @Test
    fun testAllowlistOverridesDefaultBlocking() {
        val testEngine = FilterEngine()
        val allow = FilterRule("doubleclick.net", RuleType.ALLOW, true, ThreatCategory.GENERAL, "@@||doubleclick.net^")
        val defaultBlock = FilterRule("doubleclick.net", RuleType.BLOCK, true, ThreatCategory.AD, "||doubleclick.net^")

        testEngine.loadTiers(
            explicitAllow = listOf(allow),
            customBlock = emptyList(),
            subscriptionBlock = emptyList(),
            defaultBlock = listOf(defaultBlock)
        )

        val decision = testEngine.evaluate("doubleclick.net")
        assertTrue("Allowlist must override default blocking", decision is FilterDecision.Allowed)
    }

    @Test
    fun testCustomBlockOverridesDefaultAllow() {
        val testEngine = FilterEngine()
        val customBlock = FilterRule("unwanted-site.org", RuleType.BLOCK, true, ThreatCategory.GENERAL, "||unwanted-site.org^")

        testEngine.loadTiers(
            explicitAllow = emptyList(),
            customBlock = listOf(customBlock),
            subscriptionBlock = emptyList(),
            defaultBlock = emptyList()
        )

        val decision = testEngine.evaluate("unwanted-site.org")
        assertTrue("Custom block must block normally allowed clean domains", decision is FilterDecision.Blocked)
        assertEquals("unwanted-site.org", (decision as FilterDecision.Blocked).rule.domain)
    }

    @Test
    fun testSubscriptionBlockingWorks() {
        val testEngine = FilterEngine()
        val subRule = FilterRule("telemetry-beacon.io", RuleType.BLOCK, true, ThreatCategory.TRACKER, "||telemetry-beacon.io^")

        testEngine.loadTiers(
            explicitAllow = emptyList(),
            customBlock = emptyList(),
            subscriptionBlock = listOf(subRule),
            defaultBlock = emptyList()
        )

        val decision = testEngine.evaluate("telemetry-beacon.io")
        assertTrue("Subscription blocking must block targeted domain", decision is FilterDecision.Blocked)
        assertEquals(ThreatCategory.TRACKER, (decision as FilterDecision.Blocked).category)
    }

    @Test
    fun testDefaultBlockingWorks() {
        val testEngine = FilterEngine()
        val coreRule = FilterRule("adsystem.net", RuleType.BLOCK, true, ThreatCategory.AD, "||adsystem.net^")

        testEngine.loadTiers(
            explicitAllow = emptyList(),
            customBlock = emptyList(),
            subscriptionBlock = emptyList(),
            defaultBlock = listOf(coreRule)
        )

        val decision = testEngine.evaluate("adsystem.net")
        assertTrue("Default core filter must block targeted domain", decision is FilterDecision.Blocked)
    }

    @Test
    fun testExactDomainRulesBehaveCorrectly() {
        val trie = DomainTrie()
        val exactRule = FilterRule(
            domain = "example.com",
            type = RuleType.BLOCK,
            matchSubdomains = false,
            category = ThreatCategory.AD,
            rawRule = "|http://example.com/"
        )
        trie.insert(exactRule)

        // Exact match succeeds
        assertNotNull("Exact match must match example.com", trie.match("example.com"))

        // Must NOT match inverted or suffix-hijacked domains
        assertNull("Must NOT match example.com.evil.com", trie.match("example.com.evil.com"))
        assertNull("Must NOT match evil-example.com", trie.match("evil-example.com"))
        assertNull("Must NOT match notexample.com", trie.match("notexample.com"))

        // Exact match with matchSubdomains = false must NOT match subdomains
        assertNull("Must NOT match sub.example.com", trie.match("sub.example.com"))
    }

    @Test
    fun testSubdomainRulesBehaveCorrectly() {
        val trie = DomainTrie()
        val subRule = FilterRule(
            domain = "example.com",
            type = RuleType.BLOCK,
            matchSubdomains = true,
            category = ThreatCategory.AD,
            rawRule = "||example.com^"
        )
        trie.insert(subRule)

        // Matches apex domain
        assertNotNull("Must match apex domain example.com", trie.match("example.com"))

        // Matches subdomains
        assertNotNull("Must match foo.example.com", trie.match("foo.example.com"))
        assertNotNull("Must match a.b.c.example.com", trie.match("a.b.c.example.com"))

        // Must NOT match unrelated domains
        assertNull("Must NOT match example.com.evil.com", trie.match("example.com.evil.com"))
        assertNull("Must NOT match evil-example.com", trie.match("evil-example.com"))
        assertNull("Must NOT match otherdomain.org", trie.match("otherdomain.org"))
    }

    @Test
    fun testIdnPunycodeAndNormalization() {
        val trie = DomainTrie()
        val idnRule = FilterRule(
            domain = "münchen.de",
            type = RuleType.BLOCK,
            matchSubdomains = true,
            category = ThreatCategory.AD,
            rawRule = "||münchen.de^"
        )
        trie.insert(idnRule)

        // Should match UTF-8 Unicode domain
        assertNotNull("Must match Unicode IDN münchen.de", trie.match("münchen.de"))

        // Should match ACE Punycode representation (xn--mnchen-3ya.de)
        assertNotNull("Must match ASCII punycode xn--mnchen-3ya.de", trie.match("xn--mnchen-3ya.de"))

        // Normalization handles leading/trailing dots and uppercase
        assertNotNull("Must match .MÜNCHEN.DE.", trie.match(".MÜNCHEN.DE."))
    }

    @Test
    fun testPrefixBoundaryNonCollision() {
        val trie = DomainTrie()
        val rule = FilterRule(
            domain = "ads.example.com",
            type = RuleType.BLOCK,
            matchSubdomains = true,
            category = ThreatCategory.AD,
            rawRule = "||ads.example.com^"
        )
        trie.insert(rule)

        // Must match exact domain and legitimate subdomains
        assertNotNull("Must match ads.example.com", trie.match("ads.example.com"))
        assertNotNull("Must match sub.ads.example.com", trie.match("sub.ads.example.com"))
        assertNotNull("Must match tracker.mobile.ads.example.com", trie.match("tracker.mobile.ads.example.com"))

        // Strictly MUST NOT match label substrings or sibling domains
        assertNull("Must NOT match notads.example.com", trie.match("notads.example.com"))
        assertNull("Must NOT match myads.example.com", trie.match("myads.example.com"))
        assertNull("Must NOT match bads.example.com", trie.match("bads.example.com"))
        assertNull("Must NOT match ads.example.com.attacker.org", trie.match("ads.example.com.attacker.org"))
        assertNull("Must NOT match example.com", trie.match("example.com"))
    }

    @Test
    fun testTrieNodeCountAndMemoryEstimation() {
        val trie = DomainTrie()
        assertEquals(0, trie.size)
        assertEquals(1, trie.totalNodeCount)
        assertEquals(128L, trie.estimateMemoryUsageBytes())

        trie.insert(FilterRule("tracker.example.com", RuleType.BLOCK, true, ThreatCategory.TRACKER, "||tracker.example.com^"))
        assertEquals(1, trie.size)
        assertEquals(4, trie.totalNodeCount)
        assertEquals(1, trie.subdomainRuleCount)
        assertEquals(4 * 128L, trie.estimateMemoryUsageBytes())

        trie.insert(FilterRule("analytics.example.com", RuleType.BLOCK, false, ThreatCategory.TRACKER, "|analytics.example.com"))
        assertEquals(2, trie.size)
        assertEquals(5, trie.totalNodeCount)

        trie.clear()
        assertEquals(0, trie.size)
        assertEquals(1, trie.totalNodeCount)
    }

    @Test
    fun testTrieBulkIngestionConsistency() {
        val trie = DomainTrie()
        val rules = listOf(
            FilterRule("ad1.com", RuleType.BLOCK, true, ThreatCategory.AD, "||ad1.com^"),
            FilterRule("ad2.com", RuleType.BLOCK, true, ThreatCategory.AD, "||ad2.com^"),
            FilterRule("ad3.org", RuleType.BLOCK, false, ThreatCategory.AD, "ad3.org")
        )
        trie.insertAll(rules)
        assertEquals(3, trie.size)
        assertNotNull(trie.match("sub.ad1.com"))
        assertNotNull(trie.match("sub.ad2.com"))
        assertNotNull(trie.match("ad3.org"))
        assertNull(trie.match("sub.ad3.org"))
    }

    @Test
    fun testConsecutiveDotsNormalizationAndLabelDepthGuard() {
        val trie = DomainTrie()
        val rule = FilterRule("ads..doubleclick...net", RuleType.BLOCK, true, ThreatCategory.AD, "||ads..doubleclick...net^")
        trie.insert(rule)

        assertEquals(1, trie.size)
        assertNotNull("Should match normalized query ads.doubleclick.net", trie.match("ads.doubleclick.net"))
        assertNotNull("Should match query with duplicate dots ads...doubleclick.net", trie.match("ads...doubleclick.net"))
        assertNotNull("Should match subdomain tracking.ads.doubleclick.net", trie.match("tracking.ads.doubleclick.net"))

        val excessiveLabels = (1..130).joinToString(".") { "sub$it" } + ".com"
        assertNull("Pathological domain exceeding 127 labels must return null", trie.match(excessiveLabels))
    }
}
