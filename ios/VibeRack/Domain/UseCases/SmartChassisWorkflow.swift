import Foundation

public struct BindingWriteReceipt: Equatable {
    public let op: BindingOp
    public let status: BindingStatus
    public let tableInfo: TableInfo

    public init(op: BindingOp, status: BindingStatus, tableInfo: TableInfo) {
        self.op = op
        self.status = status
        self.tableInfo = tableInfo
    }
}

public struct SmartChassisDiagnosticEvent: Identifiable, Equatable {
    public enum Kind: String, Equatable {
        case binding
        case light
    }

    public let id: UUID
    public let kind: Kind
    public let opcode: UInt8?
    public let mode: LightMode?
    public let status: UInt8?
    public let tableSeq: UInt32?
    public let payloadHex: String
    public let createdAt: Date

    public init(
        id: UUID = UUID(),
        kind: Kind,
        opcode: UInt8?,
        mode: LightMode?,
        status: UInt8?,
        tableSeq: UInt32?,
        payloadHex: String,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.kind = kind
        self.opcode = opcode
        self.mode = mode
        self.status = status
        self.tableSeq = tableSeq
        self.payloadHex = payloadHex
        self.createdAt = createdAt
    }
}

public protocol SmartChassisClient {
    func writeOne(slot: Int, protocolPartId: String, quantity: Int, flags: Int) async throws -> BindingWriteReceipt
    func setQuantity(slot: Int, quantity: Int) async throws -> BindingWriteReceipt
    func clearOne(slot: Int) async throws -> BindingWriteReceipt
    func sendLightCommand(_ command: LightCommand) async throws
}

public enum SmartChassisWorkflowError: Error, Equatable {
    case invalidProtocolPartId
    case hardwareRejected(BindingStatus)
}

public final class SmartChassisWorkflow {
    private let repository: ChassisRepository
    private let client: SmartChassisClient

    public init(repository: ChassisRepository, client: SmartChassisClient) {
        self.repository = repository
        self.client = client
    }

    public func stockIn(
        chassisID: String,
        slotNumber: Int,
        protocolPartId: String,
        quantity: Int,
        componentID: String? = nil
    ) async throws {
        let normalizedPartId = try normalize(protocolPartId)
        try await client.sendLightCommand(.stockIn(slot: slotNumber))
        let receipt = try await client.writeOne(
            slot: slotNumber,
            protocolPartId: normalizedPartId,
            quantity: quantity,
            flags: 0
        )
        try ensureSuccess(receipt.status)
        try repository.bindSlot(
            chassisID: chassisID,
            slotNumber: slotNumber,
            protocolPartId: normalizedPartId,
            componentID: componentID,
            quantity: quantity,
            source: .stockIn,
            bleOpcode: receipt.op.code,
            bleStatus: receipt.status.code
        )
    }

    public func findByLight(_ result: StockSearchResult) async throws {
        try await client.sendLightCommand(result.makeFindLightCommand())
    }

    public func pickByLight(_ group: BOMPickGroup) async throws {
        try await client.sendLightCommand(group.makePickLightCommand())
    }

    public func setQuantity(chassisID: String, slotNumber: Int, quantity: Int) async throws {
        let receipt = try await client.setQuantity(slot: slotNumber, quantity: quantity)
        try ensureSuccess(receipt.status)
        try repository.setQuantity(
            chassisID: chassisID,
            slotNumber: slotNumber,
            quantity: quantity,
            source: .manual,
            bleOpcode: receipt.op.code,
            bleStatus: receipt.status.code
        )
    }

    public func clearSlot(chassisID: String, slotNumber: Int) async throws {
        let receipt = try await client.clearOne(slot: slotNumber)
        try ensureSuccess(receipt.status)
        try repository.clearSlot(
            chassisID: chassisID,
            slotNumber: slotNumber,
            source: .manual,
            bleOpcode: receipt.op.code,
            bleStatus: receipt.status.code
        )
    }

    private func normalize(_ protocolPartId: String) throws -> String {
        let normalized = protocolPartId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !normalized.isEmpty else {
            throw SmartChassisWorkflowError.invalidProtocolPartId
        }
        return normalized
    }

    private func ensureSuccess(_ status: BindingStatus) throws {
        guard status == .ok else {
            throw SmartChassisWorkflowError.hardwareRejected(status)
        }
    }
}

public final class ChassisSimulatorClient: SmartChassisClient {
    public private(set) var sentLightCommands: [LightCommand] = []
    public private(set) var bindingReceipts: [BindingWriteReceipt] = []
    public private(set) var diagnosticEvents: [SmartChassisDiagnosticEvent] = []

    public init() {}

    public func writeOne(slot: Int, protocolPartId: String, quantity: Int, flags: Int) async throws -> BindingWriteReceipt {
        let record = SmartChassisCodec.encodeSlotRecord(
            slot: slot,
            partId: protocolPartId,
            quantity: quantity,
            flags: flags
        )
        let payload = SmartChassisCodec.encodeWriteOne(record: record)
        return appendReceipt(op: .writeOne, payload: payload)
    }

    public func setQuantity(slot: Int, quantity: Int) async throws -> BindingWriteReceipt {
        let payload = SmartChassisCodec.encodeSetQuantity(slot: slot, quantity: quantity)
        return appendReceipt(op: .setQuantity, payload: payload)
    }

    public func clearOne(slot: Int) async throws -> BindingWriteReceipt {
        let payload = SmartChassisCodec.encodeClearOne(slot: slot)
        return appendReceipt(op: .clearOne, payload: payload)
    }

    public func sendLightCommand(_ command: LightCommand) async throws {
        let payload = SmartChassisCodec.encodeLightCommand(command)
        sentLightCommands.append(command)
        diagnosticEvents.append(
            SmartChassisDiagnosticEvent(
                kind: .light,
                opcode: nil,
                mode: command.mode,
                status: nil,
                tableSeq: nil,
                payloadHex: payload.hexDump
            )
        )
    }

    private func appendReceipt(op: BindingOp, payload: Data) -> BindingWriteReceipt {
        let tableSeq = UInt32((bindingReceipts.last?.tableInfo.tableSeq ?? 17) + 1)
        let receipt = BindingWriteReceipt(
            op: op,
            status: .ok,
            tableInfo: TableInfo(tableSeq: tableSeq, crc16: 0, slotCount: SmartChassisProtocol.slotCount)
        )
        bindingReceipts.append(receipt)
        diagnosticEvents.append(
            SmartChassisDiagnosticEvent(
                kind: .binding,
                opcode: op.code,
                mode: nil,
                status: receipt.status.code,
                tableSeq: receipt.tableInfo.tableSeq,
                payloadHex: payload.hexDump
            )
        )
        return receipt
    }
}

extension LightCommand {
    public static func stockIn(slot: Int) -> LightCommand {
        LightCommand(
            mode: .stockIn,
            maskA: SmartChassisCodec.slotMask(slot: slot),
            colorA: RGBColor(0, 255, 0),
            timeoutSeconds: SmartChassisProtocol.defaultLightTimeoutSeconds
        )
    }
}

public extension Data {
    var hexDump: String {
        map { String(format: "%02X", $0) }.joined(separator: " ")
    }
}
