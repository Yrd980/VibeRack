package com.viberack.app.data.repository

import com.viberack.app.domain.model.InboundRecord
import java.io.File

internal class InboundRecordPreparer(
    private val componentImageStore: ComponentImageStore
) {
    suspend fun prepare(record: InboundRecord): InboundRecord {
        val existingLocalPath = record.component.imageLocalPath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { path ->
                runCatching { File(path).exists() && File(path).length() > 0L }.getOrDefault(false)
            }
        if (existingLocalPath != null) {
            return record
        }
        val persistedLocalPath = componentImageStore.persistImage(
            partNumber = record.component.partNumber,
            imageUrl = record.component.imageUrl
        ) ?: return record
        return record.copy(
            component = record.component.copy(
                imageLocalPath = persistedLocalPath
            )
        )
    }
}
