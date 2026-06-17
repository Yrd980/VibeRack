package com.viberack.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val appLanguageTag: String = UserPreferencesRepository.LANGUAGE_ZH,
    val bomPartBindings: Map<String, String> = emptyMap()
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            appLanguageTag = preferences[APP_LANGUAGE_TAG] ?: LANGUAGE_ZH,
            bomPartBindings = parseBomPartBindings(preferences[BOM_PART_BINDINGS])
        )
    }

    suspend fun setAppLanguage(languageTag: String) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_TAG] = languageTag
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

        val APP_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
        val BOM_PART_BINDINGS = stringPreferencesKey("bom_part_bindings")
    }
}
