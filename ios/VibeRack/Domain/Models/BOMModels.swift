import Foundation

public struct BOMLine: Identifiable, Equatable {
    public let id: String
    public let rowNumber: Int
    public let supplierPart: String?
    public let manufacturerPart: String?
    public let designator: String?

    public init(
        id: String = UUID().uuidString,
        rowNumber: Int,
        supplierPart: String?,
        manufacturerPart: String?,
        designator: String?
    ) {
        self.id = id
        self.rowNumber = rowNumber
        self.supplierPart = supplierPart?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        self.manufacturerPart = manufacturerPart?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        self.designator = designator?.trimmingCharacters(in: .whitespacesAndNewlines)
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
