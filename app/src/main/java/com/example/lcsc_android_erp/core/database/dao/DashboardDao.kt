package com.example.lcsc_android_erp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.lcsc_android_erp.core.database.model.DashboardSummaryProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM component_master) AS componentCount,
            (SELECT COUNT(*) FROM `container`) AS locationCount,
            (SELECT COUNT(*) FROM stock_item) AS inventoryCount,
            (SELECT COALESCE(SUM(quantity), 0) FROM stock_item) AS totalQuantity,
            (SELECT COUNT(*) FROM inventory_txn) AS transactionCount
        """
    )
    fun observeSummary(): Flow<DashboardSummaryProjection>
}
