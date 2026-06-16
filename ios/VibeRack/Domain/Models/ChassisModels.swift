import Foundation

public struct SmartChassisSummary: Identifiable, Equatable {
    public let id: String
    public let code: String
    public let displayName: String
    public let slotCount: Int
    public let batchId: Int?
    public let batteryPct: Int?
    public let tableSeq: Int?
    public let tableCRC16: Int?

    public init(
        id: String,
        code: String,
        displayName: String,
        slotCount: Int,
        batchId: Int?,
        batteryPct: Int?,
        tableSeq: Int?,
        tableCRC16: Int?
    ) {
        self.id = id
        self.code = code
        self.displayName = displayName
        self.slotCount = slotCount
        self.batchId = batchId
        self.batteryPct = batteryPct
        self.tableSeq = tableSeq
        self.tableCRC16 = tableCRC16
    }
}

public struct ChassisSlotState: Identifiable, Equatable {
    public let id: String
    public let chassisID: String
    public let slotNumber: Int
    public let protocolPartId: String?
    public let quantity: Int?
    public let flags: Int

    public var isEmpty: Bool {
        protocolPartId?.isEmpty != false
    }

    public init(
        id: String,
        chassisID: String,
        slotNumber: Int,
        protocolPartId: String?,
        quantity: Int?,
        flags: Int
    ) {
        self.id = id
        self.chassisID = chassisID
        self.slotNumber = slotNumber
        self.protocolPartId = protocolPartId
        self.quantity = quantity
        self.flags = flags
    }
}

public struct StockSearchResult: Identifiable, Equatable {
    public let id: String
    public let chassisID: String
    public let chassisCode: String
    public let chassisDisplayName: String
    public let slotID: String
    public let slotNumber: Int
    public let protocolPartId: String
    public let quantity: Int
    public let flags: Int

    public init(
        id: String,
        chassisID: String,
        chassisCode: String,
        chassisDisplayName: String,
        slotID: String,
        slotNumber: Int,
        protocolPartId: String,
        quantity: Int,
        flags: Int
    ) {
        self.id = id
        self.chassisID = chassisID
        self.chassisCode = chassisCode
        self.chassisDisplayName = chassisDisplayName
        self.slotID = slotID
        self.slotNumber = slotNumber
        self.protocolPartId = protocolPartId
        self.quantity = quantity
        self.flags = flags
    }

    public func makeFindLightCommand() -> LightCommand {
        LightCommand(
            mode: .find,
            maskA: SmartChassisCodec.slotMask(slot: slotNumber),
            colorA: RGBColor(0, 255, 0),
            timeoutSeconds: SmartChassisProtocol.defaultLightTimeoutSeconds
        )
    }
}

public enum StockOperationType: String, Equatable {
    case stockIn = "stock_in"
    case setQuantity = "set_quantity"
    case clearSlot = "clear_slot"
    case restore = "restore"
}

public enum StockOperationSource: String, Equatable {
    case stockIn = "stock_in"
    case manual = "manual"
    case restore = "restore"
}

public struct StockOperationRecord: Identifiable, Equatable {
    public let id: String
    public let type: StockOperationType
    public let chassisID: String
    public let slotNumber: Int
    public let protocolPartId: String
    public let quantityBefore: Int?
    public let quantityAfter: Int?
    public let quantityDelta: Int
    public let source: StockOperationSource
    public let bleOpcode: UInt8?
    public let bleStatus: UInt8?
    public let createdAt: Date

    public init(
        id: String,
        type: StockOperationType,
        chassisID: String,
        slotNumber: Int,
        protocolPartId: String,
        quantityBefore: Int?,
        quantityAfter: Int?,
        quantityDelta: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?,
        createdAt: Date
    ) {
        self.id = id
        self.type = type
        self.chassisID = chassisID
        self.slotNumber = slotNumber
        self.protocolPartId = protocolPartId
        self.quantityBefore = quantityBefore
        self.quantityAfter = quantityAfter
        self.quantityDelta = quantityDelta
        self.source = source
        self.bleOpcode = bleOpcode
        self.bleStatus = bleStatus
        self.createdAt = createdAt
    }
}
