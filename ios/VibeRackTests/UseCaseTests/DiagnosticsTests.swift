import XCTest
@testable import VibeRack

final class DiagnosticsTests: XCTestCase {
    func testSimulatorClientRecordsBindingDiagnosticsWithOpcodeStatusTableSeqAndPayloadHex() async throws {
        let client = ChassisSimulatorClient()

        let receipt = try await client.writeOne(slot: 2, protocolPartId: "C7654321", quantity: 7, flags: 0)

        let event = try XCTUnwrap(client.diagnosticEvents.last)
        XCTAssertEqual(receipt.op, .writeOne)
        XCTAssertEqual(event.kind, .binding)
        XCTAssertEqual(event.opcode, BindingOp.writeOne.code)
        XCTAssertEqual(event.status, BindingStatus.ok.code)
        XCTAssertEqual(event.tableSeq, receipt.tableInfo.tableSeq)
        XCTAssertEqual(
            event.payloadHex,
            SmartChassisCodec.encodeWriteOne(
                record: SmartChassisCodec.encodeSlotRecord(
                    slot: 2,
                    partId: "C7654321",
                    quantity: 7,
                    flags: 0
                )
            ).hexDump
        )
    }

    func testSimulatorClientRecordsLightDiagnosticsWithPayloadHex() async throws {
        let client = ChassisSimulatorClient()
        let command = LightCommand(
            mode: .pick,
            maskA: SmartChassisCodec.slotMask(slot: 7),
            colorA: RGBColor(0, 255, 0),
            timeoutSeconds: SmartChassisProtocol.defaultLightTimeoutSeconds
        )

        try await client.sendLightCommand(command)

        let event = try XCTUnwrap(client.diagnosticEvents.last)
        XCTAssertEqual(event.kind, .light)
        XCTAssertEqual(event.mode, .pick)
        XCTAssertNil(event.opcode)
        XCTAssertNil(event.status)
        XCTAssertNil(event.tableSeq)
        XCTAssertEqual(event.payloadHex, SmartChassisCodec.encodeLightCommand(command).hexDump)
    }

    func testWorkflowOperationsCanBePresentedAlongsideDiagnostics() async throws {
        let database = try DatabaseFactory.makeInMemoryQueue()
        let repository = GRDBChassisRepository(database: database)
        try repository.seedSimulatorData()
        let client = ChassisSimulatorClient()
        let workflow = SmartChassisWorkflow(repository: repository, client: client)

        try await workflow.setQuantity(chassisID: "simulator", slotNumber: 1, quantity: 3)

        let operation = try XCTUnwrap(try repository.fetchStockOperations(chassisID: "simulator").last)
        let event = try XCTUnwrap(client.diagnosticEvents.last)
        XCTAssertEqual(operation.bleOpcode, event.opcode)
        XCTAssertEqual(operation.bleStatus, event.status)
        XCTAssertEqual(event.tableSeq, 18)
        XCTAssertEqual(event.payloadHex, SmartChassisCodec.encodeSetQuantity(slot: 1, quantity: 3).hexDump)
    }
}
