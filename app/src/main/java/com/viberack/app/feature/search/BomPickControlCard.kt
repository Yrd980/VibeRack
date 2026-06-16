package com.viberack.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viberack.app.R

@Composable
fun BomPickControlCard(
    session: BomPickSessionUiModel?,
    message: String?,
    isBusy: Boolean,
    hasTargets: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.search_bom_pick_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = session?.let {
                    stringResource(
                        R.string.search_bom_pick_active_summary,
                        it.slotCount,
                        it.groups.size
                    )
                } ?: stringResource(R.string.search_bom_pick_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session?.groups?.forEach { group ->
                Text(
                    text = stringResource(
                        R.string.search_bom_pick_group,
                        group.containerCode,
                        group.slots.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            message?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (session == null) {
                    Button(
                        onClick = onStart,
                        enabled = hasTargets && !isBusy
                    ) {
                        Text(text = stringResource(R.string.search_bom_pick_start))
                    }
                } else {
                    TextButton(
                        onClick = onCancel,
                        enabled = !isBusy
                    ) {
                        Text(text = stringResource(R.string.search_bom_pick_cancel))
                    }
                }
            }
        }
    }
}
