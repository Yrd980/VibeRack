package com.viberack.app.feature.settings

data class SettingsContent(
    val title: String,
    val languageTitle: String,
    val languageChinese: String,
    val languageEnglish: String,
    val aboutTitle: String,
    val aboutSummary: String,
    val inventoryBackupTitle: String,
    val exportInventoryTitle: String,
    val exportInventorySummary: String,
    val importInventoryTitle: String,
    val importInventorySummary: String,
    val aboutBody: String,
    val stackBody: String
)
