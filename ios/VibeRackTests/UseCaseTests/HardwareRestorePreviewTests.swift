import XCTest
@testable import VibeRack

final class HardwareRestorePreviewTests: XCTestCase {
    func testBuildPreviewListsAddedUpdatedAndClearedSlots() throws {
        let localSlots = [
            slot(1, partId: "C1111111", quantity: 12),
            slot(2, partId: "R2222222", quantity: 4),
            slot(3, partId: nil, quantity: nil),
            slot(7, partId: "C2829702", quantity: 8)
        ] + (4...SmartChassisProtocol.slotCount)
            .filter { $0 != 7 }
            .map { slot($0, partId: nil, quantity: nil) }

        let snapshot = makeSnapshot([
            1: ("C1111111", 5, 0),
            3: ("C3333333", 9, 1)
        ])

        let preview = try HardwareRestorePreviewBuilder().buildPreview(
            localSlots: localSlots,
            snapshot: snapshot
        )

        XCTAssertEqual(preview.tableSeq, 42)
        XCTAssertEqual(preview.tableCRC16, Int(snapshot.tableInfo.crc16))
        XCTAssertEqual(preview.added.map(\.slotNumber), [3])
        XCTAssertEqual(preview.updated.map(\.slotNumber), [1])
        XCTAssertEqual(preview.cleared.map(\.slotNumber), [2, 7])
        XCTAssertEqual(preview.changedSlots.map(\.slotNumber), [1, 2, 3, 7])

        let added = try XCTUnwrap(preview.added.first)
        XCTAssertEqual(added.after?.protocolPartId, "C3333333")
        XCTAssertEqual(added.after?.quantity, 9)

        let updated = try XCTUnwrap(preview.updated.first)
        XCTAssertEqual(updated.before?.quantity, 12)
        XCTAssertEqual(updated.after?.quantity, 5)

        let cleared = try XCTUnwrap(preview.cleared.first)
        XCTAssertEqual(cleared.before?.protocolPartId, "R2222222")
        XCTAssertNil(cleared.after)
    }

    func testBuildPreviewRejectsCrcMismatch() throws {
        let snapshot = BindingTableSnapshot(
            tableInfo: TableInfo(
                tableSeq: 42,
                crc16: 0xFFFF,
                slotCount: SmartChassisProtocol.slotCount
            ),
            records: makeRecords([1: ("C1111111", 5, 0)])
        )

        XCTAssertThrowsError(
            try HardwareRestorePreviewBuilder().buildPreview(
                localSlots: [slot(1, partId: nil, quantity: nil)],
                snapshot: snapshot
            )
        ) { error in
            XCTAssertEqual(
                error as? SmartChassisBindingTableError,
                SmartChassisBindingTableError.crcMismatch(
                    actual: SmartChassisCodec.tableCRC16(snapshot.records),
                    expected: 0xFFFF
                )
            )
        }
    }

    private func slot(_ slotNumber: Int, partId: String?, quantity: Int?, flags: Int = 0) -> ChassisSlotState {
        ChassisSlotState(
            id: "slot-\(slotNumber)",
            chassisID: "simulator",
            slotNumber: slotNumber,
            protocolPartId: partId,
            quantity: quantity,
            flags: flags
        )
    }

    private func makeSnapshot(_ nonEmptySlots: [Int: (partId: String, quantity: Int, flags: UInt8)]) -> BindingTableSnapshot {
        let records = makeRecords(nonEmptySlots)
        return BindingTableSnapshot(
            tableInfo: TableInfo(
                tableSeq: 42,
                crc16: SmartChassisCodec.tableCRC16(records),
                slotCount: SmartChassisProtocol.slotCount
            ),
            records: records
        )
    }

    private func makeRecords(_ nonEmptySlots: [Int: (partId: String, quantity: Int, flags: UInt8)]) -> [Data] {
        (1...SmartChassisProtocol.slotCount).map { slotNumber in
            guard let value = nonEmptySlots[slotNumber] else {
                return Data(repeating: 0, count: SmartChassisProtocol.slotRecordSize)
            }
            return SmartChassisCodec.encodeSlotRecord(
                slot: slotNumber,
                partId: value.partId,
                quantity: value.quantity,
                flags: Int(value.flags)
            )
        }
    }
}
