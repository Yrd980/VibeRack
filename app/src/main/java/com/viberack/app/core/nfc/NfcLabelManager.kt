package com.viberack.app.core.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NfcLabelState(
    val nfcAvailable: Boolean = false,
    val nfcEnabled: Boolean = false,
    val pendingWriteUri: String? = null,
    val statusMessage: String = "",
)

sealed interface NfcScanResult {
    data class Label(val payload: NfcLabelPayload, val rawValue: String) : NfcScanResult
    data class Unsupported(val rawValue: String?) : NfcScanResult
    data class Error(val message: String) : NfcScanResult
    data object WriteCompleted : NfcScanResult
}

class NfcLabelManager(
    private val appContext: Context
) {
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(appContext) }
    private val _state = MutableStateFlow(
        NfcLabelState(
            nfcAvailable = adapter != null,
            nfcEnabled = adapter.safeIsEnabled(),
        )
    )
    val state: StateFlow<NfcLabelState> = _state.asStateFlow()

    @Volatile
    private var onScanResult: ((NfcScanResult) -> Unit)? = null

    fun setPendingWrite(uri: String?) {
        _state.update {
            it.copy(
                nfcAvailable = adapter != null,
                nfcEnabled = adapter.safeIsEnabled(),
                pendingWriteUri = uri,
            )
        }
    }

    fun setOnScanResult(callback: ((NfcScanResult) -> Unit)?) {
        onScanResult = callback
    }

    fun enable(activity: Activity) {
        val currentAdapter = adapter
        _state.update {
            it.copy(
                nfcAvailable = currentAdapter != null,
                nfcEnabled = currentAdapter.safeIsEnabled(),
            )
        }
        currentAdapter ?: return
        runCatching {
            currentAdapter.enableReaderMode(
                activity,
                ::handleTag,
                readerFlags,
                null,
            )
        }
    }

    fun disable(activity: Activity) {
        adapter?.let { currentAdapter ->
            runCatching {
                currentAdapter.disableReaderMode(activity)
            }
        }
    }

    private fun handleTag(tag: Tag) {
        val pendingUri = state.value.pendingWriteUri
        if (pendingUri != null) {
            val result = writeUri(tag, pendingUri)
            if (result == null) {
                setPendingWrite(null)
                onScanResult?.invoke(NfcScanResult.WriteCompleted)
            } else {
                onScanResult?.invoke(NfcScanResult.Error(result))
            }
            return
        }

        val rawValue = readUri(tag)
        if (rawValue == null) {
            onScanResult?.invoke(NfcScanResult.Unsupported(null))
            return
        }
        val payload = NfcLabelPayloadCodec.parse(rawValue)
        if (payload == null) {
            onScanResult?.invoke(NfcScanResult.Unsupported(rawValue))
        } else {
            onScanResult?.invoke(NfcScanResult.Label(payload, rawValue))
        }
    }

    private fun readUri(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        return runCatching {
            ndef.connect()
            ndef.cachedNdefMessage
                ?.records
                ?.firstNotNullOfOrNull(::readRecordText)
        }.getOrNull().also {
            runCatching { ndef.close() }
        }
    }

    private fun readRecordText(record: NdefRecord): String? {
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_URI) -> {
                val payload = record.payload
                if (payload.isEmpty()) return null
                val prefix = uriPrefixMap[payload[0].toInt() and 0xFF].orEmpty()
                prefix + payload.copyOfRange(1, payload.size).toString(StandardCharsets.UTF_8)
            }

            record.tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                record.type.toString(StandardCharsets.UTF_8)
            }

            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val payload = record.payload
                if (payload.isEmpty()) return null
                val languageCodeLength = payload[0].toInt() and 0x3F
                val textStart = 1 + languageCodeLength
                if (textStart > payload.size) return null
                payload.copyOfRange(textStart, payload.size).toString(StandardCharsets.UTF_8)
            }

            else -> null
        }
    }

    private fun writeUri(tag: Tag, uri: String): String? {
        val message = NdefMessage(arrayOf(NdefRecord.createUri(uri)))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return writeExistingNdef(ndef, message)
        }
        val formatable = NdefFormatable.get(tag) ?: return "NFC tag does not support NDEF."
        return runCatching {
            formatable.connect()
            formatable.format(message)
            null
        }.getOrElse { error ->
            error.message ?: error.javaClass.simpleName
        }.also {
            runCatching { formatable.close() }
        }
    }

    private fun writeExistingNdef(ndef: Ndef, message: NdefMessage): String? {
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                return "NFC tag is read-only."
            }
            if (ndef.maxSize < message.toByteArray().size) {
                return "NFC tag capacity is too small."
            }
            ndef.writeNdefMessage(message)
            null
        } catch (error: IOException) {
            error.message ?: error.javaClass.simpleName
        } catch (error: IllegalArgumentException) {
            error.message ?: error.javaClass.simpleName
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun NfcAdapter?.safeIsEnabled(): Boolean {
        return this?.let { currentAdapter ->
            runCatching { currentAdapter.isEnabled }.getOrDefault(false)
        } ?: false
    }

    private companion object {
        val readerFlags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

        val uriPrefixMap = mapOf(
            0x00 to "",
            0x01 to "http://www.",
            0x02 to "https://www.",
            0x03 to "http://",
            0x04 to "https://",
            0x05 to "tel:",
            0x06 to "mailto:",
            0x07 to "ftp://anonymous:anonymous@",
            0x08 to "ftp://ftp.",
            0x09 to "ftps://",
            0x0A to "sftp://",
            0x0B to "smb://",
            0x0C to "nfs://",
            0x0D to "ftp://",
            0x0E to "dav://",
            0x0F to "news:",
            0x10 to "telnet://",
            0x11 to "imap:",
            0x12 to "rtsp://",
            0x13 to "urn:",
            0x14 to "pop:",
            0x15 to "sip:",
            0x16 to "sips:",
            0x17 to "tftp:",
            0x18 to "btspp://",
            0x19 to "btl2cap://",
            0x1A to "btgoep://",
            0x1B to "tcpobex://",
            0x1C to "irdaobex://",
            0x1D to "file://",
            0x1E to "urn:epc:id:",
            0x1F to "urn:epc:tag:",
            0x20 to "urn:epc:pat:",
            0x21 to "urn:epc:raw:",
            0x22 to "urn:epc:",
            0x23 to "urn:nfc:",
        )
    }
}
