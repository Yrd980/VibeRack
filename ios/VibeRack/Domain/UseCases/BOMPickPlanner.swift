import Foundation

public struct BOMPickPlanner {
    public init() {}

    public func buildPickPlan(
        lines: [BOMLine],
        stock: [StockSearchResult]
    ) -> BOMPickPlan {
        var unmatchedLines: [BOMLine] = []
        var targetsByChassis: [String: [BOMPickTargetWithChassis]] = [:]

        for line in lines {
            let matchingStock = stock.filter { stockItem in
                stockMatches(line: line, stockItem: stockItem)
            }

            if matchingStock.isEmpty {
                unmatchedLines.append(line)
                continue
            }

            for match in matchingStock where 1...SmartChassisProtocol.slotCount ~= match.slotNumber {
                let target = BOMPickTarget(
                    lineID: line.id,
                    rowNumber: line.rowNumber,
                    designator: line.designator,
                    protocolPartId: match.protocolPartId,
                    slotNumber: match.slotNumber,
                    quantityAvailable: match.quantity
                )
                let keyedTarget = BOMPickTargetWithChassis(
                    chassisID: match.chassisID,
                    chassisCode: match.chassisCode,
                    chassisDisplayName: match.chassisDisplayName,
                    target: target
                )
                targetsByChassis[match.chassisID, default: []].append(keyedTarget)
            }
        }

        let groups = targetsByChassis.values
            .compactMap(makeGroup)
            .sorted { $0.chassisCode < $1.chassisCode }
        return BOMPickPlan(groups: groups, unmatchedLines: unmatchedLines)
    }

    private func stockMatches(line: BOMLine, stockItem: StockSearchResult) -> Bool {
        let stockPart = stockItem.protocolPartId.normalizedBOMToken
        if let supplierPart = line.supplierPart?.normalizedBOMToken, !supplierPart.isEmpty {
            return stockPart == supplierPart
        }
        if let manufacturerPart = line.manufacturerPart?.normalizedBOMToken, !manufacturerPart.isEmpty {
            return stockPart == manufacturerPart
        }
        return false
    }

    private func makeGroup(_ keyedTargets: [BOMPickTargetWithChassis]) -> BOMPickGroup? {
        guard let first = keyedTargets.first else {
            return nil
        }

        let targets = keyedTargets
            .uniqueBySlotAndPartAndLine
            .sorted {
                if $0.target.slotNumber == $1.target.slotNumber {
                    return $0.target.protocolPartId < $1.target.protocolPartId
                }
                return $0.target.slotNumber < $1.target.slotNumber
            }
            .map(\.target)

        return BOMPickGroup(
            chassisID: first.chassisID,
            chassisCode: first.chassisCode,
            chassisDisplayName: first.chassisDisplayName,
            targets: targets
        )
    }
}

private struct BOMPickTargetWithChassis: Equatable {
    let chassisID: String
    let chassisCode: String
    let chassisDisplayName: String
    let target: BOMPickTarget
}

private extension String {
    var normalizedBOMToken: String {
        trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }
}

private extension Sequence where Element == BOMPickTargetWithChassis {
    var uniqueBySlotAndPartAndLine: [BOMPickTargetWithChassis] {
        var seen: Set<String> = []
        var result: [BOMPickTargetWithChassis] = []
        for item in self {
            let key = "\(item.chassisID):\(item.target.slotNumber):\(item.target.protocolPartId):\(item.target.lineID)"
            guard seen.insert(key).inserted else {
                continue
            }
            result.append(item)
        }
        return result
    }
}
