package com.viberack.app.domain.stock

import com.viberack.app.domain.model.LocationInventoryItem
import com.viberack.app.domain.model.StorageLocationSortMode

class LocationStockSortPolicy {
    fun supportedSpecificationAttributes(
        items: List<LocationInventoryItem>,
        currentSortMode: String
    ): List<String> {
        return buildList {
            items
                .asSequence()
                .flatMap { item -> item.specifications.keys.asSequence() }
                .map(String::trim)
                .distinct()
                .filter { key -> key.isNotEmpty() && hasMultipleSpecificationValues(items, key) }
                .sorted()
                .forEach(::add)
            StorageLocationSortMode.specificationKey(currentSortMode)
                ?.takeIf { it.isNotBlank() && it !in this }
                ?.let(::add)
        }
    }

    fun togglePriority(
        current: List<String>,
        target: String
    ): List<String> {
        return if (target in current) {
            current.filterNot { it == target }
        } else {
            current + target
        }
    }

    fun toggleSelection(
        current: Set<Long>,
        inventoryItemId: Long
    ): Set<Long> {
        return if (inventoryItemId in current) {
            current - inventoryItemId
        } else {
            current + inventoryItemId
        }
    }

    private fun hasMultipleSpecificationValues(
        items: List<LocationInventoryItem>,
        specificationKey: String
    ): Boolean {
        return items
            .asSequence()
            .mapNotNull { item ->
                item.specifications[specificationKey]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .take(2)
            .count() > 1
    }
}
