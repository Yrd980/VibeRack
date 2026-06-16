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
        let queue = try DatabaseQueue(path: url.path)
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
        return migrator
    }
}
