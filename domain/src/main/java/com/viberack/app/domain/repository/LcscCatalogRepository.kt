package com.viberack.app.domain.repository

import com.viberack.app.domain.model.ComponentDetail

interface LcscCatalogRepository {
    suspend fun lookupByPartNumber(partNumber: String): ComponentDetail?
    suspend fun searchByKeyword(keyword: String): List<ComponentDetail>
}
