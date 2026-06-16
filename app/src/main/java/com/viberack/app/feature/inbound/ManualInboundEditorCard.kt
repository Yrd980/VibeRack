package com.viberack.app.feature.inbound

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.viberack.app.R
import com.viberack.app.core.ui.QuantityOutlinedTextField
import com.viberack.app.core.ui.SourceOutlinedTextField
import com.viberack.app.core.ui.clearFocusOnTapOutside
import com.viberack.app.domain.model.ComponentDetail
import java.io.File

@Composable
fun ManualInboundEditorCard(
    component: ComponentDetail,
    partNumber: String,
    name: String,
    onNameChange: (String) -> Unit,
    brand: String,
    onBrandChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    sourceUrl: String,
    originalSourceUrl: String,
    onSourceUrlChange: (String) -> Unit,
    onImagePreviewClick: () -> Unit,
    specificationText: String,
    onSpecificationTextChange: (String) -> Unit,
    showSpecificationEditor: Boolean,
    errorMessage: String?,
    locationLabel: String,
    onLocationClick: () -> Unit,
    onInboundClick: () -> Unit
) {
    val context = LocalContext.current
    val openableSourceUrl = remember(sourceUrl) { extractOpenableSourceUrl(sourceUrl) }
    val sourceChanged = sourceUrl != originalSourceUrl
    Column(
        modifier = Modifier.clearFocusOnTapOutside(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            ManualInboundImageCard(
                component = component,
                onImagePreviewClick = onImagePreviewClick
            )
            ManualInboundEditableFirstPropertyCard(
                partNumber = partNumber,
                brand = brand,
                onBrandChange = onBrandChange,
                packageName = packageName,
                onPackageNameChange = onPackageNameChange,
                category = category,
                onCategoryChange = onCategoryChange,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        ManualInboundEditableSecondPropertyCard(
            name = name,
            onNameChange = onNameChange,
            specificationText = specificationText,
            onSpecificationTextChange = onSpecificationTextChange,
            showSpecificationEditor = showSpecificationEditor,
            description = description,
            onDescriptionChange = onDescriptionChange
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val originalQuantityText = ""
            QuantityOutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.inbound_quantity_label),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                onDecrease = {
                    val current = quantityText.toIntOrNull() ?: 0
                    onQuantityTextChange((current - 1).coerceAtLeast(0).toString())
                },
                decreaseContentDescription = stringResource(R.string.common_decrease),
                onIncrease = {
                    val current = quantityText.toIntOrNull()
                    onQuantityTextChange(((current ?: 0) + 1).toString())
                },
                increaseContentDescription = stringResource(R.string.common_increase),
                showUndo = quantityText != originalQuantityText,
                onUndo = { onQuantityTextChange(originalQuantityText) },
                undoContentDescription = stringResource(R.string.common_undo)
            )
            SourceOutlinedTextField(
                value = sourceUrl,
                onValueChange = onSourceUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.inventory_source_label),
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Default
                ),
                showUndo = sourceChanged,
                onUndo = { onSourceUrlChange(originalSourceUrl) },
                undoContentDescription = stringResource(R.string.common_undo),
                onValueBlurTransform = ::normalizeSourceValue,
                showOpen = openableSourceUrl != null,
                onOpen = {
                    openableSourceUrl ?: return@SourceOutlinedTextField
                    runCatching {
                        openSourceUrl(context, openableSourceUrl)
                    }.onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.inventory_open_source_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                openContentDescription = stringResource(R.string.inventory_open_source)
            )
        }
        errorMessage?.let { message ->
            ManualInboundErrorCard(message = message)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onLocationClick) {
                Text(text = locationLabel)
            }
            Button(onClick = onInboundClick) {
                Text(text = stringResource(R.string.common_confirm))
            }
        }
    }
}

@Composable
private fun ManualInboundImageCard(
    component: ComponentDetail,
    onImagePreviewClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(168.dp)
            .combinedClickable(
                onClick = onImagePreviewClick,
                onLongClick = onImagePreviewClick
            )
    ) {
        val imageModel = component.imageLocalPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?: component.imageUrl?.takeIf { it.isNotBlank() }
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = component.name ?: component.partNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = component.partNumber.ifBlank { stringResource(R.string.inbound_component_number) },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.inbound_manual_entry_pick_image_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun normalizeSourceValue(value: String): String {
    val normalized = value.trim()
    return extractOpenableSourceUrl(normalized) ?: normalized
}

@Composable
private fun ManualInboundEditableFirstPropertyCard(
    partNumber: String,
    brand: String,
    onBrandChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_number),
                value = partNumber,
                onValueChange = {},
                readOnly = true
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_brand),
                value = brand,
                onValueChange = onBrandChange
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_package),
                value = packageName,
                onValueChange = onPackageNameChange
            )
            HorizontalDivider()
            ManualInboundEditableCell(
                label = stringResource(R.string.inbound_component_category),
                value = category,
                onValueChange = onCategoryChange
            )
        }
    }
}

@Composable
private fun ManualInboundEditableSecondPropertyCard(
    name: String,
    onNameChange: (String) -> Unit,
    specificationText: String,
    onSpecificationTextChange: (String) -> Unit,
    showSpecificationEditor: Boolean,
    description: String,
    onDescriptionChange: (String) -> Unit,
) {
    val specificationEntries = remember(specificationText) {
        parseManualSpecificationEntries(specificationText)
    }
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            ManualInboundEditableRow(
                label = stringResource(R.string.inbound_component_name),
                value = name,
                onValueChange = onNameChange
            )
            if (showSpecificationEditor) {
                specificationEntries.forEachIndexed { index, (key, value) ->
                    HorizontalDivider()
                    ManualInboundEditableRow(
                        label = key,
                        value = value,
                        onValueChange = { newValue ->
                            onSpecificationTextChange(
                                rebuildManualSpecificationText(
                                    specificationEntries.mapIndexed { entryIndex, entry ->
                                        if (entryIndex == index) {
                                            entry.first to newValue
                                        } else {
                                            entry
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
            }
            HorizontalDivider()
            ManualInboundEditableRow(
                label = stringResource(R.string.inbound_component_description),
                value = description,
                onValueChange = onDescriptionChange,
                singleLine = false,
                minLines = 3
            )
        }
    }
}

@Composable
private fun ManualInboundEditableCell(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ManualInboundEditableText(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly
        )
    }
}

@Composable
private fun ManualInboundEditableRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        ManualInboundEditableText(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            minLines = minLines,
            placeholder = placeholder
        )
    }
}

@Composable
private fun ManualInboundEditableText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null
) {
    Box(modifier = modifier) {
        if (value.isBlank() && !placeholder.isNullOrBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            )
        )
    }
}

@Composable
private fun ManualInboundErrorCard(message: String) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.inbound_component_error_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun extractOpenableSourceUrl(value: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty()) {
        return null
    }
    val urlRegex = Regex("""(?i)\bhttps?://[^\s"”」】]+""")
    return urlRegex.find(normalized)?.value?.trim()
}

private fun openSourceUrl(context: Context, rawUrl: String) {
    val normalizedUrl = rawUrl.trim()
    val browserUri: Uri = normalizedUrl.toUri()
    val intent = Intent(Intent.ACTION_VIEW, browserUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching {
        context.startActivity(intent)
    }.isSuccess
    if (!launched) {
        throw IllegalStateException("No activity can handle source url: $normalizedUrl")
    }
}
