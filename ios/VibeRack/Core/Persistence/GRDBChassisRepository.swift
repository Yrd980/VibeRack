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
                    id, container_id, container_slot_id, protocol_part_id, component_id,
                    quantity, flags
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    "simulator-stock-1",
                    "simulator",
                    "simulator-slot-1",
                    "C1234567",
                    try ensurePlaceholderComponent(db, protocolPartId: "C1234567"),
                    12,
                    0
                ])

            let now = Date()
            try db.execute(sql: """
                INSERT INTO component (
                    id, protocol_part_id, source, lcsc_part_number,
                    manufacturer_part_number, name, package_name, brand,
                    spec_summary, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    protocol_part_id = excluded.protocol_part_id,
                    source = excluded.source,
                    lcsc_part_number = excluded.lcsc_part_number,
                    manufacturer_part_number = excluded.manufacturer_part_number,
                    name = excluded.name,
                    package_name = excluded.package_name,
                    brand = excluded.brand,
                    spec_summary = excluded.spec_summary,
                    updated_at = excluded.updated_at
                """, arguments: [
                    "component-C2829702",
                    "C2829702",
                    "seed",
                    "C2829702",
                    "1.25-2A",
                    "1.25-2A connector",
                    "CONN-TH_1.25-2A",
                    "FG",
                    "BOM sample J2",
                    now,
                    now
                ])
            try db.execute(sql: """
                INSERT OR REPLACE INTO stock_item (
                    id, container_id, container_slot_id, protocol_part_id, component_id,
                    quantity, flags
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    "simulator-stock-bom-sample",
                    "simulator",
                    "simulator-slot-7",
                    "C2829702",
                    "component-C2829702",
                    8,
                    0
                ])
        }
    }

    public func upsertComponent(_ draft: ComponentDraft) throws -> Component {
        try database.write { db in
            let id = draft.protocolPartId.flatMap(normalizeOptionalIdentifier)
                .map { "component-\($0)" } ?? UUID().uuidString
            let now = Date()
            try db.execute(sql: """
                INSERT INTO component (
                    id, protocol_part_id, source, lcsc_part_number,
                    manufacturer_part_number, name, package_name, brand,
                    spec_summary, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    protocol_part_id = excluded.protocol_part_id,
                    source = excluded.source,
                    lcsc_part_number = excluded.lcsc_part_number,
                    manufacturer_part_number = excluded.manufacturer_part_number,
                    name = excluded.name,
                    package_name = excluded.package_name,
                    brand = excluded.brand,
                    spec_summary = excluded.spec_summary,
                    updated_at = excluded.updated_at
                """, arguments: [
                    id,
                    normalizeOptionalIdentifier(draft.protocolPartId),
                    draft.source,
                    normalizeOptionalIdentifier(draft.lcscPartNumber),
                    draft.manufacturerPartNumber?.trimmingCharacters(in: .whitespacesAndNewlines),
                    draft.name?.trimmingCharacters(in: .whitespacesAndNewlines),
                    draft.packageName?.trimmingCharacters(in: .whitespacesAndNewlines),
                    draft.brand?.trimmingCharacters(in: .whitespacesAndNewlines),
                    draft.specSummary?.trimmingCharacters(in: .whitespacesAndNewlines),
                    now,
                    now
                ])
            return try fetchComponent(db, id: id)
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

    public func searchStock(query: String) throws -> [StockSearchResult] {
        let normalizedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard normalizedQuery.isEmpty == false else {
            return []
        }

        return try database.read { db in
            try Row.fetchAll(db, sql: """
                SELECT stock.id, container.id AS chassis_id,
                       container.code AS chassis_code,
                       container.display_name AS chassis_display_name,
                       slot.id AS slot_id, slot.slot_number,
                       stock.protocol_part_id, stock.quantity, stock.flags,
                       component.id AS component_id,
                       component.protocol_part_id AS component_protocol_part_id,
                       component.source AS component_source,
                       component.lcsc_part_number,
                       component.manufacturer_part_number,
                       component.name AS component_name,
                       component.package_name,
                       component.brand,
                       component.spec_summary
                FROM stock_item stock
                JOIN container ON container.id = stock.container_id
                JOIN container_slot slot ON slot.id = stock.container_slot_id
                LEFT JOIN component ON component.id = stock.component_id
                WHERE UPPER(stock.protocol_part_id) LIKE ?
                   OR UPPER(COALESCE(component.protocol_part_id, '')) LIKE ?
                   OR UPPER(COALESCE(component.lcsc_part_number, '')) LIKE ?
                   OR UPPER(COALESCE(component.manufacturer_part_number, '')) LIKE ?
                   OR UPPER(COALESCE(component.name, '')) LIKE ?
                   OR UPPER(COALESCE(component.package_name, '')) LIKE ?
                   OR UPPER(COALESCE(component.spec_summary, '')) LIKE ?
                ORDER BY COALESCE(component.manufacturer_part_number, stock.protocol_part_id),
                         container.code, slot.slot_number
                """, arguments: StatementArguments(Array(repeating: "%\(normalizedQuery)%", count: 7))).map { row in
                StockSearchResult(
                    id: row["id"],
                    chassisID: row["chassis_id"],
                    chassisCode: row["chassis_code"],
                    chassisDisplayName: row["chassis_display_name"],
                    slotID: row["slot_id"],
                    slotNumber: row["slot_number"],
                    protocolPartId: row["protocol_part_id"],
                    quantity: row["quantity"],
                    flags: row["flags"] ?? 0,
                    component: component(from: row)
                )
            }
        }
    }

    public func bindSlot(
        chassisID: String,
        slotNumber: Int,
        protocolPartId: String,
        componentID: String?,
        quantity: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws {
        try database.write { db in
            let slot = try fetchSlotRow(db, chassisID: chassisID, slotNumber: slotNumber)
            let quantityBefore = slot.quantity
            let resolvedComponentID = try componentID ?? ensurePlaceholderComponent(db, protocolPartId: protocolPartId)
            try db.execute(sql: """
                INSERT OR REPLACE INTO stock_item (
                    id, container_id, container_slot_id, protocol_part_id, component_id,
                    quantity, flags
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arguments: [
                    slot.stockID ?? "stock-\(slot.id)",
                    chassisID,
                    slot.id,
                    protocolPartId,
                    resolvedComponentID,
                    quantity,
                    slot.flags
                ])
            try appendOperation(
                db,
                type: .stockIn,
                chassisID: chassisID,
                slotID: slot.id,
                slotNumber: slotNumber,
                protocolPartId: protocolPartId,
                quantityBefore: quantityBefore,
                quantityAfter: quantity,
                source: source,
                bleOpcode: bleOpcode,
                bleStatus: bleStatus
            )
        }
    }

    public func setQuantity(
        chassisID: String,
        slotNumber: Int,
        quantity: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws {
        try database.write { db in
            let slot = try fetchSlotRow(db, chassisID: chassisID, slotNumber: slotNumber)
            guard let stockID = slot.stockID,
                  let protocolPartId = slot.protocolPartId,
                  let quantityBefore = slot.quantity
            else {
                throw ChassisRepositoryError.slotIsEmpty(chassisID: chassisID, slotNumber: slotNumber)
            }

            try db.execute(sql: """
                UPDATE stock_item
                SET quantity = ?
                WHERE id = ?
                """, arguments: [quantity, stockID])
            try appendOperation(
                db,
                type: .setQuantity,
                chassisID: chassisID,
                slotID: slot.id,
                slotNumber: slotNumber,
                protocolPartId: protocolPartId,
                quantityBefore: quantityBefore,
                quantityAfter: quantity,
                source: source,
                bleOpcode: bleOpcode,
                bleStatus: bleStatus
            )
        }
    }

    public func clearSlot(
        chassisID: String,
        slotNumber: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws {
        try database.write { db in
            let slot = try fetchSlotRow(db, chassisID: chassisID, slotNumber: slotNumber)
            guard let stockID = slot.stockID,
                  let protocolPartId = slot.protocolPartId,
                  let quantityBefore = slot.quantity
            else {
                throw ChassisRepositoryError.slotIsEmpty(chassisID: chassisID, slotNumber: slotNumber)
            }

            try db.execute(sql: "DELETE FROM stock_item WHERE id = ?", arguments: [stockID])
            try appendOperation(
                db,
                type: .clearSlot,
                chassisID: chassisID,
                slotID: slot.id,
                slotNumber: slotNumber,
                protocolPartId: protocolPartId,
                quantityBefore: quantityBefore,
                quantityAfter: nil,
                source: source,
                bleOpcode: bleOpcode,
                bleStatus: bleStatus
            )
        }
    }

    public func restoreFromBindingTableSnapshot(
        chassisID: String,
        snapshot: BindingTableSnapshot
    ) throws {
        try BindingTableReadAllAggregator.validate(
            records: snapshot.records,
            tableInfo: snapshot.tableInfo
        )

        try database.write { db in
            try db.execute(sql: """
                UPDATE container
                SET table_seq = ?, table_crc16 = ?
                WHERE id = ?
                """, arguments: [
                    Int(snapshot.tableInfo.tableSeq),
                    Int(snapshot.tableInfo.crc16),
                    chassisID
                ])

            for (index, recordData) in snapshot.records.enumerated() {
                guard let record = SmartChassisCodec.parseSlotRecord(recordData) else {
                    throw SmartChassisBindingTableError.invalidRecordPayload
                }
                let slotNumber = record.slot == 0 ? index + 1 : record.slot
                let slot = try fetchSlotRow(db, chassisID: chassisID, slotNumber: slotNumber)
                if record.isEmpty {
                    try restoreEmptySlot(db, chassisID: chassisID, slot: slot, slotNumber: slotNumber)
                } else {
                    try restoreNonEmptySlot(
                        db,
                        chassisID: chassisID,
                        slot: slot,
                        slotNumber: slotNumber,
                        record: record
                    )
                }
            }
        }
    }

    public func fetchStockOperations(chassisID: String) throws -> [StockOperationRecord] {
        try database.read { db in
            try Row.fetchAll(db, sql: """
                SELECT id, type, container_id, slot_number, protocol_part_id,
                       quantity_before, quantity_after, quantity_delta,
                       source_type, ble_opcode, ble_status, created_at
                FROM stock_operation
                WHERE container_id = ?
                ORDER BY datetime(created_at), rowid
                """, arguments: [chassisID]).map { row in
                StockOperationRecord(
                    id: row["id"],
                    type: StockOperationType(rawValue: row["type"]) ?? .stockIn,
                    chassisID: row["container_id"],
                    slotNumber: row["slot_number"],
                    protocolPartId: row["protocol_part_id"],
                    quantityBefore: row["quantity_before"],
                    quantityAfter: row["quantity_after"],
                    quantityDelta: row["quantity_delta"],
                    source: StockOperationSource(rawValue: row["source_type"]) ?? .manual,
                    bleOpcode: (row["ble_opcode"] as Int?).map(UInt8.init),
                    bleStatus: (row["ble_status"] as Int?).map(UInt8.init),
                    createdAt: row["created_at"]
                )
            }
        }
    }

    private func restoreEmptySlot(
        _ db: Database,
        chassisID: String,
        slot: SlotRow,
        slotNumber: Int
    ) throws {
        guard let stockID = slot.stockID,
              let protocolPartId = slot.protocolPartId,
              let quantityBefore = slot.quantity
        else {
            return
        }

        try db.execute(sql: "DELETE FROM stock_item WHERE id = ?", arguments: [stockID])
        try appendOperation(
            db,
            type: .restore,
            chassisID: chassisID,
            slotID: slot.id,
            slotNumber: slotNumber,
            protocolPartId: protocolPartId,
            quantityBefore: quantityBefore,
            quantityAfter: nil,
            source: .restore,
            bleOpcode: BindingOp.readAll.code,
            bleStatus: BindingStatus.ok.code
        )
    }

    private func restoreNonEmptySlot(
        _ db: Database,
        chassisID: String,
        slot: SlotRow,
        slotNumber: Int,
        record: SlotRecord
    ) throws {
        let quantityBefore = slot.quantity
        let didChange = slot.protocolPartId != record.partId ||
            slot.quantity != record.quantity ||
            slot.flags != Int(record.flags)
        guard didChange else {
            return
        }

        try db.execute(sql: """
            INSERT OR REPLACE INTO stock_item (
                id, container_id, container_slot_id, protocol_part_id, component_id,
                quantity, flags
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, arguments: [
                slot.stockID ?? "stock-\(slot.id)",
                chassisID,
                slot.id,
                record.partId,
                try ensurePlaceholderComponent(db, protocolPartId: record.partId),
                record.quantity,
                Int(record.flags)
            ])
        try appendOperation(
            db,
            type: .restore,
            chassisID: chassisID,
            slotID: slot.id,
            slotNumber: slotNumber,
            protocolPartId: record.partId,
            quantityBefore: quantityBefore,
            quantityAfter: record.quantity,
            source: .restore,
            bleOpcode: BindingOp.readAll.code,
            bleStatus: BindingStatus.ok.code
        )
    }

    private func fetchSlotRow(_ db: Database, chassisID: String, slotNumber: Int) throws -> SlotRow {
        guard let row = try Row.fetchOne(db, sql: """
            SELECT slot.id, slot.container_id, slot.slot_number,
                   stock.id AS stock_id, stock.protocol_part_id,
                   stock.quantity, stock.flags
            FROM container_slot slot
            LEFT JOIN stock_item stock ON stock.container_slot_id = slot.id
            WHERE slot.container_id = ? AND slot.slot_number = ?
            """, arguments: [chassisID, slotNumber]) else {
            throw ChassisRepositoryError.slotNotFound(chassisID: chassisID, slotNumber: slotNumber)
        }

        return SlotRow(
            id: row["id"],
            stockID: row["stock_id"],
            protocolPartId: row["protocol_part_id"],
            quantity: row["quantity"],
            flags: row["flags"] ?? 0
        )
    }

    private func appendOperation(
        _ db: Database,
        type: StockOperationType,
        chassisID: String,
        slotID: String,
        slotNumber: Int,
        protocolPartId: String,
        quantityBefore: Int?,
        quantityAfter: Int?,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws {
        let quantityDelta = (quantityAfter ?? 0) - (quantityBefore ?? 0)
        try db.execute(sql: """
            INSERT INTO stock_operation (
                id, type, container_id, container_slot_id, slot_number,
                protocol_part_id, quantity_before, quantity_after, quantity_delta,
                source_type, ble_opcode, ble_status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, arguments: [
                UUID().uuidString,
                type.rawValue,
                chassisID,
                slotID,
                slotNumber,
                protocolPartId,
                quantityBefore,
                quantityAfter,
                quantityDelta,
                source.rawValue,
                bleOpcode,
                bleStatus,
                Date()
            ])
    }

    private func fetchComponent(_ db: Database, id: String) throws -> Component {
        guard let row = try Row.fetchOne(db, sql: """
            SELECT id, protocol_part_id, source, lcsc_part_number,
                   manufacturer_part_number, name, package_name, brand,
                   spec_summary
            FROM component
            WHERE id = ?
            """, arguments: [id]) else {
            throw ChassisRepositoryError.componentNotFound(id: id)
        }
        return component(from: row, idColumn: "id")
    }

    private func ensurePlaceholderComponent(_ db: Database, protocolPartId: String) throws -> String {
        let normalizedPartId = normalizeOptionalIdentifier(protocolPartId) ?? protocolPartId
        let id = "component-\(normalizedPartId)"
        let now = Date()
        try db.execute(sql: """
            INSERT INTO component (
                id, protocol_part_id, source, lcsc_part_number,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                protocol_part_id = excluded.protocol_part_id,
                updated_at = excluded.updated_at
            """, arguments: [
                id,
                normalizedPartId,
                "hardware_restore",
                normalizedPartId,
                now,
                now
            ])
        return id
    }

    private func component(from row: Row) -> Component? {
        guard let id: String = row["component_id"] else {
            return nil
        }
        return Component(
            id: id,
            protocolPartId: row["component_protocol_part_id"],
            source: row["component_source"],
            lcscPartNumber: row["lcsc_part_number"],
            manufacturerPartNumber: row["manufacturer_part_number"],
            name: row["component_name"],
            packageName: row["package_name"],
            brand: row["brand"],
            specSummary: row["spec_summary"]
        )
    }

    private func component(from row: Row, idColumn: String) -> Component {
        Component(
            id: row[idColumn],
            protocolPartId: row["protocol_part_id"],
            source: row["source"],
            lcscPartNumber: row["lcsc_part_number"],
            manufacturerPartNumber: row["manufacturer_part_number"],
            name: row["name"],
            packageName: row["package_name"],
            brand: row["brand"],
            specSummary: row["spec_summary"]
        )
    }

    private func normalizeOptionalIdentifier(_ value: String?) -> String? {
        let normalized = value?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return normalized?.isEmpty == false ? normalized : nil
    }
}

public enum ChassisRepositoryError: Error, Equatable {
    case slotNotFound(chassisID: String, slotNumber: Int)
    case slotIsEmpty(chassisID: String, slotNumber: Int)
    case componentNotFound(id: String)
}

private struct SlotRow {
    let id: String
    let stockID: String?
    let protocolPartId: String?
    let quantity: Int?
    let flags: Int
}
