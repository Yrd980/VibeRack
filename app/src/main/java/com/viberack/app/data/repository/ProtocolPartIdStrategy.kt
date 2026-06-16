package com.viberack.app.data.repository

import java.util.Locale

class ProtocolPartIdStrategy {
    fun normalize(value: String?): String? {
        return value
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(PROTOCOL_PART_ID_REGEX) }
    }

    fun forComponent(
        componentId: Long?,
        partNumber: String,
        sourceType: String? = null
    ): String? {
        val normalizedPartNumber = partNumber.trim().uppercase(Locale.ROOT)
        if (isCatalogProtocolPartId(normalizedPartNumber, sourceType)) {
            return normalizedPartNumber
        }
        return manualIdForComponent(componentId)
    }

    fun manualIdForComponent(componentId: Long?): String? {
        return componentId
            ?.takeIf { it > 0 }
            ?.let { id -> "M%09d".format(Locale.ROOT, id).takeIf(::isValid) }
    }

    fun isValid(value: String): Boolean {
        return value.trim().uppercase(Locale.ROOT).matches(PROTOCOL_PART_ID_REGEX)
    }

    private fun isCatalogProtocolPartId(partNumber: String, sourceType: String?): Boolean {
        return sourceType != SOURCE_MANUAL_INPUT &&
            !partNumber.matches(MANUAL_INBOUND_PART_NUMBER_REGEX) &&
            partNumber.startsWith("C") &&
            partNumber.matches(PROTOCOL_PART_ID_REGEX)
    }

    private companion object {
        private const val SOURCE_MANUAL_INPUT = "MANUAL_INPUT"
        private val PROTOCOL_PART_ID_REGEX = Regex("^[CM][A-Z0-9]{0,9}$")
        private val MANUAL_INBOUND_PART_NUMBER_REGEX = Regex("^C0\\d+$")
    }
}
