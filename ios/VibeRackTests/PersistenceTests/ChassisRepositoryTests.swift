import XCTest
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
}
