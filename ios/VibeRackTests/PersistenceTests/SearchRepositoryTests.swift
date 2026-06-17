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
            componentID: nil,
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

    func testSearchFindsComponentRichFieldsForBoundStock() throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)

        try repository.seedSimulatorData()
        let component = try repository.upsertComponent(
            ComponentDraft(
                protocolPartId: "C2829702",
                source: "lcsc",
                lcscPartNumber: "C2829702",
                manufacturerPartNumber: "STM32G030F6P6",
                name: "MCU ARM Cortex-M0+",
                packageName: "TSSOP-20",
                brand: "STMicroelectronics",
                specSummary: "32-bit MCU 32KB Flash"
            )
        )
        try repository.bindSlot(
            chassisID: "simulator",
            slotNumber: 3,
            protocolPartId: "C2829702",
            componentID: component.id,
            quantity: 5,
            source: .stockIn,
            bleOpcode: BindingOp.writeOne.code,
            bleStatus: BindingStatus.ok.code
        )

        let mpnResult = try XCTUnwrap(try repository.searchStock(query: "g030f6").first)
        XCTAssertEqual(mpnResult.component?.manufacturerPartNumber, "STM32G030F6P6")
        XCTAssertEqual(mpnResult.displayPartNumber, "STM32G030F6P6")

        let nameResult = try XCTUnwrap(try repository.searchStock(query: "cortex").first)
        XCTAssertEqual(nameResult.component?.name, "MCU ARM Cortex-M0+")

        let packageResult = try XCTUnwrap(try repository.searchStock(query: "tssop").first)
        XCTAssertEqual(packageResult.slotNumber, 3)

        let specResult = try XCTUnwrap(try repository.searchStock(query: "32kb").first)
        XCTAssertEqual(specResult.protocolPartId, "C2829702")
    }
}
