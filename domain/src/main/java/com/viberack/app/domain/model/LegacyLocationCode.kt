package com.viberack.app.domain.model

/**
 * Sorting rules for legacy location codes such as A1 or R12.
 *
 * Legacy locations are treated as one-slot containers during migration, but the
 * old row/column display order is still shared by inventory and stock-in flows.
 */
data class LegacyLocationCode(
    val rawCode: String
) : Comparable<LegacyLocationCode> {
    val rowLabel: String = rawCode.takeWhile { it.isLetter() }.ifBlank { "#" }
    private val rowIndex: Int = rawCode.firstOrNull()?.uppercaseChar()?.code ?: Int.MAX_VALUE
    private val columnIndex: Int = rawCode.dropWhile { it.isLetter() }.toIntOrNull() ?: Int.MAX_VALUE

    override fun compareTo(other: LegacyLocationCode): Int {
        return compareValuesBy(
            this,
            other,
            LegacyLocationCode::rowIndex,
            LegacyLocationCode::columnIndex,
            LegacyLocationCode::rawCode
        )
    }
}
