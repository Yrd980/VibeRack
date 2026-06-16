package com.viberack.app.feature.inbound

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.core.ui.performCopyFeedback
import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation

@Composable
fun ExistingStockReminderCard(
    existingStockLocations: List<ExistingStockLocation>,
    onViewItem: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3C4)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.inbound_existing_stock_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.inbound_existing_stock_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B4F00)
            )
            existingStockLocations.forEach { stock ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.inbound_existing_stock_item,
                            formatLocationLabel(stock.locationCode, stock.locationDisplayName),
                            stock.quantity
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4A3600),
                        modifier = Modifier.weight(1f)
                    )
                    if (onViewItem != null) {
                        Text(
                            text = stringResource(R.string.inbound_existing_stock_view_item),
                            modifier = Modifier
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .combinedClickable(
                                    onClick = onViewItem,
                                    onLongClick = onViewItem
                                )
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatLocationLabel(code: String, displayName: String?): String {
    val normalizedName = displayName?.trim().orEmpty()
    return if (normalizedName.isNotEmpty() && normalizedName != code) {
        "$code:$normalizedName"
    } else {
        code
    }
}

@Composable
fun ComponentDetailTable(
    component: ComponentDetail
) {
    val firstPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_number) to component.partNumber)
        add(stringResource(R.string.inbound_component_brand) to (component.brand ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_package) to (component.packageName ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_category) to (component.category ?: stringResource(R.string.inbound_field_empty)))
    }

    val secondPropertyRows = buildList {
        add(stringResource(R.string.inbound_component_name) to (component.name ?: stringResource(R.string.inbound_field_empty)))
        component.specifications.forEach { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isNotEmpty() && normalizedValue.isNotEmpty()) {
                add(normalizedKey to normalizedValue)
            }
        }
        add(stringResource(R.string.inbound_component_price) to (component.price?.let { "¥$it" } ?: stringResource(R.string.inbound_field_empty)))
        add(stringResource(R.string.inbound_component_description) to (component.description ?: stringResource(R.string.inbound_field_empty)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyValueGridCard(
            rows = firstPropertyRows
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        KeyValueTableCard(
            rows = secondPropertyRows
        )
    }
}

@Composable
private fun KeyValueGridCard(
    rows: List<Pair<String, String>>
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                KeyValueTableRow(label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun KeyValueTableCard(
    rows: List<Pair<String, String>>
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                KeyValueTableRow(label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun KeyValueTableRow(
    label: String,
    value: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(value))
                    performCopyFeedback(context, hapticFeedback)
                    Toast.makeText(
                        context,
                        context.getString(R.string.common_copied, value),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
        )
    }
}
