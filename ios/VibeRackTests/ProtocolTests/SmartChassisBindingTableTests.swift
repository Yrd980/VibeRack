import XCTest
@testable import VibeRack

final class SmartChassisBindingTableTests: XCTestCase {
    func testReadAllAggregatorReturnsSnapshotAfterEndFrameAndMatchingTableInfo() throws {
        let records = makeSequentialRecords()
        let tableInfo = makeTableInfo(for: records, tableSeq: 17)
        var aggregator = BindingTableReadAllAggregator()

        for record in records {
            XCTAssertNil(try aggregator.append(.readAllRecord(record)))
        }

        let snapshot = try XCTUnwrap(try aggregator.append(.readAllEnd, tableInfo: tableInfo))

        XCTAssertEqual(snapshot.tableInfo, tableInfo)
        XCTAssertEqual(snapshot.records, records)
    }

    func testReadAllAggregatorRejectsMissingRecordsAtEndFrame() throws {
        var aggregator = BindingTableReadAllAggregator()
        let records = Array(makeSequentialRecords().prefix(24))
        let tableInfo = makeTableInfo(for: records, slotCount: SmartChassisProtocol.slotCount)

        for record in records {
            _ = try aggregator.append(.readAllRecord(record))
        }

        XCTAssertThrowsError(try aggregator.append(.readAllEnd, tableInfo: tableInfo)) { error in
            XCTAssertEqual(error as? SmartChassisBindingTableError, .recordCountMismatch(actual: 24, expected: 25))
        }
    }

    func testReadAllAggregatorRejectsOutOfOrderSlotRecords() throws {
        var records = makeSequentialRecords()
        records[6] = SmartChassisCodec.encodeSlotRecord(slot: 8, partId: "C1000008", quantity: 8, flags: 0)
        let tableInfo = makeTableInfo(for: records)
        var aggregator = BindingTableReadAllAggregator()

        for record in records {
            _ = try aggregator.append(.readAllRecord(record))
        }

        XCTAssertThrowsError(try aggregator.append(.readAllEnd, tableInfo: tableInfo)) { error in
            XCTAssertEqual(error as? SmartChassisBindingTableError, .recordSlotMismatch(index: 7, slot: 8))
        }
    }

    func testReadAllAggregatorRejectsCrcMismatch() throws {
        let records = makeSequentialRecords()
        let tableInfo = TableInfo(tableSeq: 18, crc16: 0x1234, slotCount: SmartChassisProtocol.slotCount)
        var aggregator = BindingTableReadAllAggregator()

        for record in records {
            _ = try aggregator.append(.readAllRecord(record))
        }

        XCTAssertThrowsError(try aggregator.append(.readAllEnd, tableInfo: tableInfo)) { error in
            XCTAssertEqual(error as? SmartChassisBindingTableError, .crcMismatch(actual: SmartChassisCodec.tableCRC16(records), expected: 0x1234))
        }
    }

    func testReadAllAggregatorRejectsBindingErrorStatus() {
        var aggregator = BindingTableReadAllAggregator()

        XCTAssertThrowsError(try aggregator.append(.bindingFailure(.errFlashBusy))) { error in
            XCTAssertEqual(error as? SmartChassisBindingTableError, .bindingStatus(.errFlashBusy))
        }
    }
}

private func makeSequentialRecords() -> [Data] {
    (1...SmartChassisProtocol.slotCount).map { slot in
        SmartChassisCodec.encodeSlotRecord(
            slot: slot,
            partId: String(format: "C%07d", slot),
            quantity: slot,
            flags: 0
        )
    }
}

private func makeTableInfo(
    for records: [Data],
    tableSeq: UInt32 = 1,
    slotCount: Int = SmartChassisProtocol.slotCount
) -> TableInfo {
    TableInfo(tableSeq: tableSeq, crc16: SmartChassisCodec.tableCRC16(records), slotCount: slotCount)
}

private extension BindingResult {
    static func readAllRecord(_ record: Data) -> BindingResult {
        BindingResult(
            op: .readAll,
            rawOp: BindingOp.readAll.code,
            status: .ok,
            rawStatus: BindingStatus.ok.code,
            payload: record
        )
    }

    static var readAllEnd: BindingResult {
        BindingResult(
            op: .readAll,
            rawOp: BindingOp.readAll.code,
            status: .ok,
            rawStatus: BindingStatus.ok.code,
            payload: Data([SmartChassisProtocol.readAllEndMarker])
        )
    }

    static func bindingFailure(_ status: BindingStatus) -> BindingResult {
        BindingResult(
            op: .readAll,
            rawOp: BindingOp.readAll.code,
            status: status,
            rawStatus: status.code,
            payload: Data()
        )
    }
}
