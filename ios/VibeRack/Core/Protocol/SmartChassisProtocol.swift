import Foundation

public enum SmartChassisProtocol {
    public static let protocolVersion: UInt8 = 0x01
    public static let slotCount = 25
    public static let slotRecordSize = 16
    public static let lightCommandSize = 17
    public static let tableInfoSize = 7
    public static let readAllEndMarker: UInt8 = 0xFF
    public static let devCompanyId: UInt16 = 0xFFFF
    public static let factoryResetMagic: UInt32 = 0x5A5AA5A5
    public static let advertisementCoreSize = 9
    public static let advertisementFirmwareSize = 11
    public static let androidManufacturerPayloadCoreSize = 7
    public static let androidManufacturerPayloadFirmwareSize = 9
    public static let slotMaskMax: UInt32 = 0x01FF_FFFF
    public static let defaultLightTimeoutSeconds = 30
    public static let maxLightTimeoutSeconds = 300
    public static let maxFxTimeoutSeconds = 10

    public static let advertisementLowBatteryFlag: UInt8 = 1 << 0
    public static let advertisementHasUnboundSlotFlag: UInt8 = 1 << 1
    public static let advertisementLightActiveFlag: UInt8 = 1 << 2
    public static let advertisementFaultFlag: UInt8 = 1 << 3

    public static let slotFlagMSD: UInt8 = 1 << 0
    public static let slotFlagLowStock: UInt8 = 1 << 1
    public static let slotFlagCustomPart: UInt8 = 1 << 2
}

public enum BindingOp: Equatable {
    case readOne
    case readAll
    case writeOne
    case clearOne
    case insertAt
    case removeAt
    case moveBlock
    case setQuantity
    case factoryReset
    case unknown(UInt8)

    public init(rawValue: UInt8) {
        switch rawValue {
        case 0x01: self = .readOne
        case 0x02: self = .readAll
        case 0x10: self = .writeOne
        case 0x11: self = .clearOne
        case 0x20: self = .insertAt
        case 0x21: self = .removeAt
        case 0x22: self = .moveBlock
        case 0x30: self = .setQuantity
        case 0xF0: self = .factoryReset
        default: self = .unknown(rawValue)
        }
    }

    public var code: UInt8 {
        switch self {
        case .readOne: return 0x01
        case .readAll: return 0x02
        case .writeOne: return 0x10
        case .clearOne: return 0x11
        case .insertAt: return 0x20
        case .removeAt: return 0x21
        case .moveBlock: return 0x22
        case .setQuantity: return 0x30
        case .factoryReset: return 0xF0
        case let .unknown(rawValue): return rawValue
        }
    }
}

public enum BindingStatus: Equatable {
    case ok
    case errParam
    case errFull
    case errFlashBusy
    case errCRC
    case unknown(UInt8)

    public init(rawValue: UInt8) {
        switch rawValue {
        case 0x00: self = .ok
        case 0x01: self = .errParam
        case 0x02: self = .errFull
        case 0x03: self = .errFlashBusy
        case 0x04: self = .errCRC
        default: self = .unknown(rawValue)
        }
    }

    public var code: UInt8 {
        switch self {
        case .ok: return 0x00
        case .errParam: return 0x01
        case .errFull: return 0x02
        case .errFlashBusy: return 0x03
        case .errCRC: return 0x04
        case let .unknown(rawValue): return rawValue
        }
    }
}

public enum LightMode: Equatable {
    case off
    case find
    case pick
    case sort
    case stockIn
    case fx
    case unknown(UInt8)

    public init(rawValue: UInt8) {
        switch rawValue {
        case 0x00: self = .off
        case 0x01: self = .find
        case 0x02: self = .pick
        case 0x03: self = .sort
        case 0x04: self = .stockIn
        case 0x05: self = .fx
        default: self = .unknown(rawValue)
        }
    }

    public var code: UInt8 {
        switch self {
        case .off: return 0x00
        case .find: return 0x01
        case .pick: return 0x02
        case .sort: return 0x03
        case .stockIn: return 0x04
        case .fx: return 0x05
        case let .unknown(rawValue): return rawValue
        }
    }
}

public struct SmartChassisAdvertisement: Equatable {
    public let companyId: UInt16
    public let protoVersion: UInt8
    public let batchId: UInt16
    public let batteryPct: UInt8
    public let statusFlags: UInt8
    public let tableSeqLow16: UInt16

    public init(
        companyId: UInt16,
        protoVersion: UInt8,
        batchId: UInt16,
        batteryPct: UInt8,
        statusFlags: UInt8,
        tableSeqLow16: UInt16
    ) {
        self.companyId = companyId
        self.protoVersion = protoVersion
        self.batchId = batchId
        self.batteryPct = batteryPct
        self.statusFlags = statusFlags
        self.tableSeqLow16 = tableSeqLow16
    }
}

public struct SlotRecord: Equatable {
    public let slot: Int
    public let partId: String
    public let quantity: Int
    public let flags: UInt8
    public let crc8: UInt8

    public var isEmpty: Bool {
        slot == 0 || partId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public init(slot: Int, partId: String, quantity: Int, flags: UInt8, crc8: UInt8) {
        self.slot = slot
        self.partId = partId
        self.quantity = quantity
        self.flags = flags
        self.crc8 = crc8
    }
}

public struct BindingResult: Equatable {
    public let op: BindingOp
    public let rawOp: UInt8
    public let status: BindingStatus
    public let rawStatus: UInt8
    public let payload: Data

    public init(op: BindingOp, rawOp: UInt8, status: BindingStatus, rawStatus: UInt8, payload: Data) {
        self.op = op
        self.rawOp = rawOp
        self.status = status
        self.rawStatus = rawStatus
        self.payload = payload
    }
}

public struct TableInfo: Equatable {
    public let tableSeq: UInt32
    public let crc16: UInt16
    public let slotCount: Int

    public init(tableSeq: UInt32, crc16: UInt16, slotCount: Int) {
        self.tableSeq = tableSeq
        self.crc16 = crc16
        self.slotCount = slotCount
    }
}

public struct RGBColor: Equatable {
    public let red: UInt8
    public let green: UInt8
    public let blue: UInt8

    public init(red: UInt8, green: UInt8, blue: UInt8) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    public init(_ red: UInt8, _ green: UInt8, _ blue: UInt8) {
        self.init(red: red, green: green, blue: blue)
    }
}

public struct LightCommand: Equatable {
    public let mode: LightMode
    public let maskA: UInt32
    public let maskB: UInt32
    public let colorA: RGBColor
    public let colorB: RGBColor
    public let timeoutSeconds: Int

    public init(
        mode: LightMode,
        maskA: UInt32,
        maskB: UInt32 = 0,
        colorA: RGBColor,
        colorB: RGBColor = RGBColor(0, 0, 0),
        timeoutSeconds: Int = 0
    ) {
        self.mode = mode
        self.maskA = maskA
        self.maskB = maskB
        self.colorA = colorA
        self.colorB = colorB
        self.timeoutSeconds = timeoutSeconds
    }
}

public struct LightStatus: Equatable {
    public let mode: LightMode
    public let rawMode: UInt8
    public let remainingSeconds: Int

    public init(mode: LightMode, rawMode: UInt8, remainingSeconds: Int) {
        self.mode = mode
        self.rawMode = rawMode
        self.remainingSeconds = remainingSeconds
    }
}

public struct DeviceHealth: Equatable {
    public let batteryPct: UInt8
    public let resetReason: UInt16
    public let healthFlags: UInt8

    public init(batteryPct: UInt8, resetReason: UInt16, healthFlags: UInt8) {
        self.batteryPct = batteryPct
        self.resetReason = resetReason
        self.healthFlags = healthFlags
    }
}
