import Foundation

public struct BOMWorkbook: Equatable {
    public let sheetName: String
    public let headers: [String]
    public let lines: [BOMLine]

    public init(sheetName: String, headers: [String], lines: [BOMLine]) {
        self.sheetName = sheetName
        self.headers = headers
        self.lines = lines
    }
}

public struct BOMLine: Identifiable, Equatable {
    public let id: String
    public let rowNumber: Int
    public let quantity: Int
    public let comment: String?
    public let footprint: String?
    public let value: String?
    public let supplierPart: String?
    public let manufacturerPart: String?
    public let manufacturer: String?
    public let supplier: String?
    public let designator: String?

    public init(
        id: String = UUID().uuidString,
        rowNumber: Int,
        quantity: Int = 1,
        comment: String? = nil,
        footprint: String? = nil,
        value: String? = nil,
        supplierPart: String?,
        manufacturerPart: String?,
        manufacturer: String? = nil,
        supplier: String? = nil,
        designator: String?
    ) {
        self.id = id
        self.rowNumber = rowNumber
        self.quantity = quantity
        self.comment = comment.trimmedNilIfEmpty
        self.footprint = footprint.trimmedNilIfEmpty
        self.value = value.trimmedNilIfEmpty
        self.supplierPart = supplierPart.trimmedNilIfEmpty?.uppercased()
        self.manufacturerPart = manufacturerPart.trimmedNilIfEmpty
        self.manufacturer = manufacturer.trimmedNilIfEmpty
        self.supplier = supplier.trimmedNilIfEmpty
        self.designator = designator.trimmedNilIfEmpty
    }
}

public struct BOMPickPlan: Equatable {
    public let groups: [BOMPickGroup]
    public let unmatchedLines: [BOMLine]

    public var matchedLineCount: Int {
        groups.flatMap(\.targets).map(\.lineID).uniqueCount
    }

    public init(groups: [BOMPickGroup], unmatchedLines: [BOMLine]) {
        self.groups = groups
        self.unmatchedLines = unmatchedLines
    }
}

public struct BOMPickGroup: Identifiable, Equatable {
    public let id: String
    public let chassisID: String
    public let chassisCode: String
    public let chassisDisplayName: String
    public let targets: [BOMPickTarget]

    public var slotNumbers: [Int] {
        targets.map(\.slotNumber).uniqueSorted
    }

    public init(
        chassisID: String,
        chassisCode: String,
        chassisDisplayName: String,
        targets: [BOMPickTarget]
    ) {
        self.id = chassisID
        self.chassisID = chassisID
        self.chassisCode = chassisCode
        self.chassisDisplayName = chassisDisplayName
        self.targets = targets
    }

    public func makePickLightCommand() -> LightCommand {
        LightCommand(
            mode: .pick,
            maskA: slotNumbers.reduce(UInt32(0)) { mask, slot in
                mask | SmartChassisCodec.slotMask(slot: slot)
            },
            colorA: RGBColor(0, 255, 0),
            timeoutSeconds: SmartChassisProtocol.defaultLightTimeoutSeconds
        )
    }
}

public struct BOMPickTarget: Identifiable, Equatable {
    public let id: String
    public let lineID: String
    public let rowNumber: Int
    public let designator: String?
    public let protocolPartId: String
    public let slotNumber: Int
    public let quantityAvailable: Int

    public init(
        lineID: String,
        rowNumber: Int,
        designator: String?,
        protocolPartId: String,
        slotNumber: Int,
        quantityAvailable: Int
    ) {
        self.id = "\(lineID):\(slotNumber):\(protocolPartId)"
        self.lineID = lineID
        self.rowNumber = rowNumber
        self.designator = designator
        self.protocolPartId = protocolPartId
        self.slotNumber = slotNumber
        self.quantityAvailable = quantityAvailable
    }
}

private extension Sequence where Element: Hashable {
    var uniqueCount: Int {
        Set(self).count
    }
}

private extension Sequence where Element == Int {
    var uniqueSorted: [Int] {
        Array(Set(self)).sorted()
    }
}

private extension Optional where Wrapped == String {
    var trimmedNilIfEmpty: String? {
        guard let value = self?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        return value
    }
}
