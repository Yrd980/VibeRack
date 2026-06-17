import Foundation

public struct HardwareRestoreSlotFact: Equatable {
    public let protocolPartId: String
    public let quantity: Int
    public let flags: Int
}

public struct HardwareRestorePreviewChange: Identifiable, Equatable {
    public enum Kind: String, Equatable {
        case added
        case updated
        case cleared
    }

    public let id: String
    public let kind: Kind
    public let slotNumber: Int
    public let before: HardwareRestoreSlotFact?
    public let after: HardwareRestoreSlotFact?

    public init(
        kind: Kind,
        slotNumber: Int,
        before: HardwareRestoreSlotFact?,
        after: HardwareRestoreSlotFact?
    ) {
        self.id = "\(kind.rawValue)-\(slotNumber)"
        self.kind = kind
        self.slotNumber = slotNumber
        self.before = before
        self.after = after
    }
}

public struct HardwareRestorePreview: Equatable {
    public let tableSeq: UInt32
    public let tableCRC16: Int
    public let added: [HardwareRestorePreviewChange]
    public let updated: [HardwareRestorePreviewChange]
    public let cleared: [HardwareRestorePreviewChange]

    public var changedSlots: [HardwareRestorePreviewChange] {
        (added + updated + cleared).sorted { $0.slotNumber < $1.slotNumber }
    }

    public var changeCount: Int {
        added.count + updated.count + cleared.count
    }
}

public struct HardwareRestorePreviewBuilder {
    public init() {}

    public func buildPreview(
        localSlots: [ChassisSlotState],
        snapshot: BindingTableSnapshot
    ) throws -> HardwareRestorePreview {
        try BindingTableReadAllAggregator.validate(
            records: snapshot.records,
            tableInfo: snapshot.tableInfo
        )

        let slotsByNumber = Dictionary(uniqueKeysWithValues: localSlots.map { ($0.slotNumber, $0) })
        var added: [HardwareRestorePreviewChange] = []
        var updated: [HardwareRestorePreviewChange] = []
        var cleared: [HardwareRestorePreviewChange] = []

        for (index, recordData) in snapshot.records.enumerated() {
            guard let record = SmartChassisCodec.parseSlotRecord(recordData) else {
                throw SmartChassisBindingTableError.invalidRecordPayload
            }

            let slotNumber = record.slot == 0 ? index + 1 : record.slot
            let before = slotsByNumber[slotNumber].flatMap(Self.fact)
            let after = Self.fact(record)

            switch (before, after) {
            case (nil, nil):
                continue
            case (nil, let after?):
                added.append(change(kind: .added, slotNumber: slotNumber, before: nil, after: after))
            case (let before?, nil):
                cleared.append(change(kind: .cleared, slotNumber: slotNumber, before: before, after: nil))
            case (let before?, let after?) where before != after:
                updated.append(change(kind: .updated, slotNumber: slotNumber, before: before, after: after))
            default:
                continue
            }
        }

        return HardwareRestorePreview(
            tableSeq: snapshot.tableInfo.tableSeq,
            tableCRC16: Int(snapshot.tableInfo.crc16),
            added: added.sortedBySlot,
            updated: updated.sortedBySlot,
            cleared: cleared.sortedBySlot
        )
    }

    private func change(
        kind: HardwareRestorePreviewChange.Kind,
        slotNumber: Int,
        before: HardwareRestoreSlotFact?,
        after: HardwareRestoreSlotFact?
    ) -> HardwareRestorePreviewChange {
        HardwareRestorePreviewChange(
            kind: kind,
            slotNumber: slotNumber,
            before: before,
            after: after
        )
    }

    private static func fact(_ slot: ChassisSlotState) -> HardwareRestoreSlotFact? {
        guard let protocolPartId = slot.protocolPartId, !protocolPartId.isEmpty,
              let quantity = slot.quantity
        else {
            return nil
        }
        return HardwareRestoreSlotFact(
            protocolPartId: protocolPartId,
            quantity: quantity,
            flags: slot.flags
        )
    }

    private static func fact(_ record: SlotRecord) -> HardwareRestoreSlotFact? {
        guard !record.isEmpty else {
            return nil
        }
        return HardwareRestoreSlotFact(
            protocolPartId: record.partId,
            quantity: record.quantity,
            flags: Int(record.flags)
        )
    }
}

private extension Array where Element == HardwareRestorePreviewChange {
    var sortedBySlot: [HardwareRestorePreviewChange] {
        sorted { $0.slotNumber < $1.slotNumber }
    }
}
