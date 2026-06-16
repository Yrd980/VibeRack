package com.viberack.app.domain.model

object StorageLocationSortMode {
    const val NONE = ""
    const val NAME = "NAME"
    const val QUANTITY = "QUANTITY"
    const val INBOUND_TIME = "INBOUND_TIME"
    const val SECONDARY_LEGACY = "SECONDARY"
    private const val SPEC_PREFIX = "SPEC:"
    private const val SEPARATOR = "|"

    fun bySpecification(specificationKey: String): String {
        return SPEC_PREFIX + specificationKey.trim()
    }

    fun specificationKey(sortMode: String): String? {
        return sortMode
            .takeIf { it.startsWith(SPEC_PREFIX) }
            ?.removePrefix(SPEC_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun priorities(sortMode: String): List<String> {
        val normalized = sortMode.trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        return normalized
            .split(SEPARATOR)
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                when (token) {
                    SECONDARY_LEGACY -> null
                    NAME, QUANTITY, INBOUND_TIME -> token
                    else -> token.takeIf { specificationKey(it) != null }
                }
            }
            .distinct()
    }

    fun serialize(priorities: List<String>): String {
        return priorities
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(SEPARATOR)
    }
}
