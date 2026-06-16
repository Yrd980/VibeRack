package com.example.lcsc_android_erp.core.nfc

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class NfcLabelPayload(
    val kind: NfcLabelKind,
    val locationCode: String? = null,
    val partNumber: String? = null,
    val boxCode: String? = null,
    val layerCode: String? = null,
    val macAddress: String? = null,
    val batchId: Int? = null,
    val protoVersion: Int? = null,
)

enum class NfcLabelKind {
    LOCATION,
    MATERIAL,
    DEVICE,
}

object NfcLabelPayloadCodec {
    private const val scheme = "lcscerp"
    private const val hostLocation = "location"
    private const val hostMaterial = "material"
    private const val hostDevice = "device"
    private val macRegex = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

    fun locationUri(locationCode: String): String {
        return buildUri(hostLocation, "code" to locationCode.trim().uppercase(Locale.ROOT))
    }

    fun materialUri(
        partNumber: String,
        locationCode: String? = null,
        boxCode: String? = null,
        layerCode: String? = null
    ): String {
        val queryParameters = mutableListOf("part" to partNumber.trim().uppercase(Locale.ROOT))
        locationCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?.let { queryParameters += "location" to it }
        boxCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?.let { queryParameters += "box" to it }
        layerCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?.let { queryParameters += "layer" to it }
        return buildUri(hostMaterial, *queryParameters.toTypedArray())
    }

    fun deviceUri(
        macAddress: String,
        batchId: Int,
        protoVersion: Int
    ): String {
        return buildUri(
            hostDevice,
            "mac" to macAddress.trim().uppercase(Locale.ROOT),
            "batch" to batchId.toString(),
            "ver" to protoVersion.toString()
        )
    }

    fun parse(value: String): NfcLabelPayload? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(scheme, ignoreCase = true)) {
            return null
        }
        val queryParameters = uri.queryParameters()
        return when (uri.host?.lowercase(Locale.ROOT)) {
            hostLocation -> {
                val code = queryParameters["code"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                NfcLabelPayload(kind = NfcLabelKind.LOCATION, locationCode = code)
            }

            hostMaterial -> {
                val partNumber = queryParameters["part"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                val locationCode = queryParameters["location"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                val boxCode = queryParameters["box"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                val layerCode = queryParameters["layer"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                NfcLabelPayload(
                    kind = NfcLabelKind.MATERIAL,
                    locationCode = locationCode,
                    partNumber = partNumber,
                    boxCode = boxCode,
                    layerCode = layerCode,
                )
            }

            hostDevice -> {
                val macAddress = queryParameters["mac"]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.matches(macRegex) }
                    ?: return null
                val batchId = queryParameters["batch"]
                    ?.toIntOrNull()
                    ?.takeIf { it in 0..0xFFFF }
                    ?: return null
                val protoVersion = queryParameters["ver"]
                    ?.toIntOrNull()
                    ?.takeIf { it in 0..0xFF }
                    ?: return null
                NfcLabelPayload(
                    kind = NfcLabelKind.DEVICE,
                    macAddress = macAddress,
                    batchId = batchId,
                    protoVersion = protoVersion
                )
            }

            else -> null
        }
    }

    private fun buildUri(host: String, vararg queryParameters: Pair<String, String>): String {
        val query = queryParameters.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return "$scheme://$host?$query"
    }

    private fun URI.queryParameters(): Map<String, String> {
        return rawQuery
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { part ->
                val splitIndex = part.indexOf('=')
                if (splitIndex < 0) {
                    null
                } else {
                    urlDecode(part.substring(0, splitIndex)) to urlDecode(part.substring(splitIndex + 1))
                }
            }
            ?.toMap()
            .orEmpty()
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
