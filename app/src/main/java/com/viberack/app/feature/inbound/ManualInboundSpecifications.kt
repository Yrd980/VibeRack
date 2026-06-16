package com.viberack.app.feature.inbound

fun parseManualSpecifications(rawText: String): Map<String, String> {
    return parseManualSpecificationEntries(rawText).toMap()
}

fun parseManualSpecificationEntries(rawText: String): List<Pair<String, String>> {
    return rawText
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separatorIndex = line.indexOfFirst { it == ':' || it == '=' || it == '：' }
            if (separatorIndex <= 0 || separatorIndex >= line.lastIndex) {
                return@mapNotNull null
            }
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            if (key.isEmpty() || value.isEmpty()) {
                null
            } else {
                key to value
            }
        }
        .toList()
}

fun rebuildManualSpecificationText(entries: List<Pair<String, String>>): String {
    return entries.joinToString("\n") { (key, value) -> "$key=$value" }
}
