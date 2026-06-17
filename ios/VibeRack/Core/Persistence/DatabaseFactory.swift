import Foundation
import GRDB

public enum DatabaseFactory {
    public static func makeInMemoryQueue() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try migrator.migrate(queue)
        return queue
    }

    public static func makeAppQueue(filename: String = "VibeRack.sqlite") throws -> DatabaseQueue {
        let directory = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let url = directory.appendingPathComponent(filename)
        return try makeQueue(path: url.path)
    }

    public static func makeQueue(path: String) throws -> DatabaseQueue {
        let queue = try DatabaseQueue(path: path)
        try migrator.migrate(queue)
        return queue
    }

    private static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("m1_ledger_schema") { db in
            try db.create(table: "container") { table in
                table.column("id", .text).primaryKey()
                table.column("code", .text).notNull().unique()
                table.column("display_name", .text).notNull()
                table.column("type", .text).notNull()
                table.column("slot_count", .integer).notNull()
                table.column("batch_id", .integer)
                table.column("battery_pct", .integer)
                table.column("table_seq", .integer)
                table.column("table_crc16", .integer)
            }

            try db.create(table: "container_slot") { table in
                table.column("id", .text).primaryKey()
                table.column("container_id", .text).notNull()
                    .references("container", onDelete: .cascade)
                table.column("slot_number", .integer).notNull()
                table.uniqueKey(["container_id", "slot_number"])
            }

            try db.create(table: "stock_item") { table in
                table.column("id", .text).primaryKey()
                table.column("container_id", .text).notNull()
                    .references("container", onDelete: .cascade)
                table.column("container_slot_id", .text).notNull()
                    .references("container_slot", onDelete: .cascade)
                table.column("protocol_part_id", .text).notNull()
                table.column("quantity", .integer).notNull()
                table.column("flags", .integer).notNull().defaults(to: 0)
                table.uniqueKey(["container_slot_id"])
            }
        }

        migrator.registerMigration("m2_stock_operation_schema") { db in
            try db.create(table: "stock_operation", ifNotExists: true) { table in
                table.column("id", .text).primaryKey()
                table.column("type", .text).notNull()
                table.column("container_id", .text).notNull()
                    .references("container", onDelete: .cascade)
                table.column("container_slot_id", .text).notNull()
                    .references("container_slot", onDelete: .cascade)
                table.column("slot_number", .integer).notNull()
                table.column("protocol_part_id", .text).notNull()
                table.column("quantity_before", .integer)
                table.column("quantity_after", .integer)
                table.column("quantity_delta", .integer).notNull()
                table.column("source_type", .text).notNull()
                table.column("ble_opcode", .integer)
                table.column("ble_status", .integer)
                table.column("created_at", .datetime).notNull()
            }
        }
        migrator.registerMigration("m3_component_schema") { db in
            try db.create(table: "component", ifNotExists: true) { table in
                table.column("id", .text).primaryKey()
                table.column("protocol_part_id", .text)
                table.column("source", .text)
                table.column("lcsc_part_number", .text)
                table.column("manufacturer_part_number", .text)
                table.column("name", .text)
                table.column("package_name", .text)
                table.column("brand", .text)
                table.column("spec_summary", .text)
                table.column("created_at", .datetime).notNull()
                table.column("updated_at", .datetime).notNull()
            }

            let stockColumns = try db.columns(in: "stock_item").map(\.name)
            if stockColumns.contains("component_id") == false {
                try db.alter(table: "stock_item") { table in
                    table.add(column: "component_id", .text)
                        .references("component", onDelete: .setNull)
                }
            }

            try db.execute(sql: """
                INSERT OR IGNORE INTO component (
                    id, protocol_part_id, source, lcsc_part_number,
                    created_at, updated_at
                )
                SELECT 'component-' || protocol_part_id,
                       protocol_part_id,
                       'hardware_restore',
                       protocol_part_id,
                       CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP
                FROM stock_item
                WHERE protocol_part_id IS NOT NULL
                """)
            try db.execute(sql: """
                UPDATE stock_item
                SET component_id = 'component-' || protocol_part_id
                WHERE component_id IS NULL
                  AND protocol_part_id IS NOT NULL
                """)
        }
        return migrator
    }
}
