import XCTest
@testable import VibeRack

final class SearchRepositoryTests: XCTestCase {
    func testSearchFindsBoundStockAndBuildsFindLightCommandForSlot() throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)

        try repository.seedSimulatorData()
        try repository.bindSlot(
            chassisID: "simulator",
            slotNumber: 2,
            protocolPartId: "C7654321",
            quantity: 7,
            source: .stockIn,
            bleOpcode: BindingOp.writeOne.code,
            bleStatus: BindingStatus.ok.code
        )

        let results = try repository.searchStock(query: "c765")

        XCTAssertEqual(results.count, 1)
        let result = try XCTUnwrap(results.first)
        XCTAssertEqual(result.chassisCode, "VBRK-0000")
        XCTAssertEqual(result.slotNumber, 2)
        XCTAssertEqual(result.protocolPartId, "C7654321")
        XCTAssertEqual(result.quantity, 7)

        let command = result.makeFindLightCommand()
        XCTAssertEqual(command.mode, .find)
        XCTAssertEqual(command.maskA, SmartChassisCodec.slotMask(slot: 2))
        XCTAssertEqual(command.colorA, RGBColor(0, 255, 0))
        XCTAssertEqual(command.timeoutSeconds, SmartChassisProtocol.defaultLightTimeoutSeconds)
    }
}
