package com.clearshield.adblocker.engine.filter

import android.util.Log

sealed class FilterDecision {
    data class Blocked(val rule: FilterRule, val category: ThreatCategory, val reason: String) : FilterDecision()
    data class Allowed(val rule: FilterRule?, val reason: String) : FilterDecision()
}

/**
 * Snapshot representing the compiled Trie rules partitioned across 4 distinct priority tiers.
 */
data class FilterSnapshot(
    val explicitAllowTrie: DomainTrie = DomainTrie(),
    val customBlockTrie: DomainTrie = DomainTrie(),
    val subscriptionBlockTrie: DomainTrie = DomainTrie(),
    val defaultBlockTrie: DomainTrie = DomainTrie()
) {
    val totalBlockedRules: Int
        get() = customBlockTrie.size + subscriptionBlockTrie.size + defaultBlockTrie.size

    val totalAllowedRules: Int
        get() = explicitAllowTrie.size

    val estimatedMemoryUsageBytes: Long
        get() = explicitAllowTrie.estimateMemoryUsageBytes() +
                customBlockTrie.estimateMemoryUsageBytes() +
                subscriptionBlockTrie.estimateMemoryUsageBytes() +
                defaultBlockTrie.estimateMemoryUsageBytes()

    val totalNodes: Int
        get() = explicitAllowTrie.totalNodeCount +
                customBlockTrie.totalNodeCount +
                subscriptionBlockTrie.totalNodeCount +
                defaultBlockTrie.totalNodeCount
}

class FilterEngine {
    companion object {
        private const val TAG = "FilterEngine"
        val instance by lazy { FilterEngine() }
    }

    @Volatile
    private var snapshot = FilterSnapshot()

    val totalBlockedRules: Int
        get() = snapshot.totalBlockedRules

    val totalAllowedRules: Int
        get() = snapshot.totalAllowedRules

    val estimatedMemoryUsageBytes: Long
        get() = snapshot.estimatedMemoryUsageBytes

    val totalTrieNodes: Int
        get() = snapshot.totalNodes

    /**
     * Loads rules partitioned by priority tiers:
     * Tier 1: Explicit Allowlist (User trusted domains)
     * Tier 2: Explicit Custom Blocklist (User blocked domains)
     * Tier 3: Subscribed Filter Lists (Downloaded ABP/Hosts lists)
     * Tier 4: Default Core Filter (Bundled high-impact ad/tracker filters)
     */
    fun loadTiers(
        explicitAllow: List<FilterRule>,
        customBlock: List<FilterRule>,
        subscriptionBlock: List<FilterRule>,
        defaultBlock: List<FilterRule>
    ) {
        val allowTrie = DomainTrie()
        allowTrie.insertAll(explicitAllow)

        val customTrie = DomainTrie()
        customTrie.insertAll(customBlock)

        val subTrie = DomainTrie()
        subTrie.insertAll(subscriptionBlock)

        val defTrie = DomainTrie()
        defTrie.insertAll(defaultBlock)

        snapshot = FilterSnapshot(
            explicitAllowTrie = allowTrie,
            customBlockTrie = customTrie,
            subscriptionBlockTrie = subTrie,
            defaultBlockTrie = defTrie
        )

        Log.i(TAG, "FilterEngine loaded: ${snapshot.totalBlockedRules} blocked rules, ${snapshot.totalAllowedRules} allow rules")
    }

    /**
     * Backward-compatible loader for tests and flat lists.
     */
    fun loadRules(rules: List<FilterRule>) {
        val allow = mutableListOf<FilterRule>()
        val block = mutableListOf<FilterRule>()
        for (r in rules) {
            if (r.type == RuleType.ALLOW) allow.add(r) else block.add(r)
        }
        loadTiers(explicitAllow = allow, customBlock = emptyList(), subscriptionBlock = emptyList(), defaultBlock = block)
    }

    /**
     * Evaluates domain strictly adhering to 5-tier logical priority:
     * 1. Explicit Allowlist -> If match: ALLOWED (overrides all blocklists)
     * 2. Explicit Custom Block -> If match: BLOCKED (overrides subscriptions & defaults)
     * 3. Subscription Rules -> If match: BLOCKED
     * 4. Default Core Rules -> If match: BLOCKED
     * 5. Default Fallback -> ALLOWED (Clean domain)
     */
    fun evaluate(rawDomain: String): FilterDecision {
        val domain = rawDomain.trim().trimEnd('.').lowercase()
        if (domain.isEmpty()) {
            return FilterDecision.Allowed(null, "Empty domain")
        }

        val s = snapshot

        // Tier 1: Explicit Allowlist (Highest Precedence)
        val allowMatch = s.explicitAllowTrie.match(domain)
        if (allowMatch != null) {
            return FilterDecision.Allowed(allowMatch, "Explicit allowlist: ${allowMatch.domain}")
        }

        // Tier 2: Explicit Custom Blocklist
        val customBlockMatch = s.customBlockTrie.match(domain)
        if (customBlockMatch != null) {
            return FilterDecision.Blocked(customBlockMatch, customBlockMatch.category, "Custom blocklist: ${customBlockMatch.domain}")
        }

        // Tier 3: Subscription Rules
        val subBlockMatch = s.subscriptionBlockTrie.match(domain)
        if (subBlockMatch != null) {
            return FilterDecision.Blocked(subBlockMatch, subBlockMatch.category, "Subscription list: ${subBlockMatch.domain}")
        }

        // Tier 4: Default Core Rules
        val defaultBlockMatch = s.defaultBlockTrie.match(domain)
        if (defaultBlockMatch != null) {
            return FilterDecision.Blocked(defaultBlockMatch, defaultBlockMatch.category, "Core filter: ${defaultBlockMatch.domain}")
        }

        // Tier 5: Default Allow
        return FilterDecision.Allowed(null, "Unmatched clean domain")
    }
}
