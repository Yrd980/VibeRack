package com.viberack.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.viberack.app.R
import com.viberack.app.core.ui.MaterialListCard
import java.io.File

@Composable
fun BomBindingDialog(
    entry: BomSearchEntry,
    inventoryResults: List<SearchResultUiModel>,
    onDismiss: () -> Unit,
    onBind: (String) -> Unit
) {
    var query by remember(entry) { mutableStateOf("") }
    val filteredResults = remember(inventoryResults, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            inventoryResults
        } else {
            inventoryResults.filter { result ->
                buildList {
                    add(result.partNumber)
                    result.name?.let(::add)
                    result.mpn?.let(::add)
                    result.brand?.let(::add)
                    result.packageName?.let(::add)
                    result.category?.let(::add)
                }.any { value ->
                    value.trim().lowercase().contains(normalizedQuery)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = { Text(text = stringResource(R.string.search_bom_bind_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = entry.supplierPart?.takeIf { it.isNotBlank() }
                        ?: entry.manufacturerPart?.takeIf { it.isNotBlank() }
                        ?: entry.comment?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.search_bom_bind_dialog_subtitle_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.search_bom_bind_search_label)) },
                    singleLine = true
                )
                if (filteredResults.isEmpty()) {
                    MessageCard(text = stringResource(R.string.search_bom_bind_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredResults, key = { it.partNumber + (it.mpn ?: "") }) { result ->
                            BomBindingCandidateCard(
                                result = result,
                                onBind = { onBind(result.partNumber) }
                            )
                        }
                    }
                }
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

@Composable
private fun BomBindingCandidateCard(
    result: SearchResultUiModel,
    onBind: () -> Unit
) {
    val imageModel = result.imageLocalPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.length() > 0L }
    val secondarySummary = searchResultSecondarySummary(result)

    MaterialListCard(
        title = result.name?.takeIf { it.isNotBlank() }
            ?: result.mpn?.takeIf { it.isNotBlank() }
            ?: result.partNumber,
        subtitle = listOfNotNull(result.brand, result.packageName, result.category).joinToString(" · "),
        secondarySummary = secondarySummary,
        sourceText = result.sourceUrl,
        imageModel = imageModel,
        imageContentDescription = result.name ?: result.partNumber,
        placeholderText = result.partNumber,
        detailContent = {
            Text(
                text = stringResource(R.string.search_part_number, result.partNumber),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        bottomContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onBind) {
                    Text(text = stringResource(R.string.search_bom_bind_confirm))
                }
            }
        }
    )
}
