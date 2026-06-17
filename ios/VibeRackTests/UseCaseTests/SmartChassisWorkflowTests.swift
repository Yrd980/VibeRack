import XCTest
@testable import VibeRack

final class SmartChassisWorkflowTests: XCTestCase {
    func testStockInGuidanceWritesHardwareBeforeUpdatingLedger() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = RecordingSmartChassisClient()
        let workflow = SmartChassisWorkflow(repository: repository, client: client)

        try await workflow.stockIn(
            chassisID: "simulator",
            slotNumber: 2,
            protocolPartId: "c7654321",
            quantity: 7
        )

        XCTAssertEqual(client.lightCommands.map(\.mode), [.stockIn])
        XCTAssertEqual(client.lightCommands.first?.maskA, SmartChassisCodec.slotMask(slot: 2))
        XCTAssertEqual(client.bindingCommands, [.writeOne(slot: 2, partId: "C7654321", quantity: 7)])

        let slot = try XCTUnwrap(try repository.fetchSlots(chassisID: "simulator").first { $0.slotNumber == 2 })
        XCTAssertEqual(slot.protocolPartId, "C7654321")
        XCTAssertEqual(slot.quantity, 7)

        let operation = try XCTUnwrap(try repository.fetchStockOperations(chassisID: "simulator").last)
        XCTAssertEqual(operation.type, .stockIn)
        XCTAssertEqual(operation.bleOpcode, BindingOp.writeOne.code)
        XCTAssertEqual(operation.bleStatus, BindingStatus.ok.code)
    }

    func testStockInDoesNotUpdateLedgerWhenHardwareWriteFails() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = RecordingSmartChassisClient()
        client.nextWriteError = SmartChassisWorkflowError.hardwareRejected(BindingStatus.errFlashBusy)
        let workflow = SmartChassisWorkflow(repository: repository, client: client)

        do {
            try await workflow.stockIn(
                chassisID: "simulator",
                slotNumber: 2,
                protocolPartId: "C7654321",
                quantity: 7
            )
            XCTFail("Expected hardware failure")
        } catch SmartChassisWorkflowError.hardwareRejected(.errFlashBusy) {
        }

        let slot = try XCTUnwrap(try repository.fetchSlots(chassisID: "simulator").first { $0.slotNumber == 2 })
        XCTAssertTrue(slot.isEmpty)
        XCTAssertTrue(try repository.fetchStockOperations(chassisID: "simulator").isEmpty)
    }

    func testFindByLightSendsFindCommandWithoutChangingLedger() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = RecordingSmartChassisClient()
        let workflow = SmartChassisWorkflow(repository: repository, client: client)
        let result = try XCTUnwrap(try repository.searchStock(query: "C123").first)

        try await workflow.findByLight(result)

        XCTAssertEqual(client.lightCommands.map(\.mode), [.find])
        XCTAssertEqual(client.lightCommands.first?.maskA, SmartChassisCodec.slotMask(slot: 1))
        XCTAssertTrue(try repository.fetchStockOperations(chassisID: "simulator").isEmpty)
    }

    func testPickByLightSendsPickCommandWithoutChangingLedger() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = RecordingSmartChassisClient()
        let workflow = SmartChassisWorkflow(repository: repository, client: client)
        let group = BOMPickGroup(
            chassisID: "simulator",
            chassisCode: "VBRK-0000",
            chassisDisplayName: "VBRK-0000",
            targets: [
                BOMPickTarget(
                    lineID: "line-1",
                    rowNumber: 2,
                    designator: "J2",
                    protocolPartId: "C2829702",
                    slotNumber: 7,
                    quantityAvailable: 8
                )
            ]
        )

        try await workflow.pickByLight(group)

        XCTAssertEqual(client.lightCommands.map(\.mode), [.pick])
        XCTAssertEqual(client.lightCommands.first?.maskA, SmartChassisCodec.slotMask(slot: 7))
        XCTAssertTrue(try repository.fetchStockOperations(chassisID: "simulator").isEmpty)
    }

    func testSetQuantityWritesHardwareBeforeUpdatingLedger() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = RecordingSmartChassisClient()
        let workflow = SmartChassisWorkflow(repository: repository, client: client)

        try await workflow.setQuantity(chassisID: "simulator", slotNumber: 1, quantity: 3)

        XCTAssertEqual(client.bindingCommands, [.setQuantity(slot: 1, quantity: 3)])
        let slot = try XCTUnwrap(try repository.fetchSlots(chassisID: "simulator").first { $0.slotNumber == 1 })
        XCTAssertEqual(slot.quantity, 3)
        XCTAssertEqual(try repository.fetchStockOperations(chassisID: "simulator").map(\.type), [.setQuantity])
    }
}

private final class RecordingSmartChassisClient: SmartChassisClient {
    enum BindingCommand: Equatable {
        case writeOne(slot: Int, partId: String, quantity: Int)
        case setQuantity(slot: Int, quantity: Int)
        case clearOne(slot: Int)
    }

    var lightCommands: [LightCommand] = []
    var bindingCommands: [BindingCommand] = []
    var nextWriteError: Error?

    func writeOne(slot: Int, protocolPartId: String, quantity: Int, flags: Int) async throws -> BindingWriteReceipt {
        if let nextWriteError {
            self.nextWriteError = nil
            throw nextWriteError
        }
        bindingCommands.append(.writeOne(slot: slot, partId: protocolPartId, quantity: quantity))
        return BindingWriteReceipt(op: .writeOne, status: .ok, tableInfo: TableInfo(tableSeq: 18, crc16: 0, slotCount: 25))
    }

    func setQuantity(slot: Int, quantity: Int) async throws -> BindingWriteReceipt {
        bindingCommands.append(.setQuantity(slot: slot, quantity: quantity))
        return BindingWriteReceipt(op: .setQuantity, status: .ok, tableInfo: TableInfo(tableSeq: 19, crc16: 0, slotCount: 25))
    }

    func clearOne(slot: Int) async throws -> BindingWriteReceipt {
        bindingCommands.append(.clearOne(slot: slot))
        return BindingWriteReceipt(op: .clearOne, status: .ok, tableInfo: TableInfo(tableSeq: 20, crc16: 0, slotCount: 25))
    }

    func sendLightCommand(_ command: LightCommand) async throws {
        lightCommands.append(command)
    }
}
