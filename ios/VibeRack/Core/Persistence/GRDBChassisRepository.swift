import Foundation
import GRDB

public final class GRDBChassisRepository: ChassisRepository {
    private let database: DatabaseQueue

    public init(database: DatabaseQueue) {
        self.database = database
    }

    public func seedSimulatorData() throws {
        try database.write { db in
            try db.execute(sql: """
                INSERT OR REPLACE INTO container (
                    id, code, display_name, type, slot_count, batch_id,
                    battery_pct, table_seq, table_crc16
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    "simulator",
                    "VBRK-0000",
                    "VBRK-0000",
                    "smart_chassis",
                    SmartChassisProtocol.slotCount,
                    1,
                    100,
                    17,
                    0xF521
                ])

            for slot in 1...SmartChassisProtocol.slotCount {
                try db.execute(sql: """
                    INSERT OR IGNORE INTO container_slot (
                        id, container_id, slot_number
                    ) VALUES (?, ?, ?)
                    """, arguments: [
                        "simulator-slot-\(slot)",
                        "simulator",
                        slot
                    ])
            }

            try db.execute(sql: """
                INSERT OR REPLACE INTO stock_item (
                    id, container_id, container_slot_id, protocol_part_id,
                    quantity, flags
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, arguments: [
                    "simulator-stock-1",
                    "simulator",
                    "simulator-slot-1",
                    "C1234567",
                    12,
                    0
                ])
        }
    }

    public func fetchChassisList() throws -> [SmartChassisSummary] {
        try database.read { db in
            try Row.fetchAll(db, sql: """
                SELECT id, code, display_name, slot_count, batch_id,
                       battery_pct, table_seq, table_crc16
                FROM container
                WHERE type = 'smart_chassis'
                ORDER BY code
                """).map { row in
                SmartChassisSummary(
                    id: row["id"],
                    code: row["code"],
                    displayName: row["display_name"],
                    slotCount: row["slot_count"],
                    batchId: row["batch_id"],
                    batteryPct: row["battery_pct"],
                    tableSeq: row["table_seq"],
                    tableCRC16: row["table_crc16"]
                )
            }
        }
    }

    public func fetchSlots(chassisID: String) throws -> [ChassisSlotState] {
        try database.read { db in
            try Row.fetchAll(db, sql: """
                SELECT slot.id, slot.container_id, slot.slot_number,
                       stock.protocol_part_id, stock.quantity, stock.flags
                FROM container_slot slot
                LEFT JOIN stock_item stock ON stock.container_slot_id = slot.id
                WHERE slot.container_id = ?
                ORDER BY slot.slot_number
                """, arguments: [chassisID]).map { row in
                ChassisSlotState(
                    id: row["id"],
                    chassisID: row["container_id"],
                    slotNumber: row["slot_number"],
                    protocolPartId: row["protocol_part_id"],
                    quantity: row["quantity"],
                    flags: row["flags"] ?? 0
                )
            }
        }
    }
}
