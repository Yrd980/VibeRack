import Foundation

public struct BindingTableSnapshot: Equatable {
    public let tableInfo: TableInfo
    public let records: [Data]

    public init(tableInfo: TableInfo, records: [Data]) {
        self.tableInfo = tableInfo
        self.records = records
    }

    public var parsedRecords: [SlotRecord] {
        records.compactMap { SmartChassisCodec.parseSlotRecord($0) }
    }
}

public enum SmartChassisBindingTableError: Error, Equatable {
    case bindingStatus(BindingStatus)
    case invalidRecordPayload
    case unexpectedOperation(BindingOp)
    case recordCountMismatch(actual: Int, expected: Int)
    case tableSlotCountMismatch(actual: Int, expected: Int)
    case recordSlotMismatch(index: Int, slot: Int)
    case crcMismatch(actual: UInt16, expected: UInt16)
}

public struct BindingTableReadAllAggregator {
    private var records: [Data] = []

    public init() {}

    public mutating func append(
        _ result: BindingResult,
        tableInfo: TableInfo? = nil
    ) throws -> BindingTableSnapshot? {
        guard result.op == .readAll else {
            throw SmartChassisBindingTableError.unexpectedOperation(result.op)
        }
        guard result.status == .ok else {
            throw SmartChassisBindingTableError.bindingStatus(result.status)
        }

        if SmartChassisCodec.isReadAllEndPayload(result.payload) {
            let info = tableInfo ?? TableInfo(
                tableSeq: 0,
                crc16: SmartChassisCodec.tableCRC16(records),
                slotCount: SmartChassisProtocol.slotCount
            )
            try Self.validate(records: records, tableInfo: info)
            return BindingTableSnapshot(tableInfo: info, records: records)
        }

        guard SmartChassisCodec.parseSlotRecord(result.payload) != nil else {
            throw SmartChassisBindingTableError.invalidRecordPayload
        }
        records.append(result.payload)
        return nil
    }

    public static func validate(records: [Data], tableInfo: TableInfo) throws {
        guard tableInfo.slotCount == SmartChassisProtocol.slotCount else {
            throw SmartChassisBindingTableError.tableSlotCountMismatch(
                actual: tableInfo.slotCount,
                expected: SmartChassisProtocol.slotCount
            )
        }
        guard records.count == tableInfo.slotCount else {
            throw SmartChassisBindingTableError.recordCountMismatch(
                actual: records.count,
                expected: tableInfo.slotCount
            )
        }

        for (index, data) in records.enumerated() {
            guard let record = SmartChassisCodec.parseSlotRecord(data) else {
                throw SmartChassisBindingTableError.invalidRecordPayload
            }
            let expectedSlot = index + 1
            if record.slot != 0 && record.slot != expectedSlot {
                throw SmartChassisBindingTableError.recordSlotMismatch(
                    index: expectedSlot,
                    slot: record.slot
                )
            }
        }

        let actualCRC = SmartChassisCodec.tableCRC16(records)
        guard actualCRC == tableInfo.crc16 else {
            throw SmartChassisBindingTableError.crcMismatch(
                actual: actualCRC,
                expected: tableInfo.crc16
            )
        }
    }
}
