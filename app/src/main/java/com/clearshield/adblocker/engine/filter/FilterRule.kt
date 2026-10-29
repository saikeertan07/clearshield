package com.clearshield.adblocker.engine.filter

enum class RuleType {
    BLOCK,
    ALLOW
}

enum class ThreatCategory {
    AD,
    TRACKER,
    ANALYTICS,
    GENERAL
}

data class FilterRule(
    val domain: String,
    val type: RuleType,
    val matchSubdomains: Boolean,
    val category: ThreatCategory,
    val rawRule: String
) {
    companion object {
        private val TRACKER_KEYWORDS = listOf(
            "track", "telemetry", "analytic", "pixel", "beacon",
            "metric", "stat", "event", "crash", "measure", "attribution"
        )

        fun parse(line: String): FilterRule? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                return null
            }

            // CRITICAL: Safely ignore cosmetic/HTML element-hiding rules from EasyList
            // e.g. "example.com##.ad-banner" or "example.com#@#.ad" or "/banner/*"
            // Never strip '#' and accidentally block legitimate root domains!
            if (trimmed.contains("##") || trimmed.contains("#@#") || trimmed.contains("#?#") ||
                trimmed.startsWith("/") || trimmed.startsWith("@@/") || trimmed.contains("$$")
            ) {
                return null
            }

            var domain = ""
            var isAllow = false
            var matchSubdomains = true

            if (trimmed.startsWith("@@||")) {
                // Allowlist ABP rule: @@||example.com^
                isAllow = true
                domain = trimmed.substring(4).trimEnd('^', '/')
                matchSubdomains = true
            } else if (trimmed.startsWith("@@")) {
                // Allowlist rule: @@example.com
                isAllow = true
                domain = trimmed.substring(2).trimEnd('^', '/')
                matchSubdomains = true // Default to subdomain coverage for user-entered allow domains
            } else if (trimmed.startsWith("||")) {
                // ABP Domain rule: ||example.com^
                isAllow = false
                domain = trimmed.substring(2).trimEnd('^', '/')
                matchSubdomains = true
            } else if (trimmed.startsWith("0.0.0.0") || trimmed.startsWith("127.0.0.1")) {
                // Hosts file rule: 0.0.0.0 ads.example.com
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    domain = parts[1]
                    // If the line has an inline comment, strip it
                    val commentIdx = domain.indexOf('#')
                    if (commentIdx != -1) {
                        domain = domain.substring(0, commentIdx)
                    }
                    matchSubdomains = true
                } else {
                    return null
                }
            } else {
                // Plain domain rule
                domain = trimmed.trimEnd('^', '/')
                matchSubdomains = true
            }

            // Strip ABP rule options after $ (e.g. ||example.com^$third-party)
            val dollarIdx = domain.indexOf('$')
            if (dollarIdx != -1) {
                domain = domain.substring(0, dollarIdx)
            }

            // Strip path if present
            if (domain.contains('/')) {
                domain = domain.substring(0, domain.indexOf('/'))
            }

            // Strip port if present
            if (domain.contains(':')) {
                domain = domain.substring(0, domain.indexOf(':'))
            }

            // Normalize: trim, lowercase, strip trailing and leading dots/wildcards
            domain = domain.trim().lowercase().trimStart('*', '.').trimEnd('.')
            if (domain.contains("..")) {
                domain = domain.replace(Regex("\\.+"), ".")
            }

            // Validate domain
            if (domain.isEmpty() || !domain.contains('.') || domain.length > 253 || domain.contains(" ")) {
                return null
            }

            // Avoid invalid domains like "localhost" or numeric IPs
            if (domain.matches(Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$"))) {
                return null
            }

            // Detect category
            val category = when {
                TRACKER_KEYWORDS.any { domain.contains(it) } -> ThreatCategory.TRACKER
                else -> ThreatCategory.AD
            }

            return FilterRule(
                domain = domain,
                type = if (isAllow) RuleType.ALLOW else RuleType.BLOCK,
                matchSubdomains = matchSubdomains,
                category = category,
                rawRule = trimmed
            )
        }
    }
}
