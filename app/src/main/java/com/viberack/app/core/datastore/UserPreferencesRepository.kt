package com.viberack.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val defaultLocationCode: String? = null,
    val appLanguageTag: String = UserPreferencesRepository.LANGUAGE_ZH,
    val recentManualSearches: List<String> = emptyList(),
    val bomPartBindings: Map<String, String> = emptyMap(),
    val recentLocationColors: List<String> = emptyList(),
    val printerType: String = UserPreferencesRepository.PRINTER_TYPE_AUTO
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            defaultLocationCode = preferences[DEFAULT_LOCATION_CODE],
            appLanguageTag = preferences[APP_LANGUAGE_TAG] ?: LANGUAGE_ZH,
            recentManualSearches = parseRecentManualSearches(preferences[RECENT_MANUAL_SEARCHES]),
            bomPartBindings = parseBomPartBindings(preferences[BOM_PART_BINDINGS]),
            recentLocationColors = parseRecentLocationColors(
                value = preferences[RECENT_LOCATION_COLORS],
                usePresetFallback = true
            ),
            printerType = preferences[PRINTER_TYPE]
                ?.takeIf { it in SUPPORTED_PRINTER_TYPES }
                ?: PRINTER_TYPE_AUTO
        )
    }

    suspend fun setDefaultLocation(code: String?) {
        dataStore.edit { preferences ->
            if (code.isNullOrBlank()) {
                preferences -= DEFAULT_LOCATION_CODE
            } else {
                preferences[DEFAULT_LOCATION_CODE] = code
            }
        }
    }

    suspend fun setAppLanguage(languageTag: String) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_TAG] = languageTag
        }
    }

    suspend fun addRecentManualSearch(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            return
        }

        dataStore.edit { preferences ->
            val current = parseRecentManualSearches(preferences[RECENT_MANUAL_SEARCHES])
            val updated = buildList {
                add(normalized)
                current.forEach { item ->
                    if (item.equals(normalized, ignoreCase = true).not()) {
                        add(item)
                    }
                }
            }.take(5)
            preferences[RECENT_MANUAL_SEARCHES] = updated.joinToString("\n")
        }
    }

    suspend fun setBomPartBinding(supplierPart: String, localPartNumber: String) {
        val normalizedSupplierPart = supplierPart.trim().uppercase()
        val normalizedLocalPartNumber = localPartNumber.trim().uppercase()
        if (normalizedSupplierPart.isBlank() || normalizedLocalPartNumber.isBlank()) {
            return
        }
        dataStore.edit { preferences ->
            val updated = parseBomPartBindings(preferences[BOM_PART_BINDINGS]).toMutableMap()
            updated[normalizedSupplierPart] = normalizedLocalPartNumber
            preferences[BOM_PART_BINDINGS] = serializeBomPartBindings(updated)
        }
    }

    suspend fun setPrinterType(printerType: String) {
        val normalizedType = when (printerType.trim().lowercase()) {
            PRINTER_TYPE_DELI_Q5 -> PRINTER_TYPE_DELI_Q5
            PRINTER_TYPE_YINLIFANG_P0 -> PRINTER_TYPE_YINLIFANG_P0
            else -> PRINTER_TYPE_AUTO
        }
        dataStore.edit { preferences ->
            preferences[PRINTER_TYPE] = normalizedType
        }
    }

    suspend fun addRecentLocationColor(colorHex: String) {
        val normalized = normalizeLocationColor(colorHex) ?: return
        dataStore.edit { preferences ->
            val current = parseRecentLocationColors(
                value = preferences[RECENT_LOCATION_COLORS],
                usePresetFallback = true
            )
            val updated = buildList {
                add(normalized)
                current.forEach { item ->
                    if (!item.equals(normalized, ignoreCase = true)) {
                        add(item)
                    }
                }
            }.take(5)
            preferences[RECENT_LOCATION_COLORS] = updated.joinToString("\n")
        }
    }

    suspend fun setRecentLocationColors(colors: List<String>) {
        val normalized = colors
            .mapNotNull(::normalizeLocationColor)
            .distinct()
            .take(5)
        dataStore.edit { preferences ->
            if (normalized.isEmpty()) {
                preferences -= RECENT_LOCATION_COLORS
            } else {
                preferences[RECENT_LOCATION_COLORS] = normalized.joinToString("\n")
            }
        }
    }

    private fun parseRecentManualSearches(value: String?): List<String> {
        return value
            ?.split('\n')
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private fun parseRecentLocationColors(
        value: String?,
        usePresetFallback: Boolean = false
    ): List<String> {
        val parsed = value
            ?.split('\n')
            ?.mapNotNull(::normalizeLocationColor)
            ?.distinct()
            ?.take(5)
            .orEmpty()
        return if (parsed.isEmpty() && usePresetFallback) {
            PRESET_LOCATION_COLORS
        } else {
            parsed
        }
    }

    private fun normalizeLocationColor(colorHex: String): String? {
        val normalized = colorHex.trim().uppercase()
        return normalized.takeIf { it.matches(Regex("^#[0-9A-F]{6}$")) }
    }

    private fun parseBomPartBindings(value: String?): Map<String, String> {
        return value
            ?.split('\n')
            ?.mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                val key = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }
                val mappedValue = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                if (key == null || mappedValue == null) null else key to mappedValue
            }
            ?.toMap()
            .orEmpty()
    }

    private fun serializeBomPartBindings(bindings: Map<String, String>): String {
        return bindings.entries
            .sortedBy { it.key }
            .joinToString("\n") { (key, value) -> "$key\t$value" }
    }

    companion object {
        const val LANGUAGE_ZH = "zh"
        const val LANGUAGE_EN = "en"

        val DEFAULT_LOCATION_CODE = stringPreferencesKey("default_location_code")
        val APP_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
        val RECENT_MANUAL_SEARCHES = stringPreferencesKey("recent_manual_searches")
        val BOM_PART_BINDINGS = stringPreferencesKey("bom_part_bindings")
        val RECENT_LOCATION_COLORS = stringPreferencesKey("recent_location_colors")
        val PRINTER_TYPE = stringPreferencesKey("printer_type")

        const val PRINTER_TYPE_AUTO = "auto"
        const val PRINTER_TYPE_DELI_Q5 = "deli_q5"
        const val PRINTER_TYPE_YINLIFANG_P0 = "yinlifang_p0"

        val SUPPORTED_PRINTER_TYPES = setOf(
            PRINTER_TYPE_AUTO,
            PRINTER_TYPE_DELI_Q5,
            PRINTER_TYPE_YINLIFANG_P0,
        )

        val PRESET_LOCATION_COLORS = listOf("#C8E6C9", "#B3E5FC", "#F8BBD0", "#D1C4E9", "#FFE0B2")
    }
}
