package com.example.lcsc_android_erp.feature.search

import com.example.lcsc_android_erp.domain.model.ComponentDetail
import com.example.lcsc_android_erp.domain.model.ExistingStockLocation
import com.example.lcsc_android_erp.domain.model.SearchInventoryRecord
import com.example.lcsc_android_erp.domain.model.StorageLocation

data class SearchUiState(
    val mode: SearchMode = SearchMode.Manual,
    val query: String = "",
    val defaultLocationCode: String? = null,
    val locations: List<StorageLocation> = emptyList(),
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
    val isParsingBom: Boolean = false
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
    val quantity: Int
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

data class BomDirectInboundLookupResult(
    val component: ComponentDetail? = null,
    val existingStockLocations: List<ExistingStockLocation> = emptyList(),
    val errorMessage: String? = null
)
