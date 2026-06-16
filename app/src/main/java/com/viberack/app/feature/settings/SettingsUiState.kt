package com.viberack.app.feature.settings

data class SettingsUiState(
    val selectedLanguageTag: String = "zh",
    val isProcessingInventoryBackup: Boolean = false,
    val inventoryBackupMessage: String? = null,
    val content: SettingsContent = SettingsContent(
        title = "",
        languageTitle = "",
        languageChinese = "",
        languageEnglish = "",
        aboutTitle = "",
        aboutSummary = "",
        inventoryBackupTitle = "",
        exportInventoryTitle = "",
        exportInventorySummary = "",
        importInventoryTitle = "",
        importInventorySummary = "",
        aboutBody = "",
        stackBody = ""
    )
)
