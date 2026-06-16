import XCTest
@testable import VibeRack

final class BOMPickPlannerTests: XCTestCase {
    func testBuildPickPlanGroupsMatchedSmartChassisSlotsAndBuildsPickCommands() throws {
        let planner = BOMPickPlanner()
        let rows = [
            BOMLine(rowNumber: 1, supplierPart: "c1111111", manufacturerPart: nil, designator: "R1"),
            BOMLine(rowNumber: 2, supplierPart: "C3333333", manufacturerPart: nil, designator: "C5"),
            BOMLine(rowNumber: 3, supplierPart: "C9999999", manufacturerPart: nil, designator: "U1")
        ]
        let stock = [
            StockSearchResult(
                id: "stock-1",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-1",
                slotNumber: 1,
                protocolPartId: "C1111111",
                quantity: 5,
                flags: 0
            ),
            StockSearchResult(
                id: "stock-3",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-3",
                slotNumber: 3,
                protocolPartId: "C3333333",
                quantity: 9,
                flags: 0
            ),
            StockSearchResult(
                id: "stock-4",
                chassisID: "chassis-b",
                chassisCode: "VBRK-0001",
                chassisDisplayName: "VBRK-0001",
                slotID: "slot-4",
                slotNumber: 4,
                protocolPartId: "C3333333",
                quantity: 2,
                flags: 0
            )
        ]

        let plan = planner.buildPickPlan(lines: rows, stock: stock)

        XCTAssertEqual(plan.matchedLineCount, 2)
        XCTAssertEqual(plan.unmatchedLines.map(\.supplierPart), ["C9999999"])
        XCTAssertEqual(plan.groups.map(\.chassisCode), ["VBRK-0000", "VBRK-0001"])

        let firstGroup = try XCTUnwrap(plan.groups.first)
        XCTAssertEqual(firstGroup.slotNumbers, [1, 3])
        XCTAssertEqual(firstGroup.targets.map(\.designator), ["R1", "C5"])
        XCTAssertEqual(firstGroup.makePickLightCommand().mode, .pick)
        XCTAssertEqual(
            firstGroup.makePickLightCommand().maskA,
            SmartChassisCodec.slotMask(slot: 1) | SmartChassisCodec.slotMask(slot: 3)
        )
        XCTAssertEqual(firstGroup.makePickLightCommand().colorA, RGBColor(0, 255, 0))
        XCTAssertEqual(firstGroup.makePickLightCommand().timeoutSeconds, SmartChassisProtocol.defaultLightTimeoutSeconds)

        let secondGroup = try XCTUnwrap(plan.groups.dropFirst().first)
        XCTAssertEqual(secondGroup.slotNumbers, [4])
        XCTAssertEqual(secondGroup.targets.map(\.designator), ["C5"])
        XCTAssertEqual(secondGroup.makePickLightCommand().maskA, SmartChassisCodec.slotMask(slot: 4))
    }
}
