package com.viberack.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.nfc.NfcScanResult
import com.viberack.app.feature.containers.ContainersOpenRequest
import com.viberack.app.feature.containers.ContainersRoute
import com.viberack.app.feature.home.HomeRoute
import com.viberack.app.feature.printer.PrinterRoute
import com.viberack.app.feature.search.SearchRoute
import com.viberack.app.feature.settings.SettingsRoute

@Composable
fun VibeRackApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val items = topLevelDestinations
    var containersOpenRequestSignal by remember { mutableIntStateOf(0) }
    var containersOpenRequest by remember { mutableStateOf<ContainersOpenRequest?>(null) }

    fun navigateTo(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openPhysicalTarget: (PhysicalTarget) -> Unit = { target ->
        when (val route = PhysicalTargetRouting.toRoute(target)) {
            is PhysicalTargetRoute.Containers -> {
                containersOpenRequest = route.request
                containersOpenRequestSignal++
                navigateTo(Destination.Containers)
            }
        }
    }

    DisposableEffect(activity, appContainer) {
        appContainer.nfcLabelManager.setOnScanResult { result ->
            activity?.runOnUiThread {
                when (result) {
                    is NfcScanResult.Label -> {
                        PhysicalTargetRouting.fromNfcPayload(result.payload)
                            ?.let(openPhysicalTarget)
                    }

                    is NfcScanResult.Unsupported -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.nfc_unsupported_tag),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is NfcScanResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }

                    NfcScanResult.WriteCompleted -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.nfc_write_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        if (activity != null) {
            appContainer.nfcLabelManager.enable(activity)
        }
        onDispose {
            appContainer.nfcLabelManager.setOnScanResult(null)
            if (activity != null) {
                appContainer.nfcLabelManager.disable(activity)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { navDestination ->
                        navDestination.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) return@NavigationBarItem
                            navigateTo(destination)
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Destination.Home.route) {
                HomeRoute()
            }
            composable(Destination.Containers.route) {
                ContainersRoute(
                    openRequest = containersOpenRequest,
                    openRequestSignal = containersOpenRequestSignal,
                )
            }
            composable(Destination.Search.route) {
                SearchRoute()
            }
            composable(Destination.Printer.route) {
                PrinterRoute()
            }
            composable(Destination.Settings.route) {
                SettingsRoute(
                    onOpenHardwareRestore = {
                        navigateTo(Destination.Containers)
                    }
                )
            }
        }
    }
}

private sealed class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    data object Home : Destination("home", R.string.nav_home, Icons.Outlined.Home)
    data object Containers : Destination("containers", R.string.nav_containers, Icons.Outlined.Inventory2)
    data object Search : Destination("search", R.string.nav_search, Icons.Outlined.Search)
    data object Printer : Destination("printer", R.string.nav_printer, Icons.Outlined.Print)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private val topLevelDestinations = listOf(
    Destination.Home,
    Destination.Containers,
    Destination.Search,
    Destination.Printer,
    Destination.Settings
)
