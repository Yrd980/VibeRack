package com.example.lcsc_android_erp.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.nfc.NfcLabelKind
import com.example.lcsc_android_erp.core.nfc.NfcScanResult
import com.example.lcsc_android_erp.feature.boxes.BoxesOpenRequest
import com.example.lcsc_android_erp.feature.boxes.BoxesRoute
import com.example.lcsc_android_erp.feature.containers.ContainersOpenRequest
import com.example.lcsc_android_erp.feature.containers.ContainersRoute
import com.example.lcsc_android_erp.feature.home.HomeRoute
import com.example.lcsc_android_erp.feature.inbound.InboundRoute
import com.example.lcsc_android_erp.feature.inventory.InventoryOpenRequest
import com.example.lcsc_android_erp.feature.inventory.InventoryRoute
import com.example.lcsc_android_erp.feature.printer.PrinterRoute
import com.example.lcsc_android_erp.feature.search.SearchRoute
import com.example.lcsc_android_erp.feature.settings.SettingsRoute

@Composable
fun LcscApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val appContainer = (context.applicationContext as LcscApplication).appContainer
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val items = topLevelDestinations
    var inventoryResetToOverviewSignal by remember { mutableIntStateOf(0) }
    var inventoryOpenRequestSignal by remember { mutableIntStateOf(0) }
    var inventoryOpenRequest by remember { mutableStateOf<InventoryOpenRequest?>(null) }
    var boxesOpenRequestSignal by remember { mutableIntStateOf(0) }
    var boxesOpenRequest by remember { mutableStateOf<BoxesOpenRequest?>(null) }
    var containersOpenRequestSignal by remember { mutableIntStateOf(0) }
    var containersOpenRequest by remember { mutableStateOf<ContainersOpenRequest?>(null) }

    val jumpToInventoryItem: (String, String) -> Unit = { locationCode, partNumber ->
        inventoryOpenRequest = InventoryOpenRequest(
            locationCode = locationCode,
            partNumber = partNumber
        )
        inventoryOpenRequestSignal++
        navController.navigate(Destination.Inventory.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val jumpToInventoryLocation: (String) -> Unit = { locationCode ->
        inventoryOpenRequest = InventoryOpenRequest(locationCode = locationCode)
        inventoryOpenRequestSignal++
        navController.navigate(Destination.Inventory.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val jumpToInventoryPartNumber: (String) -> Unit = { partNumber ->
        inventoryOpenRequest = InventoryOpenRequest(partNumber = partNumber)
        inventoryOpenRequestSignal++
        navController.navigate(Destination.Inventory.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val jumpToBoxLayer: (String, String) -> Unit = { boxCode, layerCode ->
        boxesOpenRequest = BoxesOpenRequest(
            boxCode = boxCode,
            layerCode = layerCode
        )
        boxesOpenRequestSignal++
        navController.navigate(Destination.Boxes.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val jumpToDevice: (String, Int?, Int?) -> Unit = { macAddress, batchId, protoVersion ->
        containersOpenRequest = ContainersOpenRequest(
            macAddress = macAddress,
            batchId = batchId,
            protoVersion = protoVersion
        )
        containersOpenRequestSignal++
        navController.navigate(Destination.Containers.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    DisposableEffect(activity, appContainer) {
        appContainer.nfcLabelManager.setOnScanResult { result ->
            activity?.runOnUiThread {
                when (result) {
                    is NfcScanResult.Label -> {
                        when (result.payload.kind) {
                            NfcLabelKind.LOCATION -> {
                                result.payload.locationCode?.let(jumpToInventoryLocation)
                            }

                            NfcLabelKind.MATERIAL -> {
                                val locationCode = result.payload.locationCode
                                val partNumber = result.payload.partNumber
                                val boxCode = result.payload.boxCode
                                val layerCode = result.payload.layerCode
                                if (boxCode != null && layerCode != null) {
                                    jumpToBoxLayer(boxCode, layerCode)
                                } else if (locationCode != null && partNumber != null) {
                                    jumpToInventoryItem(locationCode, partNumber)
                                } else if (partNumber != null) {
                                    jumpToInventoryPartNumber(partNumber)
                                }
                            }

                            NfcLabelKind.DEVICE -> {
                                result.payload.macAddress?.let { macAddress ->
                                    jumpToDevice(
                                        macAddress,
                                        result.payload.batchId,
                                        result.payload.protoVersion
                                    )
                                }
                            }
                        }
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
                            if (selected) {
                                if (destination.route == Destination.Inventory.route) {
                                    inventoryResetToOverviewSignal++
                                }
                                return@NavigationBarItem
                            }
                            if (destination.route == Destination.Inventory.route) {
                                inventoryResetToOverviewSignal++
                            }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = { Text(text = stringResource(destination.labelRes)) }
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
            composable(Destination.Boxes.route) {
                BoxesRoute(
                    openRequest = boxesOpenRequest,
                    openRequestSignal = boxesOpenRequestSignal
                )
            }
            composable(Destination.Containers.route) {
                ContainersRoute(
                    openRequest = containersOpenRequest,
                    openRequestSignal = containersOpenRequestSignal,
                    onOpenBoxes = {
                        navController.navigate(Destination.Boxes.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Destination.Inbound.route) {
                InboundRoute(onViewInventoryItem = jumpToInventoryItem)
            }
            composable(Destination.Search.route) {
                SearchRoute(onViewInventoryItem = jumpToInventoryItem)
            }
            composable(Destination.Printer.route) {
                PrinterRoute()
            }
            composable(Destination.Inventory.route) {
                InventoryRoute(
                    openRequest = inventoryOpenRequest,
                    openRequestSignal = inventoryOpenRequestSignal,
                    resetToOverviewSignal = inventoryResetToOverviewSignal
                )
            }
            composable(Destination.Settings.route) {
                SettingsRoute(
                    onOpenHardwareRestore = {
                        navController.navigate(Destination.Containers.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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
    data object Boxes : Destination("boxes", R.string.nav_boxes, Icons.Outlined.Inventory2)
    data object Containers : Destination("containers", R.string.nav_containers, Icons.Outlined.Inventory2)
    data object Inbound : Destination("inbound", R.string.nav_inbound, Icons.Outlined.QrCodeScanner)
    data object Search : Destination("search", R.string.nav_search, Icons.Outlined.Search)
    data object Printer : Destination("printer", R.string.nav_printer, Icons.Outlined.Print)
    data object Inventory : Destination("inventory", R.string.nav_inventory, Icons.Outlined.Inventory2)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private val topLevelDestinations = listOf(
    Destination.Home,
    Destination.Containers,
    Destination.Inbound,
    Destination.Search,
    Destination.Printer,
    Destination.Settings
)
