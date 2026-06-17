package com.viberack.app.feature.containers

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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.LightbulbCircle
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.ble.smart.SmartChassisProtocol
import com.viberack.app.domain.model.ContainerSlotStock
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.StockContainer

@Composable
fun ContainersRoute(
    openRequest: ContainersOpenRequest? = null,
    openRequestSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VibeRackApplication).appContainer
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
        onFindSlot = { container, slot -> viewModel.findSlot(container, slot.slot.slotNumber) },
        onConfirmRestorePreview = viewModel::confirmRestorePreview,
        onCancelRestorePreview = viewModel::cancelRestorePreview,
        onLightsOff = viewModel::lightsOff,
        onRequestSlotInbound = viewModel::requestSlotInbound,
        onConfirmSlotInbound = viewModel::confirmSlotInbound,
        onCancelSlotInbound = viewModel::cancelSlotInbound,
        onClearSlot = viewModel::clearSlot,
        onSetSlotQuantity = viewModel::setSlotQuantity,
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
        modifier = modifier
    )
}

@Composable
fun ContainersScreen(
    uiState: ContainersUiState,
    onSelectContainer: (StockContainer) -> Unit,
    onConnectSmartChassis: (StockContainer) -> Unit,
    onReadAllSmartChassis: (StockContainer) -> Unit,
    onFindSlot: (StockContainer, ContainerSlotStock) -> Unit,
    onConfirmRestorePreview: () -> Unit,
    onCancelRestorePreview: () -> Unit,
    onLightsOff: () -> Unit,
    onRequestSlotInbound: (StockContainer, ContainerSlotStock) -> Unit,
    onConfirmSlotInbound: (String, Int) -> Unit,
    onCancelSlotInbound: () -> Unit,
    onClearSlot: (StockContainer, ContainerSlotStock) -> Unit,
    onSetSlotQuantity: (StockContainer, ContainerSlotStock, Int) -> Unit,
    onScanSmartChassis: (Boolean) -> Unit,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
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
                    } ?: smartChassisCacheText(container),
                    cacheWarningText = smartChassisCacheWarningText(container),
                    onConnectSmartChassis = { onConnectSmartChassis(container) },
                    onReadAllSmartChassis = { onReadAllSmartChassis(container) },
                    onFindSlot = { slot -> onFindSlot(container, slot) },
                    onLightsOff = onLightsOff,
                    onRequestSlotInbound = { slot -> onRequestSlotInbound(container, slot) },
                    onClearSlot = { slot -> onClearSlot(container, slot) },
                    onSetSlotQuantity = { slot, quantity -> onSetSlotQuantity(container, slot, quantity) },
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
    uiState.restorePreview?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onConfirm = onConfirmRestorePreview,
            onDismiss = onCancelRestorePreview
        )
    }
    uiState.slotInboundRequest?.let { request ->
        SlotInboundDialog(
            request = request,
            onConfirm = onConfirmSlotInbound,
            onDismiss = onCancelSlotInbound
        )
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
    cacheWarningText: String?,
    onConnectSmartChassis: () -> Unit,
    onReadAllSmartChassis: () -> Unit,
    onFindSlot: (ContainerSlotStock) -> Unit,
    onLightsOff: () -> Unit,
    onRequestSlotInbound: (ContainerSlotStock) -> Unit,
    onClearSlot: (ContainerSlotStock) -> Unit,
    onSetSlotQuantity: (ContainerSlotStock, Int) -> Unit,
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
                cacheWarningText = cacheWarningText,
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
            SmartChassisTwinGrid(
                slots = slots,
                activeLightSlot = activeLightSlot,
                onFindSlot = onFindSlot,
                onRequestSlotInbound = onRequestSlotInbound,
                onClearSlot = onClearSlot,
                onSetSlotQuantity = onSetSlotQuantity
            )
            SmartSlotList(
                slots = slots,
                onFindSlot = onFindSlot,
                onRequestSlotInbound = onRequestSlotInbound,
                onClearSlot = onClearSlot,
                onSetSlotQuantity = onSetSlotQuantity
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
    cacheWarningText: String?,
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
        cacheWarningText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
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
private fun SmartChassisTwinGrid(
    slots: List<ContainerSlotStock>,
    activeLightSlot: Int?,
    onFindSlot: (ContainerSlotStock) -> Unit,
    onRequestSlotInbound: (ContainerSlotStock) -> Unit,
    onClearSlot: (ContainerSlotStock) -> Unit,
    onSetSlotQuantity: (ContainerSlotStock, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val slotMap = slots.associateBy { it.slot.slotNumber }
    var menuSlot by remember { mutableStateOf<ContainerSlotStock?>(null) }
    var quantityEditSlot by remember { mutableStateOf<ContainerSlotStock?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.containers_twin_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        (0 until 5).forEach { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..5).forEach { columnIndex ->
                    val slotNumber = rowIndex * 5 + columnIndex
                    val slot = slotMap[slotNumber]
                    if (slot == null) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        SmartTwinSlotCell(
                            slot = slot,
                            active = activeLightSlot == slotNumber,
                            onFindSlot = onFindSlot,
                            onOpenMenu = { menuSlot = slot },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    menuSlot?.let { slot ->
        Box {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuSlot = null }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.containers_slot_find)) },
                    onClick = {
                        menuSlot = null
                        onFindSlot(slot)
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.containers_slot_inbound)) },
                    onClick = {
                        menuSlot = null
                        onRequestSlotInbound(slot)
                    }
                )
                if (slot.stockItem != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.containers_slot_set_quantity)) },
                        onClick = {
                            menuSlot = null
                            quantityEditSlot = slot
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.containers_slot_clear)) },
                        onClick = {
                            menuSlot = null
                            onClearSlot(slot)
                        }
                    )
                }
            }
        }
    }
    quantityEditSlot?.let { slot ->
        SlotQuantityDialog(
            slot = slot,
            onConfirm = { quantity ->
                onSetSlotQuantity(slot, quantity)
                quantityEditSlot = null
            },
            onDismiss = { quantityEditSlot = null }
        )
    }
}

@Composable
private fun SmartTwinSlotCell(
    slot: ContainerSlotStock,
    active: Boolean,
    onFindSlot: (ContainerSlotStock) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stock = slot.stockItem
    val background = when {
        active -> MaterialTheme.colorScheme.tertiaryContainer
        stock?.isAtOrBelowSafetyStock == true -> MaterialTheme.colorScheme.errorContainer
        stock != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        active -> MaterialTheme.colorScheme.onTertiaryContainer
        stock?.isAtOrBelowSafetyStock == true -> MaterialTheme.colorScheme.onErrorContainer
        stock != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onFindSlot(slot) },
        shape = RoundedCornerShape(8.dp),
        color = background,
        tonalElevation = if (active) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "%02d".format(slot.slot.slotNumber),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = foreground
                )
                Text(
                    text = "⋮",
                    modifier = Modifier.clickable(onClick = onOpenMenu),
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground
                )
            }
            Text(
                text = stock?.protocolPartId ?: stringResource(R.string.containers_slot_empty),
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stock?.quantity?.toString().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                maxLines = 1
            )
        }
    }
}

private fun ContainerType.label(): String {
    return when (this) {
        ContainerType.LEGACY_LOCATION -> "库位"
        ContainerType.BOX -> "盒子"
        ContainerType.SMART_CHASSIS -> "智能底盘"
    }
}

private fun smartChassisCacheText(container: StockContainer): String? {
    if (container.type != ContainerType.SMART_CHASSIS) {
        return null
    }
    val tableSeq = container.tableSeq
    val tableCrc16 = container.tableCrc16
    return when {
        tableSeq != null && tableCrc16 != null -> "缓存 seq $tableSeq / crc $tableCrc16"
        tableSeq != null -> "广播 seq low16 ${tableSeq and 0xFFFF}"
        else -> "未校验绑定表"
    }
}

private fun smartChassisCacheWarningText(container: StockContainer): String? {
    if (container.type != ContainerType.SMART_CHASSIS) {
        return null
    }
    return when {
        container.isSmartChassisCachePossiblyStale -> "绑定表可能已变化，请连接后读表校验"
        container.tableSeq == null || container.tableCrc16 == null -> "尚未读取完整绑定表"
        else -> null
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
