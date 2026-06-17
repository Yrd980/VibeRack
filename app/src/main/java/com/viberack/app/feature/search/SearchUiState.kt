package com.viberack.app.feature.search

import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.SearchInventoryRecord

data class SearchUiState(
    val mode: SearchMode = SearchMode.Manual,
    val query: String = "",
    val allInventoryResults: List<SearchResultUiModel> = emptyList(),
    val results: List<SearchResultUiModel> = emptyList(),
    val pagedResults: List<SearchResultUiModel> = emptyList(),
    val inventoryRecordCount: Int = 0,
    val currentPage: Int = 1,
    val pageCount: Int = 1,
    val bomFileName: String? = null,
    val bomFilter: BomMatchFilter = BomMatchFilter.All,
    val bomRows: List<BomSearchRowUiModel> = emptyList(),
    val bomMatchedCount: Int = 0,
    val bomError: String? = null,
    val isParsingBom: Boolean = false,
    val bomPickSession: BomPickSessionUiModel? = null,
    val bomPickMessage: String? = null,
    val isBomPickBusy: Boolean = false
)

enum class SearchMode {
    Manual,
    Bom
}

enum class BomMatchFilter {
    All,
    Matched,
    Unmatched
}

data class SearchResultUiModel(
    val partNumber: String,
    val mpn: String?,
    val name: String?,
    val brand: String?,
    val packageName: String?,
    val category: String?,
    val description: String?,
    val sourceUrl: String?,
    val specifications: Map<String, String>,
    val imageLocalPath: String?,
    val totalQuantity: Int,
    val locations: List<SearchResultLocationUiModel>,
    val records: List<SearchInventoryRecord>
)

data class SearchResultLocationUiModel(
    val code: String,
    val displayName: String?,
    val colorHex: String?,
    val quantity: Int,
    val containerType: ContainerType = ContainerType.LEGACY_LOCATION,
    val slotNumber: Int? = null,
    val canFindByLight: Boolean = false
)

data class BomSearchEntry(
    val rowNumber: String,
    val quantity: Int?,
    val comment: String?,
    val designator: String?,
    val footprint: String?,
    val value: String?,
    val manufacturerPart: String?,
    val manufacturer: String?,
    val supplierPart: String?,
    val supplier: String?
)

data class ParsedBomDocument(
    val fileName: String,
    val entries: List<BomSearchEntry>
)

data class BomSearchRowUiModel(
    val entry: BomSearchEntry,
    val matchedResults: List<SearchResultUiModel>,
    val isBound: Boolean = false,
    val isPersistentBinding: Boolean = false
)

data class BomPickSessionUiModel(
    val groups: List<BomPickGroupUiModel>
) {
    val targetCount: Int
        get() = groups.sumOf { it.targets.size }

    val slotCount: Int
        get() = groups.sumOf { it.slots.size }
}

data class BomPickGroupUiModel(
    val containerCode: String,
    val macAddress: String,
    val slots: List<Int>,
    val targets: List<BomPickTargetUiModel>
)

data class BomPickTargetUiModel(
    val partNumber: String,
    val slotNumber: Int,
    val designator: String?
)
