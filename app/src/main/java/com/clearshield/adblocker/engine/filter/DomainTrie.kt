package com.clearshield.adblocker.engine.filter

class TrieNode {
    val children = HashMap<String, TrieNode>(4)
    var rule: FilterRule? = null
    var matchesSubdomains: Boolean = false
    var isTerminal: Boolean = false
}

class DomainTrie {
    companion object {
        fun normalize(rawDomain: String): String {
            var d = rawDomain.trim().lowercase()
            while (d.startsWith(".")) d = d.substring(1)
            while (d.endsWith(".")) d = d.substring(0, d.length - 1)
            if (d.isEmpty()) return ""
            return try {
                java.net.IDN.toASCII(d, java.net.IDN.ALLOW_UNASSIGNED)
            } catch (_: Exception) {
                d
            }
        }
    }

    private val root = TrieNode()
    var size: Int = 0
        private set

    fun insert(rule: FilterRule) {
        val cleanDomain = normalize(rule.domain)
        if (cleanDomain.isEmpty()) return
        val parts = cleanDomain.split('.').filter { it.isNotEmpty() }.reversed()
        var current = root

        for (part in parts) {
            current = current.children.getOrPut(part) { TrieNode() }
        }

        if (!current.isTerminal) {
            size++
        }
        current.isTerminal = true
        current.rule = rule
        current.matchesSubdomains = rule.matchSubdomains
    }

    /**
     * Matches a query domain against the Trie:
     * - If matchesSubdomains == false: matches ONLY if the query domain is an exact match.
     * - If matchesSubdomains == true: matches the domain and any subdomain (e.g. sub.example.com),
     *   but strictly NOT inverted/unrelated domains like example.com.evil.com.
     */
    fun match(domain: String): FilterRule? {
        val cleanDomain = normalize(domain)
        if (cleanDomain.isEmpty()) return null
        val parts = cleanDomain.split('.').filter { it.isNotEmpty() }.reversed()
        var current = root

        var subdomainMatchedRule: FilterRule? = null
        var matchedPartCount = 0

        for (part in parts) {
            val next = current.children[part] ?: break
            current = next
            matchedPartCount++

            if (current.isTerminal && current.matchesSubdomains) {
                // A rule that covers subdomains was encountered at this domain suffix
                subdomainMatchedRule = current.rule
            }
        }

        // 1. Exact match: all parts of the domain matched and the terminal node has a rule
        if (matchedPartCount == parts.size && current.isTerminal) {
            return current.rule
        }

        // 2. Subdomain match: query is a deeper subdomain of a node configured with matchesSubdomains = true
        return subdomainMatchedRule
    }

    fun clear() {
        root.children.clear()
        size = 0
    }
}
