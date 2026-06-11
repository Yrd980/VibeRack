package com.example.lcsc_android_erp.feature.containers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightbulbCircle
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lcsc_android_erp.LcscApplication
import com.example.lcsc_android_erp.R
import com.example.lcsc_android_erp.core.ble.smart.SmartChassisProtocol
import com.example.lcsc_android_erp.domain.model.ContainerSlotStock
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.StockContainer

@Composable
fun ContainersRoute(
    openRequest: ContainersOpenRequest? = null,
    openRequestSignal: Int = 0,
    onOpenBoxes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as LcscApplication).appContainer
    val viewModel: ContainersViewModel = viewModel(
        factory = ContainersViewModel.factory(appContainer)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetoothPermissions = rememberSmartChassisBluetoothPermissions()
    var hasBluetoothPermission by remember(bluetoothPermissions) {
        mutableStateOf(hasSmartChassisBluetoothPermission(context, bluetoothPermissions))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBluetoothPermission = hasSmartChassisBluetoothPermission(context, bluetoothPermissions)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    LaunchedEffect(openRequestSignal) {
        openRequest?.let(viewModel::openRequest)
    }

    ContainersScreen(
        uiState = uiState,
        onSelectContainer = viewModel::selectContainer,
        onConnectSmartChassis = viewModel::connectSmartChassis,
        onReadAllSmartChassis = viewModel::readAllSmartChassis,
        onLightsOff = viewModel::lightsOff,
        onScanSmartChassis = viewModel::scanSmartChassis,
        hasBluetoothPermission = hasBluetoothPermission,
        onRequestBluetoothPermission = {
            if (bluetoothPermissions.isNotEmpty()) {
                permissionLauncher.launch(bluetoothPermissions)
            }
        },
        onEnableBluetooth = {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
        onOpenBoxes = onOpenBoxes,
        modifier = modifier
    )
}

@Composable
fun ContainersScreen(
    uiState: ContainersUiState,
    onSelectContainer: (StockContainer) -> Unit,
    onConnectSmartChassis: (StockContainer) -> Unit,
    onReadAllSmartChassis: (StockContainer) -> Unit,
    onLightsOff: () -> Unit,
    onScanSmartChassis: (Boolean) -> Unit,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBoxes: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ContainersHeader(
                isScanning = uiState.isScanning,
                discoveredCount = uiState.discoveredCount,
                scanError = uiState.scanError,
                hasBluetoothPermission = hasBluetoothPermission,
                onScanSmartChassis = { onScanSmartChassis(hasBluetoothPermission) },
                onRequestBluetoothPermission = onRequestBluetoothPermission,
                onOpenBoxes = onOpenBoxes
            )
        }
        if (uiState.containers.isEmpty()) {
            item {
                StatusCard(text = stringResource(R.string.containers_empty))
            }
        } else {
            items(
                items = uiState.containers,
                key = { container -> "container-${container.id}" }
            ) { container ->
                ContainerCard(
                    container = container,
                    selected = uiState.selectedContainer?.id == container.id,
                    onClick = { onSelectContainer(container) }
                )
            }
        }
        uiState.selectedContainer?.let { container ->
            item {
                ContainerDetail(
                    container = container,
                    slots = uiState.selectedSlots,
                    activeLightSlot = uiState.activeLightSlot,
                    connectionText = uiState.connectionState.message,
                    tableInfoText = uiState.activeTableInfo?.let { tableInfo ->
                        "seq ${tableInfo.tableSeq} / crc ${tableInfo.crc16}"
                    },
                    onConnectSmartChassis = { onConnectSmartChassis(container) },
                    onReadAllSmartChassis = { onReadAllSmartChassis(container) },
                    onLightsOff = onLightsOff,
                    hasBluetoothPermission = hasBluetoothPermission,
                    onRequestBluetoothPermission = onRequestBluetoothPermission,
                    onEnableBluetooth = onEnableBluetooth
                )
            }
        }
        uiState.message?.takeIf { it.isNotBlank() }?.let { message ->
            item {
                StatusCard(text = message)
            }
        }
        uiState.lastOperationError?.let { error ->
            item {
                StatusCard(
                    text = error.message,
                    isError = true
                )
            }
        }
    }
}

@Composable
private fun ContainersHeader(
    isScanning: Boolean,
    discoveredCount: Int,
    scanError: String?,
    hasBluetoothPermission: Boolean,
    onScanSmartChassis: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onOpenBoxes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.containers_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.containers_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onOpenBoxes,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.containers_open_boxes),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasBluetoothPermission) {
                Button(
                    onClick = onRequestBluetoothPermission,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(text = stringResource(R.string.containers_request_bluetooth))
                }
            }
            Button(
                onClick = onScanSmartChassis,
                enabled = hasBluetoothPermission && !isScanning,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.containers_scanning)
                    } else {
                        stringResource(R.string.containers_scan)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isScanning || discoveredCount > 0 || scanError != null) {
            Text(
                text = scanError ?: stringResource(R.string.containers_scan_summary, discoveredCount),
                style = MaterialTheme.typography.bodySmall,
                color = if (scanError == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun ContainerCard(
    container: StockContainer,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = container.displayName?.takeIf { it.isNotBlank() } ?: container.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = container.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(text = container.type.label()) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.containers_slot_count, container.slotCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (container.type == ContainerType.SMART_CHASSIS) {
                    Text(
                        text = stringResource(
                            R.string.containers_battery,
                            container.batteryPct ?: 0
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.containers_table_seq,
                            container.tableSeq ?: container.tableCrc16 ?: 0
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerDetail(
    container: StockContainer,
    slots: List<ContainerSlotStock>,
    activeLightSlot: Int?,
    connectionText: String?,
    tableInfoText: String?,
    onConnectSmartChassis: () -> Unit,
    onReadAllSmartChassis: () -> Unit,
    onLightsOff: () -> Unit,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val containerTitle = container.displayName?.takeIf { it.isNotBlank() } ?: container.code
        Text(
            text = stringResource(R.string.containers_detail_title, containerTitle),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (container.type == ContainerType.SMART_CHASSIS) {
            SmartChassisActions(
                connectionText = connectionText,
                tableInfoText = tableInfoText,
                onConnect = onConnectSmartChassis,
                onReadAll = onReadAllSmartChassis,
                onLightsOff = onLightsOff,
                hasBluetoothPermission = hasBluetoothPermission,
                onRequestBluetoothPermission = onRequestBluetoothPermission,
                onEnableBluetooth = onEnableBluetooth
            )
            SmartChassisLightStrip(
                slots = slots,
                activeLightSlot = activeLightSlot
            )
        } else {
            SlotList(slots = slots)
        }
    }
}

@Composable
private fun SmartChassisActions(
    connectionText: String?,
    tableInfoText: String?,
    onConnect: () -> Unit,
    onReadAll: () -> Unit,
    onLightsOff: () -> Unit,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasBluetoothPermission) {
                Button(
                    onClick = onRequestBluetoothPermission,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.containers_request_bluetooth),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                enabled = hasBluetoothPermission,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LightbulbCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.containers_connect),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onReadAll,
                modifier = Modifier.weight(1f),
                enabled = hasBluetoothPermission,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.containers_read_all),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onLightsOff,
                enabled = hasBluetoothPermission,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = stringResource(R.string.containers_lights_off),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        OutlinedButton(
            onClick = onEnableBluetooth,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(text = stringResource(R.string.containers_enable_bluetooth))
        }
        connectionText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        tableInfoText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmartChassisLightStrip(
    slots: List<ContainerSlotStock>,
    activeLightSlot: Int?,
    modifier: Modifier = Modifier
) {
    val slotMap = slots.associateBy { it.slot.slotNumber }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..SmartChassisProtocol.SLOT_COUNT).forEach { slotNumber ->
            val stockItem = slotMap[slotNumber]?.stockItem
            val active = activeLightSlot == slotNumber
            val color = when {
                active -> MaterialTheme.colorScheme.tertiary
                stockItem?.isAtOrBelowSafetyStock == true -> MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                stockItem != null -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.62f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(percent = 50),
                color = color,
                tonalElevation = if (active) 4.dp else 0.dp
            ) {}
        }
    }
}

@Composable
private fun SlotList(
    slots: List<ContainerSlotStock>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (slots.isEmpty()) {
            StatusCard(text = stringResource(R.string.containers_no_slots))
        } else {
            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slot.slot.displayName?.takeIf { it.isNotBlank() }
                                ?: slot.slot.slotCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = slot.stockItem?.partNumber
                                ?: stringResource(R.string.containers_slot_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = slot.stockItem?.quantity?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val background = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun ContainerType.label(): String {
    return when (this) {
        ContainerType.LEGACY_LOCATION -> "库位"
        ContainerType.BOX -> "盒子"
        ContainerType.SMART_CHASSIS -> "智能底盘"
    }
}

@Composable
private fun rememberSmartChassisBluetoothPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        emptyArray()
    }
}

private fun hasSmartChassisBluetoothPermission(
    context: android.content.Context,
    permissions: Array<String>
): Boolean {
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
