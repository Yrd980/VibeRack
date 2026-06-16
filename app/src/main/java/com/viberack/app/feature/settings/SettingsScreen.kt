package com.viberack.app.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viberack.app.VibeRackApplication
import com.viberack.app.R
import com.viberack.app.core.datastore.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GITHUB_URL = "https://github.com/BrokenClient/viberack_app"
private const val GITEE_URL = "https://gitee.com/BrokenClient/viberack_app"

@Composable
fun SettingsRoute(
    onOpenHardwareRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appContainer = (LocalContext.current.applicationContext as VibeRackApplication).appContainer
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(appContainer)
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        modifier = modifier,
        uiState = uiState.value,
        onLanguageSelected = viewModel::onLanguageSelected,
        onExportInventory = viewModel::exportInventory,
        onImportInventory = viewModel::importInventory,
        onOpenHardwareRestore = onOpenHardwareRestore,
        onClearInventoryBackupMessage = viewModel::clearInventoryBackupMessage
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLanguageSelected: (String) -> Unit,
    onExportInventory: (android.net.Uri) -> Unit,
    onImportInventory: (android.net.Uri) -> Unit,
    onOpenHardwareRestore: () -> Unit,
    onClearInventoryBackupMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        uri?.let(onExportInventory)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportInventory)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = uiState.content.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column {
                SettingsActionRow(
                    title = uiState.content.languageTitle,
                    subtitle = when (uiState.selectedLanguageTag) {
                        UserPreferencesRepository.LANGUAGE_EN -> uiState.content.languageEnglish
                        else -> uiState.content.languageChinese
                    },
                    onClick = { showLanguageDialog = true }
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = uiState.content.aboutTitle,
                    subtitle = uiState.content.aboutSummary,
                    onClick = { showAboutDialog = true }
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = uiState.content.exportInventoryTitle,
                    subtitle = uiState.content.exportInventorySummary,
                    onClick = { exportLauncher.launch(buildInventoryExportFileName()) },
                    enabled = !uiState.isProcessingInventoryBackup
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = uiState.content.importInventoryTitle,
                    subtitle = uiState.content.importInventorySummary,
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "*/*"
                            )
                        )
                    },
                    enabled = !uiState.isProcessingInventoryBackup
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = stringResource(R.string.settings_hardware_restore),
                    subtitle = stringResource(R.string.settings_hardware_restore_summary),
                    onClick = onOpenHardwareRestore,
                    enabled = !uiState.isProcessingInventoryBackup
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            content = uiState.content,
            selectedLanguageTag = uiState.selectedLanguageTag,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { languageTag ->
                onLanguageSelected(languageTag)
                showLanguageDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            content = uiState.content,
            onDismiss = { showAboutDialog = false }
        )
    }

    uiState.inventoryBackupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onClearInventoryBackupMessage,
            title = { Text(text = uiState.content.inventoryBackupTitle) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = onClearInventoryBackupMessage) {
                    Text(text = stringResource(R.string.common_confirm))
                }
            }
        )
    }
}

private fun buildInventoryExportFileName(now: Date = Date()): String {
    val dateSuffix = SimpleDateFormat("MMdd", Locale.ROOT).format(now)
    return "lcsc_inventory_backup_$dateSuffix.xlsx"
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    content: SettingsContent,
    selectedLanguageTag: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = content.languageTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageOptionRow(
                    label = content.languageChinese,
                    selected = selectedLanguageTag == UserPreferencesRepository.LANGUAGE_ZH,
                    onClick = { onLanguageSelected(UserPreferencesRepository.LANGUAGE_ZH) }
                )
                LanguageOptionRow(
                    label = content.languageEnglish,
                    selected = selectedLanguageTag == UserPreferencesRepository.LANGUAGE_EN,
                    onClick = { onLanguageSelected(UserPreferencesRepository.LANGUAGE_EN) }
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

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun AboutDialog(
    content: SettingsContent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(text = content.aboutTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = content.aboutBody,
                    style = MaterialTheme.typography.bodyMedium
                )
                StaticLinkText(
                    label = "github",
                    url = GITHUB_URL,
                    onClick = { openExternalUrl(context, GITHUB_URL) }
                )
                StaticLinkText(
                    label = "gitee",
                    url = GITEE_URL,
                    onClick = { openExternalUrl(context, GITEE_URL) }
                )
                if (content.stackBody.isNotBlank()) {
                    Text(
                        text = content.stackBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_confirm))
            }
        }
    )
}

@Composable
private fun StaticLinkText(
    label: String,
    url: String,
    onClick: () -> Unit
) {
    Text(
        text = "$label: $url",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun openExternalUrl(context: android.content.Context, rawUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(rawUrl)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
