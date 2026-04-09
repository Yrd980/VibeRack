package com.example.lcsc_android_erp.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.lcsc_android_erp.R

data class LocationPickerOption(
    val code: String,
    val displayName: String?,
    val colorHex: String?
)

@Composable
fun LocationPickerDialog(
    title: String,
    options: List<LocationPickerOption>,
    selectedCode: String,
    currentOption: LocationPickerOption? = null,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedOptions = options
        .sortedWith(
            compareBy<LocationPickerOption>(
                { it.code.firstOrNull()?.uppercaseChar()?.code ?: Int.MAX_VALUE },
                { it.code.dropWhile(Char::isLetter).toIntOrNull() ?: Int.MAX_VALUE },
                { it.code }
            )
        )
        .groupBy { it.code.takeWhile(Char::isLetter).ifBlank { "#" } }
        .toList()
        .sortedBy { it.first }
    val targetCode = currentOption?.code ?: selectedCode
    val targetRowIndex = groupedOptions.indexOfFirst { row ->
        row.second.any { option -> option.code == targetCode }
    }.coerceAtLeast(0)
    val verticalListState = rememberLazyListState()

    LaunchedEffect(targetCode, groupedOptions) {
        if (groupedOptions.isNotEmpty()) {
            verticalListState.scrollToItem(targetRowIndex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(text = title) },
        text = {
            LazyColumn(
                state = verticalListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(groupedOptions, key = { _, row -> row.first }) { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = row.first,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f)
                        ) {
                            val horizontalListState = rememberLazyListState()
                            val targetColumnIndex = row.second.indexOfFirst { option ->
                                option.code == targetCode
                            }

                            LaunchedEffect(targetCode, rowIndex, row.second) {
                                if (targetRowIndex == rowIndex && targetColumnIndex >= 0) {
                                    horizontalListState.scrollToItem(targetColumnIndex)
                                }
                            }

                            LazyRow(
                                state = horizontalListState,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(row.second, key = { _, option -> option.code }) { _, option ->
                                LocationPickerCard(
                                    option = option,
                                    selected = option.code == selectedCode,
                                    onClick = { onSelect(option.code) }
                                )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = null,
        confirmButton = {}
    )
}

@Composable
private fun LocationPickerCard(
    option: LocationPickerOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = parseLocationPickerColor(option.colorHex)
    val contentColor = if (backgroundColor.luminance() > 0.6f) Color.Black else Color.White

    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 128.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = option.displayName?.takeIf { it.isNotBlank() } ?: option.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                softWrap = false
            )
            Text(
                text = option.code,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.88f),
                softWrap = false,
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}

@Composable
private fun parseLocationPickerColor(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    return try {
        if (colorHex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
