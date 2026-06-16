package com.viberack.app.feature.inventory

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.viberack.app.R
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.feature.inbound.MaterialInboundDialog
import com.viberack.app.feature.inbound.ScannerCard
import java.io.File

@Composable
fun LocationScanAddDialog(
    locationCode: String,
    onDismiss: () -> Unit,
    onLookupScannedComponent: (String, (InventoryScanLookupResult) -> Unit) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String?) -> Unit,
    onViewExistingStock: (String, String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var scannerPaused by rememberSaveable { mutableStateOf(false) }
    var lookupInProgress by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<InventoryScanLookupResult?>(null) }
    val initialQuantityText = remember(scanResult?.rawPayload, scanResult?.quantity) {
        scanResult?.quantity
            ?.takeIf { it > 0 }
            ?.toString()
            .orEmpty()
    }
    var quantityText by remember(scanResult?.rawPayload, scanResult?.quantity) {
        mutableStateOf(initialQuantityText)
    }

    if (lookupInProgress || scanResult != null || scanErrorMessage != null) {
        MaterialInboundDialog(
            title = stringResource(R.string.inventory_location_scan_add),
            component = scanResult?.component,
            isLoading = lookupInProgress,
            loadingText = stringResource(R.string.inbound_component_loading),
            errorMessage = scanErrorMessage,
            existingStockLocations = scanResult?.existingStockLocations.orEmpty(),
            quantityText = quantityText,
            quantityEditable = true,
            quantityLabel = stringResource(R.string.inbound_quantity_label),
            onQuantityChange = { quantityText = it.filter(Char::isDigit) },
            quantityShowUndo = quantityText != initialQuantityText,
            onQuantityUndo = { quantityText = initialQuantityText },
            selectedLocationCode = locationCode,
            availableLocations = emptyList(),
            onLocationSelected = {},
            onDismiss = onDismiss,
            onConfirm = {
                scanResult?.component?.let { component ->
                    onConfirmInbound(
                        component,
                        quantityText.toIntOrNull() ?: 0,
                        scanResult?.rawPayload
                    )
                }
            },
            confirmEnabled = scanResult?.component != null && !lookupInProgress,
            confirmText = stringResource(R.string.common_confirm),
            locationPickerEnabled = false,
            selectedLocationLabelOverride = locationCode,
            onViewExistingStock = {
                val component = scanResult?.component ?: return@MaterialInboundDialog
                val targetLocation = scanResult?.existingStockLocations?.firstOrNull()
                    ?: return@MaterialInboundDialog
                onViewExistingStock(targetLocation.locationCode, component.partNumber)
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.inventory_location_scan_add)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.inventory_location_scan_target, locationCode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ScannerCard(
                        hasCameraPermission = hasCameraPermission,
                        scannerPaused = scannerPaused,
                        onQrScanned = { rawText ->
                            if (lookupInProgress || scanResult != null) {
                                return@ScannerCard
                            }
                            scannerPaused = true
                            lookupInProgress = true
                            onLookupScannedComponent(rawText) { result ->
                                lookupInProgress = false
                                if (result.component != null) {
                                    scanResult = result
                                    scanErrorMessage = null
                                } else {
                                    scannerPaused = false
                                    scanResult = null
                                    scanErrorMessage = result.errorMessage
                                }
                            }
                        },
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun LocationInventoryItemCard(
    item: LocationInventoryItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imageModel = item.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
        ?: item.imageUrl?.takeIf { it.isNotBlank() }
    val secondarySummary = locationItemSecondarySummary(item)
    val hasTaobaoSource = remember(item.sourceUrl) { inventoryCardHasTaobaoSource(item.sourceUrl) }

    Card(
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onClick()
                    },
                    onLongClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onLongClick()
                    }
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "x${displayQuantity(item.quantity)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (hasTaobaoSource) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset { IntOffset(x = -4.dp.roundToPx(), y = -4.dp.roundToPx()) }
                                .size(14.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(Color(0xFFFF6A00))
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name ?: item.mpn ?: item.partNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listOfNotNull(item.brand, item.packageName, item.category).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    secondarySummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.inventory_item_quantity, displayQuantity(item.quantity)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun inventoryCardHasTaobaoSource(sourceText: String?): Boolean {
    val normalized = sourceText?.trim()?.lowercase().orEmpty()
    return !normalized.startsWith("https://item.szlcsc.com")
}

@Composable
private fun displayQuantity(quantity: Int): String {
    return if (quantity == 0) stringResource(R.string.inventory_unknown_quantity) else quantity.toString()
}

private fun locationItemSecondarySummary(item: LocationInventoryItem): String? {
    val preferredKeys = listOf("电阻类型", "阻值", "精度", "功率")
    val specificationSummary = buildList {
        preferredKeys.forEach { key ->
            item.specifications[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
        item.specifications
            .filterKeys { it !in preferredKeys }
            .toSortedMap()
            .values
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .forEach(::add)
    }.distinct().joinToString(" · ")

    return specificationSummary.takeIf { it.isNotBlank() }
        ?: item.mpn?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.description?.trim()?.takeIf { it.isNotEmpty() }
}
