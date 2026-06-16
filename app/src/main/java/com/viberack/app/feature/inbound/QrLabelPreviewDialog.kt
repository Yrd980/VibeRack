package com.viberack.app.feature.inbound

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viberack.app.R
import com.viberack.app.core.printer.PrinterConnectionState
import com.viberack.app.core.printer.PrinterState
import com.viberack.app.domain.model.ComponentDetail

@Composable
fun QrLabelPreviewDialog(
    component: ComponentDetail,
    bitmap: Bitmap?,
    printerState: PrinterState,
    loading: Boolean,
    saving: Boolean,
    printing: Boolean,
    onDismiss: () -> Unit,
    onWriteNfc: () -> Unit,
    onPrint: (Bitmap) -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val busy = saving || printing
    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                onDismiss()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        title = { Text(text = stringResource(R.string.inbound_manual_print_qr_preview_title)) },
        text = {
            QrLabelPreviewContent(
                component = component,
                bitmap = bitmap,
                printerState = printerState,
                loading = loading,
                printing = printing
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            QrLabelPreviewActions(
                bitmap = bitmap,
                printerState = printerState,
                loading = loading,
                saving = saving,
                printing = printing,
                onWriteNfc = onWriteNfc,
                onPrint = onPrint,
                onSave = onSave
            )
        }
    )
}

@Composable
private fun QrLabelPreviewContent(
    component: ComponentDetail,
    bitmap: Bitmap?,
    printerState: PrinterState,
    loading: Boolean,
    printing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.inbound_manual_print_qr_preview_loading))
            }
        } else {
            bitmap?.let { previewBitmap ->
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = component.name ?: component.partNumber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = if (printerState.connectionState == PrinterConnectionState.CONNECTED) {
                    printerState.connectionSummary
                } else {
                    stringResource(R.string.printer_not_connected)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (printing || printerState.isPrinting) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.printer_print_in_progress))
                }
            }
        }
    }
}

@Composable
private fun QrLabelPreviewActions(
    bitmap: Bitmap?,
    printerState: PrinterState,
    loading: Boolean,
    saving: Boolean,
    printing: Boolean,
    onWriteNfc: () -> Unit,
    onPrint: (Bitmap) -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val bitmapReady = bitmap != null
    val busy = loading || saving || printing
    val printerReady = printerState.connectionState == PrinterConnectionState.CONNECTED &&
        !printerState.isPrinting
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onWriteNfc,
            enabled = !busy && bitmapReady
        ) {
            Text(text = stringResource(R.string.nfc_write_tag))
        }
        TextButton(
            onClick = { bitmap?.let(onPrint) },
            enabled = !busy && bitmapReady && printerReady
        ) {
            Text(text = stringResource(R.string.printer_print_label))
        }
        TextButton(
            onClick = { bitmap?.let(onSave) },
            enabled = !busy && bitmapReady
        ) {
            Text(text = stringResource(R.string.common_save))
        }
    }
}
