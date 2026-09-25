package com.clearshield.adblocker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.automirrored.filled.FormatListBulleted

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Shield)
    object Protection : Screen("protection", "Live Traffic", Icons.Default.Sensors)
    object Statistics : Screen("statistics", "Statistics", Icons.Default.BarChart)
    object Applications : Screen("applications", "Apps Filter", Icons.Default.Apps)
    object FilterLists : Screen("filter_lists", "Filter Lists", Icons.AutoMirrored.Filled.FormatListBulleted)
    object Allowlist : Screen("allowlist", "Allowlist", Icons.Default.CheckCircle)
    object CustomBlock : Screen("custom_block", "Custom Block", Icons.Default.Block)
    object DnsSettings : Screen("dns_settings", "DNS Server", Icons.Default.Dns)
    object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Default.BugReport)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object About : Screen("about", "About", Icons.Default.Info)

    companion object {
        val bottomNavScreens: List<Screen>
            get() = listOf(Dashboard, Protection, Statistics, Applications)

        val allScreens: List<Screen>
            get() = listOf(
                Dashboard, Protection, Statistics, Applications,
                Diagnostics, FilterLists, Allowlist, CustomBlock, DnsSettings, Settings, About
            )
    }
}
