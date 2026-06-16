package com.viberack.app.data.repository

internal object ManualInboundPartNumberStrategy {
    fun nextPartNumber(existingPartNumbers: List<String?>): String {
        val nextIndex = existingPartNumbers
            .asSequence()
            .mapNotNull(::parseIndex)
            .maxOrNull()
            ?.plus(1)
            ?: 1
        return format(nextIndex)
    }

    fun parseIndex(partNumber: String?): Int? {
        return partNumber
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("^C0\\d+$")) }
            ?.removePrefix("C0")
            ?.toIntOrNull()
    }

    private fun format(index: Int): String {
        return "C0$index"
    }
}
