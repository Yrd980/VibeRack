import XCTest
import GRDB
@testable import VibeRack

final class ChassisRepositoryTests: XCTestCase {
    func testSimulatorSeedReturnsSmartChassisWithTwentyFiveSlotsAndFirstSlotStock() throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)

        try repository.seedSimulatorData()
        let chassis = try XCTUnwrap(try repository.fetchChassisList().first)
        let slots = try repository.fetchSlots(chassisID: chassis.id)

        XCTAssertEqual(chassis.code, "VBRK-0000")
        XCTAssertEqual(chassis.displayName, "VBRK-0000")
        XCTAssertEqual(chassis.slotCount, SmartChassisProtocol.slotCount)
        XCTAssertEqual(chassis.batchId, 1)
        XCTAssertEqual(chassis.batteryPct, 100)
        XCTAssertEqual(chassis.tableSeq, 17)
        XCTAssertEqual(slots.count, SmartChassisProtocol.slotCount)
        XCTAssertEqual(slots.map { $0.slotNumber }, Array(1...SmartChassisProtocol.slotCount))

        let firstSlot = try XCTUnwrap(slots.first)
        XCTAssertEqual(firstSlot.protocolPartId, "C1234567")
        XCTAssertEqual(firstSlot.quantity, 12)
        XCTAssertFalse(firstSlot.isEmpty)

        let secondSlot = try XCTUnwrap(slots.dropFirst().first)
        XCTAssertNil(secondSlot.protocolPartId)
        XCTAssertNil(secondSlot.quantity)
        XCTAssertTrue(secondSlot.isEmpty)
    }

    func testStockInQuantityAndClearOperationsUpdateSlotAndAppendOperationLedger() throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)

        try repository.seedSimulatorData()
        try repository.bindSlot(
            chassisID: "simulator",
            slotNumber: 2,
            protocolPartId: "R7654321",
            quantity: 30,
            source: .stockIn,
            bleOpcode: BindingOp.writeOne.code,
            bleStatus: 0
        )
        try repository.setQuantity(
            chassisID: "simulator",
            slotNumber: 2,
            quantity: 18,
            source: .manual,
            bleOpcode: BindingOp.setQuantity.code,
            bleStatus: 0
        )
        try repository.clearSlot(
            chassisID: "simulator",
            slotNumber: 2,
            source: .manual,
            bleOpcode: BindingOp.clearOne.code,
            bleStatus: 0
        )

        let slot = try XCTUnwrap(try repository.fetchSlots(chassisID: "simulator").first { $0.slotNumber == 2 })
        XCTAssertTrue(slot.isEmpty)
        XCTAssertNil(slot.protocolPartId)
        XCTAssertNil(slot.quantity)

        let operations = try repository.fetchStockOperations(chassisID: "simulator")
        XCTAssertEqual(operations.map(\.type), [.stockIn, .setQuantity, .clearSlot])
        XCTAssertEqual(operations.map(\.slotNumber), [2, 2, 2])
        XCTAssertEqual(operations.map(\.protocolPartId), ["R7654321", "R7654321", "R7654321"])
        XCTAssertEqual(operations.map(\.quantityBefore), [nil, 30, 18])
        XCTAssertEqual(operations.map(\.quantityAfter), [30, 18, nil])
        XCTAssertEqual(operations.map(\.quantityDelta), [30, -12, -18])
        XCTAssertEqual(operations.map(\.bleStatus), [0, 0, 0])
    }

    func testMigrationToleratesDevelopmentDatabaseWhereOperationTableWasCreatedByM1() throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("sqlite")
        defer { try? FileManager.default.removeItem(at: url) }

        let database = try DatabaseQueue(path: url.path)
        try database.write { db in
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
            try db.create(table: "stock_operation") { table in
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
            try db.create(table: "grdb_migrations") { table in
                table.column("identifier", .text).primaryKey()
            }
            try db.execute(
                sql: "INSERT INTO grdb_migrations (identifier) VALUES (?)",
                arguments: ["m1_ledger_schema"]
            )
        }

        let migrated = try DatabaseFactory.makeQueue(path: url.path)
        let repository = GRDBChassisRepository(database: migrated)
        try repository.seedSimulatorData()

        XCTAssertEqual(try repository.fetchChassisList().first?.code, "VBRK-0000")
    }
}
