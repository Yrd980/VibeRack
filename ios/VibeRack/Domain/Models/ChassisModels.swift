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
