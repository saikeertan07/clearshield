package com.clearshield.adblocker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.ui.screens.*
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    onToggleVpn: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "ClearShield",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Native Local Ad Blocker",
                        style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Screen.allScreens.filterNotNull().forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = null, tint = if (selected) CyanAccent else TextSecondary) },
                        label = {
                            Text(
                                text = screen.title,
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = DeepNavy,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = CyberNavy,
            topBar = {
                TopAppBar(
                    title = {
                        val currentScreen = Screen.allScreens.filterNotNull().find { it.route == currentRoute }
                        Text(
                            text = currentScreen?.title ?: "ClearShield",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CyberNavy
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    Screen.bottomNavScreens.filterNotNull().forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberNavy,
                                selectedTextColor = CyanAccent,
                                indicatorColor = CyanAccent,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onToggleVpn = onToggleVpn,
                        onNavigateTo = { route -> navController.navigate(route) }
                    )
                }
                composable(Screen.Protection.route) {
                    ProtectionScreen(
                        onAddAllowlist = { domain ->
                            scope.launch {
                                try {
                                    ClearShieldApp.instance.filterRepository.addAllowlist(domain)
                                } catch (e: Exception) {
                                    android.util.Log.e("AppNavHost", "Error adding allowlist", e)
                                }
                            }
                        },
                        onAddBlocklist = { domain ->
                            scope.launch {
                                try {
                                    ClearShieldApp.instance.filterRepository.addCustomBlock(domain)
                                } catch (e: Exception) {
                                    android.util.Log.e("AppNavHost", "Error adding custom block", e)
                                }
                            }
                        }
                    )
                }
                composable(Screen.Statistics.route) {
                    StatisticsScreen()
                }
                composable(Screen.Applications.route) {
                    ApplicationsScreen()
                }
                composable(Screen.FilterLists.route) {
                    FilterListsScreen()
                }
                composable(Screen.Allowlist.route) {
                    AllowlistScreen()
                }
                composable(Screen.CustomBlock.route) {
                    CustomBlockScreen()
                }
                composable(Screen.DnsSettings.route) {
                    DnsSettingsScreen()
                }
                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
                composable(Screen.About.route) {
                    AboutScreen()
                }
            }
        }
    }
}
