package com.viberack.app.feature.settings

import android.content.Context
import com.viberack.app.R
import org.json.JSONObject

object SettingsContentLoader {
    @Volatile
    private var cachedRoot: JSONObject? = null

    fun load(context: Context, languageTag: String): SettingsContent {
        val root = cachedRoot ?: synchronized(this) {
            cachedRoot ?: runCatching {
                context.assets.open("settings_content.json").use { input ->
                    JSONObject(input.bufferedReader().readText())
                }
            }.getOrNull().also { cachedRoot = it }
        }

        val section = root?.optJSONObject(normalizeLanguageTag(languageTag))
            ?: root?.optJSONObject("zh")

        return SettingsContent(
            title = section?.optString("title").orFallback(context.getString(R.string.settings_title)),
            languageTitle = section?.optString("languageTitle").orFallback(context.getString(R.string.settings_language)),
            languageChinese = section?.optString("languageChinese").orFallback(context.getString(R.string.settings_language_chinese)),
            languageEnglish = section?.optString("languageEnglish").orFallback(context.getString(R.string.settings_language_english)),
            aboutTitle = section?.optString("aboutTitle").orFallback(context.getString(R.string.settings_about)),
            aboutSummary = section?.optString("aboutSummary").orFallback(context.getString(R.string.settings_about_summary)),
            inventoryBackupTitle = section?.optString("inventoryBackupTitle").orFallback(context.getString(R.string.settings_inventory_backup)),
            exportInventoryTitle = section?.optString("exportInventoryTitle").orFallback(context.getString(R.string.settings_export_inventory)),
            exportInventorySummary = section?.optString("exportInventorySummary").orFallback(context.getString(R.string.settings_export_inventory_summary)),
            importInventoryTitle = section?.optString("importInventoryTitle").orFallback(context.getString(R.string.settings_import_inventory)),
            importInventorySummary = section?.optString("importInventorySummary").orFallback(context.getString(R.string.settings_import_inventory_summary)),
            aboutBody = section?.optString("aboutBody").orFallback(context.getString(R.string.settings_about_body)),
            stackBody = section?.optString("stackBody").orFallback(context.getString(R.string.settings_stack_body))
        )
    }

    private fun normalizeLanguageTag(languageTag: String): String {
        return when {
            languageTag.startsWith("en", ignoreCase = true) -> "en"
            else -> "zh"
        }
    }

    private fun String?.orFallback(fallback: String): String {
        return this?.takeIf { it.isNotBlank() } ?: fallback
    }
}
