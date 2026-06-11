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
            (SELECT COUNT(*) FROM storage_location) AS locationCount,
            (
                SELECT COUNT(*)
                FROM stock_item si
                INNER JOIN `container` c ON c.id = si.container_id
                WHERE c.type = 'LEGACY_LOCATION'
            ) AS inventoryCount,
            (
                SELECT COALESCE(SUM(si.quantity), 0)
                FROM stock_item si
                INNER JOIN `container` c ON c.id = si.container_id
                WHERE c.type = 'LEGACY_LOCATION'
            ) AS totalQuantity,
            (SELECT COUNT(*) FROM inventory_txn) AS transactionCount
        """
    )
    fun observeSummary(): Flow<DashboardSummaryProjection>
}
