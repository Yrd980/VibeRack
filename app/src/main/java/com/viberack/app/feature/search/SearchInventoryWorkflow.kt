package com.viberack.app.feature.search

import com.viberack.app.domain.model.SearchInventoryRecord
import java.util.Locale

internal object SearchInventoryWorkflow {
    fun filterRecords(
        records: List<SearchInventoryRecord>,
        queryText: String
    ): List<SearchInventoryRecord> {
        val normalizedQuery = queryText.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            return records
        }

        return records.filter { record ->
            buildSearchTokens(record).any { token ->
                token.contains(normalizedQuery)
            }
        }
    }

    fun groupRecords(records: List<SearchInventoryRecord>): List<SearchResultUiModel> {
        return records
            .groupBy { "${it.partNumber}|${it.mpn.orEmpty()}" }
            .values
            .map { group ->
                val first = group.first()
                SearchResultUiModel(
                    partNumber = first.partNumber,
                    mpn = first.mpn,
                    name = first.name,
                    brand = first.brand,
                    packageName = first.packageName,
                    category = first.category,
                    description = first.description,
                    sourceUrl = first.sourceUrl,
                    specifications = first.specifications,
                    imageLocalPath = first.imageLocalPath,
                    totalQuantity = group.sumOf { it.quantity },
                    locations = group
                        .sortedBy { it.locationCode }
                        .map { record ->
                            SearchResultLocationUiModel(
                                code = record.locationCode,
                                displayName = record.locationDisplayName,
                                colorHex = record.locationColorHex,
                                quantity = record.quantity,
                                containerType = record.containerType,
                                slotNumber = record.slotNumber,
                                canFindByLight = record.canFindByLight
                            )
                        },
                    records = group.sortedWith(
                        compareBy<SearchInventoryRecord> { it.locationCode }
                            .thenBy { it.slotNumber ?: 0 }
                    )
                )
            }
            .sortedWith(
                compareBy<SearchResultUiModel> {
                    it.name?.trim()?.takeIf(String::isNotEmpty)
                        ?: it.mpn?.trim()?.takeIf(String::isNotEmpty)
                        ?: it.partNumber
                }.thenBy { it.partNumber }
            )
    }

    private fun buildSearchTokens(record: SearchInventoryRecord): List<String> {
        return buildList {
            add(record.partNumber)
            record.mpn?.let(::add)
            record.name?.let(::add)
            record.brand?.let(::add)
            record.packageName?.let(::add)
            record.category?.let(::add)
            record.description?.let(::add)
            add(record.locationCode)
            record.locationDisplayName?.let(::add)
            record.specifications.forEach { (key, value) ->
                add(key)
                add(value)
            }
        }.mapNotNull { value ->
            value.trim()
                .takeIf { it.isNotEmpty() }
                ?.lowercase(Locale.ROOT)
        }
    }
}
