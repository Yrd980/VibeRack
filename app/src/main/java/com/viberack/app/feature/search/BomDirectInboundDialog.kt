package com.viberack.app.feature.search

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.viberack.app.R
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.StorageLocation
import com.viberack.app.feature.inbound.MaterialInboundDialog

@Composable
fun BomDirectInboundDialog(
    entry: BomSearchEntry,
    locations: List<StorageLocation>,
    defaultLocationCode: String?,
    onLookup: (String, (BomDirectInboundLookupResult) -> Unit) -> Unit,
    onConfirmInbound: (ComponentDetail, Int, String, (String?) -> Unit) -> Unit,
    onMatchUpdated: (String) -> Unit,
    onDismiss: () -> Unit,
    onViewInventoryItem: (String, String) -> Unit
) {
    val context = LocalContext.current
    var lookupResult by remember(entry) { mutableStateOf(BomDirectInboundLookupResult()) }
    var isLoading by remember(entry) { mutableStateOf(true) }
    val initialQuantityText = remember(entry) { entry.quantity?.toString() ?: "0" }
    var quantityText by remember(entry) { mutableStateOf(initialQuantityText) }
    var selectedLocationCode by remember(entry, locations, defaultLocationCode) {
        mutableStateOf(
            defaultLocationCode
                ?.takeIf { default -> locations.any { it.code == default } }
                ?: locations.firstOrNull()?.code
                ?: ""
        )
    }
    var actionError by remember(entry) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(entry) { mutableStateOf(false) }

    LaunchedEffect(entry) {
        isLoading = true
        lookupResult = BomDirectInboundLookupResult()
        actionError = null
        onLookup(entry.supplierPart.orEmpty()) { result ->
            lookupResult = result
            isLoading = false
        }
    }

    MaterialInboundDialog(
        title = stringResource(R.string.search_bom_direct_inbound),
        component = lookupResult.component,
        isLoading = isLoading,
        loadingText = stringResource(R.string.search_bom_direct_inbound_loading),
        errorMessage = actionError ?: lookupResult.errorMessage,
        existingStockLocations = lookupResult.existingStockLocations,
        quantityText = quantityText,
        quantityEditable = true,
        quantityLabel = stringResource(R.string.search_bom_direct_inbound_quantity),
        onQuantityChange = { quantityText = it.filter(Char::isDigit) },
        quantityShowUndo = quantityText != initialQuantityText,
        onQuantityUndo = { quantityText = initialQuantityText },
        selectedLocationCode = selectedLocationCode,
        availableLocations = locations,
        onLocationSelected = { selectedLocationCode = it },
        onDismiss = onDismiss,
        onConfirm = {
            val component = lookupResult.component ?: return@MaterialInboundDialog
            val quantity = quantityText.toIntOrNull()
            if (quantity == null) {
                actionError = context.getString(R.string.search_bom_direct_inbound_quantity_error)
                return@MaterialInboundDialog
            }
            if (selectedLocationCode.isBlank()) {
                actionError = context.getString(R.string.search_bom_direct_inbound_location_error)
                return@MaterialInboundDialog
            }
            isSubmitting = true
            actionError = null
            onConfirmInbound(component, quantity, selectedLocationCode) { error ->
                isSubmitting = false
                actionError = error
                if (error == null) {
                    onMatchUpdated(component.partNumber)
                    Toast.makeText(
                        context,
                        context.getString(R.string.search_bom_direct_inbound_success, component.partNumber),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        },
        confirmEnabled = !isSubmitting && !isLoading && lookupResult.component != null,
        confirmText = stringResource(R.string.search_bom_direct_inbound_confirm),
        onViewExistingStock = {
            val component = lookupResult.component ?: return@MaterialInboundDialog
            val targetLocation = lookupResult.existingStockLocations.firstOrNull()
                ?: return@MaterialInboundDialog
            onViewInventoryItem(targetLocation.locationCode, component.partNumber)
        }
    )
}
