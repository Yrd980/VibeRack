package com.viberack.app.feature.inbound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viberack.app.R

@Composable
fun ScannerCard(
    hasCameraPermission: Boolean,
    scannerPaused: Boolean,
    torchEnabled: Boolean = false,
    onTorchAvailabilityChanged: (Boolean) -> Unit = {},
    onQrScanned: (String) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    fullBleed: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fullBleed) {
                    Modifier
                } else {
                    Modifier.clip(MaterialTheme.shapes.large)
                }
            )
    ) {
        if (hasCameraPermission) {
            QrScannerPreview(
                modifier = Modifier.fillMaxSize(),
                enabled = !scannerPaused,
                torchEnabled = torchEnabled,
                onTorchAvailabilityChanged = onTorchAvailabilityChanged,
                onQrCodeDetected = onQrScanned
            )
            CameraGuideOverlay()
        } else {
            PermissionPlaceholder(onRequestPermission = onRequestPermission)
        }
    }
}

@Composable
private fun CameraGuideOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(180.dp)
        ) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = size.minDimension * 0.22f
            val maxX = size.width
            val maxY = size.height

            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(cornerLength, 0f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(0f, cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(maxX - cornerLength, 0f),
                end = Offset(maxX, 0f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(maxX, 0f),
                end = Offset(maxX, cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(0f, maxY - cornerLength),
                end = Offset(0f, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, maxY),
                end = Offset(cornerLength, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color.White,
                start = Offset(maxX - cornerLength, maxY),
                end = Offset(maxX, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(maxX, maxY - cornerLength),
                end = Offset(maxX, maxY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.inbound_camera_permission_needed),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = stringResource(R.string.inbound_grant_permission))
        }
    }
}

@Composable
fun ScanStatusCard(
    uiState: InboundUiState
) {
    val statusText = when {
        uiState.isLoadingComponent -> stringResource(R.string.inbound_status_loading_component)
        uiState.componentDetail != null -> stringResource(R.string.inbound_status_success)
        uiState.componentLookupError != null -> uiState.componentLookupError
        uiState.parseError != null -> uiState.parseError
        else -> stringResource(R.string.inbound_status_waiting)
    }

    PayloadFieldCard(
        title = stringResource(R.string.inbound_status_title),
        value = buildString {
            append(statusText)
            if (!uiState.lastRawText.isNullOrBlank()) {
                append("\n")
                append(uiState.lastRawText)
            }
        }
    )
}

@Composable
fun PayloadFieldCard(
    title: String,
    value: String
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
