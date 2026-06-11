package com.example.lcsc_android_erp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lcsc_android_erp.domain.model.ContainerType
import com.example.lcsc_android_erp.domain.model.QuantityState
import com.example.lcsc_android_erp.domain.model.StorageLocationSortMode

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE storage_location ADD COLUMN displayName TEXT")
            db.execSQL("ALTER TABLE storage_location ADD COLUMN colorHex TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE component_master ADD COLUMN image_local_path TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE storage_location ADD COLUMN sortMode TEXT NOT NULL " +
                    "DEFAULT '${StorageLocationSortMode.NONE}'"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS storage_location_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    displayName TEXT,
                    colorHex TEXT,
                    sortMode TEXT NOT NULL DEFAULT '',
                    inbound_category TEXT,
                    inbound_package_name TEXT,
                    inbound_profile_updated_at INTEGER NOT NULL DEFAULT 0,
                    remark TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO storage_location_new (
                    id,
                    code,
                    displayName,
                    colorHex,
                    sortMode,
                    remark,
                    createdAt
                )
                SELECT
                    id,
                    code,
                    displayName,
                    colorHex,
                    sortMode,
                    remark,
                    createdAt
                FROM storage_location
                """.trimIndent()
            )
            db.execSQL("DROP TABLE storage_location")
            db.execSQL("ALTER TABLE storage_location_new RENAME TO storage_location")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_storage_location_code " +
                    "ON storage_location (code)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS component_box (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    name TEXT,
                    layerCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_component_box_code " +
                    "ON component_box (code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS box_layer (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    box_id INTEGER NOT NULL,
                    layer_code TEXT NOT NULL,
                    displayName TEXT,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(box_id) REFERENCES component_box(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_box_layer_box_id ON box_layer (box_id)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_box_layer_box_id_layer_code " +
                    "ON box_layer (box_id, layer_code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS layer_material (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    layer_id INTEGER NOT NULL,
                    component_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    sourceType TEXT,
                    rawPayload TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(layer_id) REFERENCES box_layer(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(component_id) REFERENCES component_master(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_layer_material_layer_id " +
                    "ON layer_material (layer_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_layer_material_component_id " +
                    "ON layer_material (component_id)"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE component_master ADD COLUMN protocol_part_id TEXT")
            db.execSQL(
                """
                UPDATE component_master
                SET protocol_part_id = CASE
                    WHEN upper(trim(part_number)) LIKE 'C0%' THEN 'M' || printf('%09d', id)
                    WHEN length(trim(part_number)) <= 10
                        AND upper(trim(part_number)) GLOB '[CM]*'
                        AND upper(trim(part_number)) NOT GLOB '*[^A-Z0-9]*'
                        AND NOT EXISTS (
                            SELECT 1
                            FROM component_master AS duplicate
                            WHERE duplicate.id != component_master.id
                                AND upper(trim(duplicate.part_number)) = upper(trim(component_master.part_number))
                                AND upper(trim(duplicate.part_number)) NOT LIKE 'C0%'
                                AND length(trim(duplicate.part_number)) <= 10
                                AND upper(trim(duplicate.part_number)) GLOB '[CM]*'
                                AND upper(trim(duplicate.part_number)) NOT GLOB '*[^A-Z0-9]*'
                        )
                        AND NOT EXISTS (
                            SELECT 1
                            FROM component_master AS fallback
                            WHERE fallback.id != component_master.id
                                AND upper(trim(component_master.part_number)) = 'M' || printf('%09d', fallback.id)
                        )
                    THEN upper(trim(part_number))
                    ELSE 'M' || printf('%09d', id)
                END
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_component_master_protocol_part_id " +
                    "ON component_master (protocol_part_id)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `container` (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    displayName TEXT,
                    type TEXT NOT NULL,
                    slotCount INTEGER NOT NULL,
                    colorHex TEXT,
                    sortMode TEXT NOT NULL DEFAULT '',
                    remark TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    macAddress TEXT,
                    batchId INTEGER,
                    protoVersion INTEGER,
                    firmwareVersion TEXT,
                    hardwareVersion TEXT,
                    batteryPct INTEGER,
                    statusFlags INTEGER,
                    tableSeq INTEGER,
                    tableCrc16 INTEGER,
                    lastSeenAt INTEGER,
                    lastSyncedAt INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_container_code ON `container` (code)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_container_macAddress " +
                    "ON `container` (macAddress)"
            )
            db.execSQL(
                """
                INSERT INTO `container` (
                    id,
                    code,
                    displayName,
                    type,
                    slotCount,
                    colorHex,
                    sortMode,
                    remark,
                    createdAt,
                    updatedAt
                )
                SELECT
                    id,
                    code,
                    displayName,
                    '${ContainerType.LEGACY_LOCATION.name}',
                    1,
                    colorHex,
                    sortMode,
                    remark,
                    createdAt,
                    createdAt
                FROM storage_location
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `container` (
                    id,
                    code,
                    displayName,
                    type,
                    slotCount,
                    colorHex,
                    sortMode,
                    remark,
                    createdAt,
                    updatedAt
                )
                SELECT
                    1000000000 + component_box.id,
                    CASE
                        WHEN NOT EXISTS (
                            SELECT 1
                            FROM storage_location
                            WHERE storage_location.code = component_box.code
                        )
                        THEN component_box.code
                        ELSE 'BOX-' || (1000000000 + component_box.id)
                    END,
                    CASE
                        WHEN NOT EXISTS (
                            SELECT 1
                            FROM storage_location
                            WHERE storage_location.code = component_box.code
                        )
                        THEN component_box.name
                        ELSE COALESCE(component_box.name, component_box.code)
                    END,
                    '${ContainerType.BOX.name}',
                    component_box.layerCount,
                    NULL,
                    '${StorageLocationSortMode.NONE}',
                    NULL,
                    component_box.createdAt,
                    component_box.updatedAt
                FROM component_box
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS container_slot (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    container_id INTEGER NOT NULL,
                    slot_number INTEGER NOT NULL,
                    slot_code TEXT NOT NULL,
                    displayName TEXT,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(container_id) REFERENCES `container`(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_container_slot_container_id " +
                    "ON container_slot (container_id)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_container_slot_container_id_slot_number " +
                    "ON container_slot (container_id, slot_number)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_container_slot_container_id_slot_code " +
                    "ON container_slot (container_id, slot_code)"
            )
            db.execSQL(
                """
                INSERT INTO container_slot (
                    id,
                    container_id,
                    slot_number,
                    slot_code,
                    displayName,
                    sortOrder,
                    createdAt,
                    updatedAt
                )
                SELECT
                    id,
                    id,
                    1,
                    code,
                    COALESCE(displayName, code),
                    1,
                    createdAt,
                    createdAt
                FROM storage_location
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO container_slot (
                    id,
                    container_id,
                    slot_number,
                    slot_code,
                    displayName,
                    sortOrder,
                    createdAt,
                    updatedAt
                )
                SELECT
                    2000000000 + box_layer.id,
                    1000000000 + box_layer.box_id,
                    box_layer.sortOrder,
                    box_layer.layer_code,
                    box_layer.displayName,
                    box_layer.sortOrder,
                    box_layer.createdAt,
                    box_layer.updatedAt
                FROM box_layer
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stock_item (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    component_id INTEGER NOT NULL,
                    container_id INTEGER NOT NULL,
                    container_slot_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    quantity_state TEXT NOT NULL DEFAULT 'KNOWN',
                    safety_stock_threshold INTEGER,
                    last_inbound_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(component_id) REFERENCES component_master(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(container_id) REFERENCES `container`(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(container_slot_id) REFERENCES container_slot(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_item_component_id " +
                    "ON stock_item (component_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_item_container_id " +
                    "ON stock_item (container_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_item_container_slot_id " +
                    "ON stock_item (container_slot_id)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_stock_item_component_id_container_slot_id " +
                    "ON stock_item (component_id, container_slot_id)"
            )
            db.execSQL(
                """
                INSERT INTO stock_item (
                    component_id,
                    container_id,
                    container_slot_id,
                    quantity,
                    quantity_state,
                    safety_stock_threshold,
                    last_inbound_at,
                    updated_at
                )
                SELECT
                    component_id,
                    location_id,
                    location_id,
                    quantity,
                    '${QuantityState.KNOWN.name}',
                    NULL,
                    last_inbound_at,
                    updated_at
                FROM inventory_item
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO stock_item (
                    component_id,
                    container_id,
                    container_slot_id,
                    quantity,
                    quantity_state,
                    safety_stock_threshold,
                    last_inbound_at,
                    updated_at
                )
                SELECT
                    layer_material.component_id,
                    1000000000 + box_layer.box_id,
                    2000000000 + layer_material.layer_id,
                    layer_material.quantity,
                    '${QuantityState.KNOWN.name}',
                    NULL,
                    layer_material.createdAt,
                    layer_material.updatedAt
                FROM layer_material
                INNER JOIN box_layer ON box_layer.id = layer_material.layer_id
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stock_operation (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    container_id INTEGER,
                    container_slot_id INTEGER,
                    component_id INTEGER,
                    quantity_delta INTEGER NOT NULL DEFAULT 0,
                    sourceType TEXT,
                    sourceRef TEXT,
                    rawPayload TEXT,
                    bleOpcode INTEGER,
                    bleStatus INTEGER,
                    tableSeqBefore INTEGER,
                    tableSeqAfter INTEGER,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(container_id) REFERENCES `container`(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(container_slot_id) REFERENCES container_slot(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(component_id) REFERENCES component_master(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_operation_type ON stock_operation (type)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_operation_container_id " +
                    "ON stock_operation (container_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_operation_container_slot_id " +
                    "ON stock_operation (container_slot_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_operation_component_id " +
                    "ON stock_operation (component_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stock_operation_created_at " +
                    "ON stock_operation (created_at)"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7
    )
}
