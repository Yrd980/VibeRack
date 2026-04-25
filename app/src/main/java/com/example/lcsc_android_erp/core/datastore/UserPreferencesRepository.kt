package com.example.lcsc_android_erp.core.datastore

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
            printerType = preferences[PRINTER_TYPE]
                ?.takeIf { it == PRINTER_TYPE_AUTO || it == PRINTER_TYPE_DELI_Q5 }
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
            else -> PRINTER_TYPE_AUTO
        }
        dataStore.edit { preferences ->
            preferences[PRINTER_TYPE] = normalizedType
        }
    }

    private fun parseRecentManualSearches(value: String?): List<String> {
        return value
            ?.split('\n')
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
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
        val PRINTER_TYPE = stringPreferencesKey("printer_type")

        const val PRINTER_TYPE_AUTO = "auto"
        const val PRINTER_TYPE_DELI_Q5 = "deli_q5"
    }
}
