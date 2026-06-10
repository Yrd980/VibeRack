package com.example.lcsc_android_erp.core.nfc

import android.net.Uri
import java.util.Locale

data class NfcLabelPayload(
    val kind: NfcLabelKind,
    val locationCode: String? = null,
    val partNumber: String? = null,
)

enum class NfcLabelKind {
    LOCATION,
    MATERIAL,
}

object NfcLabelPayloadCodec {
    private const val scheme = "lcscerp"
    private const val hostLocation = "location"
    private const val hostMaterial = "material"

    fun locationUri(locationCode: String): String {
        return Uri.Builder()
            .scheme(scheme)
            .authority(hostLocation)
            .appendQueryParameter("code", locationCode.trim().uppercase(Locale.ROOT))
            .build()
            .toString()
    }

    fun materialUri(partNumber: String, locationCode: String? = null): String {
        val builder = Uri.Builder()
            .scheme(scheme)
            .authority(hostMaterial)
            .appendQueryParameter("part", partNumber.trim().uppercase(Locale.ROOT))
        locationCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.appendQueryParameter("location", it) }
        return builder.build().toString()
    }

    fun parse(value: String): NfcLabelPayload? {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(scheme, ignoreCase = true)) {
            return null
        }
        return when (uri.host?.lowercase(Locale.ROOT)) {
            hostLocation -> {
                val code = uri.getQueryParameter("code")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                NfcLabelPayload(kind = NfcLabelKind.LOCATION, locationCode = code)
            }

            hostMaterial -> {
                val partNumber = uri.getQueryParameter("part")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                val locationCode = uri.getQueryParameter("location")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
                NfcLabelPayload(
                    kind = NfcLabelKind.MATERIAL,
                    locationCode = locationCode,
                    partNumber = partNumber,
                )
            }

            else -> null
        }
    }
}
