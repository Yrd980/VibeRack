package com.viberack.app.feature.inbound

object LcscQrParser {
    fun parse(rawText: String): InboundQrPayload? {
        val normalized = rawText.trim()
        if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
            return null
        }

        val content = normalized.removePrefix("{").removeSuffix("}")
        if (content.isBlank()) {
            return null
        }

        val fields = linkedMapOf<String, String>()
        content.split(",").forEach { token ->
            val parts = token.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"').trim('\'')
                val value = parts[1].trim().trim('"').trim('\'')
                if (key.isNotBlank()) {
                    fields[key] = value
                }
            }
        }

        val partNumber = fields.firstCaseInsensitiveValue("pc")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.startsWith("C") }
            ?: return null
        val quantity = fields.firstCaseInsensitiveValue("qty")
            ?.trim()
            ?.toIntOrNull()
            ?: 0

        return InboundQrPayload(
            orderNo = fields.firstCaseInsensitiveValue("on")?.ifBlank { null },
            partNumber = partNumber,
            manufacturerPartNo = fields.firstCaseInsensitiveValue("pm")?.ifBlank { null },
            quantity = quantity,
            rawText = rawText,
            extraFields = fields
        )
    }

    private fun Map<String, String>.firstCaseInsensitiveValue(targetKey: String): String? {
        return entries.firstOrNull { (key, _) -> key.equals(targetKey, ignoreCase = true) }?.value
    }
}
